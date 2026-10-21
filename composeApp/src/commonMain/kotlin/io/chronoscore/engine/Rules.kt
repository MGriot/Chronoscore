package io.chronoscore.engine

import io.chronoscore.model.GameSession
import io.chronoscore.model.Limits
import io.chronoscore.model.Player
import io.chronoscore.model.WinMode

/**
 * Result of applying the match's win rule to the current state. It is derived, never
 * stored, so Undo and manual corrections reverse eliminations and wins automatically.
 */
data class Outcome(
    /** Player ids that are out, by concession or by the rule. */
    val eliminated: Set<String>,
    /** Player ids that won; empty while the match is open, or for a draw. */
    val winners: List<String>,
    val finished: Boolean,
) {
    companion object {
        val Open = Outcome(emptySet(), emptyList(), finished = false)
    }
}

fun GameSession.outcome(): Outcome {
    val rule = options.winRule
    val eliminated = players.filter { p ->
        p.conceded || when (rule.mode) {
            WinMode.ELIMINATION -> p.primaryScore <= rule.target || (options.mtgExtras && p.isMtgDead())
            else -> false
        }
    }.map { it.id }.toSet()
    val alive = players.filter { it.id !in eliminated }

    if (rule.mode == WinMode.TARGET_SCORE) {
        val ascending = rule.target >= startingScore
        val reached = alive.any { if (ascending) it.primaryScore >= rule.target else it.primaryScore <= rule.target }
        if (reached || (players.size > 1 && alive.size <= 1)) {
            val best = if (rule.lowestWins) alive.minOfOrNull { it.primaryScore } else alive.maxOfOrNull { it.primaryScore }
            return Outcome(eliminated, alive.filter { it.primaryScore == best }.map { it.id }, finished = true)
        }
        return Outcome(eliminated, emptyList(), finished = false)
    }

    // Last seat standing; with one seat there is nobody to outlast.
    if (players.size > 1 && alive.size <= 1) return Outcome(eliminated, alive.map { it.id }, finished = true)
    return Outcome(eliminated, emptyList(), finished = false)
}

/** MTG state-based losses: 21 damage from one commander, or 10 poison. */
private fun Player.isMtgDead(): Boolean =
    commanderDamage.values.any { it >= Limits.COMMANDER_LETHAL } ||
        subCounters.any { it.name.equals("Poison", ignoreCase = true) && it.value >= Limits.POISON_LETHAL }
