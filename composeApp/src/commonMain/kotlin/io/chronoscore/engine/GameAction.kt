package io.chronoscore.engine

import io.chronoscore.model.CounterTarget
import io.chronoscore.model.DayNight
import io.chronoscore.model.MatchOptions
import io.chronoscore.model.Profile

sealed interface GameAction {
    /** Commander targets also take the same amount off the seat's life, as one undo step. */
    data class Adjust(val playerId: String, val target: CounterTarget, val delta: Int) : GameAction
    data class SetValue(val playerId: String, val target: CounterTarget, val value: Int) : GameAction

    data class AddSubCounter(val playerId: String, val name: String) : GameAction
    data class RemoveSubCounter(val playerId: String, val subCounterId: String) : GameAction
    data class RenameSubCounter(val playerId: String, val subCounterId: String, val name: String) : GameAction

    data class ToggleFlip(val playerId: String) : GameAction
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

    /** Marks a seat out by hand, or back in. */
    data class SetConceded(val playerId: String, val conceded: Boolean) : GameAction

    /** Links a seat to a saved player (taking its name and color), or unlinks it with null. */
    data class SetProfile(val playerId: String, val profile: Profile?) : GameAction

    /** Round sheet: sets a seat's points for [round]; the change is logged in that round. */
    data class SetRoundPoints(val playerId: String, val round: Int, val points: Int) : GameAction

    data class SetMonarch(val playerId: String?) : GameAction
    data class SetInitiative(val playerId: String?) : GameAction
    data class SetDayNight(val dayNight: DayNight?) : GameAction

    data object NextRound : GameAction
    data object Undo : GameAction
    data object ResetScores : GameAction

    /**
     * Starts a fresh match. [counters] null keeps each carried-over seat's counter layout;
     * otherwise every seat gets exactly these counters. [options] null keeps the current ones.
     */
    data class NewGame(
        val playerCount: Int,
        val startingScore: Int,
        val gameName: String? = null,
        val counters: List<String>? = null,
        val options: MatchOptions? = null,
    ) : GameAction
}

enum class BulkMode {
    /** Set every seat to the value. */
    SET,

    /** Add the (possibly negative) value to every seat. */
    ADD,
}
