package io.chronoscore.engine

import io.chronoscore.model.CounterTarget
import io.chronoscore.model.DayNight
import io.chronoscore.model.MatchOptions
import io.chronoscore.model.Profile
import io.chronoscore.model.TeamMember

sealed interface GameAction {
    /** Commander targets also take the same amount off the seat's life, as one undo step. */
    data class Adjust(val playerId: String, val target: CounterTarget, val delta: Int) : GameAction
    data class SetValue(val playerId: String, val target: CounterTarget, val value: Int) : GameAction

    data class AddSubCounter(val playerId: String, val name: String) : GameAction
    data class RemoveSubCounter(val playerId: String, val subCounterId: String) : GameAction
    data class RenameSubCounter(val playerId: String, val subCounterId: String, val name: String) : GameAction

    /** Turns a seat by [degrees] (snapped to 30° steps; negative turns counter-clockwise). */
    data class RotateSeat(val playerId: String, val degrees: Int) : GameAction
    data class RenamePlayer(val playerId: String, val name: String) : GameAction
    data class SetColor(val playerId: String, val colorHex: String) : GameAction
    data object AddPlayer : GameAction
    data class RemovePlayer(val playerId: String) : GameAction

    /**
     * Changes many seats in one step ("Edit all seats"). [counterName] null targets the primary
     * score; otherwise the sub-counter with that name on each seat (seats without it are skipped).
     * Logged as one entry per seat sharing a batch id, so a single Undo reverts all of them.
     */
    data class BulkEdit(
        val playerIds: List<String>,
        val counterName: String?,
        val mode: BulkMode,
        val value: Int,
    ) : GameAction

    /** Adds (at 0) or removes a sub-counter, matched by name, on every seat. Full seats are skipped. */
    data class SetCounterOnAllSeats(val name: String, val enabled: Boolean) : GameAction

    data class SetGameName(val gameName: String?) : GameAction

    /** Turns optional features on or off for the current match. */
    data class SetOptions(val options: MatchOptions) : GameAction

    /** Pauses or resumes the match clock and seat timers. */
    data object ToggleClock : GameAction

    /** Passes the turn to the next seat still in; after the last seat the round advances. */
    data object NextTurn : GameAction

    /** Passes the turn back; before the first seat the round goes back one. */
    data object PreviousTurn : GameAction

    data class SetActivePlayer(val playerId: String) : GameAction

    /**
     * Timer mode: the active seat's bank ran out. The engine checks the clock itself, so a
     * late or repeated dispatch does nothing; with "out on time-out" the turn passes on.
     */
    data object TimeOut : GameAction

    /** Timer mode: adds (or with a negative value takes) time from a seat's bank. */
    data class AdjustClock(val playerId: String, val deltaMillis: Long) : GameAction

    /** Marks a seat out by hand, or back in. */
    data class SetConceded(val playerId: String, val conceded: Boolean) : GameAction

    /** Links a seat to a saved player (taking its name and color), or unlinks it with null. */
    data class SetProfile(val playerId: String, val profile: Profile?) : GameAction

    /** Makes a seat a team of [members] sharing its score; an empty list makes it a single player again. */
    data class SetTeamMembers(val playerId: String, val members: List<TeamMember>) : GameAction

    /** Round sheet: sets a seat's points for [round]; the change is logged in that round. */
    data class SetRoundPoints(val playerId: String, val round: Int, val points: Int) : GameAction

    /** Round sheet: sets several seats' points for [round] at once (a hand calculator); one Undo reverts it. */
    data class SetRoundPointsBatch(val round: Int, val points: Map<String, Int>) : GameAction

    data class SetMonarch(val playerId: String?) : GameAction
    data class SetInitiative(val playerId: String?) : GameAction
    data class SetDayNight(val dayNight: DayNight?) : GameAction

    /**
     * Darts mode: the active seat throws one dart ([segment] 1–20, 25 for the bull, 0 for a
     * miss). After the third dart, a bust or a win, the turn passes on by itself.
     */
    data class ThrowDart(val segment: Int, val multiplier: Int = 1) : GameAction

    /** Darts mode: ends the active seat's visit early; the darts not thrown count as misses. */
    data object EndVisit : GameAction

    data object NextRound : GameAction
    data object Undo : GameAction
    data object ResetScores : GameAction

    /**
     * Starts a fresh match. [counters] null keeps each carried-over seat's counter layout;
     * otherwise every seat gets exactly these counters. [options] null keeps the current ones.
     * [teamSize] makes every seat a team of that many players; null keeps seats as they were.
     */
    data class NewGame(
        val playerCount: Int,
        val startingScore: Int,
        val gameName: String? = null,
        val counters: List<String>? = null,
        val options: MatchOptions? = null,
        val teamSize: Int? = null,
    ) : GameAction
}

enum class BulkMode {
    /** Set every seat to the value. */
    SET,

    /** Add the (possibly negative) value to every seat. */
    ADD,
}
