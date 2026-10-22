package io.chronoscore

import io.chronoscore.engine.GameAction
import io.chronoscore.engine.GameEngine
import io.chronoscore.engine.outcome
import io.chronoscore.engine.sanitized
import io.chronoscore.model.GameSession
import io.chronoscore.model.MatchOptions
import io.chronoscore.model.TimeBonus
import io.chronoscore.model.TimeControl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 1.2 timer mode: per-seat time banks with increment or delay. */
class TimerModeTest {

    private var now = 1_000_000L
    private var nextId = 0
    private val engine = GameEngine(clock = { now }, newId = { "id${nextId++}" })

    private fun GameSession.apply(vararg actions: GameAction) = actions.fold(this) { s, a -> engine.reduce(s, a) }
    private fun GameSession.id(seat: Int) = players[seat].id

    private fun timer(control: TimeControl, seats: Int = 2) =
        engine.newSession(seats, 0, options = MatchOptions(turnTracking = true, timeControl = control))

    @Test
    fun startsPausedWithFullBanks() {
        val s = timer(TimeControl(bankSeconds = 60))
        assertFalse(s.timing.isRunning)
        assertTrue(s.players.all { it.clockMillis == 60_000L })
        now += 5_000
        assertEquals(60_000L, s.liveClockMillis(s.players[0], now))
    }

    @Test
    fun bankRunsDownAndIncrementIsAdded() {
        val s = timer(TimeControl(bankSeconds = 60, bonus = TimeBonus.INCREMENT, bonusSeconds = 5))
            .apply(GameAction.ToggleClock)
        now += 10_000
        assertEquals(50_000L, s.liveClockMillis(s.players[0], now))
        val r = s.apply(GameAction.NextTurn)
        assertEquals(55_000L, r.players[0].clockMillis)
        assertEquals(10_000L, r.players[0].timeMillis)
        assertEquals(60_000L, r.players[1].clockMillis)
    }

    @Test
    fun delaySecondsAreFree() {
        val s = timer(TimeControl(bankSeconds = 60, bonus = TimeBonus.DELAY, bonusSeconds = 5))
            .apply(GameAction.ToggleClock)
        now += 3_000
        assertEquals(60_000L, s.liveClockMillis(s.players[0], now))
        now += 7_000
        assertEquals(55_000L, s.apply(GameAction.NextTurn).players[0].clockMillis)
    }

    @Test
    fun timeOutKnocksTheSeatOutAndEndsTheMatch() {
        val s = timer(TimeControl(bankSeconds = 30, bonus = TimeBonus.INCREMENT, bonusSeconds = 5), seats = 3)
            .apply(GameAction.ToggleClock)
        now += 10_000
        // Too early: a stale dispatch changes nothing.
        assertEquals(s, s.apply(GameAction.TimeOut))
        now += 25_000
        val r = s.apply(GameAction.TimeOut)
        assertEquals(0L, r.players[0].clockMillis)
        assertEquals(setOf(s.id(0)), r.outcome().eliminated)
        assertEquals(s.id(1), r.activePlayerId)
        // The out seat is skipped from now on.
        assertEquals(s.id(2), r.apply(GameAction.NextTurn).activePlayerId)
        assertEquals(s.id(1), r.apply(GameAction.NextTurn, GameAction.NextTurn).activePlayerId)

        now += 40_000
        val end = r.apply(GameAction.TimeOut)
        assertTrue(end.outcome().finished)
        assertEquals(listOf(s.id(2)), end.outcome().winners)
        assertFalse(end.timing.isRunning)
    }

    @Test
    fun overtimeKeepsEverySeatIn() {
        val s = timer(TimeControl(bankSeconds = 10, outOnTimeout = false)).apply(GameAction.ToggleClock)
        now += 15_000
        val r = s.apply(GameAction.TimeOut, GameAction.NextTurn)
        assertEquals(-5_000L, r.players[0].clockMillis)
        assertEquals(emptySet(), r.outcome().eliminated)
    }

    @Test
    fun adjustClockRevivesASeat() {
        val s = timer(TimeControl(bankSeconds = 10), seats = 3).apply(GameAction.ToggleClock)
        now += 12_000
        val out = s.apply(GameAction.TimeOut)
        assertEquals(setOf(s.id(0)), out.outcome().eliminated)
        val back = out.apply(GameAction.AdjustClock(s.id(0), 60_000))
        assertEquals(60_000L, back.players[0].clockMillis)
        assertEquals(emptySet(), back.outcome().eliminated)
    }

    @Test
    fun optionsSetUpAndClearBanks() {
        val s = engine.newSession(2, 0, options = MatchOptions(turnTracking = true))
        assertNull(s.players[0].clockMillis)
        val on = s.apply(GameAction.SetOptions(s.options.copy(timeControl = TimeControl(bankSeconds = 300))))
        assertTrue(on.players.all { it.clockMillis == 300_000L })
        val added = on.apply(GameAction.AddPlayer)
        assertEquals(300_000L, added.players.last().clockMillis)
        val off = on.apply(GameAction.SetOptions(on.options.copy(timeControl = null)))
        assertTrue(off.players.all { it.clockMillis == null })

        // Timer mode needs the turn tracker.
        assertNull(MatchOptions(timeControl = TimeControl()).sanitized().timeControl)
    }

    @Test
    fun resetRefillsBanks() {
        val s = timer(TimeControl(bankSeconds = 60)).apply(GameAction.ToggleClock)
        now += 20_000
        val r = s.apply(GameAction.NextTurn, GameAction.ResetScores)
        assertTrue(r.players.all { it.clockMillis == 60_000L })
        assertFalse(r.timing.isRunning)
    }
}
