package ru.fixbyte.quizand.models

import kotlinx.serialization.Serializable
import java.util.UUID

enum class UserRole {
    HOST, PLAYER
}

enum class AppPhase {
    SPLASH,
    ROLE_SELECTION,
    HOST_LOBBY,
    HOST_CONTROL,
    PLAYER_JOIN,
    PLAYER_WAITING,
    PLAYER_QUESTION
}

@Serializable
data class PlayerInfo(
    val id: String = UUID.randomUUID().toString(),
    val nickname: String
)

@Serializable
data class QuestionPayload(
    val id: String = UUID.randomUUID().toString(),
    val category: String,
    val text: String,
    val options: List<String>,
    val correctIndex: Int? = null
)

@Serializable
data class AnswerPayload(
    val questionID: String = UUID.randomUUID().toString(),
    val playerID: String,
    val selectedIndex: Int = 0,
    val sentAt: Long = System.currentTimeMillis()
)

@Serializable
data class AnswerResultPayload(
    val playerID: String,
    val isCorrect: Boolean,
    val awardedPoints: Int
)

enum class MessageKind {
    HELLO,
    PLAYER_LIST,
    GAME_STARTED,
    ROUND_OPENED,
    BUZZ,
    RESPONDER_SELECTED,
    RESPONDER_CLEARED,
    ROUND_CLOSED,
    ANSWER,
    ANSWER_RESULT,
    ERROR
}

@Serializable
data class GameMessage(
    val id: String = UUID.randomUUID().toString(),
    val kind: String,
    val senderID: String,
    val senderNickname: String? = null,
    val player: PlayerInfo? = null,
    val players: List<PlayerInfo>? = null,
    val question: QuestionPayload? = null,
    val answer: AnswerPayload? = null,
    val answerResult: AnswerResultPayload? = null,
    val scoreValue: Int? = null,
    val text: String? = null,
    val sentAt: Long = System.currentTimeMillis()
)

sealed class NetworkEvent {
    data class PlayerConnected(val player: PlayerInfo) : NetworkEvent()
    data class PlayerDisconnected(val player: PlayerInfo) : NetworkEvent()
    data class Message(val message: GameMessage) : NetworkEvent()
}

data class DiscoveredServer(
    val id: String,
    val name: String,
    val ipAddress: String,
    val port: Int
)

