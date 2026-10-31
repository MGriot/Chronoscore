package io.chronoscore

import io.chronoscore.ui.board.CellCorner
import io.chronoscore.ui.board.bestTiltedSize
import io.chronoscore.ui.board.coverSize
import io.chronoscore.ui.board.fittedSize
import io.chronoscore.ui.board.freeCorners
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Slanted seats: the turned card fits its cell, and the tap layer covers all of it. */
class RotatedSeatGeometryTest {

    /** [x], [y] (around the cell's centre) inside a [w] × [h] card turned by [degrees]? */
    private fun inside(x: Float, y: Float, w: Float, h: Float, degrees: Int): Boolean {
        val rad = degrees * PI / 180
        val u = x * cos(rad) + y * sin(rad)
        val v = -x * sin(rad) + y * cos(rad)
        return abs(u) <= w / 2 + 0.01 && abs(v) <= h / 2 + 0.01
    }

    private val cellCorners = listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)

    @Test
    fun coverReachesEveryCellCorner() {
        for (degrees in (0 until 360 step 30)) {
            val (w, h) = coverSize(220f, 420f, degrees)
            cellCorners.forEach { (sx, sy) ->
                assertTrue(inside(sx * 110f, sy * 210f, w, h, degrees), "corner ($sx, $sy) uncovered at $degrees°")
            }
        }
    }

    @Test
    fun rightAnglesKeepTheCellShape() {
        assertEquals(220 to 420, coverSize(220f, 420f, 0).let { (w, h) -> w.roundToInt() to h.roundToInt() })
        assertEquals(420 to 220, coverSize(220f, 420f, 90).let { (w, h) -> w.roundToInt() to h.roundToInt() })
        assertEquals(420f to 220f, fittedSize(220f, 420f, 90))
    }

    @Test
    fun tiltedCardStaysInsideTheCell() {
        for (degrees in listOf(30, 60, 120, 150, 210, 330)) {
            val (w, h) = bestTiltedSize(220f, 420f, degrees) { x, y -> minOf(x / 2, y) }
            // Every card corner lands inside the cell.
            val rad = degrees * PI / 180
            cellCorners.forEach { (u, v) ->
                val x = u * w / 2 * cos(rad) - v * h / 2 * sin(rad)
                val y = u * w / 2 * sin(rad) + v * h / 2 * cos(rad)
                assertTrue(abs(x) <= 110.01 && abs(y) <= 210.01, "card sticks out at $degrees°")
            }
        }
    }

    @Test
    fun aSlantedCardLeavesTwoOppositeCornersFree() {
        val (w, h) = fittedSize(220f, 420f, 30)
        val corners = assertNotNull(freeCorners(220f, 420f, 30, w, h, 52f))
        val pairs = setOf(
            setOf(CellCorner.TOP_LEFT, CellCorner.BOTTOM_RIGHT),
            setOf(CellCorner.TOP_RIGHT, CellCorner.BOTTOM_LEFT),
        )
        assertTrue(setOf(corners.first, corners.second) in pairs)
        // An upright card fills the cell, so no corner is free.
        assertEquals(null, freeCorners(220f, 420f, 0, 220f, 420f, 52f))
    }
}
