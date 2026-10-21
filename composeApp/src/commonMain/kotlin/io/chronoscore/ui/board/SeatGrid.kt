package io.chronoscore.ui.board

/**
 * Row-major seat grid. When the seat count doesn't fill the last row, the remaining
 * seats in that row share its full width (e.g. 3 players in 2x2 → bottom seat spans).
 */
data class SeatGrid(val rows: Int, val cols: Int, val count: Int) {

    fun rowOf(seat: Int): Int = seat / cols

    /** Seat indices laid out in each row, top to bottom. */
    fun rowsOfSeats(): List<List<Int>> = (0 until count).chunked(cols)

    /** Seats in the top half face the far side of the table, so they start rotated 180°. */
    fun defaultFlipped(seat: Int): Boolean = rows >= 2 && rowOf(seat) < rows / 2

    /** The control strip sits on the flip boundary, or below a single row. */
    val stripAfterRow: Int get() = if (rows >= 2) rows / 2 - 1 else rows - 1
}

fun gridFor(count: Int, isLandscape: Boolean): SeatGrid {
    val n = count.coerceIn(1, 8)
    val (rows, cols) = when {
        n == 1 -> 1 to 1
        n == 2 -> if (isLandscape) 1 to 2 else 2 to 1
        n <= 4 -> 2 to 2
        n <= 6 -> if (isLandscape) 2 to 3 else 3 to 2
        else -> if (isLandscape) 2 to 4 else 4 to 2
    }
    return SeatGrid(rows, cols, n)
}
