package ru.fixbyte.quizand.network

import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import ru.fixbyte.quizand.models.*
import java.io.*
import java.net.*
import java.util.*
import kotlin.collections.mutableMapOf

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
    var buffer = ByteArrayOutputStream()
    var playerInfo: PlayerInfo? = null
    var inputStream: InputStream? = null
    var outputStream: OutputStream? = null
}

class NetworkManager(private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())) {
    companion object {
        const val DEFAULT_PORT = 5000
        const val SERVICE_TYPE = "_yaznayu._tcp"
    }

    private var _mode = NetworkMode.IDLE
    private var _status = ConnectionStatus.DISCONNECTED
    private var _discoveredServers = mutableListOf<DiscoveredServer>()

    var mode: NetworkMode
        get() = _mode
        set(value) { _mode = value }

    var status: ConnectionStatus
        get() = _status
        set(value) { _status = value }

    val discoveredServers: List<DiscoveredServer>
        get() = _discoveredServers.toList()

    var onEvent: ((NetworkEvent) -> Unit)? = null

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private val peers = mutableMapOf<String, PeerConnection>()
    private var browsingJob: Job? = null
    private var currentServerPort = DEFAULT_PORT
    private var currentServiceName = "Host"

    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun startServer(port: Int = DEFAULT_PORT, serviceName: String) {
        scope.launch {
            try {
                stopAll()
                mode = NetworkMode.HOST
                status = ConnectionStatus.CONNECTING

                currentServerPort = port
                currentServiceName = serviceName.trim().ifEmpty { "Host" }

                serverSocket = ServerSocket(port.toInt()).apply {
                    status = ConnectionStatus.CONNECTED
                }

                while (mode == NetworkMode.HOST && serverSocket != null) {
                    try {
                        val clientConnection = serverSocket?.accept()
                        if (clientConnection != null) {
                            acceptNewPeer(clientConnection)
                        }
                    } catch (e: SocketException) {
                        if (mode == NetworkMode.HOST) {
                            delay(2000)
                            startServer(port, serviceName)
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                status = ConnectionStatus.FAILED
                delay(2000)
                if (mode == NetworkMode.HOST) {
                    startServer(port, serviceName)
                }
            }
        }
    }

    fun startBrowsingServers() {
        browsingJob?.cancel()
        browsingJob = scope.launch(Dispatchers.Default) {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces().toList()

            for (networkInterface in networkInterfaces) {
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                for (address in networkInterface.interfaceAddresses) {
                    if (address.address !is Inet4Address) continue

                    val ip = address.address.hostAddress
                    if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                        // Scan ports
                        scanNetworkRange(ip, DEFAULT_PORT)
                    }
                }
            }
        }
    }

    private fun scanNetworkRange(baseIp: String, port: Int) {
        val parts = baseIp.split(".")
        if (parts.size != 4) return

        val base = parts.take(3).joinToString(".")
        val servers = mutableListOf<DiscoveredServer>()

        for (i in 1..254) {
            val ip = "$base.$i"
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(ip, port), 500)
                socket.close()

                servers.add(
                    DiscoveredServer(
                        id = "$ip:$port",
                        name = "Host",
                        ipAddress = ip,
                        port = port
                    )
                )
            } catch (e: Exception) {
                // Host not available
            }
        }

        _discoveredServers = servers
    }

    fun connectToServer(ip: String, port: Int = DEFAULT_PORT) {
        scope.launch {
            try {
                stopClientOnly()
                mode = NetworkMode.CLIENT
                status = ConnectionStatus.CONNECTING

                clientSocket = Socket(ip, port).apply {
                    status = ConnectionStatus.CONNECTED
                    val peer = PeerConnection(socket = this)
                    peer.inputStream = getInputStream()
                    peer.outputStream = getOutputStream()
                    startReceiveLoop(peer, isClient = true)
                }
            } catch (e: Exception) {
                status = ConnectionStatus.FAILED
                delay(2000)
                connectToServer(ip, port)
            }
        }
    }

    fun send(message: GameMessage, toPlayerID: String? = null) {
        scope.launch {
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
        browsingJob?.cancel()

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
                            // JSON parsing error
                            println("JSON parsing error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                if (isClient) {
                    delay(2000)
                    // Reconnect logic
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

