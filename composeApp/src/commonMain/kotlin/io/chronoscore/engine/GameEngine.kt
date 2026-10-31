package io.chronoscore.engine

import io.chronoscore.model.Accents
import io.chronoscore.model.CounterTarget
import io.chronoscore.model.Dart
import io.chronoscore.model.DartsGame
import io.chronoscore.model.DartsRules
import io.chronoscore.model.GameSession
import io.chronoscore.model.Limits
import io.chronoscore.model.MatchOptions
import io.chronoscore.model.Player
import io.chronoscore.model.ScoreHistoryEntry
import io.chronoscore.model.SubCounter
import io.chronoscore.model.TeamMember
import io.chronoscore.model.TimeBonus
import io.chronoscore.model.Timing
import io.chronoscore.model.WinRule
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
        val game = gameName.cleanGameName()
        val opts = options.sanitized()
        val score = opts.darts?.startScore ?: startingScore.clampValue()
        val now = clock()
        val layout = counters.cleanCounterNames()
        val players = List(count) { seat ->
            newPlayer(seat, score, opts).copy(subCounters = layout.map { SubCounter(newId(), it) })
        }
        return GameSession(
            id = newId(),
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
            session.copy(gameName = game)
        }

        is GameAction.SetOptions -> setOptions(session, action.options.sanitized())

        GameAction.ToggleClock -> {
            val now = clock()
            val t = session.timing
            session.copy(timing = if (t.isRunning) t.paused(now) else t.copy(runningSince = now))
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

        is GameAction.SetTeamMembers -> session.updatePlayer(action.playerId) { p ->
            p.copy(members = action.members.cleanMembers())
        }

        is GameAction.SetRoundPoints -> {
            val round = action.round.coerceIn(1, session.round)
            val current = session.pointsIn(round, action.playerId)
            adjust(session, action.playerId, CounterTarget.Primary, action.points - current, coalesce = false, round = round)
        }

        is GameAction.SetRoundPointsBatch -> setRoundPointsBatch(session, action)

        is GameAction.SetMonarch -> session.copy(monarchId = action.playerId?.takeIf { session.player(it) != null })
        is GameAction.SetInitiative -> session.copy(initiativeId = action.playerId?.takeIf { session.player(it) != null })
        is GameAction.SetDayNight -> session.copy(dayNight = action.dayNight)

        is GameAction.ThrowDart -> throwDart(session, action.segment, action.multiplier)
        GameAction.EndVisit -> endVisit(session)

        GameAction.NextRound -> session.copy(round = session.round + 1)

        GameAction.Undo -> if (session.options.darts != null && session.darts.isNotEmpty()) undoDart(session) else undo(session)

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
                darts = emptyList(),
            )
        }

        is GameAction.NewGame -> {
            val options = action.options ?: session.options
            val fresh = newSession(action.playerCount, action.startingScore, action.gameName, options, action.counters.orEmpty())
            val teamSize = action.teamSize?.coerceIn(1, Limits.MAX_TEAM_MEMBERS)
            // Keep names, colors, profiles and (unless counters were given) counter layout for seats that carry over.
            fresh.copy(players = fresh.players.map { p ->
                val old = session.players.getOrNull(p.seatIndex)
                val carried = if (old == null) p else p.copy(
                    name = old.name,
                    colorHex = old.colorHex,
                    rotation = old.rotation,
                    profileId = old.profileId,
                    subCounters = if (action.counters == null) old.subCounters.map { it.copy(value = 0) } else p.subCounters,
                    members = old.members,
                )
                when {
                    teamSize == null -> carried
                    teamSize == 1 -> carried.copy(name = soloSeatName(old, p.seatIndex), members = emptyList())
                    // A team of the same size carries over as it was.
                    carried.members.size == teamSize -> carried
                    else -> carried.copy(
                        name = "Team ${p.seatIndex + 1}",
                        profileId = null,
                        members = List(teamSize) { TeamMember("Player ${p.seatIndex * teamSize + it + 1}") },
                    )
                }
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

    private fun setRoundPointsBatch(session: GameSession, action: GameAction.SetRoundPointsBatch): GameSession {
        val round = action.round.coerceIn(1, session.round)
        val batch = newId()
        val now = clock()
        var result = session
        val entries = mutableListOf<ScoreHistoryEntry>()
        session.players.filter { it.id in action.points }.forEach { p ->
            val scored = session.pointsIn(round, p.id)
            val next = (p.primaryScore + action.points.getValue(p.id) - scored).clampValue()
            if (next == p.primaryScore) return@forEach
            result = result.withValue(p.id, CounterTarget.Primary, next)
            entries += ScoreHistoryEntry(newId(), now, round, p.id, null, next - p.primaryScore, next, batch)
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
            darts = passed.darts.filterNot { it.playerId == playerId },
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
            !options.usesClock && s.timing.isRunning -> s.timing.paused(now)
            else -> s.timing
        }
        val control = options.timeControl
        val players = when {
            control == null -> s.players.map { it.copy(clockMillis = null) }
            // A new or resized bank starts every seat over; bonus changes apply from the next turn.
            control.bankSeconds != s.options.timeControl?.bankSeconds -> s.players.map { it.copy(clockMillis = control.bankMillis) }
            else -> s.players
        }
        val updated = s.copy(options = options, timing = timing, players = players)
        // New darts rules start the scores over: old darts and points don't carry into another game.
        val darts = options.darts
        if (darts == null || darts == session.options.darts) return updated
        val start = darts.startScore
        return updated.copy(
            players = updated.players.map { it.copy(primaryScore = start) },
            startingScore = start,
            darts = emptyList(),
            historyLog = emptyList(),
            round = 1,
        )
    }

    private fun throwDart(session: GameSession, segment: Int, multiplier: Int): GameSession {
        if (session.options.darts == null || session.outcome().finished) return session
        val active = session.activePlayerId?.let(session::player) ?: return session
        if (segment !in 0..20 && segment != BULL) return session
        val ring = when (segment) {
            0 -> 1
            BULL -> multiplier.coerceIn(1, 2)
            else -> multiplier.coerceIn(1, 3)
        }
        val open = session.dartsState().openVisit?.takeIf { it.playerId == active.id }
        val visit = open?.number ?: ((session.darts.maxOfOrNull { it.visit } ?: 0) + 1)
        val dart = Dart(newId(), active.id, visit, session.round, segment, ring)
        val thrown = withDartScores(session.copy(darts = session.darts + dart), batch = dart.id)
        val after = thrown.dartsState()
        return if (after.visits.last().closed && !after.finished) passTurn(thrown, forward = true) else thrown
    }

    /** Fills the active seat's visit with misses, which passes the turn. */
    private fun endVisit(session: GameSession): GameSession {
        if (session.options.darts == null) return session
        val active = session.activePlayerId ?: return session
        val thrown = session.dartsState().openVisit?.takeIf { it.playerId == active }?.darts?.size ?: 0
        return (thrown until DARTS_PER_VISIT).fold(session) { s, _ ->
            if (s.activePlayerId == active) throwDart(s, 0, 1) else s
        }
    }

    /** Takes back the last dart and gives the turn back to whoever threw it. */
    private fun undoDart(session: GameSession): GameSession {
        val last = session.darts.last()
        val trimmed = withDartScores(
            session.copy(darts = session.darts.dropLast(1), historyLog = session.historyLog.filterNot { it.batchId == last.id }),
            batch = null,
        )
        val back = if (trimmed.activePlayerId == last.playerId || trimmed.player(last.playerId) == null) trimmed
        else handOver(trimmed, last.playerId)
        return back.copy(round = last.round)
    }

    /** Sets every seat's score from its darts; with a [batch], logs the changes under it. */
    private fun withDartScores(session: GameSession, batch: String?): GameSession {
        val seats = session.dartsState().seats
        val now = clock()
        val entries = mutableListOf<ScoreHistoryEntry>()
        val players = session.players.map { p ->
            val score = seats[p.id]?.score ?: return@map p
            if (batch != null && score != p.primaryScore) {
                entries += ScoreHistoryEntry(newId(), now, session.round, p.id, null, score - p.primaryScore, score, batch)
            }
            p.copy(primaryScore = score)
        }
        return session.copy(players = players, historyLog = session.historyLog + entries)
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
        return if (passed.outcome().finished) passed.copy(timing = passed.timing.paused(now)) else passed
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

/**
 * Seat timers, the turn limit and timer mode only make sense with an active seat. Darts
 * always track turns, use the board instead of the round sheet, and decide the result themselves.
 */
fun MatchOptions.sanitized(): MatchOptions = (darts?.let { d ->
    copy(
        turnTracking = true,
        sheetMode = false,
        winRule = WinRule(),
        darts = d.copy(start = d.start.coerceIn(MIN_X01_START, MAX_X01_START), rounds = d.rounds.coerceIn(1, 20)),
    )
} ?: this).run { copy(
    handCalculator = handCalculator?.takeIf { sheetMode },
    seatTimers = seatTimers && turnTracking,
    turnLimitSeconds = turnLimitSeconds?.takeIf { turnTracking }?.coerceIn(5, Limits.MAX_TURN_LIMIT_SECONDS),
    timeControl = timeControl?.takeIf { turnTracking }?.let {
        it.copy(
            bankSeconds = it.bankSeconds.coerceIn(Limits.MIN_BANK_SECONDS, Limits.MAX_BANK_SECONDS),
            bonusSeconds = if (it.bonus == TimeBonus.NONE) 0 else it.bonusSeconds.coerceIn(0, Limits.MAX_BONUS_SECONDS),
        )
    },
    winRule = winRule.copy(target = winRule.target.coerceIn(Limits.MIN_VALUE, Limits.MAX_VALUE)),
) }

/** Low starts are allowed for checkout practice. */
private const val MIN_X01_START = 2
private const val MAX_X01_START = 1001

/** Points each seat scored in each round, from the score changes logged in that round. */
fun GameSession.roundPoints(): Map<Pair<Int, String>, Int> =
    historyLog.filter { it.subCounterId == null }
        .groupBy { it.round to it.playerId }
        .mapValues { (_, entries) -> entries.sumOf { it.delta } }

/** Points [playerId] scored in [round]. */
fun GameSession.pointsIn(round: Int, playerId: String): Int =
    historyLog.filter { it.playerId == playerId && it.subCounterId == null && it.round == round }.sumOf { it.delta }

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

private val AutoTeamName = Regex("""Team \d+""")

/**
 * Name of seat [seat] in a one-per-seat game: [old]'s name carries over, unless [old] was a team
 * or still has an automatic "Team n" name — a team's name isn't a player's.
 */
fun soloSeatName(old: Player?, seat: Int): String = when {
    old == null || old.isTeam || AutoTeamName.matches(old.name.trim()) -> "Player ${seat + 1}"
    else -> old.name
}

private fun String.cleanName(fallback: String): String =
    trim().take(Limits.MAX_NAME_LENGTH).ifEmpty { fallback }

private fun String?.cleanGameName(): String? =
    this?.trim()?.take(Limits.MAX_GAME_NAME_LENGTH)?.ifEmpty { null }

private fun List<String>.cleanCounterNames(): List<String> =
    map { it.cleanName("Counter") }.distinctBy { it.lowercase() }.take(Limits.MAX_SUB_COUNTERS)

/** Trimmed, non-empty names, at most [Limits.MAX_TEAM_MEMBERS]. */
internal fun List<TeamMember>.cleanMembers(): List<TeamMember> =
    mapNotNull { m -> m.name.trim().take(Limits.MAX_NAME_LENGTH).takeIf { it.isNotEmpty() }?.let { m.copy(name = it) } }
        .take(Limits.MAX_TEAM_MEMBERS)

private fun Player.hasCounter(name: String) = subCounters.any { it.name.equals(name, ignoreCase = true) }
