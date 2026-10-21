package io.chronoscore.engine

import io.chronoscore.model.Accents
import io.chronoscore.model.CounterTarget
import io.chronoscore.model.GameSession
import io.chronoscore.model.Limits
import io.chronoscore.model.Player
import io.chronoscore.model.ScoreHistoryEntry
import io.chronoscore.model.SubCounter
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalTime::class)
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

@OptIn(ExperimentalUuidApi::class)
fun randomId(): String = Uuid.random().toHexString().take(16)

/**
 * Pure state reducer for a match. All mutations go through [reduce]; the engine never
 * mutates its input. Clock and id generation are injectable so tests are deterministic.
 */
class GameEngine(
    private val clock: () -> Long = ::nowMillis,
    private val newId: () -> String = ::randomId,
    /** Consecutive adjustments to the same counter within this window merge into one log entry. */
    private val coalesceWindowMs: Long = 1_500,
) {

    fun newSession(playerCount: Int = 4, startingScore: Int = 20, gameName: String? = null): GameSession {
        val count = playerCount.coerceIn(Limits.MIN_PLAYERS, Limits.MAX_PLAYERS)
        val score = startingScore.clampValue()
        val game = gameName.cleanGameName()
        return GameSession(
            id = newId(),
            title = game ?: "Match",
            players = List(count) { seat -> newPlayer(seat, score) },
            startingScore = score,
            timestamp = clock(),
            gameName = game,
        )
    }

    fun reduce(session: GameSession, action: GameAction): GameSession = when (action) {
        is GameAction.Adjust -> adjust(session, action.playerId, action.target, action.delta, coalesce = true)
        is GameAction.SetValue -> {
            val current = session.player(action.playerId)?.valueOf(action.target)
            if (current == null) session
            else adjust(session, action.playerId, action.target, action.value.clampValue() - current, coalesce = false)
        }

        is GameAction.AddSubCounter -> session.updatePlayer(action.playerId) { p ->
            if (p.subCounters.size >= Limits.MAX_SUB_COUNTERS) p
            else p.copy(subCounters = p.subCounters + SubCounter(newId(), action.name.cleanName("Counter")))
        }

        is GameAction.RemoveSubCounter -> session.updatePlayer(action.playerId) { p ->
            p.copy(subCounters = p.subCounters.filterNot { it.id == action.subCounterId })
        }.let { s ->
            s.copy(historyLog = s.historyLog.filterNot {
                it.playerId == action.playerId && it.subCounterId == action.subCounterId
            })
        }

        is GameAction.RenameSubCounter -> session.updatePlayer(action.playerId) { p ->
            p.copy(subCounters = p.subCounters.map {
                if (it.id == action.subCounterId) it.copy(name = action.name.cleanName(it.name)) else it
            })
        }

        is GameAction.ToggleFlip -> session.updatePlayer(action.playerId) { it.copy(isFlipped = !it.isFlipped) }
        is GameAction.RenamePlayer -> session.updatePlayer(action.playerId) { it.copy(name = action.name.cleanName(it.name)) }
        is GameAction.SetColor -> session.updatePlayer(action.playerId) { it.copy(colorHex = action.colorHex) }

        GameAction.AddPlayer -> {
            if (session.players.size >= Limits.MAX_PLAYERS) session
            else {
                val seat = session.players.size
                val used = session.players.map { it.colorHex }.toSet()
                val color = Accents.all.firstOrNull { it !in used } ?: Accents.all[seat % Accents.all.size]
                session.copy(players = session.players + newPlayer(seat, session.startingScore).copy(colorHex = color))
            }
        }

        is GameAction.RemovePlayer -> {
            if (session.players.size <= Limits.MIN_PLAYERS || session.player(action.playerId) == null) session
            else session.copy(
                players = session.players
                    .filterNot { it.id == action.playerId }
                    .mapIndexed { index, p -> p.copy(seatIndex = index) },
                historyLog = session.historyLog.filterNot { it.playerId == action.playerId },
            )
        }

        is GameAction.BulkEdit -> bulkEdit(session, action)

        is GameAction.SetCounterOnAllSeats -> {
            val name = action.name.cleanName("Counter")
            if (action.enabled) session.copy(players = session.players.map { p ->
                if (p.hasCounter(name) || p.subCounters.size >= Limits.MAX_SUB_COUNTERS) p
                else p.copy(subCounters = p.subCounters + SubCounter(newId(), name))
            })
            else {
                val removed = session.players.flatMap { p ->
                    p.subCounters.filter { it.name.equals(name, ignoreCase = true) }.map { p.id to it.id }
                }.toSet()
                session.copy(
                    players = session.players.map { p ->
                        p.copy(subCounters = p.subCounters.filterNot { it.name.equals(name, ignoreCase = true) })
                    },
                    historyLog = session.historyLog.filterNot { (it.playerId to it.subCounterId) in removed },
                )
            }
        }

        is GameAction.SetGameName -> {
            val game = action.gameName.cleanGameName()
            session.copy(gameName = game, title = game ?: "Match")
        }

        GameAction.NextRound -> session.copy(round = session.round + 1)

        GameAction.Undo -> undo(session)

        GameAction.ResetScores -> session.copy(
            players = session.players.map { p ->
                p.copy(
                    primaryScore = session.startingScore,
                    subCounters = p.subCounters.map { it.copy(value = 0) },
                )
            },
            historyLog = emptyList(),
            round = 1,
        )

        is GameAction.NewGame -> {
            val fresh = newSession(action.playerCount, action.startingScore, action.gameName)
            // Keep names, colors and sub-counter layout for seats that carry over.
            fresh.copy(players = fresh.players.map { p ->
                val old = session.players.getOrNull(p.seatIndex) ?: return@map p
                p.copy(
                    name = old.name,
                    colorHex = old.colorHex,
                    isFlipped = old.isFlipped,
                    subCounters = old.subCounters.map { it.copy(value = 0) },
                )
            })
        }
    }

    private fun adjust(
        session: GameSession,
        playerId: String,
        target: CounterTarget,
        delta: Int,
        coalesce: Boolean,
    ): GameSession {
        val player = session.player(playerId) ?: return session
        val current = player.valueOf(target) ?: return session
        val next = (current.toLong() + delta).coerceIn(Limits.MIN_VALUE.toLong(), Limits.MAX_VALUE.toLong()).toInt()
        val applied = next - current
        if (applied == 0) return session

        val now = clock()
        val subId = (target as? CounterTarget.Sub)?.subCounterId
        val last = session.historyLog.lastOrNull()
        val merge = coalesce && last != null && last.batchId == null &&
            last.playerId == playerId &&
            last.subCounterId == subId &&
            last.round == session.round &&
            now - last.timestamp in 0..coalesceWindowMs

        val history = if (merge) {
            val mergedDelta = last!!.delta + applied
            val rest = session.historyLog.dropLast(1)
            if (mergedDelta == 0) rest
            else rest + last.copy(timestamp = now, delta = mergedDelta, resultingValue = next)
        } else {
            session.historyLog + ScoreHistoryEntry(
                id = newId(),
                timestamp = now,
                round = session.round,
                playerId = playerId,
                subCounterId = subId,
                delta = applied,
                resultingValue = next,
            )
        }

        return session.withValue(playerId, target, next).copy(historyLog = history)
    }

    private fun bulkEdit(session: GameSession, action: GameAction.BulkEdit): GameSession {
        val batch = newId()
        val now = clock()
        val ids = action.playerIds.toSet()
        var result = session
        val entries = mutableListOf<ScoreHistoryEntry>()
        session.players.filter { it.id in ids }.forEach { p ->
            val target = if (action.counterName == null) CounterTarget.Primary
            else p.subCounters.firstOrNull { it.name.equals(action.counterName, ignoreCase = true) }
                ?.let { CounterTarget.Sub(it.id) } ?: return@forEach
            val current = p.valueOf(target) ?: return@forEach
            val next = when (action.mode) {
                BulkMode.SET -> action.value
                BulkMode.ADD -> current + action.value
            }.clampValue()
            if (next == current) return@forEach
            result = result.withValue(p.id, target, next)
            entries += ScoreHistoryEntry(
                id = newId(),
                timestamp = now,
                round = session.round,
                playerId = p.id,
                subCounterId = (target as? CounterTarget.Sub)?.subCounterId,
                delta = next - current,
                resultingValue = next,
                batchId = batch,
            )
        }
        return result.copy(historyLog = session.historyLog + entries)
    }

    private fun undo(session: GameSession): GameSession {
        val last = session.historyLog.lastOrNull() ?: return session
        val step = if (last.batchId == null) listOf(last)
        else session.historyLog.takeLastWhile { it.batchId == last.batchId }
        var result = session.copy(historyLog = session.historyLog.dropLast(step.size))
        step.asReversed().forEach { e ->
            val current = result.player(e.playerId)?.valueOf(e.target) ?: return@forEach
            result = result.withValue(e.playerId, e.target, current - e.delta)
        }
        return result
    }

    private fun newPlayer(seat: Int, score: Int) = Player(
        id = newId(),
        name = "Player ${seat + 1}",
        colorHex = Accents.all[seat % Accents.all.size],
        primaryScore = score,
        seatIndex = seat,
    )
}

private fun GameSession.updatePlayer(id: String, transform: (Player) -> Player): GameSession =
    copy(players = players.map { if (it.id == id) transform(it) else it })

private fun GameSession.withValue(playerId: String, target: CounterTarget, value: Int): GameSession =
    updatePlayer(playerId) { p ->
        when (target) {
            CounterTarget.Primary -> p.copy(primaryScore = value)
            is CounterTarget.Sub -> p.copy(subCounters = p.subCounters.map {
                if (it.id == target.subCounterId) it.copy(value = value) else it
            })
        }
    }

private fun Int.clampValue() = coerceIn(Limits.MIN_VALUE, Limits.MAX_VALUE)

private fun String.cleanName(fallback: String): String =
    trim().take(Limits.MAX_NAME_LENGTH).ifEmpty { fallback }

private fun String?.cleanGameName(): String? =
    this?.trim()?.take(Limits.MAX_GAME_NAME_LENGTH)?.ifEmpty { null }

private fun Player.hasCounter(name: String) = subCounters.any { it.name.equals(name, ignoreCase = true) }
