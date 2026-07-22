package ru.fixbyte.quizand.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import ru.fixbyte.quizand.models.*
import ru.fixbyte.quizand.network.NetworkManager
import java.util.UUID

class AppViewModel : ViewModel() {
    // UI State
    private val _phase = MutableStateFlow(AppPhase.SPLASH)
    val phase: StateFlow<AppPhase> = _phase

    private val _selectedRole = MutableStateFlow<UserRole?>(null)
    val selectedRole: StateFlow<UserRole?> = _selectedRole

    // Host Settings
    private val _hostNickname = MutableStateFlow("Ведущий")
    val hostNickname: StateFlow<String> = _hostNickname

    private val _hostPortText = MutableStateFlow(NetworkManager.DEFAULT_PORT.toString())
    val hostPortText: StateFlow<String> = _hostPortText

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

    private val localPlayerID = UUID.randomUUID().toString()
    private val network = NetworkManager(viewModelScope)
    private var attemptedPlayerIDsInRound = mutableSetOf<String>()

    init {
        _phase.value = AppPhase.SPLASH
        bootSplash()

        network.onEvent = { event ->
            handleNetworkEvent(event)
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
            refreshServerDiscovery()
            _connectionHint.value = "Поиск ведущих в локальной сети..."
        }
    }

    fun resetToRoleSelection() {
        network.stopAll()
        _players.value = emptyList()
        _selectedServerID.value = null
        _selectedRole.value = null
        _connectionHint.value = ""

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
        val port = _hostPortText.value.toIntOrNull() ?: 5000
        if (port <= 0) {
            _connectionHint.value = "Неверный порт"
            return
        }

        viewModelScope.launch {
            network.startServer(port, _hostNickname.value)
            _connectionHint.value = "Сервер \"${_hostNickname.value}\" запущен на порту $port"
        }
    }

    fun refreshServerDiscovery() {
        viewModelScope.launch {
            network.startBrowsingServers()
            delay(3000)
            _discoveredServers.value = network.discoveredServers
        }
    }

    fun startGameAsHost() {
        if (_players.value.isEmpty()) {
            _connectionHint.value = "Нужен хотя бы 1 подключенный игрок"
            return
        }

        _phase.value = AppPhase.HOST_CONTROL

        viewModelScope.launch {
            val msg = GameMessage(
                kind = MessageKind.GAME_STARTED.toString(),
                senderID = localPlayerID,
                senderNickname = _hostNickname.value
            )
            network.send(msg)
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

    fun connectAsPlayer() {
        val nickname = _playerNickname.value.trim()
        if (nickname.isEmpty()) {
            _connectionHint.value = "Введите ник"
            return
        }

        val selectedServer = _selectedServerID.value
        if (selectedServer == null) {
            _connectionHint.value = "Выберите сервер из списка"
            return
        }

        val server = _discoveredServers.value.find { it.id == selectedServer }
        if (server == null) {
            _connectionHint.value = "Сервер не найден"
            return
        }

        viewModelScope.launch {
            network.connectToServer(server.ipAddress, server.port)
            delay(500)

            val me = PlayerInfo(id = localPlayerID, nickname = nickname)
            val hello = GameMessage(
                kind = MessageKind.HELLO.toString(),
                senderID = localPlayerID,
                senderNickname = nickname,
                player = me
            )
            network.send(hello)

            _connectionHint.value = "Подключение к ${server.name}"
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

    fun onHostPortTextChanged(value: String) {
        _hostPortText.value = value
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
                        _connectionHint.value = msg.text ?: "Ошибка сети"
                    }

                    else -> {}
                }
            }
        }
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

