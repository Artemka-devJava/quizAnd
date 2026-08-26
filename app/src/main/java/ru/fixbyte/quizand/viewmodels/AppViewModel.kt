package ru.fixbyte.quizand.viewmodels

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import ru.fixbyte.quizand.models.*
import ru.fixbyte.quizand.network.NetworkManager
import java.util.UUID

private const val TAG = "YaZnayuNetwork"
private const val PREFS_NAME = "yaznayu_prefs"
private const val KEY_PLAYER_ID = "player_id"

class AppViewModel(application: Application) : AndroidViewModel(application) {
    // UI State
    private val _phase = MutableStateFlow(AppPhase.SPLASH)
    val phase: StateFlow<AppPhase> = _phase

    private val _selectedRole = MutableStateFlow<UserRole?>(null)
    val selectedRole: StateFlow<UserRole?> = _selectedRole

    /** Собственный IP-адрес хоста в текущей Wi-Fi-сети — показывается игрокам
     *  для ручного подключения/QR как запасной вариант, если у них не сработает автопоиск. */
    private val _hostLocalIp = MutableStateFlow<String?>(null)
    val hostLocalIp: StateFlow<String?> = _hostLocalIp

    // Host Settings
    private val _hostNickname = MutableStateFlow("Ведущий")
    val hostNickname: StateFlow<String> = _hostNickname

    /** Порт, на котором реально поднялся сервер (один из NetworkManager.CANDIDATE_PORTS) —
     *  заполняется после успешного startServer(), а не выбирается пользователем. */
    private val _hostBoundPort = MutableStateFlow<Int?>(null)
    val hostBoundPort: StateFlow<Int?> = _hostBoundPort

    // Player Settings
    private val _playerNickname = MutableStateFlow("")
    val playerNickname: StateFlow<String> = _playerNickname

    private val _selectedServerID = MutableStateFlow<String?>(null)
    val selectedServerID: StateFlow<String?> = _selectedServerID

    // Game State
    private val _players = MutableStateFlow<List<PlayerInfo>>(emptyList())
    val players: StateFlow<List<PlayerInfo>> = _players

    private val _connectionHint = MutableStateFlow("")
    val connectionHint: StateFlow<String> = _connectionHint

    // Round State
    private val _roundIsOpen = MutableStateFlow(false)
    val roundIsOpen: StateFlow<Boolean> = _roundIsOpen

    private val _activeResponder = MutableStateFlow<PlayerInfo?>(null)
    val activeResponder: StateFlow<PlayerInfo?> = _activeResponder

    private val _buzzHistory = MutableStateFlow<List<PlayerInfo>>(emptyList())
    val buzzHistory: StateFlow<List<PlayerInfo>> = _buzzHistory

    private val _lastResult = MutableStateFlow<AnswerResultPayload?>(null)
    val lastResult: StateFlow<AnswerResultPayload?> = _lastResult

    private val _scores = MutableStateFlow<Map<String, Int>>(emptyMap())
    val scores: StateFlow<Map<String, Int>> = _scores

    private val _localHasAttemptedInRound = MutableStateFlow(false)
    val localHasAttemptedInRound: StateFlow<Boolean> = _localHasAttemptedInRound

    private val _localIsCurrentResponder = MutableStateFlow(false)
    val localIsCurrentResponder: StateFlow<Boolean> = _localIsCurrentResponder

    private val _discoveredServers = MutableStateFlow<List<DiscoveredServer>>(emptyList())
    val discoveredServers: StateFlow<List<DiscoveredServer>> = _discoveredServers

    /** Персистентный ID этого устройства как игрока — раньше генерировался заново при
     *  каждом запуске приложения, из-за чего обрыв связи в середине игры и повторное
     *  подключение показывали игрока как нового участника с нулевым счётом (хост хранит
     *  счёт по id игрока и никогда не обнуляет уже существующую запись при переподключении —
     *  единственным недостающим звеном был именно непостоянный id на стороне игрока). */
    private val localPlayerID: String = run {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_PLAYER_ID, null) ?: UUID.randomUUID().toString().also { newID ->
            prefs.edit().putString(KEY_PLAYER_ID, newID).apply()
        }
    }
    private val network = NetworkManager(getApplication(), viewModelScope)
    private var attemptedPlayerIDsInRound = mutableSetOf<String>()

    init {
        _phase.value = AppPhase.SPLASH
        bootSplash()

        network.onEvent = { event ->
            handleNetworkEvent(event)
        }

        // Игрок с уже известным (например, ранее подключавшимся в этой игре) id
        // не отклоняется правилом "игра уже началась" — это не новый игрок, а
        // переподключение после обрыва связи, и счёт по этому id уже сохранён.
        network.isKnownPlayerId = { id -> _scores.value.containsKey(id) }

        // Реактивно обновляем список хостов по мере того, как JmDNS резолвит найденные сервисы,
        // а не только один раз после фиксированной задержки.
        network.onServersChanged = {
            _discoveredServers.value = network.discoveredServers
        }
    }

    private fun bootSplash() {
        viewModelScope.launch {
            delay(2000)
            _phase.value = AppPhase.ROLE_SELECTION
        }
    }

    fun chooseRole(role: UserRole) {
        _selectedRole.value = role
        _phase.value = when (role) {
            UserRole.HOST -> AppPhase.HOST_LOBBY
            UserRole.PLAYER -> AppPhase.PLAYER_JOIN
        }

        if (role == UserRole.PLAYER) {
            // Не спрашиваем, роутер это или хотспот: всегда пробуем автопоиск (mDNS +
            // скан подсети) в фоне, а ручной ввод IP/QR остаётся рядом как запасной
            // вариант на случай, если автопоиск в этой сети не работает.
            refreshServerDiscovery()
            _connectionHint.value = "Поиск ведущих в локальной сети..."
        }
    }

    fun resetToRoleSelection() {
        network.stopAll()
        _players.value = emptyList()
        _discoveredServers.value = emptyList()
        _selectedServerID.value = null
        _selectedRole.value = null
        _connectionHint.value = ""
        _hostLocalIp.value = null

        _roundIsOpen.value = false
        _activeResponder.value = null
        _buzzHistory.value = emptyList()
        _lastResult.value = null
        _scores.value = emptyMap()

        _localHasAttemptedInRound.value = false
        _localIsCurrentResponder.value = false
        attemptedPlayerIDsInRound.clear()

        _phase.value = AppPhase.ROLE_SELECTION
    }

    fun startHosting() {
        network.startServer(_hostNickname.value) { boundPort ->
            if (boundPort != null) {
                _hostBoundPort.value = boundPort
                _connectionHint.value = "Сервер \"${_hostNickname.value}\" запущен на порту $boundPort"
            } else {
                _hostBoundPort.value = null
                _connectionHint.value = "Не удалось запустить сервер — все порты заняты"
            }
        }
        viewModelScope.launch {
            _hostLocalIp.value = network.getLocalIPv4Address()
        }
    }

    fun refreshServerDiscovery() {
        _discoveredServers.value = emptyList()
        network.startBrowsingServers()
        network.startSubnetScan()
        // Список будет обновляться реактивно через network.onServersChanged по мере
        // резолва JmDNS и по мере нахождения открытых портов при переборе подсети.
    }

    fun startGameAsHost() {
        if (_players.value.isEmpty()) {
            Log.d(TAG, "startGameAsHost: отменено, _players.value пуст в момент тапа")
            _connectionHint.value = "Нужен хотя бы 1 подключенный игрок"
            return
        }

        Log.d(TAG, "startGameAsHost: отправка gameStarted, игроков=${_players.value.size}")
        _phase.value = AppPhase.HOST_CONTROL
        // С этого момента новые подключения отклоняются — нельзя зайти посреди игры.
        network.gameInProgress = true

        viewModelScope.launch {
            val msg = GameMessage(
                kind = MessageKind.GAME_STARTED.toString(),
                senderID = localPlayerID,
                senderNickname = _hostNickname.value
            )
            network.send(msg)
            Log.d(TAG, "startGameAsHost: gameStarted отправлен network.send()")
        }
    }

    fun openRoundAsHost() {
        _roundIsOpen.value = true
        _activeResponder.value = null
        _buzzHistory.value = emptyList()
        _lastResult.value = null

        _localHasAttemptedInRound.value = false
        _localIsCurrentResponder.value = false
        attemptedPlayerIDsInRound.clear()

        viewModelScope.launch {
            val msg = GameMessage(
                kind = MessageKind.ROUND_OPENED.toString(),
                senderID = localPlayerID,
                senderNickname = _hostNickname.value
            )
            network.send(msg)
        }
    }

    fun closeRoundAsHost() {
        _roundIsOpen.value = false
        _activeResponder.value = null

        _localIsCurrentResponder.value = false
        attemptedPlayerIDsInRound.clear()

        viewModelScope.launch {
            val msg = GameMessage(
                kind = MessageKind.ROUND_CLOSED.toString(),
                senderID = localPlayerID,
                senderNickname = _hostNickname.value
            )
            network.send(msg)
        }
    }

    fun judgeCurrentResponder(isCorrect: Boolean) {
        val responder = _activeResponder.value ?: return
        if (_selectedRole.value != UserRole.HOST) return

        if (isCorrect) {
            val newScore = (_scores.value[responder.id] ?: 0) + 1
            val newScores = _scores.value.toMutableMap()
            newScores[responder.id] = newScore
            _scores.value = newScores
            _roundIsOpen.value = false

            val result = AnswerResultPayload(
                playerID = responder.id,
                isCorrect = true,
                awardedPoints = 1
            )
            _lastResult.value = result

            viewModelScope.launch {
                val msg = GameMessage(
                    kind = MessageKind.ANSWER_RESULT.toString(),
                    senderID = localPlayerID,
                    senderNickname = _hostNickname.value,
                    player = responder,
                    answerResult = result,
                    scoreValue = newScore,
                    text = "Верный ответ!"
                )
                network.send(msg)

                val closeMsg = GameMessage(
                    kind = MessageKind.ROUND_CLOSED.toString(),
                    senderID = localPlayerID,
                    senderNickname = _hostNickname.value
                )
                network.send(closeMsg)
            }

            _activeResponder.value = null
            _localIsCurrentResponder.value = false
            attemptedPlayerIDsInRound.clear()
        } else {
            val result = AnswerResultPayload(
                playerID = responder.id,
                isCorrect = false,
                awardedPoints = 0
            )
            _lastResult.value = result
            _activeResponder.value = null
            _localIsCurrentResponder.value = false

            viewModelScope.launch {
                val resultMsg = GameMessage(
                    kind = MessageKind.ANSWER_RESULT.toString(),
                    senderID = localPlayerID,
                    senderNickname = _hostNickname.value,
                    player = responder,
                    answerResult = result,
                    text = "Неверный ответ!"
                )
                network.send(resultMsg)

                val clearMsg = GameMessage(
                    kind = MessageKind.RESPONDER_CLEARED.toString(),
                    senderID = localPlayerID,
                    senderNickname = _hostNickname.value
                )
                network.send(clearMsg)
            }
        }
    }

    /** Обнуляет очки всех игроков (включая ранее известные id отключившихся игроков —
     *  чтобы переподключение после сброса не выглядело новым игроком), не разрывая
     *  соединения и не покидая лобби — в отличие от resetToRoleSelection(). */
    fun resetScoresAsHost() {
        if (_selectedRole.value != UserRole.HOST) return

        _scores.value = _scores.value.keys.associateWith { 0 }

        _roundIsOpen.value = false
        _activeResponder.value = null
        _buzzHistory.value = emptyList()
        _lastResult.value = null
        _localHasAttemptedInRound.value = false
        _localIsCurrentResponder.value = false
        attemptedPlayerIDsInRound.clear()

        viewModelScope.launch {
            val msg = GameMessage(
                kind = MessageKind.SCORES_RESET.toString(),
                senderID = localPlayerID,
                senderNickname = _hostNickname.value
            )
            network.send(msg)
        }
    }

    fun connectAsPlayer() {
        Log.d(TAG, "connectAsPlayer() вызван, nickname='${_playerNickname.value}', selectedServerID=${_selectedServerID.value}")
        val nickname = _playerNickname.value.trim()
        if (nickname.isEmpty()) {
            Log.d(TAG, "connectAsPlayer: отменено — пустой ник")
            _connectionHint.value = "Введите ник"
            return
        }

        val selectedServer = _selectedServerID.value
        if (selectedServer == null) {
            Log.d(TAG, "connectAsPlayer: отменено — сервер не выбран")
            _connectionHint.value = "Выберите сервер из списка"
            return
        }

        val server = _discoveredServers.value.find { it.id == selectedServer }
        if (server == null) {
            Log.d(TAG, "connectAsPlayer: отменено — id=$selectedServer нет среди ${_discoveredServers.value.map { it.id }}")
            _connectionHint.value = "Сервер не найден"
            return
        }

        connectToHostAndJoin(server.ipAddress, server.port, server.name)
    }

    /**
     * Подключение вручную по IP:порт — резервный путь для сетей, где mDNS/multicast
     * не доходит между устройствами (например, Wi-Fi-хотспот с телефона: сам хотспот
     * обычно не пробрасывает multicast-трафик от AP к подключённому клиенту).
     */
    fun connectAsPlayerManual(hostText: String) {
        val nickname = _playerNickname.value.trim()
        if (nickname.isEmpty()) {
            _connectionHint.value = "Введите ник"
            return
        }

        val trimmed = hostText.trim()
        if (trimmed.isEmpty()) {
            _connectionHint.value = "Введите IP-адрес хоста"
            return
        }

        val parts = trimmed.split(":")
        val ip = parts[0].trim()
        val port = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: NetworkManager.CANDIDATE_PORTS.first()
        if (ip.isEmpty()) {
            _connectionHint.value = "Неверный формат. Пример: 192.168.1.5:5001"
            return
        }

        connectToHostAndJoin(ip, port, ip)
    }

    private fun connectToHostAndJoin(ip: String, port: Int, displayName: String) {
        val nickname = _playerNickname.value.trim()
        Log.d(TAG, "connectToHostAndJoin: подключаемся к $ip:$port")
        viewModelScope.launch {
            network.connectToServer(ip, port)
            delay(500)

            val me = PlayerInfo(id = localPlayerID, nickname = nickname)
            val hello = GameMessage(
                kind = MessageKind.HELLO.toString(),
                senderID = localPlayerID,
                senderNickname = nickname,
                player = me
            )
            network.send(hello)

            _connectionHint.value = "Подключение к $displayName"
            _phase.value = AppPhase.PLAYER_WAITING
        }
    }

    fun playerPressedAnswerButton() {
        if (_selectedRole.value != UserRole.PLAYER) return
        if (!_roundIsOpen.value || _activeResponder.value != null || _localHasAttemptedInRound.value) return

        _localHasAttemptedInRound.value = true

        val me = PlayerInfo(id = localPlayerID, nickname = _playerNickname.value)
        viewModelScope.launch {
            val msg = GameMessage(
                kind = MessageKind.BUZZ.toString(),
                senderID = localPlayerID,
                senderNickname = _playerNickname.value,
                player = me
            )
            network.send(msg)
        }
    }

    fun onHostNicknameChanged(value: String) {
        _hostNickname.value = value
    }

    fun onPlayerNicknameChanged(value: String) {
        _playerNickname.value = value
    }

    fun onServerSelected(serverId: String) {
        _selectedServerID.value = serverId
    }

    fun scoreFor(playerID: String): Int {
        return _scores.value[playerID] ?: 0
    }

    private fun handleNetworkEvent(event: NetworkEvent) {
        when (event) {
            is NetworkEvent.PlayerConnected -> {
                val newPlayers = _players.value.toMutableList()
                if (!newPlayers.any { it.id == event.player.id }) {
                    newPlayers.add(event.player)
                }
                _players.value = newPlayers

                if (_scores.value[event.player.id] == null) {
                    val newScores = _scores.value.toMutableMap()
                    newScores[event.player.id] = 0
                    _scores.value = newScores
                }

                broadcastPlayersIfHost()
            }

            is NetworkEvent.PlayerDisconnected -> {
                val newPlayers = _players.value.filter { it.id != event.player.id }
                _players.value = newPlayers
                attemptedPlayerIDsInRound.remove(event.player.id)

                if (_activeResponder.value?.id == event.player.id) {
                    _activeResponder.value = null
                    _localIsCurrentResponder.value = false
                }
                broadcastPlayersIfHost()
            }

            is NetworkEvent.HostConnectionLost -> {
                // Хост вышел из игры/закрыл приложение, или сеть легла — соединение
                // разорвано не по инициативе игрока. Автоматически возвращаем его
                // на экран поиска, а не оставляем висеть в ожидании ответа хоста.
                if (_selectedRole.value == UserRole.PLAYER) {
                    returnPlayerToJoinScreen("Ведущий покинул игру")
                }
            }

            is NetworkEvent.Message -> {
                val msg = event.message
                when (msg.kind) {
                    MessageKind.GAME_STARTED.toString() -> {
                        if (_selectedRole.value == UserRole.PLAYER) {
                            _phase.value = AppPhase.PLAYER_QUESTION
                            _connectionHint.value = "Игра начинается. Жди открытия раунда"
                        }
                    }

                    MessageKind.ROUND_OPENED.toString() -> {
                        _roundIsOpen.value = true
                        _activeResponder.value = null
                        _lastResult.value = null
                        _localHasAttemptedInRound.value = false
                        _localIsCurrentResponder.value = false
                        attemptedPlayerIDsInRound.clear()
                        if (_selectedRole.value == UserRole.PLAYER) {
                            _phase.value = AppPhase.PLAYER_QUESTION
                        }
                    }

                    MessageKind.BUZZ.toString() -> {
                        if (_selectedRole.value == UserRole.HOST &&
                            _roundIsOpen.value &&
                            _activeResponder.value == null &&
                            msg.player != null &&
                            !attemptedPlayerIDsInRound.contains(msg.player!!.id)
                        ) {
                            attemptedPlayerIDsInRound.add(msg.player!!.id)
                            _activeResponder.value = msg.player
                            val history = _buzzHistory.value.toMutableList()
                            history.add(msg.player!!)
                            _buzzHistory.value = history

                            viewModelScope.launch {
                                val selectMsg = GameMessage(
                                    kind = MessageKind.RESPONDER_SELECTED.toString(),
                                    senderID = localPlayerID,
                                    senderNickname = _hostNickname.value,
                                    player = msg.player,
                                    text = "Отвечает ${msg.player!!.nickname}"
                                )
                                network.send(selectMsg)
                            }
                        }
                    }

                    MessageKind.RESPONDER_SELECTED.toString() -> {
                        _activeResponder.value = msg.player
                        _localIsCurrentResponder.value = (msg.player?.id == localPlayerID)
                    }

                    MessageKind.RESPONDER_CLEARED.toString() -> {
                        _activeResponder.value = null
                        _localIsCurrentResponder.value = false
                    }

                    MessageKind.ROUND_CLOSED.toString() -> {
                        _roundIsOpen.value = false
                        _activeResponder.value = null
                        _localIsCurrentResponder.value = false
                        attemptedPlayerIDsInRound.clear()
                    }

                    MessageKind.ANSWER_RESULT.toString() -> {
                        _lastResult.value = msg.answerResult
                        if (msg.player != null &&
                            msg.scoreValue != null &&
                            msg.answerResult?.isCorrect == true
                        ) {
                            val newScores = _scores.value.toMutableMap()
                            newScores[msg.player!!.id] = msg.scoreValue!!
                            _scores.value = newScores
                        }
                    }

                    MessageKind.SCORES_RESET.toString() -> {
                        _scores.value = _scores.value.keys.associateWith { 0 }
                        _roundIsOpen.value = false
                        _activeResponder.value = null
                        _buzzHistory.value = emptyList()
                        _lastResult.value = null
                        _localHasAttemptedInRound.value = false
                        _localIsCurrentResponder.value = false
                        attemptedPlayerIDsInRound.clear()
                        if (_selectedRole.value == UserRole.PLAYER) {
                            _connectionHint.value = "Ведущий сбросил счёт"
                        }
                    }

                    MessageKind.PLAYER_LIST.toString() -> {
                        _players.value = msg.players ?: emptyList()
                        msg.players?.forEach { player ->
                            if (_scores.value[player.id] == null) {
                                val newScores = _scores.value.toMutableMap()
                                newScores[player.id] = 0
                                _scores.value = newScores
                            }
                        }
                    }

                    MessageKind.ERROR.toString() -> {
                        if (_selectedRole.value == UserRole.PLAYER && _phase.value != AppPhase.PLAYER_QUESTION) {
                            returnPlayerToJoinScreen(msg.text ?: "Ошибка сети")
                        } else {
                            _connectionHint.value = msg.text ?: "Ошибка сети"
                        }
                    }

                    else -> {}
                }
            }
        }
    }

    /** Возвращает игрока на экран поиска хоста, сбрасывая состояние игры/раунда —
     *  используется и при явной ошибке от сервера (ник занят и т.п.), и при потере
     *  соединения с хостом. */
    private fun returnPlayerToJoinScreen(hint: String) {
        network.stopAll()
        _selectedServerID.value = null
        _players.value = emptyList()

        _roundIsOpen.value = false
        _activeResponder.value = null
        _buzzHistory.value = emptyList()
        _lastResult.value = null
        _localHasAttemptedInRound.value = false
        _localIsCurrentResponder.value = false
        attemptedPlayerIDsInRound.clear()

        _phase.value = AppPhase.PLAYER_JOIN
        _connectionHint.value = hint
        refreshServerDiscovery()
    }

    private fun broadcastPlayersIfHost() {
        if (_selectedRole.value != UserRole.HOST) return

        viewModelScope.launch {
            val msg = GameMessage(
                kind = MessageKind.PLAYER_LIST.toString(),
                senderID = localPlayerID,
                senderNickname = _hostNickname.value,
                players = _players.value
            )
            network.send(msg)
        }
    }
}