package io.chronoscore

import io.chronoscore.engine.GameAction
import io.chronoscore.model.BuiltInPresets
import io.chronoscore.model.CounterTarget
import io.chronoscore.model.GamePreset
import io.chronoscore.model.MatchOptions
import io.chronoscore.model.PresetCategory
import io.chronoscore.model.category
import io.chronoscore.ui.dialogs.newGameAction
import io.chronoscore.ui.dialogs.rematch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Launch-to-board flow: preset groups, one-tap quick start and Rematch. */
class FlowTest : EngineTest() {

    private fun builtIn(name: String) = BuiltInPresets.all.first { it.name == name }

    @Test
    fun presetsAreGroupedByWhatTheyTurnOn() {
        assertEquals(PresetCategory.CARDS, builtIn("Commander").category())
        assertEquals(PresetCategory.CARDS, builtIn("Hearts").category())
        assertEquals(PresetCategory.BOARD, builtIn("Catan").category())
        assertEquals(PresetCategory.DARTS, builtIn("Cricket").category())
        assertEquals(PresetCategory.TIMERS, builtIn("Chess clock").category())
        assertEquals(PresetCategory.BOULES, builtIn("Pétanque").category())
        assertEquals(PresetCategory.SAVED, GamePreset(id = "mine", name = "House rules").category())
        // Every built-in preset lands in a shipped group.
        assertTrue(BuiltInPresets.all.none { it.category() == PresetCategory.SAVED })
    }

    @Test
    fun rematchKeepsSetupAndPlayersWithFreshScores() {
        val options = MatchOptions(turnTracking = true)
        val fresh = engine.newSession(3, 40, "Catan", options, listOf("VP"))
        val s = fresh.apply(GameAction.RenamePlayer(fresh.id(0), "Mara"), GameAction.Adjust(fresh.id(0), CounterTarget.Primary, -5))
        val r = s.apply(s.rematch())
        assertEquals(3, r.players.size)
        assertEquals("Catan", r.gameName)
        assertEquals(options, r.options)
        assertEquals("Mara", r.players[0].name)
        assertTrue(r.players.all { it.primaryScore == 40 })
        assertTrue(r.players.all { p -> p.subCounters.map { it.name } == listOf("VP") })
        assertTrue(r.historyLog.isEmpty())
    }

    @Test
    fun quickStartTakesThePresetWithTheTablesPlayers() {
        val fresh = engine.newSession(2, 20)
        val s = fresh.apply(GameAction.RenamePlayer(fresh.id(0), "Mara"))
        val preset = builtIn("Commander")
        val r = s.apply(preset.newGameAction())
        assertEquals(preset.playerCount, r.players.size)
        assertEquals("Commander", r.gameName)
        assertEquals("Mara", r.players[0].name)
        assertEquals(preset.options.mtgExtras, r.options.mtgExtras)
    }
}
