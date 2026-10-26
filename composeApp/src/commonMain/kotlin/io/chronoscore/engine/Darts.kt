package io.chronoscore.engine

import io.chronoscore.model.Dart
import io.chronoscore.model.DartsGame
import io.chronoscore.model.DartsRules
import io.chronoscore.model.GameSession

/** The bull's segment number; a single bull scores 25, the bull's eye (double) 50. */
const val BULL = 25

/** Darts per visit. */
const val DARTS_PER_VISIT = 3

/** Cricket numbers, in board order for display. */
val CricketTargets = listOf(20, 19, 18, 17, 16, 15, BULL)

/** Marks that close a cricket number. */
const val CRICKET_CLOSED = 3

/** One player's visit to the board: up to three darts. */
data class DartVisit(
    val number: Int,
    val playerId: String,
    val darts: List<Dart>,
    /** X01: the visit went bust, so the score went back to where it started. */
    val bust: Boolean,
    /** No more darts in this visit: three thrown, bust, or the game was won. */
    val closed: Boolean,
)

/** What a seat has reached, derived from its darts. */
data class DartsSeat(
    /** X01 score left, cricket or Shanghai points, or Around the Clock numbers done. */
    val score: Int,
    /** X01 with double-in: whether scoring has started. */
    val opened: Boolean = true,
    /** Cricket: marks per number (may pass 3; only 3 count toward closing). */
    val marks: Map<Int, Int> = emptyMap(),
    /** Around the Clock: the number to hit next (1–20, then [BULL]); null once done. */
    val next: Int? = 1,
    /** Visits this seat has started. */
    val visits: Int = 0,
) {
    fun closed(number: Int) = (marks[number] ?: 0) >= CRICKET_CLOSED
}

data class DartsState(
    val seats: Map<String, DartsSeat>,
    val visits: List<DartVisit>,
    val winners: List<String>,
    val finished: Boolean,
) {
    /** The visit still taking darts, if any. */
    val openVisit: DartVisit? get() = visits.lastOrNull()?.takeUnless { it.closed }
}

/**
 * Replays every dart of the match. Like [outcome] it is derived and never stored, so Undo
 * (which just drops the last dart) reverses busts, closed numbers and wins.
 */
fun GameSession.dartsState(): DartsState {
    val rules = options.darts ?: DartsRules()
    val order = players.sortedBy { it.seatIndex }.map { it.id }
    val seats = order.associateWith { initialSeat(rules) }.toMutableMap()
    val visits = mutableListOf<DartVisit>()
    var winners = emptyList<String>()

    darts.groupBy { it.visit }.toSortedMap().forEach { (number, thrown) ->
        if (winners.isNotEmpty()) return@forEach
        val playerId = thrown.first().playerId
        val start = seats[playerId] ?: return@forEach
        var seat = start.copy(visits = start.visits + 1)
        var bust = false
        var won = false
        val taken = mutableListOf<Dart>()
        for (dart in thrown.take(DARTS_PER_VISIT)) {
            taken += dart
            when (rules.game) {
                DartsGame.X01 -> {
                    val result = x01(seat, dart, rules)
                    if (result == null) {
                        bust = true
                        seat = start.copy(visits = seat.visits)
                    } else {
                        seat = result
                        won = seat.score == 0
                    }
                }
                DartsGame.CRICKET -> {
                    seat = cricket(seat, dart, seats.filterKeys { it != playerId }.values)
                    seats[playerId] = seat
                    val others = seats.filterKeys { it != playerId }.values
                    won = CricketTargets.all(seat::closed) && others.all { seat.score >= it.score }
                }
                DartsGame.AROUND_THE_CLOCK -> {
                    seat = aroundTheClock(seat, dart)
                    won = seat.next == null
                }
                DartsGame.SHANGHAI -> {
                    val target = seat.visits
                    if (dart.segment == target) seat = seat.copy(score = seat.score + dart.points)
                    // Single, double and treble of the number in one visit wins outright.
                    won = target in 1..20 && taken.filter { it.segment == target }.map { it.multiplier }.toSet() == setOf(1, 2, 3)
                }
            }
            if (bust || won) break
        }
        seats[playerId] = seat
        if (won) winners = listOf(playerId)
        visits += DartVisit(number, playerId, taken, bust, closed = bust || won || taken.size >= DARTS_PER_VISIT)
    }

    // Shanghai without a knockout: highest score after the last round, ties shared.
    if (winners.isEmpty() && rules.game == DartsGame.SHANGHAI && order.isNotEmpty() &&
        order.all { (seats.getValue(it).visits) >= rules.rounds } && visits.lastOrNull()?.closed != false
    ) {
        val best = order.maxOf { seats.getValue(it).score }
        winners = order.filter { seats.getValue(it).score == best }
        return DartsState(seats, visits, winners, finished = true)
    }
    return DartsState(seats, visits, winners, finished = winners.isNotEmpty())
}

private fun initialSeat(rules: DartsRules) = when (rules.game) {
    DartsGame.X01 -> DartsSeat(score = rules.start, opened = !rules.doubleIn)
    DartsGame.AROUND_THE_CLOCK -> DartsSeat(score = 0, next = 1)
    else -> DartsSeat(score = 0)
}

/** The seat after an X01 dart, or null for a bust. */
private fun x01(seat: DartsSeat, dart: Dart, rules: DartsRules): DartsSeat? {
    if (!seat.opened) return if (dart.isDouble) seat.copy(score = seat.score - dart.points, opened = true).takeIf { it.score >= 0 } else seat
    val left = seat.score - dart.points
    return when {
        left < 0 -> null
        rules.doubleOut && left == 1 -> null
        left == 0 && rules.doubleOut && !dart.isDouble -> null
        else -> seat.copy(score = left)
    }
}

/** Marks past three score the number, while at least one other seat still has it open. */
private fun cricket(seat: DartsSeat, dart: Dart, others: Collection<DartsSeat>): DartsSeat {
    if (dart.segment !in CricketTargets) return seat
    val before = seat.marks[dart.segment] ?: 0
    val after = before + dart.multiplier
    val scoring = after.coerceAtLeast(CRICKET_CLOSED) - before.coerceAtLeast(CRICKET_CLOSED)
    val points = if (scoring > 0 && others.any { !it.closed(dart.segment) }) scoring * dart.segment else 0
    return seat.copy(marks = seat.marks + (dart.segment to after), score = seat.score + points)
}

/** Any hit on the current number counts once, whatever the ring. */
private fun aroundTheClock(seat: DartsSeat, dart: Dart): DartsSeat {
    val target = seat.next ?: return seat
    if (dart.segment != target) return seat
    val next = when (target) {
        20 -> BULL
        BULL -> null
        else -> target + 1
    }
    return seat.copy(next = next, score = seat.score + 1)
}

/** The number a seat aims at next, for the board's header ("T20", "Bull"…); null when not one number. */
fun DartsState.targetFor(playerId: String, rules: DartsRules): Int? {
    val seat = seats[playerId] ?: return null
    return when (rules.game) {
        DartsGame.AROUND_THE_CLOCK -> seat.next
        DartsGame.SHANGHAI -> {
            val open = openVisit?.takeIf { it.playerId == playerId }
            (if (open != null) seat.visits else seat.visits + 1).takeIf { it <= rules.rounds }
        }
        else -> null
    }
}

/** Short label for a dart: "T20", "D16", "Bull", "Miss". */
fun Dart.label(): String = when {
    segment == 0 -> "Miss"
    segment == BULL -> if (multiplier == 2) "Bull" else "25"
    else -> listOf("", "", "D", "T")[multiplier] + segment
}

/**
 * A way to finish [score] in at most [dartsLeft] darts (fewest darts first, big trebles
 * first), e.g. 170 → T20 T20 Bull; null when there is none. With [doubleOut] the last
 * dart must be a double.
 */
fun checkout(score: Int, dartsLeft: Int = DARTS_PER_VISIT, doubleOut: Boolean = true): List<String>? {
    if (score <= 0 || dartsLeft <= 0) return null
    val finishers = if (doubleOut) Doubles else Throws
    for (n in 1..dartsLeft) {
        search(score, n, finishers)?.let { return it.map(Dart::label) }
    }
    return null
}

private fun search(score: Int, darts: Int, finishers: List<Dart>): List<Dart>? {
    if (darts == 1) return finishers.firstOrNull { it.points == score }?.let(::listOf)
    for (first in Throws) {
        if (first.points >= score) continue
        search(score - first.points, darts - 1, finishers)?.let { return listOf(first) + it }
    }
    return null
}

private fun d(segment: Int, multiplier: Int) = Dart("", "", 0, 0, segment, multiplier)

/** Every scoring dart, in the order a checkout prefers them for setting up. */
private val Throws: List<Dart> =
    (20 downTo 1).map { d(it, 3) } + d(BULL, 2) + d(BULL, 1) + (20 downTo 1).map { d(it, 1) } + (20 downTo 1).map { d(it, 2) }

/** Finishing doubles, the classic ones first. */
private val Doubles: List<Dart> = (listOf(20, 16, 18, 12, 10, 8) + (19 downTo 1).filter { it !in listOf(16, 18, 12, 10, 8) })
    .map { d(it, 2) } + d(BULL, 2)
