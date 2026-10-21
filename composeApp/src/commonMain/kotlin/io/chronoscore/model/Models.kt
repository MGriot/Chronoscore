package io.chronoscore.model

/** What a score mutation applies to: the seat's primary score or one of its sub-counters. */
sealed interface CounterTarget {
    data object Primary : CounterTarget
    data class Sub(val subCounterId: String) : CounterTarget
}

data class SubCounter(
    val id: String,
    val name: String, // e.g., "Infect", "Tax", "VP"
    val value: Int = 0,
)

data class Player(
    val id: String,
    val name: String,
    val colorHex: String,
    val primaryScore: Int = 20,
    /**
     * Rotates the seat 180° relative to its default orientation. Seats in the top half of
     * the grid already face the far side of the table; toggling this turns them back.
     */
    val isFlipped: Boolean = false,
    val seatIndex: Int = 0,
    val subCounters: List<SubCounter> = emptyList(),
) {
    fun valueOf(target: CounterTarget): Int? = when (target) {
        CounterTarget.Primary -> primaryScore
        is CounterTarget.Sub -> subCounters.firstOrNull { it.id == target.subCounterId }?.value
    }
}

data class ScoreHistoryEntry(
    val id: String,
    val timestamp: Long,
    val round: Int,
    val playerId: String,
    /** Null when the entry targets the primary score. */
    val subCounterId: String?,
    val delta: Int,
    val resultingValue: Int,
    /** Entries created together (e.g. "Edit all seats") share an id and undo as one step. */
    val batchId: String? = null,
) {
    val target: CounterTarget
        get() = subCounterId?.let { CounterTarget.Sub(it) } ?: CounterTarget.Primary
}

data class GameSession(
    val id: String,
    val title: String,
    val players: List<Player>,
    val historyLog: List<ScoreHistoryEntry> = emptyList(),
    val round: Int = 1,
    val startingScore: Int = 20,
    val timestamp: Long = 0L,
    /** Optional game this match belongs to (e.g. "Catan"); groups matches in the library. */
    val gameName: String? = null,
) {
    fun player(id: String): Player? = players.firstOrNull { it.id == id }
}

object Limits {
    const val MIN_PLAYERS = 1
    const val MAX_PLAYERS = 8
    const val MAX_SUB_COUNTERS = 4
    const val MAX_VALUE = 99_999
    const val MIN_VALUE = -99_999
    const val MAX_NAME_LENGTH = 20
    const val MAX_GAME_NAME_LENGTH = 32
}

/** Seat accent colors: the four spec accents first, then four more so 8 seats stay distinct. */
object Accents {
    const val ROSE = "#E11D48"
    const val BLUE = "#2563EB"
    const val GREEN = "#16A34A"
    const val AMBER = "#D97706"
    const val VIOLET = "#7C3AED"
    const val CYAN = "#0891B2"
    const val LIME = "#65A30D"
    const val SLATE = "#64748B"

    val all = listOf(BLUE, ROSE, GREEN, AMBER, VIOLET, CYAN, LIME, SLATE)
}

/** Lightweight row for the saved-matches library. */
data class SessionSummary(
    val id: String,
    val title: String,
    val gameName: String?,
    val round: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val playerNames: List<String>,
    val entryCount: Int,
)

/** Common sub-counter names offered when adding a counter. */
val SubCounterPresets = listOf("Poison", "Commander", "Energy", "VP", "Fouls", "Tax")
