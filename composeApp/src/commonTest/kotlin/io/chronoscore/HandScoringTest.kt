package io.chronoscore

import io.chronoscore.engine.BurracoSide
import io.chronoscore.engine.GameAction
import io.chronoscore.engine.GameEngine
import io.chronoscore.engine.ScopaSide
import io.chronoscore.engine.boccePoints
import io.chronoscore.engine.briscolaHands
import io.chronoscore.engine.burracoCards
import io.chronoscore.engine.burracoPoints
import io.chronoscore.engine.primiera
import io.chronoscore.engine.sanitized
import io.chronoscore.engine.scala40Cards
import io.chronoscore.engine.scala40Penalty
import io.chronoscore.engine.scopaPoints
import io.chronoscore.model.HandCalc
import io.chronoscore.model.MatchOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 1.5: hand calculators for the round sheet. */
class HandScoringTest {

    @Test
    fun scopaScoresTheFourPointsAndScope() {
        val a = ScopaSide(cards = 24, denari = 7, settebello = true, primiera = false, scope = 2)
        val b = ScopaSide(cards = 16, denari = 3, settebello = false, primiera = true, scope = 1)
        assertEquals(listOf(5, 2), scopaPoints(listOf(a, b)))
    }

    @Test
    fun scopaTiesOnCardsOrDenariScoreNobody() {
        val a = ScopaSide(cards = 20, denari = 5)
        val b = ScopaSide(cards = 20, denari = 5, settebello = true)
        assertEquals(listOf(0, 1), scopaPoints(listOf(a, b)))
    }

    @Test
    fun primieraTakesTheBestCardOfEachSuit() {
        // 7, 7, 6, ace → 21 + 21 + 18 + 16.
        assertEquals(76, primiera(listOf(listOf(7, 2), listOf(7), listOf(6, 10), listOf(1, 9))))
        // Face cards are 10 each.
        assertEquals(40, primiera(listOf(listOf(8), listOf(9), listOf(10), listOf(10))))
        // No primiera without all four suits.
        assertEquals(0, primiera(listOf(listOf(7), listOf(7), listOf(7), emptyList())))
    }

    @Test
    fun burracoAddsBonusesAndCards() {
        val closer = BurracoSide(clean = 1, dirty = 1, closed = true, pozzetto = true, table = 285, hand = 0)
        assertEquals(685, burracoPoints(closer))
        // No pozzetto costs 100, cards left in hand are taken off.
        val other = BurracoSide(dirty = 0, pozzetto = false, table = 80, hand = 95)
        assertEquals(-115, burracoPoints(other))
        assertEquals(30 + 2 * 20 + 15 + 3 * 10 + 2 * 5, burracoCards(jolly = 1, pinelle = 2, aces = 1, high = 3, low = 2))
    }

    @Test
    fun briscolaGoesTo61AndDrawsAt60() {
        assertEquals(listOf(1, 0), briscolaHands(61))
        assertEquals(listOf(0, 1), briscolaHands(59))
        assertEquals(listOf(0, 0), briscolaHands(60))
        assertEquals(listOf(1, 0), briscolaHands(500))
    }

    @Test
    fun bocceScoresOnlyTheClosestSide() {
        assertEquals(listOf(0, 3), boccePoints(2, scorer = 1, points = 3))
    }

    @Test
    fun scala40Penalties() {
        assertEquals(0, scala40Penalty(closed = true, opened = true, cardsInHand = 50))
        assertEquals(100, scala40Penalty(closed = false, opened = false, cardsInHand = 12))
        assertEquals(37, scala40Penalty(closed = false, opened = true, cardsInHand = 37))
        assertEquals(11 + 10 + 7 + 25, scala40Cards(aces = 1, figures = 1, numbers = 7, jolly = 1))
    }

    @Test
    fun batchPointsAreOneUndoStep() {
        var id = 0
        val engine = GameEngine(clock = { 1L }, newId = { "id${id++}" })
        val s = engine.newSession(2, 0, options = MatchOptions(sheetMode = true, handCalculator = HandCalc.SCOPA))
        val ids = s.players.map { it.id }
        val scored = engine.reduce(s, GameAction.SetRoundPointsBatch(1, mapOf(ids[0] to 5, ids[1] to 2)))
        assertEquals(listOf(5, 2), scored.players.map { it.primaryScore })
        // Re-entering the hand replaces its points instead of adding to them.
        val fixed = engine.reduce(scored, GameAction.SetRoundPointsBatch(1, mapOf(ids[0] to 4, ids[1] to 3)))
        assertEquals(listOf(4, 3), fixed.players.map { it.primaryScore })
        assertEquals(listOf(5, 2), engine.reduce(fixed, GameAction.Undo).players.map { it.primaryScore })
        assertEquals(listOf(0, 0), engine.reduce(scored, GameAction.Undo).players.map { it.primaryScore })
    }

    @Test
    fun calculatorNeedsTheRoundSheet() {
        assertNull(MatchOptions(handCalculator = HandCalc.BURRACO).sanitized().handCalculator)
    }
}
