package io.chronoscore.model

import kotlinx.serialization.Serializable

/** What a score mutation applies to: the seat's primary score, a sub-counter, or commander damage. */
sealed interface CounterTarget {
    data object Primary : CounterTarget
    data class Sub(val subCounterId: String) : CounterTarget

    /** Commander damage this seat has taken from [fromPlayerId]'s commander (MTG extras). */
    data class Commander(val fromPlayerId: String) : CounterTarget

    companion object {
        /** History entries store commander damage in the sub-counter column with this prefix. */
        const val COMMANDER_PREFIX = "cmd:"

        fun decode(subCounterId: String?): CounterTarget = when {
            subCounterId == null -> Primary
            subCounterId.startsWith(COMMANDER_PREFIX) -> Commander(subCounterId.removePrefix(COMMANDER_PREFIX))
            else -> Sub(subCounterId)
        }
    }
}

/** The sub-counter column value a history entry stores for [this] target. */
val CounterTarget.storedId: String?
    get() = when (this) {
        CounterTarget.Primary -> null
        is CounterTarget.Sub -> subCounterId
        is CounterTarget.Commander -> CounterTarget.COMMANDER_PREFIX + fromPlayerId
    }

@Serializable
data class SubCounter(
    val id: String,
    val name: String, // e.g., "Infect", "Tax", "VP"
    val value: Int = 0,
)

@Serializable
data class Player(
    val id: String,
    val name: String,
    val colorHex: String,
    val primaryScore: Int = 20,
    /**
     * Degrees (0–330, in steps of [Limits.ROTATION_STEP]) the seat is turned from its default
     * orientation. Seats in the top half of the grid already face the far side of the table;
     * 180 turns them back.
     */
    val rotation: Int = 0,
    val seatIndex: Int = 0,
    val subCounters: List<SubCounter> = emptyList(),
    /** Time this seat has spent as the active seat, excluding the turn in progress. */
    val timeMillis: Long = 0,
    /** Marked out by hand (conceded); rule-based eliminations are derived, see [outcome]. */
    val conceded: Boolean = false,
    /** Saved player profile this seat belongs to, for stats across matches. */
    val profileId: String? = null,
    /** Commander damage taken, keyed by the dealing seat's player id (MTG extras). */
    val commanderDamage: Map<String, Int> = emptyMap(),
    /**
     * Timer mode: time left in this seat's bank, excluding the turn in progress (see
     * [GameSession.liveClockMillis]); negative in overtime, null when timer mode is off.
     */
    val clockMillis: Long? = null,
) {
    fun valueOf(target: CounterTarget): Int? = when (target) {
        CounterTarget.Primary -> primaryScore
        is CounterTarget.Sub -> subCounters.firstOrNull { it.id == target.subCounterId }?.value
        is CounterTarget.Commander -> commanderDamage[target.fromPlayerId] ?: 0
    }
}

@Serializable
data class ScoreHistoryEntry(
    val id: String,
    val timestamp: Long,
    val round: Int,
    val playerId: String,
    /** Null when the entry targets the primary score; see [CounterTarget.storedId]. */
    val subCounterId: String?,
    val delta: Int,
    val resultingValue: Int,
    /** Entries created together (e.g. "Edit all seats") share an id and undo as one step. */
    val batchId: String? = null,
) {
    val target: CounterTarget
        get() = CounterTarget.decode(subCounterId)
}

@Serializable
enum class WinMode {
    /** No automatic result; seats can still concede. */
    NONE,

    /** The match ends when a seat reaches [WinRule.target]; best score wins. */
    TARGET_SCORE,

    /** A seat at or below [WinRule.target] is out; the last seat standing wins. */
    ELIMINATION,
}

@Serializable
data class WinRule(
    val mode: WinMode = WinMode.NONE,
    /** Target score for TARGET_SCORE, elimination threshold for ELIMINATION. */
    val target: Int = 0,
    /** TARGET_SCORE: the lowest score wins (e.g. Hearts) instead of the highest. */
    val lowestWins: Boolean = false,
)

@Serializable
enum class TimeBonus {
    NONE,

    /** Fischer: the seat gains [TimeControl.bonusSeconds] after each of its turns. */
    INCREMENT,

    /** Simple delay: the first [TimeControl.bonusSeconds] of each turn don't touch the bank. */
    DELAY,
}

/** Timer mode: every seat counts down its own bank, like a chess clock for 1–8 seats. */
@Serializable
data class TimeControl(
    val bankSeconds: Int = 600,
    val bonus: TimeBonus = TimeBonus.NONE,
    val bonusSeconds: Int = 0,
    /** A seat whose bank runs out is out; otherwise its clock just runs into overtime. */
    val outOnTimeout: Boolean = true,
) {
    val bankMillis: Long get() = bankSeconds * 1000L
    val delayMillis: Long get() = if (bonus == TimeBonus.DELAY) bonusSeconds * 1000L else 0
    val incrementMillis: Long get() = if (bonus == TimeBonus.INCREMENT) bonusSeconds * 1000L else 0
}

/** Optional features for one match. With every option off the table behaves like 1.0. */
@Serializable
data class MatchOptions(
    /** One seat is active; Previous / Next pass the turn and advance rounds. */
    val turnTracking: Boolean = false,
    /** Total match time, with pause and resume. */
    val matchClock: Boolean = false,
    /** Time each seat has spent on its turns (needs [turnTracking]). */
    val seatTimers: Boolean = false,
    /** Per-turn countdown in seconds (needs [turnTracking]); null for none. */
    val turnLimitSeconds: Int? = null,
    val winRule: WinRule = WinRule(),
    /** Scorepad layout: points entered per round, totals summed. */
    val sheetMode: Boolean = false,
    /** Commander damage, Monarch, Initiative and Day/Night. */
    val mtgExtras: Boolean = false,
    /** Timer mode (needs [turnTracking]); null for none. */
    val timeControl: TimeControl? = null,
) {
    val usesClock: Boolean
        get() = matchClock || (turnTracking && (seatTimers || turnLimitSeconds != null || timeControl != null))
}

/**
 * Match and turn time. Accumulated values exclude the running segment, which started at
 * [runningSince] (wall clock); null while paused. See [liveMatchMillis] and [liveTurnMillis].
 */
@Serializable
data class Timing(
    val matchMillis: Long = 0,
    val turnMillis: Long = 0,
    val runningSince: Long? = null,
) {
    val isRunning: Boolean get() = runningSince != null
    private fun running(now: Long) = runningSince?.let { (now - it).coerceAtLeast(0) } ?: 0
    fun liveMatchMillis(now: Long): Long = matchMillis + running(now)
    fun liveTurnMillis(now: Long): Long = turnMillis + running(now)

    /** Folds the running segment into the accumulators; keeps running from [now] if it was. */
    fun settled(now: Long): Timing =
        if (runningSince == null) this
        else Timing(liveMatchMillis(now), liveTurnMillis(now), now)
}

@Serializable
enum class DayNight { DAY, NIGHT }

@Serializable
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
    val options: MatchOptions = MatchOptions(),
    /** The seat whose turn it is; null unless [MatchOptions.turnTracking] is on. */
    val activePlayerId: String? = null,
    val timing: Timing = Timing(),
    val monarchId: String? = null,
    val initiativeId: String? = null,
    val dayNight: DayNight? = null,
) {
    fun player(id: String): Player? = players.firstOrNull { it.id == id }

    /** Timer mode: [player]'s bank right now, counting the turn in progress; null when off. */
    fun liveClockMillis(player: Player, now: Long): Long? {
        val bank = player.clockMillis ?: return null
        val control = options.timeControl ?: return bank
        if (player.id != activePlayerId) return bank
        return bank - (timing.liveTurnMillis(now) - control.delayMillis).coerceAtLeast(0)
    }
}

/** A reusable match setup: seats, starting score, counters and options. */
@Serializable
data class GamePreset(
    val id: String,
    val name: String,
    val gameName: String? = null,
    val playerCount: Int = 4,
    val startingScore: Int = 20,
    val counters: List<String> = emptyList(),
    val options: MatchOptions = MatchOptions(),
)

/** A saved player, reused across matches so stats can follow them. */
@Serializable
data class Profile(
    val id: String,
    val name: String,
    val colorHex: String,
)

object Limits {
    const val MIN_PLAYERS = 1
    const val MAX_PLAYERS = 8
    const val MAX_SUB_COUNTERS = 4
    const val MAX_VALUE = 99_999
    const val MIN_VALUE = -99_999
    const val MAX_NAME_LENGTH = 20
    const val MAX_GAME_NAME_LENGTH = 32
    const val MAX_TURN_LIMIT_SECONDS = 99 * 60
    const val MIN_BANK_SECONDS = 10
    const val MAX_BANK_SECONDS = 10 * 3600
    const val MAX_BONUS_SECONDS = 600

    /** Seats turn in steps of this many degrees. */
    const val ROTATION_STEP = 30

    /** MTG: commander damage from a single commander that knocks a player out. */
    const val COMMANDER_LETHAL = 21

    /** MTG: poison counters that knock a player out. */
    const val POISON_LETHAL = 10
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
