package io.chronoscore

import io.chronoscore.ui.board.gridFor
import kotlin.test.Test
import kotlin.test.assertEquals

class SeatGridTest {

    @Test
    fun gridDimensions() {
        val portrait = (1..8).map { gridFor(it, isLandscape = false).let { g -> g.rows to g.cols } }
        assertEquals(listOf(1 to 1, 2 to 1, 2 to 2, 2 to 2, 3 to 2, 3 to 2, 4 to 2, 4 to 2), portrait)

        val landscape = (1..8).map { gridFor(it, isLandscape = true).let { g -> g.rows to g.cols } }
        assertEquals(listOf(1 to 1, 1 to 2, 2 to 2, 2 to 2, 2 to 3, 2 to 3, 2 to 4, 2 to 4), landscape)
    }

    @Test
    fun oddCountsLeaveShortLastRow() {
        assertEquals(listOf(listOf(0, 1), listOf(2)), gridFor(3, false).rowsOfSeats())
        assertEquals(listOf(listOf(0, 1, 2), listOf(3, 4)), gridFor(5, true).rowsOfSeats())
    }

    @Test
    fun topHalfSeatsFlipByDefault() {
        fun flips(count: Int, landscape: Boolean) = gridFor(count, landscape).let { g -> (0 until count).map(g::defaultFlipped) }

        assertEquals(listOf(false), flips(1, false))
        assertEquals(listOf(true, false), flips(2, false))
        assertEquals(listOf(false, false), flips(2, true))
        assertEquals(listOf(true, true, false, false), flips(4, false))
        assertEquals(listOf(true, true, false, false, false, false), flips(6, false))
        assertEquals(listOf(true, true, true, true, false, false, false, false), flips(8, false))
        assertEquals(listOf(true, true, true, true, false, false, false, false), flips(8, true))
    }
}
