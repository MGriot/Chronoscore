package io.chronoscore.engine

import io.chronoscore.model.Accents
import io.chronoscore.model.CounterTarget
import io.chronoscore.model.GameSession
import io.chronoscore.model.Limits
import io.chronoscore.model.MatchOptions
import io.chronoscore.model.Player
import io.chronoscore.model.ScoreHistoryEntry
import io.chronoscore.model.SubCounter
import io.chronoscore.model.TimeBonus
import io.chronoscore.model.Timing
import io.chronoscore.model.storedId
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

    fun newSession(
        playerCount: Int = 4,
        startingScore: Int = 20,
        gameName: String? = null,
        options: MatchOptions = MatchOptions(),
        counters: List<String> = emptyList(),
    ): GameSession {
        val count = playerCount.coerceIn(Limits.MIN_PLAYERS, Limits.MAX_PLAYERS)
        val score = startingScore.clampValue()
        val game = gameName.cleanGameName()
        val opts = options.sanitized()
        val now = clock()
        val layout = counters.cleanCounterNames()
        val players = List(count) { seat ->
            newPlayer(seat, score, opts).copy(subCounters = layout.map { SubCounter(newId(), it) })
        }
        return GameSession(
            id = newId(),
            title = game ?: "Match",
            players = players,
            startingScore = score,
            timestamp = now,
            gameName = game,
            options = opts,
            activePlayerId = if (opts.turnTracking) players.first().id else null,
            timing = freshTiming(opts, now),
        )
    }

    fun reduce(session: GameSession, action: GameAction): GameSession = when (action) {
        is GameAction.Adjust -> when (val t = action.target) {
            is CounterTarget.Commander -> commanderDamage(session, action.playerId, t, action.delta)
            else -> adjust(session, action.playerId, t, action.delta, coalesce = true)
        }
        is GameAction.SetValue -> {
            val current = session.player(action.playerId)?.valueOf(action.target)
            when {
                current == null -> session
                action.target is CounterTarget.Commander ->
                    commanderDamage(session, action.playerId, action.target, action.value - current)
                else -> adjust(session, action.playerId, action.target, action.value.clampValue() - current, coalesce = false)
            }
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

        is GameAction.RotateSeat -> session.updatePlayer(action.playerId) { it.copy(rotation = normalizedRotation(it.rotation + action.degrees)) }
        is GameAction.RenamePlayer -> session.updatePlayer(action.playerId) { it.copy(name = action.name.cleanName(it.name)) }
        is GameAction.SetColor -> session.updatePlayer(action.playerId) { it.copy(colorHex = action.colorHex) }

        GameAction.AddPlayer -> {
            if (session.players.size >= Limits.MAX_PLAYERS) session
            else {
                val seat = session.players.size
                val used = session.players.map { it.colorHex }.toSet()
                val color = Accents.all.firstOrNull { it !in used } ?: Accents.all[seat % Accents.all.size]
                session.copy(players = session.players + newPlayer(seat, session.startingScore, session.options).copy(colorHex = color))
            }
        }

        is GameAction.RemovePlayer -> removePlayer(session, action.playerId)

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

        is GameAction.SetOptions -> setOptions(session, action.options.sanitized())

        GameAction.ToggleClock -> {
            val now = clock()
            val t = session.timing
            session.copy(timing = if (t.isRunning) t.settled(now).copy(runningSince = null) else t.copy(runningSince = now))
        }

        GameAction.NextTurn -> passTurn(session, forward = true)
        GameAction.PreviousTurn -> passTurn(session, forward = false)
        is GameAction.SetActivePlayer ->
            if (!session.options.turnTracking || session.player(action.playerId) == null) session
            else handOver(session, action.playerId)

        GameAction.TimeOut -> timeOut(session)

        is GameAction.AdjustClock -> session.updatePlayer(action.playerId) { p ->
            p.copy(clockMillis = p.clockMillis?.let { (it + action.deltaMillis).coerceIn(-MAX_CLOCK_MILLIS, MAX_CLOCK_MILLIS) })
        }

        is GameAction.SetConceded -> {
            val updated = session.updatePlayer(action.playerId) { it.copy(conceded = action.conceded) }
            // A seat that concedes on its own turn hands the turn on.
            if (action.conceded && session.activePlayerId == action.playerId) passTurn(updated, forward = true) else updated
        }

        is GameAction.SetProfile -> session.updatePlayer(action.playerId) { p ->
            val profile = action.profile
            if (profile == null) p.copy(profileId = null)
            else p.copy(name = profile.name.cleanName(p.name), colorHex = profile.colorHex, profileId = profile.id)
        }

        is GameAction.SetRoundPoints -> {
            val round = action.round.coerceIn(1, session.round)
            val current = session.historyLog
                .filter { it.playerId == action.playerId && it.subCounterId == null && it.round == round }
                .sumOf { it.delta }
            adjust(session, action.playerId, CounterTarget.Primary, action.points - current, coalesce = false, round = round)
        }

        is GameAction.SetMonarch -> session.copy(monarchId = action.playerId?.takeIf { session.player(it) != null })
        is GameAction.SetInitiative -> session.copy(initiativeId = action.playerId?.takeIf { session.player(it) != null })
        is GameAction.SetDayNight -> session.copy(dayNight = action.dayNight)

        GameAction.NextRound -> session.copy(round = session.round + 1)

        GameAction.Undo -> undo(session)

        GameAction.ResetScores -> {
            val now = clock()
            session.copy(
                players = session.players.map { p ->
                    p.copy(
                        primaryScore = session.startingScore,
                        subCounters = p.subCounters.map { it.copy(value = 0) },
                        timeMillis = 0,
                        conceded = false,
                        commanderDamage = emptyMap(),
                        clockMillis = session.options.timeControl?.bankMillis,
                    )
                },
                historyLog = emptyList(),
                round = 1,
                activePlayerId = if (session.options.turnTracking) session.players.first().id else null,
                timing = freshTiming(session.options, now),
                monarchId = null,
                initiativeId = null,
                dayNight = null,
            )
        }

        is GameAction.NewGame -> {
            val options = action.options ?: session.options
            val fresh = newSession(action.playerCount, action.startingScore, action.gameName, options, action.counters.orEmpty())
            // Keep names, colors, profiles and (unless counters were given) counter layout for seats that carry over.
            fresh.copy(players = fresh.players.map { p ->
                val old = session.players.getOrNull(p.seatIndex) ?: return@map p
                p.copy(
                    name = old.name,
                    colorHex = old.colorHex,
                    rotation = old.rotation,
                    profileId = old.profileId,
                    subCounters = if (action.counters == null) old.subCounters.map { it.copy(value = 0) } else p.subCounters,
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
        round: Int = session.round,
    ): GameSession {
        val player = session.player(playerId) ?: return session
        val current = player.valueOf(target) ?: return session
        val next = (current.toLong() + delta).coerceIn(Limits.MIN_VALUE.toLong(), Limits.MAX_VALUE.toLong()).toInt()
        val applied = next - current
        if (applied == 0) return session

        val now = clock()
        val subId = target.storedId
        val last = session.historyLog.lastOrNull()
        val merge = coalesce && last != null && last.batchId == null &&
            last.playerId == playerId &&
            last.subCounterId == subId &&
            last.round == round &&
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
                round = round,
                playerId = playerId,
                subCounterId = subId,
                delta = applied,
                resultingValue = next,
            )
        }

        return session.withValue(playerId, target, next).copy(historyLog = history)
    }

    /** Commander damage also costs that much life; both are logged as one undoable batch. */
    private fun commanderDamage(
        session: GameSession,
        playerId: String,
        target: CounterTarget.Commander,
        delta: Int,
    ): GameSession {
        val player = session.player(playerId) ?: return session
        if (target.fromPlayerId == playerId || session.player(target.fromPlayerId) == null) return session
        val current = player.valueOf(target) ?: 0
        val next = (current + delta).coerceIn(0, MAX_COMMANDER_DAMAGE)
        val applied = next - current
        if (applied == 0) return session
        val life = (player.primaryScore - applied).clampValue()

        val batch = newId()
        val now = clock()
        val entries = listOf(
            ScoreHistoryEntry(newId(), now, session.round, playerId, target.storedId, applied, next, batch),
            ScoreHistoryEntry(newId(), now, session.round, playerId, null, life - player.primaryScore, life, batch),
        ).filter { it.delta != 0 }
        return session
            .withValue(playerId, target, next)
            .withValue(playerId, CounterTarget.Primary, life)
            .copy(historyLog = session.historyLog + entries)
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
                subCounterId = target.storedId,
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

    private fun removePlayer(session: GameSession, playerId: String): GameSession {
        if (session.players.size <= Limits.MIN_PLAYERS || session.player(playerId) == null) return session
        // The turn moves on first, so the removed seat's turn time is credited before it goes.
        val passed = if (session.activePlayerId == playerId) passTurn(session, forward = true, countRound = false) else session
        val damageId = CounterTarget.Commander(playerId).storedId
        val remaining = passed.players
            .filterNot { it.id == playerId }
            .mapIndexed { index, p -> p.copy(seatIndex = index, commanderDamage = p.commanderDamage - playerId) }
        return passed.copy(
            players = remaining,
            historyLog = passed.historyLog.filterNot { it.playerId == playerId || it.subCounterId == damageId },
            activePlayerId = passed.activePlayerId?.takeIf { it != playerId } ?: remaining.first().id.takeIf { passed.options.turnTracking },
            monarchId = passed.monarchId?.takeIf { it != playerId },
            initiativeId = passed.initiativeId?.takeIf { it != playerId },
        )
    }

    private fun setOptions(session: GameSession, options: MatchOptions): GameSession {
        val now = clock()
        var s = session
        if (!options.turnTracking && s.activePlayerId != null) {
            // Credit the turn in progress before tracking stops.
            s = creditTurn(s, s.timing.settled(now)).copy(activePlayerId = null)
        }
        if (options.turnTracking && s.activePlayerId == null) {
            val out = s.outcome().eliminated
            s = s.copy(activePlayerId = (s.players.firstOrNull { it.id !in out } ?: s.players.first()).id)
        }
        val timing = when {
            options.usesClock && !s.options.usesClock && !s.timing.isRunning -> s.timing.copy(runningSince = now)
            !options.usesClock && s.timing.isRunning -> s.timing.settled(now).copy(runningSince = null)
            else -> s.timing
        }
        val control = options.timeControl
        val players = when {
            control == null -> s.players.map { it.copy(clockMillis = null) }
            // A new or resized bank starts every seat over; bonus changes apply from the next turn.
            control.bankSeconds != s.options.timeControl?.bankSeconds -> s.players.map { it.copy(clockMillis = control.bankMillis) }
            else -> s.players
        }
        return s.copy(options = options, timing = timing, players = players)
    }

    /** Timer mode: knocks the active seat out once its bank is empty, if the match says so. */
    private fun timeOut(session: GameSession): GameSession {
        val control = session.options.timeControl ?: return session
        val active = session.activePlayerId?.let(session::player) ?: return session
        val now = clock()
        val left = session.liveClockMillis(active, now) ?: return session
        if (!control.outOnTimeout || left > 0) return session
        val settled = creditTurn(session, session.timing.settled(now))
        val flagged = settled.updatePlayer(active.id) { it.copy(clockMillis = 0) }
        val passed = passTurn(flagged, forward = true, countRound = false)
        return if (passed.outcome().finished) passed.copy(timing = passed.timing.settled(now).copy(runningSince = null)) else passed
    }

    /** Moves the turn one seat (skipping seats that are out) and rolls the round over at the ends. */
    private fun passTurn(session: GameSession, forward: Boolean, countRound: Boolean = true): GameSession {
        if (!session.options.turnTracking || session.players.isEmpty()) return session
        val order = session.players.sortedBy { it.seatIndex }
        val start = order.indexOfFirst { it.id == session.activePlayerId }
        if (start < 0) return handOver(session, order.first().id)

        val out = session.outcome().eliminated
        val skip = if (order.all { it.id in out }) emptySet() else out
        var index = start
        var wrapped = false
        do {
            val next = if (forward) (index + 1) % order.size else (index - 1 + order.size) % order.size
            if (forward && next <= index || !forward && next >= index) wrapped = true
            index = next
        } while (order[index].id in skip && index != start)

        val round = when {
            !countRound || !wrapped -> session.round
            forward -> session.round + 1
            else -> (session.round - 1).coerceAtLeast(1)
        }
        return handOver(session, order[index].id).copy(round = round)
    }

    /** Credits the outgoing seat's turn time and starts a fresh turn for [playerId]. */
    private fun handOver(session: GameSession, playerId: String): GameSession {
        val credited = creditTurn(session, session.timing.settled(clock()))
        return credited.copy(activePlayerId = playerId)
    }

    private fun creditTurn(session: GameSession, settled: Timing): GameSession {
        val active = session.activePlayerId
        val control = session.options.timeControl
        val turn = settled.turnMillis
        val withTime = if (active == null) session
        else session.updatePlayer(active) { p ->
            val bank = p.clockMillis
            val left = if (control == null || bank == null) bank else {
                val charged = bank - (turn - control.delayMillis).coerceAtLeast(0)
                // The increment rewards finishing in time; a seat already out of time gets none.
                if (charged > 0) charged + control.incrementMillis else charged
            }
            p.copy(timeMillis = p.timeMillis + turn, clockMillis = left)
        }
        return withTime.copy(timing = settled.copy(turnMillis = 0))
    }

    private fun newPlayer(seat: Int, score: Int, options: MatchOptions) = Player(
        id = newId(),
        name = "Player ${seat + 1}",
        colorHex = Accents.all[seat % Accents.all.size],
        primaryScore = score,
        seatIndex = seat,
        clockMillis = options.timeControl?.bankMillis,
    )

    /** Timer mode starts paused, so the first tap on the clock starts the match. */
    private fun freshTiming(options: MatchOptions, now: Long) =
        Timing(runningSince = if (options.usesClock && options.timeControl == null) now else null)

    companion object {
        const val MAX_COMMANDER_DAMAGE = 99
        private const val MAX_CLOCK_MILLIS = Limits.MAX_BANK_SECONDS * 1000L
    }
}

/** Seat timers, the turn limit and timer mode only make sense with an active seat. */
fun MatchOptions.sanitized(): MatchOptions = copy(
    seatTimers = seatTimers && turnTracking,
    turnLimitSeconds = turnLimitSeconds?.takeIf { turnTracking }?.coerceIn(5, Limits.MAX_TURN_LIMIT_SECONDS),
    timeControl = timeControl?.takeIf { turnTracking }?.let {
        it.copy(
            bankSeconds = it.bankSeconds.coerceIn(Limits.MIN_BANK_SECONDS, Limits.MAX_BANK_SECONDS),
            bonusSeconds = if (it.bonus == TimeBonus.NONE) 0 else it.bonusSeconds.coerceIn(0, Limits.MAX_BONUS_SECONDS),
        )
    },
    winRule = winRule.copy(target = winRule.target.coerceIn(Limits.MIN_VALUE, Limits.MAX_VALUE)),
)

private fun GameSession.updatePlayer(id: String, transform: (Player) -> Player): GameSession =
    copy(players = players.map { if (it.id == id) transform(it) else it })

private fun GameSession.withValue(playerId: String, target: CounterTarget, value: Int): GameSession =
    updatePlayer(playerId) { p ->
        when (target) {
            CounterTarget.Primary -> p.copy(primaryScore = value)
            is CounterTarget.Sub -> p.copy(subCounters = p.subCounters.map {
                if (it.id == target.subCounterId) it.copy(value = value) else it
            })
            is CounterTarget.Commander -> p.copy(
                commanderDamage = if (value == 0) p.commanderDamage - target.fromPlayerId
                else p.commanderDamage + (target.fromPlayerId to value),
            )
        }
    }

private fun Int.clampValue() = coerceIn(Limits.MIN_VALUE, Limits.MAX_VALUE)

/** Snaps [degrees] to a rotation step in 0 until 360. */
fun normalizedRotation(degrees: Int): Int {
    val step = Limits.ROTATION_STEP
    val snapped = kotlin.math.round(degrees.toDouble() / step).toInt() * step
    return ((snapped % 360) + 360) % 360
}

private fun String.cleanName(fallback: String): String =
    trim().take(Limits.MAX_NAME_LENGTH).ifEmpty { fallback }

private fun String?.cleanGameName(): String? =
    this?.trim()?.take(Limits.MAX_GAME_NAME_LENGTH)?.ifEmpty { null }

private fun List<String>.cleanCounterNames(): List<String> =
    map { it.cleanName("Counter") }.distinctBy { it.lowercase() }.take(Limits.MAX_SUB_COUNTERS)

private fun Player.hasCounter(name: String) = subCounters.any { it.name.equals(name, ignoreCase = true) }
