package io.chronoscore

import io.chronoscore.random.CoinSide
import io.chronoscore.random.TableRng
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TableRngTest {

    private val rng = TableRng(Random(42))

    @Test
    fun rollsStayInRangeAndCoverAllFaces() {
        (TableRng.STANDARD_DICE + listOf(2, 100, 1000)).forEach { sides ->
            val results = List(5_000) { rng.roll(sides) }
            assertTrue(results.all { it in 1..sides }, "d$sides out of range")
            if (sides <= 20) assertEquals((1..sides).toSet(), results.toSet(), "d$sides missed a face")
        }
    }

    @Test
    fun invalidDiceAreRejected() {
        assertFailsWith<IllegalArgumentException> { rng.roll(1) }
        assertFailsWith<IllegalArgumentException> { rng.roll(1001) }
        assertFailsWith<IllegalArgumentException> { rng.rollMany(0, 6) }
    }

    @Test
    fun rollManyReturnsRequestedCount() {
        assertEquals(4, rng.rollMany(4, 6).size)
    }

    @Test
    fun coinIsRoughlyBalanced() {
        val heads = List(10_000) { rng.flipCoin() }.count { it == CoinSide.HEADS }
        assertTrue(heads in 4_500..5_500, "heads=$heads")
    }

    @Test
    fun firstPlayerAndTurnOrderComeFromRoster() {
        val roster = listOf("A", "B", "C", "D")
        repeat(100) { assertTrue(rng.pickFirstPlayer(roster) in roster) }
        assertEquals(roster.toSet(), List(200) { rng.pickFirstPlayer(roster) }.toSet())
        assertEquals(roster.sorted(), rng.shuffleTurnOrder(roster).sorted())
        assertFailsWith<IllegalArgumentException> { rng.pickFirstPlayer(emptyList<String>()) }
    }
}
