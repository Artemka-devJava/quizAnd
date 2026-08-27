package ru.fixbyte.quizand.viewmodels

import android.app.Application
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.fixbyte.quizand.models.AnswerResultPayload
import ru.fixbyte.quizand.models.AppPhase
import ru.fixbyte.quizand.models.GameMessage
import ru.fixbyte.quizand.models.MessageKind
import ru.fixbyte.quizand.models.NetworkEvent
import ru.fixbyte.quizand.models.PlayerInfo
import ru.fixbyte.quizand.models.UserRole

/**
 * Тесты бизнес-логики [AppViewModel] в обход реальной сети: события с "провода" подаются
 * напрямую через `network.onEvent`, вместо поднятия настоящих сокетов (см. NetworkManager
 * остаётся `internal`-полем именно ради этого). Это покрывает ровно ту логику — дедупликация
 * BUZZ, семантика верного/неверного ответа, сохранение счёта при переподключении, сброс
 * счёта в том же лобби — где в этой сессии не раз находились реальные баги только через
 * ручное тестирование на живых телефонах; здесь эти сценарии закреплены как regression-тесты.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private lateinit var viewModel: AppViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putString(any(), any()) } returns editor

        val prefs = mockk<SharedPreferences>()
        every { prefs.getString(any(), any()) } returns null
        every { prefs.edit() } returns editor

        val application = mockk<Application>(relaxed = true)
        every { application.getSharedPreferences(any(), any()) } returns prefs

        viewModel = AppViewModel(application)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // MARK: - helpers

    private fun deliver(event: NetworkEvent) {
        viewModel.network.onEvent?.invoke(event)
            ?: error("network.onEvent was not wired by AppViewModel.init()")
    }

    private fun deliverMessage(
        kind: MessageKind,
        senderID: String = "host-1",
        player: PlayerInfo? = null,
        players: List<PlayerInfo>? = null,
        answerResult: AnswerResultPayload? = null,
        scoreValue: Int? = null
    ) {
        deliver(
            NetworkEvent.Message(
                GameMessage(
                    kind = kind.toString(),
                    senderID = senderID,
                    player = player,
                    players = players,
                    answerResult = answerResult,
                    scoreValue = scoreValue
                )
            )
        )
    }

    private val alice = PlayerInfo(id = "alice", nickname = "Alice")
    private val bob = PlayerInfo(id = "bob", nickname = "Bob")

    // MARK: - connect / disconnect / score persistence (the core reconnect fix)

    @Test
    fun `playerConnected adds the player and seeds their score at zero`() {
        viewModel._selectedRole.value = UserRole.HOST

        deliver(NetworkEvent.PlayerConnected(alice))

        assertEquals(listOf(alice), viewModel.players.value)
        assertEquals(0, viewModel.scoreFor(alice.id))
    }

    @Test
    fun `playerConnected twice with the same id does not duplicate the player`() {
        viewModel._selectedRole.value = UserRole.HOST

        deliver(NetworkEvent.PlayerConnected(alice))
        deliver(NetworkEvent.PlayerConnected(alice))

        assertEquals(listOf(alice), viewModel.players.value)
    }

    @Test
    fun `score survives a disconnect and is not reset when the same id reconnects`() {
        viewModel._selectedRole.value = UserRole.HOST
        deliver(NetworkEvent.PlayerConnected(alice))
        viewModel.openRoundAsHost()
        deliverMessage(MessageKind.BUZZ, senderID = alice.id, player = alice)
        viewModel.judgeCurrentResponder(isCorrect = true)
        assertEquals(1, viewModel.scoreFor(alice.id))

        deliver(NetworkEvent.PlayerDisconnected(alice))
        assertTrue(viewModel.players.value.isEmpty())
        assertEquals("score must survive disconnect", 1, viewModel.scoreFor(alice.id))

        deliver(NetworkEvent.PlayerConnected(alice))
        assertEquals("reconnecting with the same id must not reset the score to 0", 1, viewModel.scoreFor(alice.id))
    }

    @Test
    fun `isKnownPlayerId reflects whether the id has a score entry`() {
        viewModel._selectedRole.value = UserRole.HOST
        val isKnown = viewModel.network.isKnownPlayerId
            ?: error("isKnownPlayerId was not wired by AppViewModel.init()")

        assertFalse(isKnown("ghost"))
        deliver(NetworkEvent.PlayerConnected(alice))
        assertTrue(isKnown(alice.id))
    }

    // MARK: - buzz dedup

    @Test
    fun `second buzz in the same round is ignored once a responder is already selected`() {
        viewModel._selectedRole.value = UserRole.HOST
        deliver(NetworkEvent.PlayerConnected(alice))
        deliver(NetworkEvent.PlayerConnected(bob))
        viewModel.openRoundAsHost()

        deliverMessage(MessageKind.BUZZ, senderID = alice.id, player = alice)
        assertEquals(alice, viewModel.activeResponder.value)

        deliverMessage(MessageKind.BUZZ, senderID = bob.id, player = bob)
        assertEquals("a later buzz must not steal the responder slot", alice, viewModel.activeResponder.value)
        assertEquals(listOf(alice), viewModel.buzzHistory.value)
    }

    @Test
    fun `opening a new round clears the previous responder and buzz history`() {
        viewModel._selectedRole.value = UserRole.HOST
        deliver(NetworkEvent.PlayerConnected(alice))
        viewModel.openRoundAsHost()
        deliverMessage(MessageKind.BUZZ, senderID = alice.id, player = alice)
        assertEquals(alice, viewModel.activeResponder.value)

        viewModel.openRoundAsHost()

        assertNull(viewModel.activeResponder.value)
        assertTrue(viewModel.buzzHistory.value.isEmpty())
    }

    // MARK: - judging: correct closes the round, incorrect does not (real bug found via live testing)

    @Test
    fun `correct judgement awards a point, closes the round, and clears the responder`() {
        viewModel._selectedRole.value = UserRole.HOST
        deliver(NetworkEvent.PlayerConnected(alice))
        viewModel.openRoundAsHost()
        deliverMessage(MessageKind.BUZZ, senderID = alice.id, player = alice)

        viewModel.judgeCurrentResponder(isCorrect = true)

        assertEquals(1, viewModel.scoreFor(alice.id))
        assertFalse("a correct answer must close the round", viewModel.roundIsOpen.value)
        assertNull(viewModel.activeResponder.value)
    }

    @Test
    fun `incorrect judgement does not close the round, so another player can still attempt it`() {
        viewModel._selectedRole.value = UserRole.HOST
        deliver(NetworkEvent.PlayerConnected(alice))
        deliver(NetworkEvent.PlayerConnected(bob))
        viewModel.openRoundAsHost()
        deliverMessage(MessageKind.BUZZ, senderID = alice.id, player = alice)

        viewModel.judgeCurrentResponder(isCorrect = false)

        assertEquals(0, viewModel.scoreFor(alice.id))
        assertTrue("an incorrect answer must leave the round open, by design", viewModel.roundIsOpen.value)
        assertNull(viewModel.activeResponder.value)

        // и второй игрок всё ещё может нажать в этом же (не закрытом) раунде
        deliverMessage(MessageKind.BUZZ, senderID = bob.id, player = bob)
        assertEquals(bob, viewModel.activeResponder.value)
    }

    @Test
    fun `a player judged incorrect cannot buzz again within the same round`() {
        viewModel._selectedRole.value = UserRole.HOST
        deliver(NetworkEvent.PlayerConnected(alice))
        viewModel.openRoundAsHost()
        deliverMessage(MessageKind.BUZZ, senderID = alice.id, player = alice)
        viewModel.judgeCurrentResponder(isCorrect = false)

        deliverMessage(MessageKind.BUZZ, senderID = alice.id, player = alice)

        assertNull("the same player must not get a second attempt in one open round", viewModel.activeResponder.value)
    }

    // MARK: - reset score in the same lobby

    @Test
    fun `resetScoresAsHost zeroes every known id, including disconnected ones, without touching the lobby`() {
        viewModel._selectedRole.value = UserRole.HOST
        deliver(NetworkEvent.PlayerConnected(alice))
        deliver(NetworkEvent.PlayerConnected(bob))
        viewModel.openRoundAsHost()
        deliverMessage(MessageKind.BUZZ, senderID = alice.id, player = alice)
        viewModel.judgeCurrentResponder(isCorrect = true)
        deliver(NetworkEvent.PlayerDisconnected(bob))

        viewModel.resetScoresAsHost()

        assertEquals(0, viewModel.scoreFor(alice.id))
        assertEquals(0, viewModel.scoreFor(bob.id))
        assertTrue(
            "a disconnected player's id must stay known after a reset, or their reconnect would be rejected",
            viewModel.network.isKnownPlayerId?.invoke(bob.id) == true
        )
        assertFalse(viewModel.roundIsOpen.value)
        assertEquals("the lobby/connection must be untouched by a score reset", listOf(alice), viewModel.players.value)
    }

    @Test
    fun `resetScoresAsHost does nothing when called by a player`() {
        viewModel._selectedRole.value = UserRole.PLAYER
        deliverMessage(MessageKind.ROUND_OPENED)
        assertTrue(viewModel.roundIsOpen.value)

        viewModel.resetScoresAsHost()

        assertTrue("a non-host call must be a no-op", viewModel.roundIsOpen.value)
    }

    @Test
    fun `receiving a scoresReset message zeroes the local mirrored scores and closes the round`() {
        viewModel._selectedRole.value = UserRole.PLAYER
        deliverMessage(MessageKind.PLAYER_LIST, players = listOf(alice))
        deliverMessage(
            MessageKind.ANSWER_RESULT,
            player = alice,
            answerResult = AnswerResultPayload(playerID = alice.id, isCorrect = true, awardedPoints = 1),
            scoreValue = 1
        )
        deliverMessage(MessageKind.ROUND_OPENED)
        assertEquals(1, viewModel.scoreFor(alice.id))

        deliverMessage(MessageKind.SCORES_RESET)

        assertEquals(0, viewModel.scoreFor(alice.id))
        assertFalse(viewModel.roundIsOpen.value)
    }

    // MARK: - player-side phase transitions

    @Test
    fun `gameStarted moves a player into the question phase`() {
        viewModel._selectedRole.value = UserRole.PLAYER

        deliverMessage(MessageKind.GAME_STARTED)

        assertEquals(AppPhase.PLAYER_QUESTION, viewModel.phase.value)
    }

    @Test
    fun `playerPressedAnswerButton only sends once per round`() {
        viewModel._selectedRole.value = UserRole.PLAYER
        deliverMessage(MessageKind.ROUND_OPENED)

        viewModel.playerPressedAnswerButton()
        assertTrue(viewModel.localHasAttemptedInRound.value)

        // повторный вызов не должен падать и не должен ничего сбрасывать —
        // это же состояние проверяет и сама кнопка в UI перед вызовом.
        viewModel.playerPressedAnswerButton()
        assertTrue(viewModel.localHasAttemptedInRound.value)
    }
}
