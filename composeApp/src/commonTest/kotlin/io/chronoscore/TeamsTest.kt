package io.chronoscore

import io.chronoscore.data.matchCsv
import io.chronoscore.engine.GameAction
import io.chronoscore.engine.computeStats
import io.chronoscore.engine.outcome
import io.chronoscore.model.BuiltInPresets
import io.chronoscore.model.GameSession
import io.chronoscore.model.Limits
import io.chronoscore.model.MatchOptions
import io.chronoscore.model.Profile
import io.chronoscore.model.TeamMember
import io.chronoscore.model.WinMode
import io.chronoscore.model.WinRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 1.3: team seats, out-at-or-above (Scala 40) and the card and bocce presets. */
class TeamsTest : EngineTest() {

    @Test
    fun newGameWithTeamSizeMakesTeamSeats() {
        val s = engine.newSession(4, 0).apply(GameAction.NewGame(2, 0, "Scopone", teamSize = 2))
        assertEquals(listOf("Team 1", "Team 2"), s.players.map { it.name })
        assertEquals(listOf("Player 1", "Player 2"), s.players[0].members.map { it.name })
        assertEquals(listOf("Player 3", "Player 4"), s.players[1].members.map { it.name })

        // Teams of the same size carry over; null keeps them, 1 makes single seats again.
        val renamed = s.apply(GameAction.SetTeamMembers(s.id(0), listOf(TeamMember("Anna"), TeamMember("Marco"))))
        assertEquals(listOf("Anna", "Marco"), renamed.apply(GameAction.NewGame(2, 0, teamSize = 2)).players[0].members.map { it.name })
        assertEquals(listOf("Anna", "Marco"), renamed.apply(GameAction.NewGame(2, 0)).players[0].members.map { it.name })
        assertFalse(renamed.apply(GameAction.NewGame(2, 0, teamSize = 1)).players[0].isTeam)
    }

    @Test
    fun singleSeatsAfterTeamsArePlayersAgain() {
        val teams = engine.newSession(2, 0).apply(GameAction.NewGame(2, 0, teamSize = 2))
        val solo = teams.apply(GameAction.NewGame(3, 0, teamSize = 1))
        assertEquals(listOf("Player 1", "Player 2", "Player 3"), solo.players.map { it.name })

        // A leftover automatic team name goes too; a real player name stays.
        val plain = engine.newSession(2, 0).let { s ->
            s.apply(GameAction.RenamePlayer(s.id(0), "Team 1"), GameAction.RenamePlayer(s.id(1), "Anna"))
        }
        assertEquals(listOf("Player 1", "Anna"), plain.apply(GameAction.NewGame(2, 0, teamSize = 1)).players.map { it.name })
    }

    @Test
    fun teamMembersAreCleaned() {
        val s = engine.newSession(2, 0)
        val long = "x".repeat(40)
        val r = s.apply(GameAction.SetTeamMembers(s.id(0), listOf(TeamMember("  Anna "), TeamMember("   "), TeamMember(long)) + List(5) { TeamMember("P$it") }))
        val members = r.players[0].members
        assertEquals("Anna", members[0].name)
        assertEquals(Limits.MAX_NAME_LENGTH, members[1].name.length)
        assertEquals(Limits.MAX_TEAM_MEMBERS, members.size)
        assertFalse(r.apply(GameAction.SetTeamMembers(s.id(0), emptyList())).players[0].isTeam)
    }

    @Test
    fun statsCountATeamResultForEveryMember() {
        val anna = Profile("pa", "Anna", "#E11D48")
        val rule = MatchOptions(winRule = WinRule(WinMode.TARGET_SCORE, 11))
        val s = engine.newSession(2, 0, "Scopone", rule).let { s ->
            s.apply(
                GameAction.SetTeamMembers(s.id(0), listOf(TeamMember("Anna", anna.id), TeamMember("Marco"))),
                GameAction.SetTeamMembers(s.id(1), listOf(TeamMember("Luca"), TeamMember("Sara"))),
                GameAction.SetRoundPoints(s.id(0), 1, 11),
                GameAction.SetRoundPoints(s.id(1), 1, 4),
            )
        }
        val stats = computeStats(listOf(s), listOf(anna)).associateBy { it.name }
        assertEquals(setOf("Anna", "Marco", "Luca", "Sara"), stats.keys)
        assertEquals(1, stats.getValue("Anna").wins)
        assertTrue(stats.getValue("Anna").isProfile)
        assertEquals(1, stats.getValue("Marco").wins)
        assertEquals(0, stats.getValue("Luca").wins)
        assertEquals(11, stats.getValue("Marco").bestScore)
    }

    @Test
    fun csvListsTeamMembers() {
        val s = engine.newSession(2, 0).let { it.apply(GameAction.SetTeamMembers(it.id(0), listOf(TeamMember("Anna"), TeamMember("Marco")))) }
        val csv = matchCsv(s) { "t" }
        assertTrue("Player,Members,Score,Status" in csv)
        assertTrue("Player 1,Anna & Marco,0," in csv)
    }

    @Test
    fun outAtOrAboveIsDerivedSoUndoRevivesASeat() {
        val rule = MatchOptions(sheetMode = true, winRule = WinRule(WinMode.ELIMINATION_ABOVE, 101))
        val s = engine.newSession(3, 0, options = rule)
        val r = s.apply(GameAction.SetRoundPoints(s.id(0), 1, 60), GameAction.SetRoundPoints(s.id(1), 1, 100))
        assertEquals(emptySet(), r.outcome().eliminated)

        val out = r.apply(GameAction.NextRound, GameAction.SetRoundPoints(s.id(0), 2, 45))
        assertEquals(setOf(s.id(0)), out.outcome().eliminated)
        assertFalse(out.outcome().finished)

        val over = out.apply(GameAction.SetRoundPoints(s.id(1), 2, 1))
        assertTrue(over.outcome().finished)
        assertEquals(listOf(s.id(2)), over.outcome().winners)

        assertEquals(setOf(s.id(0)), over.apply(GameAction.Undo).outcome().eliminated)
        assertEquals(emptySet(), over.apply(GameAction.Undo, GameAction.Undo).outcome().eliminated)
    }

    @Test
    fun cardAndBoccePresetsStartTheRightTable() {
        val byId = BuiltInPresets.all.associateBy { it.id.removePrefix(BuiltInPresets.ID_PREFIX) }
        listOf("raffa" to 12, "petanque" to 13, "briscola" to 2, "scopa" to 11, "scopone" to 11, "burraco" to 2005).forEach { (id, target) ->
            val p = byId.getValue(id)
            assertTrue(p.options.sheetMode, id)
            assertEquals(WinRule(WinMode.TARGET_SCORE, target), p.options.winRule, id)
        }
        assertEquals(WinRule(WinMode.ELIMINATION_ABOVE, 101), byId.getValue("scala40").options.winRule)
        assertEquals("Raffa", byId.getValue("raffa").gameName)
        assertEquals(WinRule(WinMode.TARGET_SCORE, 500), byId.getValue("uno").options.winRule)
        assertEquals(WinRule(WinMode.TARGET_SCORE, 100, lowestWins = true), byId.getValue("skyjo").options.winRule)
        assertNull(byId.getValue("uno").options.handCalculator)
        assertTrue(byId.getValue("catan").options.turnTracking)

        val scopone = byId.getValue("scopone")
        val s = engine.newSession().apply(
            GameAction.NewGame(scopone.playerCount, scopone.startingScore, scopone.gameName, scopone.counters, scopone.options, scopone.teamSize),
        )
        assertEquals(2, s.players.size)
        assertTrue(s.players.all { it.members.size == 2 })
    }
}
