package io.chronoscore.data

import app.cash.sqldelight.db.SqlDriver
import io.chronoscore.db.ChronoscoreDatabase
import io.chronoscore.db.Session
import io.chronoscore.engine.nowMillis
import io.chronoscore.model.GamePreset
import io.chronoscore.model.GameSession
import io.chronoscore.model.Player
import io.chronoscore.model.Profile
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

    fun save(session: GameSession) = queries.transaction { write(session, clock()) }

    private fun write(session: GameSession, updatedAt: Long) {
        val id = session.id
        val state = SessionState(
            session.options, session.activePlayerId, session.timing,
            session.monarchId, session.initiativeId, session.dayNight,
        )
        queries.upsertSession(
            id, session.title, session.startingScore.toLong(), session.round.toLong(),
            session.timestamp, updatedAt, session.gameName,
            ChronoJson.encodeToString(SessionState.serializer(), state),
        )
        queries.deleteHistory(id)
        queries.deleteSubCounters(id)
        queries.deletePlayers(id)
        session.players.forEach { p ->
            val extras = PlayerExtras(p.timeMillis, p.conceded, p.profileId, p.commanderDamage)
            queries.insertPlayer(
                p.id, id, p.seatIndex.toLong(), p.name, p.colorHex, p.primaryScore.toLong(), if (p.isFlipped) 1L else 0L,
                ChronoJson.encodeToString(PlayerExtras.serializer(), extras),
            )
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

    /** Every saved match in full, most recently played first (for stats and backups). */
    fun allSessions(): List<GameSession> = queries.allSessionIds().executeAsList().mapNotNull(::load)

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

    fun presets(): List<GamePreset> = queries.presets().executeAsList().mapNotNull { row ->
        runCatching { ChronoJson.decodeFromString(GamePreset.serializer(), row.body) }.getOrNull()
    }

    fun savePreset(preset: GamePreset) =
        queries.upsertPreset(preset.id, preset.name, ChronoJson.encodeToString(GamePreset.serializer(), preset), clock())

    fun deletePreset(id: String) = queries.deletePreset(id)

    fun profiles(): List<Profile> = queries.profiles().executeAsList().map { Profile(it.id, it.name, it.color_hex) }

    fun saveProfile(profile: Profile) = queries.upsertProfile(profile.id, profile.name, profile.colorHex, clock())

    fun deleteProfile(id: String) = queries.deleteProfile(id)

    fun exportBackup(): Backup = Backup(exportedAt = clock(), sessions = allSessions(), presets = presets(), profiles = profiles())

    /**
     * Adds a backup's matches, presets and profiles, replacing ones with the same id.
     * [keepSessionId] (the match on the table) is never overwritten. Returns matches imported.
     */
    fun importBackup(backup: Backup, keepSessionId: String?): Int = queries.transactionWithResult {
        backup.presets.forEach(::savePreset)
        backup.profiles.forEach(::saveProfile)
        // Older matches first, each stamped with its own start time so library order stays meaningful.
        val sessions = backup.sessions.filter { it.id != keepSessionId }.sortedBy { it.timestamp }
        sessions.forEach { write(it, it.timestamp) }
        sessions.size
    }

    override fun close() = driver.close()

    private fun hydrate(s: Session): GameSession? {
        val counters = queries.subCountersFor(s.id).executeAsList().groupBy { it.player_id }
        val players = queries.playersFor(s.id).executeAsList().map { p ->
            val extras = p.extras_json.decodeOr(PlayerExtras()) { ChronoJson.decodeFromString(PlayerExtras.serializer(), it) }
            Player(
                id = p.id,
                name = p.name,
                colorHex = p.color_hex,
                primaryScore = p.primary_score.toInt(),
                isFlipped = p.is_flipped != 0L,
                seatIndex = p.seat_index.toInt(),
                subCounters = counters[p.id].orEmpty().map { SubCounter(it.id, it.name, it.counter_value.toInt()) },
                timeMillis = extras.timeMillis,
                conceded = extras.conceded,
                profileId = extras.profileId,
                commanderDamage = extras.commanderDamage,
            )
        }
        if (players.isEmpty()) return null
        val history = queries.historyFor(s.id).executeAsList().map { h ->
            ScoreHistoryEntry(
                h.id, h.timestamp, h.round.toInt(), h.player_id, h.sub_counter_id,
                h.delta.toInt(), h.resulting_value.toInt(), h.batch_id,
            )
        }
        val state = s.state_json.decodeOr(SessionState()) { ChronoJson.decodeFromString(SessionState.serializer(), it) }
        return GameSession(
            id = s.id,
            title = s.title,
            players = players,
            historyLog = history,
            round = s.round.toInt(),
            startingScore = s.starting_score.toInt(),
            timestamp = s.timestamp,
            gameName = s.game_name,
            options = state.options,
            activePlayerId = state.activePlayerId,
            timing = state.timing,
            monarchId = state.monarchId,
            initiativeId = state.initiativeId,
            dayNight = state.dayNight,
        )
    }
}

/** Rows written by 1.0 have no JSON; a corrupt value falls back to the default too. */
private inline fun <T> String?.decodeOr(default: T, decode: (String) -> T): T =
    if (this == null) default else runCatching { decode(this) }.getOrDefault(default)
