package io.chronoscore

import io.chronoscore.engine.BULL
import io.chronoscore.engine.GameAction
import io.chronoscore.engine.GameEngine
import io.chronoscore.engine.checkout
import io.chronoscore.engine.dartsState
import io.chronoscore.engine.outcome
import io.chronoscore.model.DartsGame
import io.chronoscore.model.DartsRules
import io.chronoscore.model.GameSession
import io.chronoscore.model.MatchOptions
import io.chronoscore.model.WinMode
import io.chronoscore.model.WinRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 1.4: darts mode, entered one dart at a time. */
class DartsTest {

    private var nextId = 0
    private val engine = GameEngine(clock = { 1_000_000L }, newId = { "id${nextId++}" })

    private fun GameSession.apply(vararg actions: GameAction) = actions.fold(this) { s, a -> engine.reduce(s, a) }
    private fun GameSession.id(seat: Int) = players[seat].id
    private fun GameSession.score(seat: Int) = players[seat].primaryScore

    private fun game(rules: DartsRules, seats: Int = 2) = engine.newSession(seats, 0, options = MatchOptions(darts = rules))

    private fun t(n: Int) = GameAction.ThrowDart(n, 3)
    private fun d(n: Int) = GameAction.ThrowDart(n, 2)
    private fun s(n: Int) = GameAction.ThrowDart(n, 1)
    private val miss = GameAction.ThrowDart(0)

    @Test
    fun dartsOptionsForceTurnsAndStartScore() {
        val g = engine.newSession(2, 40, options = MatchOptions(sheetMode = true, winRule = WinRule(WinMode.TARGET_SCORE, 10), darts = DartsRules(start = 301)))
        assertTrue(g.options.turnTracking)
        assertFalse(g.options.sheetMode)
        assertEquals(WinMode.NONE, g.options.winRule.mode)
        assertEquals(301, g.score(0))
        assertEquals(g.id(0), g.activePlayerId)
    }

    @Test
    fun x01CountsDownAndPassesTurnAfterThreeDarts() {
        val g = game(DartsRules(start = 501))
        val r = g.apply(t(20), t(20), t(20))
        assertEquals(321, r.score(0))
        assertEquals(g.id(1), r.activePlayerId)
        // Every dart that scored is in the log, and the second seat starts a new visit.
        assertEquals(3, r.historyLog.size)
        val next = r.apply(s(1))
        assertEquals(500, next.score(1))
        assertEquals(2, next.dartsState().visits.size)
    }

    @Test
    fun x01BustsBelowZeroOnOneAndWithoutADouble() {
        val g = game(DartsRules(start = 40))
        // Below zero: the visit's darts don't count and the turn passes.
        val below = g.apply(s(20), t(20))
        assertEquals(40, below.score(0))
        assertTrue(below.dartsState().visits.last().bust)
        assertEquals(g.id(1), below.activePlayerId)
        // Leaving 1 with double-out is a bust.
        assertEquals(40, g.apply(t(13)).score(0))
        // Reaching 0 on a single is a bust with double-out…
        assertEquals(40, g.apply(s(20), s(20)).score(0))
        // …and a win without it.
        val free = game(DartsRules(start = 40, doubleOut = false)).apply(s(20), s(20))
        assertTrue(free.outcome().finished)
    }

    @Test
    fun x01FinishesOnADoubleOrTheBull() {
        val g = game(DartsRules(start = 90))
        val won = g.apply(t(20), s(10), d(10))
        assertEquals(0, won.score(0))
        assertEquals(listOf(g.id(0)), won.outcome().winners)
        // No more darts once it's won; the turn stays put.
        assertEquals(won, won.apply(t(20)))

        val bull = game(DartsRules(start = 50)).apply(GameAction.ThrowDart(BULL, 2))
        assertTrue(bull.outcome().finished)
        // The single bull is 25, not a double.
        assertEquals(25, game(DartsRules(start = 50)).apply(GameAction.ThrowDart(BULL, 1)).score(0))
    }

    @Test
    fun x01DoubleInStartsScoringOnADouble() {
        val g = game(DartsRules(start = 301, doubleIn = true))
        val r = g.apply(t(20), d(5), s(20))
        assertEquals(301 - 10 - 20, r.score(0))
    }

    @Test
    fun undoTakesBackOneDartAndReversesABustOrWin() {
        val g = game(DartsRules(start = 40))
        val bust = g.apply(s(20), t(20))
        val undone = bust.apply(GameAction.Undo)
        assertEquals(20, undone.score(0))
        assertEquals(g.id(0), undone.activePlayerId)
        assertFalse(undone.dartsState().visits.last().bust)

        val won = undone.apply(d(10))
        assertTrue(won.outcome().finished)
        val back = won.apply(GameAction.Undo)
        assertFalse(back.outcome().finished)
        assertEquals(20, back.score(0))
        assertEquals(1, back.historyLog.size)
    }

    @Test
    fun endVisitCountsTheRestAsMisses() {
        val g = game(DartsRules(start = 501))
        val r = g.apply(t(20), GameAction.EndVisit)
        assertEquals(441, r.score(0))
        assertEquals(g.id(1), r.activePlayerId)
        assertEquals(3, r.darts.size)
        // A whole visit with nothing thrown.
        assertEquals(g.id(0), r.apply(GameAction.EndVisit).activePlayerId)
    }

    @Test
    fun cricketScoresOnlyWhileAnOpponentHasTheNumberOpen() {
        val g = game(DartsRules(DartsGame.CRICKET))
        // Seat 1: T20 closes 20, the next T20 scores 60.
        val r = g.apply(t(20), t(20), miss)
        assertEquals(60, r.score(0))
        assertEquals(6, r.dartsState().seats.getValue(g.id(0)).marks[20])
        // Seat 2 closes 20 too; from then on nobody scores on it.
        val closed = r.apply(t(20), miss, miss, t(20))
        assertEquals(0, closed.score(1))
        assertEquals(60, closed.score(0))
        // Numbers outside 15–20 and the bull don't count.
        assertEquals(60, closed.apply(t(14)).score(0))
    }

    @Test
    fun cricketIsWonByClosingEverythingWithoutTrailing() {
        val g = game(DartsRules(DartsGame.CRICKET))
        // Level on points: closing everything wins.
        val level = g.apply(t(20), t(19), t(18), miss, miss, miss, t(17), t(16), t(15), miss, miss, miss, d(BULL))
        assertFalse(level.outcome().finished)
        assertEquals(listOf(g.id(0)), level.apply(s(BULL)).outcome().winners)

        // Behind on points: closing everything isn't enough until the points catch up.
        var r = g.apply(miss, miss, miss, t(20), t(20), miss)
        assertEquals(60, r.score(1))
        r = r.apply(t(20), t(19), t(18), miss, miss, miss, t(17), t(16), t(15), miss, miss, miss)
        r = r.apply(d(BULL), s(BULL))
        assertFalse(r.outcome().finished, "closed everything but trails 0–60")
        r = r.apply(t(19))
        assertEquals(57, r.score(0))
        assertFalse(r.outcome().finished)
        r = r.apply(miss, miss, miss, t(19))
        assertEquals(listOf(g.id(0)), r.outcome().winners)
    }

    @Test
    fun aroundTheClockMovesOnlyOnTheRightNumber() {
        val g = game(DartsRules(DartsGame.AROUND_THE_CLOCK), seats = 1)
        val r = g.apply(s(2), t(1), d(2))
        assertEquals(3, r.dartsState().seats.getValue(g.id(0)).next)
        assertEquals(2, r.score(0))
        val all = (3..20).fold(r) { acc, n -> acc.apply(s(n)) }
        assertEquals(BULL, all.dartsState().seats.getValue(g.id(0)).next)
        assertFalse(all.outcome().finished)
        val won = all.apply(GameAction.ThrowDart(BULL, 1))
        assertEquals(listOf(g.id(0)), won.outcome().winners)
    }

    @Test
    fun shanghaiScoresTheRoundNumberAndKnocksOut() {
        val g = game(DartsRules(DartsGame.SHANGHAI, rounds = 2))
        val r = g.apply(t(1), s(5), d(1))
        assertEquals(5, r.score(0))
        assertFalse(r.outcome().finished, "only treble and double of 1")
        // Seat 2: single, double and treble of 1 in one visit wins outright.
        val ko = r.apply(s(1), d(1), t(1))
        assertEquals(listOf(g.id(1)), ko.outcome().winners)
    }

    @Test
    fun shanghaiEndsAfterTheLastRoundWithTiesShared() {
        val g = game(DartsRules(DartsGame.SHANGHAI, rounds = 2))
        val r = g.apply(s(1), miss, miss, s(1), miss, miss, d(2), miss, miss)
        assertFalse(r.outcome().finished)
        val tie = r.apply(d(2), miss, miss)
        assertEquals(5, tie.score(0))
        assertEquals(setOf(g.id(0), g.id(1)), tie.outcome().winners.toSet())
        val win = r.apply(t(2), miss, miss)
        assertEquals(listOf(g.id(1)), win.outcome().winners)
    }

    @Test
    fun changingDartsRulesRestartsTheScores() {
        val g = game(DartsRules(start = 501)).apply(t(20))
        val r = g.apply(GameAction.SetOptions(g.options.copy(darts = DartsRules(DartsGame.CRICKET))))
        assertEquals(0, r.score(0))
        assertTrue(r.darts.isEmpty())
        assertTrue(r.historyLog.isEmpty())
    }

    @Test
    fun checkoutSuggestions() {
        assertEquals(listOf("T20", "T20", "Bull"), checkout(170))
        assertEquals(listOf("T20", "D20"), checkout(100))
        assertEquals(listOf("D20"), checkout(40))
        assertNull(checkout(169))
        assertNull(checkout(1))
        assertNull(checkout(100, dartsLeft = 1))
        assertEquals(listOf("19"), checkout(19, doubleOut = false))
    }
}
