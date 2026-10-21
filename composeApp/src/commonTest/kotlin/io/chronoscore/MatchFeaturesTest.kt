package io.chronoscore

import io.chronoscore.data.BackupCodec
import io.chronoscore.data.Backup
import io.chronoscore.data.matchCsv
import io.chronoscore.engine.GameAction
import io.chronoscore.engine.GameEngine
import io.chronoscore.engine.outcome
import io.chronoscore.model.CounterTarget
import io.chronoscore.model.GameSession
import io.chronoscore.model.MatchOptions
import io.chronoscore.model.Profile
import io.chronoscore.model.WinMode
import io.chronoscore.model.WinRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 1.1 features: turns, clocks, win rules, round sheet, MTG extras, profiles, backups. */
class MatchFeaturesTest {

    private var now = 1_000_000L
    private var nextId = 0
    private val engine = GameEngine(clock = { now }, newId = { "id${nextId++}" })

    private fun GameSession.apply(vararg actions: GameAction) = actions.fold(this) { s, a -> engine.reduce(s, a) }
    private fun GameSession.id(seat: Int) = players[seat].id

    private val turns = MatchOptions(turnTracking = true, seatTimers = true, matchClock = true)

    @Test
    fun allOptionsOffBehavesLikeBefore() {
        val s = engine.newSession(3, 20)
        assertNull(s.activePlayerId)
        assertFalse(s.timing.isRunning)
        assertEquals(s, s.apply(GameAction.NextTurn, GameAction.PreviousTurn))
        assertEquals(emptySet(), s.outcome().eliminated)
    }

    @Test
    fun nextTurnCyclesSeatsAndAdvancesRound() {
        val s = engine.newSession(3, 20, options = MatchOptions(turnTracking = true))
        assertEquals(s.id(0), s.activePlayerId)
        val r = s.apply(GameAction.NextTurn, GameAction.NextTurn)
        assertEquals(s.id(2), r.activePlayerId)
        assertEquals(1, r.round)
        val wrapped = r.apply(GameAction.NextTurn)
        assertEquals(s.id(0), wrapped.activePlayerId)
        assertEquals(2, wrapped.round)

        val back = wrapped.apply(GameAction.PreviousTurn)
        assertEquals(s.id(2), back.activePlayerId)
        assertEquals(1, back.round)
        // Never below round 1.
        val first = back.apply(GameAction.PreviousTurn, GameAction.PreviousTurn, GameAction.PreviousTurn)
        assertEquals(1, first.round)
    }

    @Test
    fun turnsSkipSeatsThatAreOut() {
        val s = engine.newSession(3, 20, options = MatchOptions(turnTracking = true))
            .apply(GameAction.SetConceded("id2", true))
        val conceded = s.players.first { it.conceded }.id
        var r = s
        repeat(4) {
            r = r.apply(GameAction.NextTurn)
            assertTrue(r.activePlayerId != conceded)
        }
    }

    @Test
    fun seatTimersCreditTurnTimeAndPauseStopsTheClock() {
        val s = engine.newSession(2, 20, options = turns)
        assertTrue(s.timing.isRunning)
        now += 5_000
        val r = s.apply(GameAction.NextTurn)
        assertEquals(5_000, r.players[0].timeMillis)
        assertEquals(0, r.timing.turnMillis)
        now += 3_000
        assertEquals(8_000, r.timing.liveMatchMillis(now))
        assertEquals(3_000, r.timing.liveTurnMillis(now))

        val paused = r.apply(GameAction.ToggleClock)
        now += 60_000
        assertEquals(8_000, paused.timing.liveMatchMillis(now))
        val resumed = paused.apply(GameAction.ToggleClock)
        now += 1_000
        val passed = resumed.apply(GameAction.NextTurn)
        assertEquals(4_000, passed.players[1].timeMillis)
        assertEquals(9_000, passed.timing.liveMatchMillis(now))
    }

    @Test
    fun turningOptionsOnAndOffStartsAndStopsTracking() {
        val s = engine.newSession(2, 20)
        val on = s.apply(GameAction.SetOptions(turns))
        assertEquals(s.id(0), on.activePlayerId)
        assertTrue(on.timing.isRunning)
        now += 2_000
        val off = on.apply(GameAction.SetOptions(MatchOptions()))
        assertNull(off.activePlayerId)
        assertFalse(off.timing.isRunning)
        assertEquals(2_000, off.players[0].timeMillis)
        // Seat timers and turn limits need turn tracking.
        val invalid = s.apply(GameAction.SetOptions(MatchOptions(seatTimers = true, turnLimitSeconds = 30)))
        assertFalse(invalid.options.seatTimers)
        assertNull(invalid.options.turnLimitSeconds)
    }

    @Test
    fun eliminationIsDerivedSoUndoRevivesASeat() {
        val rule = MatchOptions(winRule = WinRule(WinMode.ELIMINATION, target = 0))
        val s = engine.newSession(2, 5, options = rule)
        val dead = s.apply(GameAction.Adjust(s.id(1), CounterTarget.Primary, -5))
        val outcome = dead.outcome()
        assertEquals(setOf(s.id(1)), outcome.eliminated)
        assertEquals(listOf(s.id(0)), outcome.winners)
        assertTrue(outcome.finished)

        val undone = dead.apply(GameAction.Undo)
        assertFalse(undone.outcome().finished)
        assertEquals(emptySet(), undone.outcome().eliminated)
    }

    @Test
    fun targetScoreHighestOrLowestWins() {
        val race = engine.newSession(3, 0, options = MatchOptions(winRule = WinRule(WinMode.TARGET_SCORE, target = 10)))
        val r = race.apply(
            GameAction.SetValue(race.id(0), CounterTarget.Primary, 7),
            GameAction.SetValue(race.id(1), CounterTarget.Primary, 10),
        )
        assertEquals(listOf(race.id(1)), r.outcome().winners)

        val hearts = engine.newSession(3, 0, options = MatchOptions(winRule = WinRule(WinMode.TARGET_SCORE, 100, lowestWins = true)))
        val h = hearts.apply(
            GameAction.SetValue(hearts.id(0), CounterTarget.Primary, 104),
            GameAction.SetValue(hearts.id(1), CounterTarget.Primary, 40),
            GameAction.SetValue(hearts.id(2), CounterTarget.Primary, 61),
        )
        assertTrue(h.outcome().finished)
        assertEquals(listOf(hearts.id(1)), h.outcome().winners)
        assertFalse(hearts.apply(GameAction.SetValue(hearts.id(0), CounterTarget.Primary, 99)).outcome().finished)
    }

    @Test
    fun roundPointsAreLoggedInTheirRound() {
        val s = engine.newSession(2, 0, options = MatchOptions(sheetMode = true))
        val p = s.id(0)
        val r = s.apply(
            GameAction.SetRoundPoints(p, 1, 12),
            GameAction.NextRound,
            GameAction.SetRoundPoints(p, 2, 5),
            GameAction.SetRoundPoints(p, 1, 10), // correct an earlier round
        )
        assertEquals(15, r.players[0].primaryScore)
        assertEquals(10, r.historyLog.filter { it.round == 1 }.sumOf { it.delta })
        assertEquals(5, r.historyLog.filter { it.round == 2 }.sumOf { it.delta })
        assertEquals(17, r.apply(GameAction.Undo).players[0].primaryScore)
    }

    @Test
    fun commanderDamageCostsLifeAndUndoesAsOne() {
        val mtg = MatchOptions(mtgExtras = true, winRule = WinRule(WinMode.ELIMINATION, 0))
        val s = engine.newSession(3, 40, options = mtg)
        val victim = s.id(0)
        val dealer = s.id(1)
        val hit = s.apply(GameAction.Adjust(victim, CounterTarget.Commander(dealer), 7))
        assertEquals(7, hit.players[0].commanderDamage[dealer])
        assertEquals(33, hit.players[0].primaryScore)
        assertEquals(2, hit.historyLog.size)

        val undone = hit.apply(GameAction.Undo)
        assertEquals(emptyMap(), undone.players[0].commanderDamage)
        assertEquals(40, undone.players[0].primaryScore)
        assertTrue(undone.historyLog.isEmpty())

        // 21 from one commander is lethal even with life left; damage never goes below 0 or to yourself.
        val lethal = s.apply(GameAction.SetValue(victim, CounterTarget.Commander(dealer), 21))
        assertEquals(19, lethal.players[0].primaryScore)
        assertTrue(victim in lethal.outcome().eliminated)
        assertEquals(s, s.apply(GameAction.Adjust(victim, CounterTarget.Commander(dealer), -3)))
        assertEquals(s, s.apply(GameAction.Adjust(victim, CounterTarget.Commander(victim), 3)))
    }

    @Test
    fun removingASeatClearsItsReferences() {
        val s = engine.newSession(3, 40, options = MatchOptions(turnTracking = true, mtgExtras = true))
        val gone = s.id(0)
        val r = s.apply(
            GameAction.Adjust(s.id(1), CounterTarget.Commander(gone), 5),
            GameAction.SetMonarch(gone),
            GameAction.RemovePlayer(gone),
        )
        assertEquals(2, r.players.size)
        assertEquals(emptyMap(), r.players[0].commanderDamage)
        assertNull(r.monarchId)
        assertEquals(s.id(1), r.activePlayerId)
        assertTrue(r.historyLog.none { it.subCounterId == CounterTarget.Commander(gone).let { t -> "cmd:${t.fromPlayerId}" } })
    }

    @Test
    fun newGameUsesPresetCountersAndOptions() {
        val s = engine.newSession(2, 20).apply(GameAction.AddSubCounter("id1", "Energy"))
        val keep = s.apply(GameAction.NewGame(2, 40))
        assertEquals(listOf("Energy"), keep.players[1].subCounters.map { it.name })

        val preset = s.apply(GameAction.NewGame(4, 40, "Commander", listOf("Poison", "Tax"), MatchOptions(turnTracking = true)))
        assertEquals(4, preset.players.size)
        assertTrue(preset.players.all { p -> p.subCounters.map { it.name } == listOf("Poison", "Tax") })
        assertEquals(preset.players[0].id, preset.activePlayerId)
        assertEquals("Commander", preset.gameName)
    }

    @Test
    fun profilesNameAndColorTheSeat() {
        val s = engine.newSession(2, 20)
        val r = s.apply(GameAction.SetProfile(s.id(0), Profile("pr1", "Mara", "#16A34A")))
        assertEquals("Mara", r.players[0].name)
        assertEquals("#16A34A", r.players[0].colorHex)
        assertEquals("pr1", r.players[0].profileId)
        assertNull(r.apply(GameAction.SetProfile(s.id(0), null)).players[0].profileId)
    }

    @Test
    fun backupRoundTripsAndRejectsOtherFiles() {
        val s = engine.newSession(3, 40, "Commander", MatchOptions(mtgExtras = true, turnTracking = true))
            .apply(GameAction.Adjust("id1", CounterTarget.Commander("id2"), 4), GameAction.SetMonarch("id2"))
        val backup = Backup(exportedAt = 5, sessions = listOf(s), profiles = listOf(Profile("p", "Mara", "#2563EB")))
        assertEquals(backup, BackupCodec.decode(BackupCodec.encode(backup)))
        assertFailsWith<IllegalArgumentException> { BackupCodec.decode("""{"hello":1}""") }
        assertFailsWith<IllegalArgumentException> { BackupCodec.decode("not json") }
    }

    @Test
    fun csvListsChangesAndStandings() {
        val s = engine.newSession(2, 20, "Catan, the game")
            .apply(GameAction.Adjust("id1", CounterTarget.Primary, 3))
        val csv = matchCsv(s) { "t$it" }
        assertTrue(csv.startsWith("Match,\"Catan, the game\"\r\n"))
        assertTrue("1,t1000000,Player 2,Score,3,23\r\n" in csv)
        assertTrue("Player 1,20,\r\n" in csv)
    }
}
