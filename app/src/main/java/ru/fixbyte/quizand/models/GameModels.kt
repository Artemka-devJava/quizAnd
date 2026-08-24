package ru.fixbyte.quizand.models

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

enum class UserRole {
    HOST, PLAYER
}

/**
 * Как телефоны связаны в локальной сети — определяет, как игрок ищет хоста.
 * ROUTER: оба устройства в сети обычного Wi-Fi-роутера — mDNS/multicast работает
 * штатно, поэтому доступен автопоиск.
 * HOTSPOT: один из телефонов раздаёт точку доступа. Многие хотспоты либо не
 * пробрасывают multicast-трафик, либо включают изоляцию клиентов — автопоиск
 * ненадёжен, поэтому игрок подключается по IP, а хост показывает свой адрес.
 */
enum class ConnectionMode {
    ROUTER, HOTSPOT
}

enum class AppPhase {
    SPLASH,
    CONNECTION_MODE_SELECTION,
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
data class AnswerResultPayload(
    val playerID: String,
    val isCorrect: Boolean,
    val awardedPoints: Int
)

/**
 * Строковые представления должны СОВПАДАТЬ ДОСЛОВНО с rawValue-значениями
 * Swift-перечисления MessageKind на iOS (camelCase = имя case без изменений).
 * Раньше здесь использовался стандартный Kotlin toString() (HELLO, PLAYER_LIST, ...),
 * из-за чего iOS и Android никогда не распознавали сообщения друг друга.
 */
enum class MessageKind {
    HELLO {
        override fun toString() = "hello"
    },
    PLAYER_LIST {
        override fun toString() = "playerList"
    },
    GAME_STARTED {
        override fun toString() = "gameStarted"
    },
    ROUND_OPENED {
        override fun toString() = "roundOpened"
    },
    BUZZ {
        override fun toString() = "buzz"
    },
    RESPONDER_SELECTED {
        override fun toString() = "responderSelected"
    },
    RESPONDER_CLEARED {
        override fun toString() = "responderCleared"
    },
    ROUND_CLOSED {
        override fun toString() = "roundClosed"
    },
    ANSWER_RESULT {
        override fun toString() = "answerResult"
    },
    ERROR {
        override fun toString() = "error"
    }
}

/** Текущее время в формате ISO8601 (UTC), совместимом с JSONEncoder.dateEncodingStrategy = .iso8601 на iOS. */
private fun iso8601Now(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date())
}

@Serializable
data class GameMessage(
    val id: String = UUID.randomUUID().toString(),
    val kind: String,
    val senderID: String,
    val senderNickname: String? = null,
    val player: PlayerInfo? = null,
    val players: List<PlayerInfo>? = null,
    val answerResult: AnswerResultPayload? = null,
    val scoreValue: Int? = null,
    val text: String? = null,
    // ВАЖНО: строка ISO8601, а не Long. На iOS JSONEncoder кодирует Date как строку
    // ("2026-08-23T10:15:30Z"), а не как число миллисекунд — с Long-полем Android
    // не мог разобрать НИ ОДНО сообщение от iOS.
    val sentAt: String = iso8601Now()
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