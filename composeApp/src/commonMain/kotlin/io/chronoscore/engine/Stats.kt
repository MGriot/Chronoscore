package io.chronoscore.engine

import io.chronoscore.model.GameSession
import io.chronoscore.model.Profile

/** One player's record across saved matches. */
data class PlayerStats(
    /** Profile id, or "name:<lowercased name>" for seats not linked to a profile. */
    val key: String,
    val name: String,
    val colorHex: String,
    val isProfile: Boolean,
    val played: Int,
    /** Matches with a decided result (a win rule or concessions); wins count only these. */
    val decided: Int,
    val wins: Int,
    val averageScore: Double,
    val bestScore: Int,
    val timeMillis: Long,
) {
    val winRate: Double? get() = if (decided == 0) null else wins.toDouble() / decided
}

/**
 * Aggregates [sessions] per player: linked seats by profile, others by name (so 1.0 matches
 * count too). [game] limits it to one game name (case-insensitive); null for all games.
 * Sorted by wins, then matches played.
 */
fun computeStats(sessions: List<GameSession>, profiles: List<Profile>, game: String? = null): List<PlayerStats> {
    val byId = profiles.associateBy { it.id }
    data class Row(val key: String, val name: String, val color: String, val isProfile: Boolean, val score: Int, val won: Boolean?, val time: Long)

    val rows = sessions
        .filter { game == null || it.gameName.equals(game, ignoreCase = true) }
        .flatMap { s ->
            val outcome = s.outcome()
            s.players.flatMap { p ->
                val won = if (outcome.finished) p.id in outcome.winners else null
                // A team seat counts once for each member, with the team's score and result.
                val people = if (p.isTeam) p.members.map { it.name to it.profileId } else listOf(p.name to p.profileId)
                people.map { (name, profileId) ->
                    val profile = profileId?.let(byId::get)
                    Row(
                        key = profile?.id ?: ("name:" + name.lowercase()),
                        name = profile?.name ?: name,
                        color = profile?.colorHex ?: p.colorHex,
                        isProfile = profile != null,
                        score = p.primaryScore,
                        won = won,
                        time = p.timeMillis,
                    )
                }
            }
        }

    return rows.groupBy { it.key }.map { (key, list) ->
        val latest = list.first()
        PlayerStats(
            key = key,
            name = latest.name,
            colorHex = latest.color,
            isProfile = latest.isProfile,
            played = list.size,
            decided = list.count { it.won != null },
            wins = list.count { it.won == true },
            averageScore = list.map { it.score }.average(),
            bestScore = list.maxOf { it.score },
            timeMillis = list.sumOf { it.time },
        )
    }.sortedWith(compareByDescending<PlayerStats> { it.wins }.thenByDescending { it.played }.thenBy { it.name.lowercase() })
}
