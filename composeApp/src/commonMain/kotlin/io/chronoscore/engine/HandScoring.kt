package io.chronoscore.engine

/**
 * Hand calculators for the round sheet: each turns what happened in one hand into the
 * points every seat (or team) scores for it. Pure, so the dialog only collects inputs.
 */

/** What one side took in a hand of Scopa or Scopone. */
data class ScopaSide(
    val cards: Int = 0,
    val denari: Int = 0,
    val settebello: Boolean = false,
    val primiera: Boolean = false,
    val scope: Int = 0,
)

/**
 * One point each for most cards, most denari, the settebello and the primiera (a tie for
 * cards or denari scores nobody), plus one per scopa.
 */
fun scopaPoints(sides: List<ScopaSide>): List<Int> = sides.map { side ->
    fun most(value: (ScopaSide) -> Int) = sides.count { value(it) >= value(side) } == 1
    listOf(
        most { it.cards },
        most { it.denari },
        side.settebello,
        side.primiera,
    ).count { it } + side.scope
}

/** Primiera value of a card by rank (1 = ace … 7, then 8 fante, 9 cavallo, 10 re). */
fun primieraValue(rank: Int): Int = when (rank) {
    7 -> 21
    6 -> 18
    1 -> 16
    5 -> 15
    4 -> 14
    3 -> 13
    2 -> 12
    in 8..10 -> 10
    else -> 0
}

/**
 * A side's primiera: the best card of each of the four suits added up; 0 when a suit is
 * missing (no primiera without all four). [suits] holds the ranks taken in each suit.
 */
fun primiera(suits: List<List<Int>>): Int =
    if (suits.size < 4 || suits.any { it.isEmpty() }) 0 else suits.sumOf { s -> s.maxOf(::primieraValue) }

/** One side's end of hand in Burraco. */
data class BurracoSide(
    /** Clean burrachi (no jolly or pinella), 200 each. */
    val clean: Int = 0,
    /** Dirty burrachi, 100 each. */
    val dirty: Int = 0,
    /** This side closed the hand (+100). */
    val closed: Boolean = false,
    /** This side took the pozzetto (otherwise −100). */
    val pozzetto: Boolean = true,
    /** Value of the cards laid on the table. */
    val table: Int = 0,
    /** Value of the cards still in hand, taken off. */
    val hand: Int = 0,
)

fun burracoPoints(side: BurracoSide): Int =
    side.clean * 200 + side.dirty * 100 + (if (side.closed) 100 else 0) - (if (side.pozzetto) 0 else 100) + side.table - side.hand

/** Card values in Burraco: jolly 30, pinella (any 2) 20, ace 15, 8 to king 10, 3 to 7 five. */
fun burracoCards(jolly: Int = 0, pinelle: Int = 0, aces: Int = 0, high: Int = 0, low: Int = 0): Int =
    jolly * 30 + pinelle * 20 + aces * 15 + high * 10 + low * 5

/** Card points in a Briscola deck. */
const val BRISCOLA_TOTAL = 120

/**
 * Hands won in Briscola from the first side's card points (the other side has the rest of
 * the 120): 61 or more wins the hand, 60–60 is a draw.
 */
fun briscolaHands(firstSidePoints: Int): List<Int> {
    val a = firstSidePoints.coerceIn(0, BRISCOLA_TOTAL)
    val b = BRISCOLA_TOTAL - a
    return listOf(if (a > b) 1 else 0, if (b > a) 1 else 0)
}

/** Bocce or pétanque: only the side closest to the jack scores, [points] for that end. */
fun boccePoints(seats: Int, scorer: Int, points: Int): List<Int> = List(seats) { if (it == scorer) points.coerceAtLeast(0) else 0 }

/** Scala 40 penalty for one seat: 0 for whoever closed, 100 for not having opened, else the cards in hand. */
fun scala40Penalty(closed: Boolean, opened: Boolean, cardsInHand: Int): Int = when {
    closed -> 0
    !opened -> SCALA40_NOT_OPENED
    else -> cardsInHand.coerceAtLeast(0)
}

const val SCALA40_NOT_OPENED = 100

/** Cards left in hand in Scala 40: ace 11, J/Q/K 10, number cards face value, jolly 25. */
fun scala40Cards(aces: Int = 0, figures: Int = 0, numbers: Int = 0, jolly: Int = 0): Int =
    aces * 11 + figures * 10 + numbers + jolly * 25
