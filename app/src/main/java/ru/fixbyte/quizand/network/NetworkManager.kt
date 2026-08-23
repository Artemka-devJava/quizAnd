package ru.fixbyte.quizand.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import ru.fixbyte.quizand.models.*
import java.io.*
import java.net.*
import java.util.*
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

/** Единый тег для всех логов сетевого слоя — фильтруйте Logcat именно по нему. */
private const val TAG = "YaZnayuNetwork"

enum class NetworkMode {
    IDLE, HOST, CLIENT
}

enum class ConnectionStatus {
    DISCONNECTED, CONNECTING, CONNECTED, FAILED
}

class PeerConnection(
    val id: String = UUID.randomUUID().toString(),
    val socket: Socket
) {
    var playerInfo: PlayerInfo? = null
    var inputStream: InputStream? = null
    var outputStream: OutputStream? = null
}

/**
 * Сетевой слой Android-клиента игры "Я знаю".
 *
 * Обнаружение хостов реализовано через JmDNS — Java-реализацию mDNS/DNS-SD
 * (тот же протокол, RFC 6762/6763, что использует Apple Bonjour через
 * Network.framework на iOS). Раньше здесь был перебор всех адресов подсети
 * прямыми TCP-подключениями — это не позволяло iOS увидеть Android-хост
 * вообще (он ничего не анонсировал в сети) и делало поиск на Android
 * медленным и ненадёжным.
 */
class NetworkManager(
    private val appContext: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())
) {
    companion object {
        const val DEFAULT_PORT = 5000
        // JmDNS ожидает домен ".local." в типе сервиса — тот же формат,
        // в котором Bonjour регистрирует сервисы на iOS (NWListener с domain: nil).
        const val SERVICE_TYPE = "_yaznayu._tcp.local."
    }

    private var _mode = NetworkMode.IDLE
    private var _status = ConnectionStatus.DISCONNECTED
    private val _discoveredServers = mutableMapOf<String, DiscoveredServer>()

    var mode: NetworkMode
        get() = _mode
        set(value) { _mode = value }

    var status: ConnectionStatus
        get() = _status
        set(value) { _status = value }

    val discoveredServers: List<DiscoveredServer>
        get() = _discoveredServers.values.toList()

    var onEvent: ((NetworkEvent) -> Unit)? = null

    /** Вызывается при любом изменении списка найденных хостов — для реактивного обновления UI. */
    var onServersChanged: (() -> Unit)? = null

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private val peers = mutableMapOf<String, PeerConnection>()

    private var jmdns: JmDNS? = null
    private var registeredServiceInfo: ServiceInfo? = null
    private var serviceListener: ServiceListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    // encodeDefaults = true — критично для совместимости с iOS: без этого kotlinx.serialization
    // может пропускать поля со значением по умолчанию (например, sentAt) в исходящем JSON,
    // а Swift Codable на iOS требует ВСЕ non-optional поля присутствующими явно — иначе decode
    // падает с keyNotFound, даже если поле там формально не нужно смыслово.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ——— ХОСТ: TCP-сервер + анонс в mDNS/Bonjour ———

    fun startServer(port: Int = DEFAULT_PORT, serviceName: String) {
        Log.d(TAG, "startServer(port=$port, name=$serviceName) вызван")
        scope.launch {
            try {
                stopAll()
                mode = NetworkMode.HOST
                status = ConnectionStatus.CONNECTING

                val trimmedName = serviceName.trim().ifEmpty { "Host" }

                val socket = withContext(Dispatchers.IO) { ServerSocket(port) }
                serverSocket = socket
                Log.d(TAG, "ServerSocket поднят на порту $port")

                withContext(Dispatchers.IO) { registerBonjourService(trimmedName, port) }

                status = ConnectionStatus.CONNECTED

                while (mode == NetworkMode.HOST && serverSocket != null) {
                    try {
                        val clientConnection = withContext(Dispatchers.IO) { socket.accept() }
                        Log.d(TAG, "Принято TCP-подключение от ${clientConnection.inetAddress?.hostAddress}")
                        acceptNewPeer(clientConnection)
                    } catch (e: SocketException) {
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "startServer упал с исключением", e)
                status = ConnectionStatus.FAILED
            }
        }
    }

    /** Должен вызываться на фоновом потоке — блокирующие сетевые операции JmDNS. */
    private fun registerBonjourService(serviceName: String, port: Int) {
        try {
            val localAddress = findLocalIPv4Address() ?: run {
                Log.e(TAG, "registerBonjourService: не найден локальный IPv4-адрес — Bonjour-анонс отменён")
                return
            }
            Log.d(TAG, "registerBonjourService: локальный адрес ${localAddress.hostAddress}")
            acquireMulticastLock()
            Log.d(TAG, "MulticastLock: held=${multicastLock?.isHeld}")

            // ВАЖНО: адрес передаём ЯВНО. JmDNS.create() без аргументов на Android ненадёжен —
            // внутри он может скатиться на InetAddress.getLocalHost(), который на Android часто
            // возвращает 127.0.0.1 (loopback), поскольку у устройства нет настоящего DNS-хостнейма.
            // В этом случае JmDNS слушает и рассылает multicast с loopback — сервис становится
            // невидимым для остальных устройств в сети, и сам он тоже никого не видит.
            val instance = jmdns ?: JmDNS.create(localAddress, null).also { jmdns = it }
            Log.d(TAG, "JmDNS создан, hostName=${instance.name}")
            val info = ServiceInfo.create(SERVICE_TYPE, serviceName, port, "")
            instance.registerService(info)
            registeredServiceInfo = info
            Log.d(TAG, "registerService(\"$SERVICE_TYPE\", \"$serviceName\", port=$port) успешно вызван")
        } catch (e: IOException) {
            // Bonjour-анонс не удался (например, нет доступа к локальной сети) —
            // сервер всё равно поднят и доступен по прямому IP, просто не будет виден в автопоиске.
            Log.e(TAG, "registerBonjourService упал с IOException", e)
        }
    }

    // ——— ИГРОК: поиск хостов через mDNS/Bonjour ———

    fun startBrowsingServers() {
        Log.d(TAG, "startBrowsingServers() вызван")
        stopBrowsingServers()

        scope.launch {
            try {
                val localAddress = withContext(Dispatchers.IO) { findLocalIPv4Address() } ?: run {
                    Log.e(TAG, "Не найден локальный IPv4-адрес (нет Wi-Fi?) — поиск отменён")
                    status = ConnectionStatus.FAILED
                    return@launch
                }
                Log.d(TAG, "Локальный адрес для JmDNS: ${localAddress.hostAddress}")

                withContext(Dispatchers.IO) { acquireMulticastLock() }
                Log.d(TAG, "MulticastLock: held=${multicastLock?.isHeld}")

                val instance = jmdns ?: withContext(Dispatchers.IO) {
                    JmDNS.create(localAddress, null)
                }.also { jmdns = it }
                Log.d(TAG, "JmDNS создан/переиспользован, hostName=${instance.name}")

                val listener = object : ServiceListener {
                    override fun serviceAdded(event: ServiceEvent) {
                        Log.d(TAG, "serviceAdded: type=${event.type} name=${event.name} — запрашиваю подробности")
                        // Полная информация (IP/порт) придёт асинхронно в serviceResolved.
                        instance.requestServiceInfo(event.type, event.name, 3000)
                    }

                    override fun serviceRemoved(event: ServiceEvent) {
                        Log.d(TAG, "serviceRemoved: name=${event.name}")
                        _discoveredServers.remove(event.name)
                        onServersChanged?.invoke()
                    }

                    override fun serviceResolved(event: ServiceEvent) {
                        val info = event.info
                        val address = info.hostAddresses.firstOrNull()
                        Log.d(TAG, "serviceResolved: name=${event.name} address=$address port=${info.port}")
                        if (address == null) return
                        _discoveredServers[event.name] = DiscoveredServer(
                            id = event.name,
                            name = event.name,
                            ipAddress = address,
                            port = info.port
                        )
                        onServersChanged?.invoke()
                    }
                }

                serviceListener = listener
                withContext(Dispatchers.IO) {
                    instance.addServiceListener(SERVICE_TYPE, listener)
                }
                Log.d(TAG, "addServiceListener(\"$SERVICE_TYPE\") зарегистрирован, ждём ответов...")
            } catch (e: IOException) {
                Log.e(TAG, "Ошибка при запуске поиска", e)
                status = ConnectionStatus.FAILED
            }
        }
    }

    fun stopBrowsingServers() {
        val instance = jmdns
        val listener = serviceListener
        if (instance != null && listener != null) {
            scope.launch(Dispatchers.IO) {
                instance.removeServiceListener(SERVICE_TYPE, listener)
            }
        }
        serviceListener = null
        _discoveredServers.clear()
    }

    /**
     * Ищет локальный IPv4-адрес для привязки JmDNS. Явно предпочитает Wi-Fi интерфейс
     * (обычно "wlan0" на Android) и пропускает VPN/мобильные интерфейсы (tun/ppp/rmnet) —
     * на устройствах с несколькими одновременно активными интерфейсами (например, Wi-Fi + VPN,
     * или Wi-Fi + мобильные данные) простой перебор "первого попавшегося" адреса мог выбрать
     * не тот интерфейс, из-за чего multicast-трафик не доходил до реальной Wi-Fi сети.
     */
    private fun findLocalIPv4Address(): InetAddress? {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return null

        val wifiAddress = interfaces
            .firstOrNull { iface ->
                iface.isUp && !iface.isLoopback && iface.name.startsWith("wlan")
            }
            ?.inetAddresses?.toList()
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }

        if (wifiAddress != null) return wifiAddress

        for (iface in interfaces) {
            if (iface.isLoopback || !iface.isUp) continue

            val name = iface.name.lowercase()
            if (name.startsWith("tun") || name.startsWith("ppp") ||
                name.startsWith("rmnet") || name.startsWith("p2p")
            ) continue

            for (address in iface.inetAddresses) {
                if (address is Inet4Address && !address.isLoopbackAddress) {
                    return address
                }
            }
        }
        return null
    }

    /** Без MulticastLock Android по умолчанию фильтрует входящие multicast-пакеты Wi-Fi,
     *  и mDNS-объявления (в обе стороны) до приложения просто не доходят. */
    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        val wifiManager = appContext.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: run {
            Log.e(TAG, "acquireMulticastLock: WifiManager недоступен (null)")
            return
        }
        val lock = wifiManager.createMulticastLock("yaznayuMulticastLock")
        lock.setReferenceCounted(true)
        lock.acquire()
        multicastLock = lock
        Log.d(TAG, "MulticastLock захвачен: held=${lock.isHeld}")
    }

    private fun releaseMulticastLock() {
        multicastLock?.let { if (it.isHeld) it.release() }
        multicastLock = null
    }

    // ——— Подключение к хосту напрямую по IP (адрес получен из mDNS-резолва) ———

    fun connectToServer(ip: String, port: Int = DEFAULT_PORT) {
        Log.d(TAG, "connectToServer(ip=$ip, port=$port) вызван")
        scope.launch {
            try {
                stopClientOnly()
                mode = NetworkMode.CLIENT
                status = ConnectionStatus.CONNECTING

                val socket = withContext(Dispatchers.IO) { Socket(ip, port) }
                clientSocket = socket
                status = ConnectionStatus.CONNECTED
                Log.d(TAG, "TCP-соединение установлено с $ip:$port")

                val peer = PeerConnection(socket = socket)
                peer.inputStream = socket.getInputStream()
                peer.outputStream = socket.getOutputStream()
                startReceiveLoop(peer, isClient = true)
            } catch (e: Exception) {
                Log.e(TAG, "connectToServer($ip:$port) упал с исключением", e)
                status = ConnectionStatus.FAILED
            }
        }
    }

    fun send(message: GameMessage, toPlayerID: String? = null) {
        scope.launch(Dispatchers.IO) {
            try {
                val jsonString = json.encodeToString(message)
                val data = (jsonString + "\n").toByteArray()

                when (mode) {
                    NetworkMode.HOST -> {
                        if (toPlayerID != null) {
                            peers[toPlayerID]?.let {
                                it.outputStream?.write(data)
                                it.outputStream?.flush()
                            }
                        } else {
                            peers.forEach { (_, peer) ->
                                try {
                                    peer.outputStream?.write(data)
                                    peer.outputStream?.flush()
                                } catch (e: Exception) {
                                    // Peer might be disconnected
                                }
                            }
                        }
                    }
                    NetworkMode.CLIENT -> {
                        clientSocket?.getOutputStream()?.write(data)
                        clientSocket?.getOutputStream()?.flush()
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                status = ConnectionStatus.FAILED
            }
        }
    }

    fun stopAll() {
        stopBrowsingServers()

        registeredServiceInfo?.let { info ->
            jmdns?.let { instance ->
                scope.launch(Dispatchers.IO) {
                    try { instance.unregisterService(info) } catch (e: Exception) { }
                }
            }
        }
        registeredServiceInfo = null

        jmdns?.let { instance ->
            scope.launch(Dispatchers.IO) {
                try { instance.close() } catch (e: IOException) { }
            }
        }
        jmdns = null
        releaseMulticastLock()

        serverSocket?.close()
        serverSocket = null

        clientSocket?.close()
        clientSocket = null

        peers.values.forEach { it.socket.close() }
        peers.clear()

        mode = NetworkMode.IDLE
        status = ConnectionStatus.DISCONNECTED
    }

    private suspend fun acceptNewPeer(socket: Socket) {
        val peer = PeerConnection(socket = socket)
        peer.inputStream = socket.getInputStream()
        peer.outputStream = socket.getOutputStream()
        peers[peer.id] = peer

        startReceiveLoop(peer, isClient = false)
    }

    private fun startReceiveLoop(peer: PeerConnection, isClient: Boolean) {
        scope.launch(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(peer.inputStream))
                while (isActive) {
                    val line = reader.readLine() ?: break
                    if (line.isNotEmpty()) {
                        try {
                            val message = json.decodeFromString<GameMessage>(line)
                            Log.d(TAG, "Получено сообщение: kind=${message.kind} sender=${message.senderNickname} player=${message.player?.nickname}")

                            if (message.kind == MessageKind.HELLO.toString() && message.player != null) {
                                peer.playerInfo = message.player
                                withContext(Dispatchers.Main) {
                                    onEvent?.invoke(NetworkEvent.PlayerConnected(message.player!!))
                                }
                            }

                            withContext(Dispatchers.Main) {
                                onEvent?.invoke(NetworkEvent.Message(message))
                            }
                        } catch (e: Exception) {
                            // Печатаем сырую строку и полную ошибку в Logcat — этого достаточно,
                            // чтобы сразу увидеть, какое именно поле/формат не совпадает с iOS.
                            println("——— [Decode] Не удалось декодировать GameMessage ———")
                            println("——— [Decode] Сырые данные: $line")
                            println("——— [Decode] Ошибка: ${e::class.simpleName}: ${e.message}")
                            println("———————————————————————————————————————————")
                        }
                    }
                }
            } catch (e: Exception) {
                if (isClient) {
                    // Переподключение управляется на уровне ViewModel/UI при необходимости.
                } else {
                    peers.remove(peer.id)
                    peer.playerInfo?.let {
                        withContext(Dispatchers.Main) {
                            onEvent?.invoke(NetworkEvent.PlayerDisconnected(it))
                        }
                    }
                }
            }
        }
    }

    private fun stopClientOnly() {
        clientSocket?.close()
        clientSocket = null
    }
}