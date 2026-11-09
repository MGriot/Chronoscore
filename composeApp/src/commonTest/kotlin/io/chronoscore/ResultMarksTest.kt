package io.chronoscore

import io.chronoscore.data.Backup
import io.chronoscore.data.BackupCodec
import io.chronoscore.engine.GameAction
import io.chronoscore.engine.computeStats
import io.chronoscore.engine.outcome
import io.chronoscore.model.Compare
import io.chronoscore.model.CounterTarget
import io.chronoscore.model.Limits
import io.chronoscore.model.Mark
import io.chronoscore.model.MatchOptions
import io.chronoscore.model.ResultRule
import io.chronoscore.model.TeamMember
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Winner/loser marks: hand-set per seat or team member, and score-based result rules. */
class ResultMarksTest : EngineTest() {

    private val zeroLoses = MatchOptions(resultRules = listOf(ResultRule(Compare.EQ, 0, Mark.LOSER)))

    @Test
    fun ruleMarksASeatAtZeroAsLoserAndUndoRevivesIt() {
        val s = engine.newSession(2, 5, options = zeroLoses)
        val dead = s.apply(GameAction.Adjust(s.id(1), CounterTarget.Primary, -5))
        assertEquals(setOf(s.id(1)), dead.outcome().eliminated)
        assertEquals(listOf(s.id(0)), dead.outcome().winners)
        assertTrue(dead.outcome().finished)

        val undone = dead.apply(GameAction.Undo)
        assertEquals(emptySet(), undone.outcome().eliminated)
        assertFalse(undone.outcome().finished)
    }

    @Test
    fun winnerRuleEndsTheMatch() {
        val opts = MatchOptions(resultRules = listOf(ResultRule(Compare.GE, 10, Mark.WINNER)))
        val s = engine.newSession(3, 0, options = opts)
        val r = s.apply(GameAction.SetValue(s.id(2), CounterTarget.Primary, 12))
        assertEquals(listOf(s.id(2)), r.outcome().winners)
        assertTrue(r.outcome().finished)
    }

    @Test
    fun handMarkOverridesRules() {
        val s = engine.newSession(2, 0, options = zeroLoses)
        // Both seats sit on 0 and would lose; a hand-set winner wins regardless.
        val r = s.apply(GameAction.SetMark(s.id(0), null, Mark.WINNER))
        assertEquals(listOf(s.id(0)), r.outcome().winners)
        // Back to Auto: the rule applies again.
        val back = r.apply(GameAction.SetMark(s.id(0), null, Mark.NONE))
        assertEquals(setOf(s.id(0), s.id(1)), back.outcome().eliminated)
    }

    @Test
    fun resetScoresClearsMarks() {
        val s = engine.newSession(2, 5)
            .apply(GameAction.SetMark("id0", null, Mark.LOSER))
        val reset = s.apply(GameAction.ResetScores)
        assertEquals(Mark.NONE, reset.players[0].mark)
    }

    @Test
    fun memberMarkOverridesSeatResultInStats() {
        val s = engine.newSession(2, 0, options = MatchOptions(resultRules = listOf(ResultRule(Compare.GE, 5, Mark.WINNER))))
            .apply(
                GameAction.SetTeamMembers("id0", listOf(TeamMember("Ann"), TeamMember("Bo"))),
                GameAction.SetMark("id0", 1, Mark.LOSER),
                GameAction.SetValue("id0", CounterTarget.Primary, 6),
            )
        assertEquals(Mark.LOSER, s.players[0].members[1].mark)
        val stats = computeStats(listOf(s), emptyList()).associateBy { it.name }
        assertEquals(1, stats.getValue("Ann").wins)
        assertEquals(0, stats.getValue("Bo").wins)
        assertEquals(1, stats.getValue("Bo").decided)
    }

    @Test
    fun marksAndRulesSurviveBackupAndAreCapped() {
        val many = List(Limits.MAX_RESULT_RULES + 3) { ResultRule(Compare.LE, it, Mark.LOSER) } +
            ResultRule(Compare.EQ, 1, Mark.NONE)
        val s = engine.newSession(2, 5, options = MatchOptions(resultRules = many))
            .apply(
                GameAction.SetTeamMembers("id1", listOf(TeamMember("Ann"))),
                GameAction.SetMark("id1", 0, Mark.WINNER),
                GameAction.SetMark("id0", null, Mark.LOSER),
            )
        val back = BackupCodec.decode(BackupCodec.encode(Backup(sessions = listOf(s)))).sessions.single()
        assertEquals(Limits.MAX_RESULT_RULES, back.options.resultRules.size)
        assertEquals(Mark.LOSER, back.players[0].mark)
        assertEquals(Mark.WINNER, back.players[1].members[0].mark)
    }
}
