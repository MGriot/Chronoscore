package io.chronoscore.data

import app.cash.sqldelight.db.SqlDriver
import io.chronoscore.db.ChronoscoreDatabase
import io.chronoscore.db.Session
import io.chronoscore.engine.nowMillis
import io.chronoscore.model.GameSession
import io.chronoscore.model.Player
import io.chronoscore.model.ScoreHistoryEntry
import io.chronoscore.model.SessionSummary
import io.chronoscore.model.SubCounter

/**
 * Persists every match as a full snapshot; each [save] rewrites the session's rows in one
 * transaction and marks it most recently used. Matches are kept until deleted, so any of
 * them can be reopened and continued with its own log.
 */
class SessionRepository(
    private val driver: SqlDriver,
    private val clock: () -> Long = ::nowMillis,
) : AutoCloseable {

    private val queries = ChronoscoreDatabase(driver).chronoscoreQueries

    fun save(session: GameSession) = queries.transaction {
        val id = session.id
        queries.upsertSession(
            id, session.title, session.startingScore.toLong(), session.round.toLong(),
            session.timestamp, clock(), session.gameName,
        )
        queries.deleteHistory(id)
        queries.deleteSubCounters(id)
        queries.deletePlayers(id)
        session.players.forEach { p ->
            queries.insertPlayer(p.id, id, p.seatIndex.toLong(), p.name, p.colorHex, p.primaryScore.toLong(), if (p.isFlipped) 1L else 0L)
            p.subCounters.forEachIndexed { pos, c ->
                queries.insertSubCounter(c.id, id, p.id, pos.toLong(), c.name, c.value.toLong())
            }
        }
        session.historyLog.forEachIndexed { pos, e ->
            queries.insertHistory(
                e.id, id, pos.toLong(), e.timestamp, e.round.toLong(), e.playerId, e.subCounterId,
                e.delta.toLong(), e.resultingValue.toLong(), e.batchId,
            )
        }
    }

    fun loadLatest(): GameSession? = queries.latestSession().executeAsOneOrNull()?.let(::hydrate)

    fun load(sessionId: String): GameSession? = queries.sessionById(sessionId).executeAsOneOrNull()?.let(::hydrate)

    /** All saved matches, most recently played first. */
    fun summaries(): List<SessionSummary> {
        val names = queries.playerNamesBySeat().executeAsList().groupBy({ it.session_id }, { it.name })
        return queries.sessionSummaries().executeAsList().map { r ->
            SessionSummary(
                id = r.id,
                title = r.title,
                gameName = r.game_name,
                round = r.round.toInt(),
                createdAt = r.timestamp,
                updatedAt = r.updated_at,
                playerNames = names[r.id].orEmpty(),
                entryCount = r.entry_count.toInt(),
            )
        }
    }

    fun delete(sessionId: String) = queries.deleteSession(sessionId)

    /** Child rows still attached to a session (used to verify cascade deletes). */
    fun childRowCount(sessionId: String): Long = queries.countChildRows(sessionId).executeAsOne()

    fun setting(key: String): String? = queries.getSetting(key).executeAsOneOrNull()

    fun putSetting(key: String, value: String) = queries.putSetting(key, value)

    override fun close() = driver.close()

    private fun hydrate(s: Session): GameSession? {
        val counters = queries.subCountersFor(s.id).executeAsList().groupBy { it.player_id }
        val players = queries.playersFor(s.id).executeAsList().map { p ->
            Player(
                id = p.id,
                name = p.name,
                colorHex = p.color_hex,
                primaryScore = p.primary_score.toInt(),
                isFlipped = p.is_flipped != 0L,
                seatIndex = p.seat_index.toInt(),
                subCounters = counters[p.id].orEmpty().map { SubCounter(it.id, it.name, it.counter_value.toInt()) },
            )
        }
        if (players.isEmpty()) return null
        val history = queries.historyFor(s.id).executeAsList().map { h ->
            ScoreHistoryEntry(
                h.id, h.timestamp, h.round.toInt(), h.player_id, h.sub_counter_id,
                h.delta.toInt(), h.resulting_value.toInt(), h.batch_id,
            )
        }
        return GameSession(
            id = s.id,
            title = s.title,
            players = players,
            historyLog = history,
            round = s.round.toInt(),
            startingScore = s.starting_score.toInt(),
            timestamp = s.timestamp,
            gameName = s.game_name,
        )
    }
}
