package ru.fixbyte.quizand.models

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Строковые значения MessageKind должны совпадать буквально с rawValue-значениями
 * enum MessageKind на iOS (см. GameModels.kt) — расхождение здесь однажды уже приводило
 * к тому, что Android и iOS вообще не распознавали сообщения друг друга. Эти тесты
 * фиксируют контракт, чтобы случайное переименование значения сразу падало здесь,
 * а не только при живом тестировании между платформами.
 */
class GameModelsTest {

    @Test
    fun `MessageKind string values match the iOS wire format exactly`() {
        assertEquals("hello", MessageKind.HELLO.toString())
        assertEquals("playerList", MessageKind.PLAYER_LIST.toString())
        assertEquals("gameStarted", MessageKind.GAME_STARTED.toString())
        assertEquals("roundOpened", MessageKind.ROUND_OPENED.toString())
        assertEquals("buzz", MessageKind.BUZZ.toString())
        assertEquals("responderSelected", MessageKind.RESPONDER_SELECTED.toString())
        assertEquals("responderCleared", MessageKind.RESPONDER_CLEARED.toString())
        assertEquals("roundClosed", MessageKind.ROUND_CLOSED.toString())
        assertEquals("answerResult", MessageKind.ANSWER_RESULT.toString())
        assertEquals("scoresReset", MessageKind.SCORES_RESET.toString())
        assertEquals("error", MessageKind.ERROR.toString())
    }

    @Test
    fun `GameMessage kind is encoded as its wire string, not the Kotlin enum name`() {
        val json = Json { encodeDefaults = true }
        val msg = GameMessage(kind = MessageKind.SCORES_RESET.toString(), senderID = "host-1")

        val encoded = json.encodeToString(msg)

        assertTrue(encoded.contains("\"kind\":\"scoresReset\""))
        assertTrue("must not leak the Kotlin enum constant name", !encoded.contains("SCORES_RESET"))
    }

    @Test
    fun `GameMessage round-trips through JSON with ignoreUnknownKeys`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val original = GameMessage(
            kind = MessageKind.ANSWER_RESULT.toString(),
            senderID = "host-1",
            senderNickname = "Ведущий",
            player = PlayerInfo(id = "p1", nickname = "Игрок"),
            answerResult = AnswerResultPayload(playerID = "p1", isCorrect = true, awardedPoints = 1),
            scoreValue = 3,
            text = "Верно"
        )

        val decoded = json.decodeFromString<GameMessage>(json.encodeToString(original))

        assertEquals(original.kind, decoded.kind)
        assertEquals(original.senderID, decoded.senderID)
        assertEquals(original.player, decoded.player)
        assertEquals(original.answerResult, decoded.answerResult)
        assertEquals(original.scoreValue, decoded.scoreValue)
    }

    @Test
    fun `decoding tolerates unknown fields, as required for forward-compatibility with the other platform`() {
        val json = Json { ignoreUnknownKeys = true }
        val withExtraField = """
            {"id":"m1","kind":"hello","senderID":"s1","sentAt":"2026-08-26T00:00:00Z","someFutureField":"ignored"}
        """.trimIndent()

        val decoded = json.decodeFromString<GameMessage>(withExtraField)

        assertEquals("hello", decoded.kind)
        assertEquals("s1", decoded.senderID)
    }
}
