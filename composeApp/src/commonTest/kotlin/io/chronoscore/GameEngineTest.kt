package io.chronoscore

import io.chronoscore.engine.BulkMode
import io.chronoscore.engine.GameAction
import io.chronoscore.engine.GameEngine
import io.chronoscore.model.CounterTarget
import io.chronoscore.model.GameSession
import io.chronoscore.model.Limits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameEngineTest : EngineTest() {

    private val GameSession.p0 get() = players[0]

    @Test
    fun newSessionClampsPlayerCountAndAssignsDistinctColors() {
        assertEquals(1, engine.newSession(playerCount = 0).players.size)
        val eight = engine.newSession(playerCount = 99)
        assertEquals(Limits.MAX_PLAYERS, eight.players.size)
        assertEquals(8, eight.players.map { it.colorHex }.toSet().size)
        assertEquals((0..7).toList(), eight.players.map { it.seatIndex })
    }

    @Test
    fun adjustChangesScoreAndLogs() {
        val s = engine.newSession(2, 20)
        val r = s.apply(GameAction.Adjust(s.p0.id, CounterTarget.Primary, -3))
        assertEquals(17, r.p0.primaryScore)
        assertEquals(1, r.historyLog.size)
        assertEquals(-3, r.historyLog[0].delta)
        assertEquals(17, r.historyLog[0].resultingValue)
    }

    @Test
    fun rapidAdjustmentsCoalesceAndUndoTogether() {
        val s = engine.newSession(2, 40)
        var r = s
        repeat(20) {
            r = r.apply(GameAction.Adjust(s.p0.id, CounterTarget.Primary, -1))
            now += 150
        }
        assertEquals(20, r.p0.primaryScore)
        assertEquals(1, r.historyLog.size)
        assertEquals(-20, r.historyLog.single().delta)

        r = r.apply(GameAction.Undo)
        assertEquals(40, r.p0.primaryScore)
        assertTrue(r.historyLog.isEmpty())
    }

    @Test
    fun adjustmentsOutsideWindowOrOnOtherTargetsAreSeparate() {
        val s = engine.newSession(2, 20)
        val p1 = s.players[1].id
        var r = s.apply(GameAction.Adjust(s.p0.id, CounterTarget.Primary, 1))
        now += 2_000
        r = r.apply(GameAction.Adjust(s.p0.id, CounterTarget.Primary, 1))
        r = r.apply(GameAction.Adjust(p1, CounterTarget.Primary, 1))
        assertEquals(3, r.historyLog.size)

        r = r.apply(GameAction.Undo)
        assertEquals(20, r.players[1].primaryScore)
        assertEquals(22, r.p0.primaryScore)
    }

    @Test
    fun coalescedDeltaOfZeroDropsEntry() {
        val s = engine.newSession(1, 10)
        val r = s.apply(
            GameAction.Adjust(s.p0.id, CounterTarget.Primary, 1),
            GameAction.Adjust(s.p0.id, CounterTarget.Primary, -1),
        )
        assertEquals(10, r.p0.primaryScore)
        assertTrue(r.historyLog.isEmpty())
    }

    @Test
    fun setValueRecordsDeltaAndNeverCoalesces() {
        val s = engine.newSession(1, 20)
        val r = s.apply(
            GameAction.Adjust(s.p0.id, CounterTarget.Primary, 1),
            GameAction.SetValue(s.p0.id, CounterTarget.Primary, 5),
        )
        assertEquals(5, r.p0.primaryScore)
        assertEquals(listOf(1, -16), r.historyLog.map { it.delta })
        assertEquals(21, r.apply(GameAction.Undo).p0.primaryScore)
    }

    @Test
    fun undoOnEmptyHistoryIsNoOp() {
        val s = engine.newSession(2)
        assertEquals(s, s.apply(GameAction.Undo))
    }

    @Test
    fun subCountersAreLimitedAndAdjustable() {
        val s = engine.newSession(1)
        var r = s
        repeat(6) { r = r.apply(GameAction.AddSubCounter(s.p0.id, "C$it")) }
        assertEquals(Limits.MAX_SUB_COUNTERS, r.p0.subCounters.size)

        val poison = r.p0.subCounters[0].id
        r = r.apply(GameAction.Adjust(s.p0.id, CounterTarget.Sub(poison), 3))
        assertEquals(3, r.p0.subCounters[0].value)
        assertEquals(poison, r.historyLog.single().subCounterId)

        r = r.apply(GameAction.RemoveSubCounter(s.p0.id, poison))
        assertEquals(3, r.p0.subCounters.size)
        assertTrue(r.historyLog.isEmpty(), "history for a removed counter is purged")
    }

    @Test
    fun playerBoundsAndRemovalReindexSeats() {
        var r = engine.newSession(Limits.MAX_PLAYERS)
        r = r.apply(GameAction.AddPlayer)
        assertEquals(Limits.MAX_PLAYERS, r.players.size)

        val removed = r.players[2].id
        r = r.apply(GameAction.Adjust(removed, CounterTarget.Primary, 5), GameAction.RemovePlayer(removed))
        assertEquals(7, r.players.size)
        assertEquals((0..6).toList(), r.players.map { it.seatIndex })
        assertTrue(r.historyLog.isEmpty())

        val single = engine.newSession(1)
        assertEquals(1, single.apply(GameAction.RemovePlayer(single.p0.id)).players.size)
    }

    @Test
    fun addPlayerPicksUnusedColor() {
        val s = engine.newSession(2)
        val r = s.apply(GameAction.RemovePlayer(s.p0.id), GameAction.AddPlayer)
        assertEquals(2, r.players.size)
        assertEquals(2, r.players.map { it.colorHex }.toSet().size)
    }

    @Test
    fun valuesAreClamped() {
        val s = engine.newSession(1)
        val r = s.apply(GameAction.SetValue(s.p0.id, CounterTarget.Primary, 1_000_000))
        assertEquals(Limits.MAX_VALUE, r.p0.primaryScore)
    }

    @Test
    fun resetAndNewGame() {
        val s = engine.newSession(3, 40)
        var r = s.apply(
            GameAction.RenamePlayer(s.p0.id, "  Alice  "),
            GameAction.AddSubCounter(s.p0.id, "Poison"),
            GameAction.Adjust(s.p0.id, CounterTarget.Primary, -10),
            GameAction.NextRound,
        )
        assertEquals("Alice", r.p0.name)
        assertEquals(2, r.round)

        val reset = r.apply(GameAction.ResetScores)
        assertEquals(40, reset.p0.primaryScore)
        assertEquals(1, reset.round)
        assertTrue(reset.historyLog.isEmpty())

        r = r.apply(GameAction.NewGame(playerCount = 4, startingScore = 20))
        assertEquals(4, r.players.size)
        assertTrue(r.players.all { it.primaryScore == 20 })
        assertEquals("Alice", r.p0.name, "names carry over to the same seat")
        assertEquals(0, r.p0.subCounters.single().value)
        assertTrue(r.id != s.id)
    }

    @Test
    fun bulkEditSetsSelectedSeatsAndUndoesAsOneStep() {
        val s = engine.newSession(4, 40)
        val ids = s.players.map { it.id }
        var r = s.apply(GameAction.Adjust(ids[0], CounterTarget.Primary, -7))
        now += 2_000
        r = r.apply(GameAction.BulkEdit(ids.take(3), counterName = null, mode = BulkMode.SET, value = 20))
        assertEquals(listOf(20, 20, 20, 40), r.players.map { it.primaryScore })
        assertEquals(3, r.historyLog.count { it.batchId != null })
        assertEquals(1, r.historyLog.mapNotNull { it.batchId }.toSet().size)

        r = r.apply(GameAction.Undo)
        assertEquals(listOf(33, 40, 40, 40), r.players.map { it.primaryScore }, "one undo reverts the whole batch")
        assertEquals(1, r.historyLog.size)
    }

    @Test
    fun bulkEditAddTargetsCountersByNameAndSkipsSeatsWithoutIt() {
        val s = engine.newSession(3)
        val ids = s.players.map { it.id }
        val r = s.apply(
            GameAction.AddSubCounter(ids[0], "Poison"),
            GameAction.AddSubCounter(ids[1], "poison"),
            GameAction.BulkEdit(ids, counterName = "POISON", mode = BulkMode.ADD, value = 2),
        )
        assertEquals(2, r.players[0].subCounters.single().value)
        assertEquals(2, r.players[1].subCounters.single().value)
        assertTrue(r.players[2].subCounters.isEmpty())
        assertEquals(2, r.historyLog.size)
    }

    @Test
    fun tapsNeverCoalesceIntoABatch() {
        val s = engine.newSession(2, 10)
        val p = s.p0.id
        val r = s.apply(
            GameAction.BulkEdit(listOf(p), null, BulkMode.ADD, 5),
            GameAction.Adjust(p, CounterTarget.Primary, 1),
        )
        assertEquals(2, r.historyLog.size)
        assertEquals(15, r.apply(GameAction.Undo).p0.primaryScore)
    }

    @Test
    fun counterOnAllSeatsAddsSkipsFullSeatsAndRemoves() {
        val s = engine.newSession(2)
        var r = s
        repeat(Limits.MAX_SUB_COUNTERS) { r = r.apply(GameAction.AddSubCounter(s.p0.id, "C$it")) }
        r = r.apply(GameAction.SetCounterOnAllSeats("VP", enabled = true))
        assertTrue(r.p0.subCounters.none { it.name == "VP" }, "full seat skipped")
        assertEquals(listOf("VP"), r.players[1].subCounters.map { it.name })

        r = r.apply(GameAction.SetCounterOnAllSeats("vp", enabled = true))
        assertEquals(1, r.players[1].subCounters.size, "no duplicates")

        val vp = r.players[1].subCounters.single().id
        r = r.apply(GameAction.Adjust(r.players[1].id, CounterTarget.Sub(vp), 3), GameAction.SetCounterOnAllSeats("VP", enabled = false))
        assertTrue(r.players[1].subCounters.isEmpty())
        assertTrue(r.historyLog.isEmpty())
    }

    @Test
    fun gameNameIsTrimmedAndCarriedByNewGame() {
        val s = engine.newSession(2, gameName = "  Catan  ")
        assertEquals("Catan", s.gameName)
        assertEquals("Catan", s.title)
        assertEquals(null, s.apply(GameAction.SetGameName("   ")).gameName)
        assertEquals("Commander", s.apply(GameAction.NewGame(4, 40, "Commander")).gameName)
        assertEquals(null, s.apply(GameAction.NewGame(4, 40)).gameName)
    }

    @Test
    fun rotationSnapsToSteps() {
        val s = engine.newSession(2)
        assertEquals(30, s.apply(GameAction.RotateSeat(s.p0.id, 30)).p0.rotation)
        assertEquals(330, s.apply(GameAction.RotateSeat(s.p0.id, -30)).p0.rotation)
        assertEquals(0, s.apply(GameAction.RotateSeat(s.p0.id, 180), GameAction.RotateSeat(s.p0.id, 180)).p0.rotation)
        // Odd angles snap to the nearest step.
        assertEquals(60, s.apply(GameAction.RotateSeat(s.p0.id, 50)).p0.rotation)
    }
}
