package io.chronoscore.data

import io.chronoscore.engine.outcome
import io.chronoscore.engine.normalizedRotation
import io.chronoscore.engine.sanitized
import io.chronoscore.model.Accents
import io.chronoscore.model.CounterTarget
import io.chronoscore.model.Dart
import io.chronoscore.model.DayNight
import io.chronoscore.model.GamePreset
import io.chronoscore.model.GameSession
import io.chronoscore.model.Limits
import io.chronoscore.model.MatchOptions
import io.chronoscore.engine.cleanMembers
import io.chronoscore.model.Profile
import io.chronoscore.model.TeamMember
import io.chronoscore.model.Timing
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Lenient on read (unknown keys from newer versions are ignored), compact on write. */
internal val ChronoJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    coerceInputValues = true
}

/** Match-level extras stored in session.state_json. */
@Serializable
internal data class SessionState(
    val options: MatchOptions = MatchOptions(),
    val activePlayerId: String? = null,
    val timing: Timing = Timing(),
    val monarchId: String? = null,
    val initiativeId: String? = null,
    val dayNight: DayNight? = null,
    val darts: List<Dart> = emptyList(),
)

/** Seat-level extras stored in player.extras_json. */
@Serializable
internal data class PlayerExtras(
    val timeMillis: Long = 0,
    val conceded: Boolean = false,
    val profileId: String? = null,
    val commanderDamage: Map<String, Int> = emptyMap(),
    val clockMillis: Long? = null,
    /** Seat rotation in degrees; null in rows from before 1.2, which only had is_flipped. */
    val rotation: Int? = null,
    val members: List<TeamMember> = emptyList(),
)

/** A full local backup: every saved match, preset and player profile. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Backup(
    // Always written, so a backup is recognizable; a file without it is rejected on import.
    @EncodeDefault val format: String = FORMAT,
    @EncodeDefault val version: Int = 1,
    val exportedAt: Long = 0,
    val sessions: List<GameSession> = emptyList(),
    val presets: List<GamePreset> = emptyList(),
    val profiles: List<Profile> = emptyList(),
) {
    companion object {
        const val FORMAT = "chronoscore-backup"
    }
}

object BackupCodec {
    private val pretty = Json(ChronoJson) { prettyPrint = true }

    fun encode(backup: Backup): String = pretty.encodeToString(Backup.serializer(), backup)

    /** Parses and sanitizes a backup; throws [IllegalArgumentException] if it isn't one. */
    fun decode(text: String): Backup {
        val backup = try {
            val format = ChronoJson.parseToJsonElement(text).jsonObject["format"]?.jsonPrimitive?.contentOrNull
            require(format == Backup.FORMAT)
            ChronoJson.decodeFromString(Backup.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Not a Chronoscore backup file", e)
        }
        return backup.copy(
            sessions = backup.sessions.mapNotNull { it.sanitized() },
            presets = backup.presets.map { it.sanitized() },
            profiles = backup.profiles.map { it.copy(name = it.name.trim().take(Limits.MAX_NAME_LENGTH).ifEmpty { "Player" }) },
        )
    }
}

/** Clamps imported data to what the app can show; drops matches it can't. */
internal fun GameSession.sanitized(): GameSession? {
    if (players.isEmpty() || players.size > Limits.MAX_PLAYERS) return null
    fun Int.clamp() = coerceIn(Limits.MIN_VALUE, Limits.MAX_VALUE)
    val fixed = players.sortedBy { it.seatIndex }.mapIndexed { i, p ->
        p.copy(
            seatIndex = i,
            name = p.name.trim().take(Limits.MAX_NAME_LENGTH).ifEmpty { "Player ${i + 1}" },
            colorHex = p.colorHex.takeIf { Regex("#[0-9A-Fa-f]{6}").matches(it) } ?: Accents.all[i % Accents.all.size],
            primaryScore = p.primaryScore.clamp(),
            subCounters = p.subCounters.take(Limits.MAX_SUB_COUNTERS).map { it.copy(value = it.value.clamp()) },
            timeMillis = p.timeMillis.coerceAtLeast(0),
            rotation = normalizedRotation(p.rotation),
            members = p.members.cleanMembers(),
        )
    }
    val ids = fixed.map { it.id }.toSet()
    if (ids.size != fixed.size) return null
    val opts = options.sanitized()
    // Timer mode needs a bank on every seat; without it the banks are meaningless.
    val bank = opts.timeControl?.bankMillis
    return copy(
        players = fixed.map { it.copy(clockMillis = bank?.let { b -> it.clockMillis ?: b }) },
        historyLog = historyLog.filter { it.playerId in ids },
        round = round.coerceAtLeast(1),
        options = opts,
        activePlayerId = activePlayerId?.takeIf { it in ids },
        monarchId = monarchId?.takeIf { it in ids },
        initiativeId = initiativeId?.takeIf { it in ids },
        darts = if (opts.darts == null) emptyList() else darts.filter { it.playerId in ids },
    )
}

internal fun GamePreset.sanitized(): GamePreset = copy(
    name = name.trim().take(Limits.MAX_GAME_NAME_LENGTH).ifEmpty { "Preset" },
    playerCount = playerCount.coerceIn(Limits.MIN_PLAYERS, Limits.MAX_PLAYERS),
    startingScore = startingScore.coerceIn(Limits.MIN_VALUE, Limits.MAX_VALUE),
    counters = counters.take(Limits.MAX_SUB_COUNTERS),
    options = options.sanitized(),
    teamSize = teamSize?.coerceIn(1, Limits.MAX_TEAM_MEMBERS),
)

/**
 * One match as CSV for a spreadsheet: every logged change in order, then the final standings.
 * [formatTime] renders entry timestamps.
 */
fun matchCsv(session: GameSession, formatTime: (Long) -> String): String {
    val players = session.players.associateBy { it.id }
    fun counterName(playerId: String, target: CounterTarget) = when (target) {
        CounterTarget.Primary -> "Score"
        is CounterTarget.Sub -> players[playerId]?.subCounters?.firstOrNull { it.id == target.subCounterId }?.name ?: "Counter"
        is CounterTarget.Commander -> "Commander damage from " + (players[target.fromPlayerId]?.name ?: "?")
    }
    val outcome = session.outcome()
    return buildString {
        appendCsvRow("Match", session.title)
        appendCsvRow("Started", formatTime(session.timestamp))
        appendCsvRow("Rounds", session.round.toString())
        append(CRLF)
        appendCsvRow("Round", "Time", "Player", "Counter", "Change", "Result")
        session.historyLog.forEach { e ->
            appendCsvRow(
                e.round.toString(), formatTime(e.timestamp), players[e.playerId]?.name ?: "?",
                counterName(e.playerId, e.target), e.delta.toString(), e.resultingValue.toString(),
            )
        }
        append(CRLF)
        val counters = session.players.flatMap { p -> p.subCounters.map { it.name } }.distinctBy { it.lowercase() }
        val teams = session.players.any { it.isTeam }
        val header = listOf("Player") + (if (teams) listOf("Members") else emptyList()) + "Score" + counters + "Status"
        appendCsvRow(*header.toTypedArray())
        session.players.forEach { p ->
            val status = when (p.id) {
                in outcome.winners -> "Winner"
                in outcome.eliminated -> "Out"
                else -> ""
            }
            val values = counters.map { name -> p.subCounters.firstOrNull { it.name.equals(name, true) }?.value?.toString().orEmpty() }
            val members = if (teams) listOf(p.members.joinToString(" & ") { it.name }) else emptyList()
            appendCsvRow(*(listOf(p.name) + members + p.primaryScore.toString() + values + status).toTypedArray())
        }
    }
}

private fun StringBuilder.appendCsvRow(vararg cells: String) {
    cells.joinTo(this, ",") { cell ->
        if (cell.any { it == ',' || it == '"' || it == '\n' }) "\"" + cell.replace("\"", "\"\"") + "\"" else cell
    }
    append(CRLF)
}

/** CSV rows end with CRLF (RFC 4180), which spreadsheet apps on every platform accept. */
private const val CRLF = "\r\n"
