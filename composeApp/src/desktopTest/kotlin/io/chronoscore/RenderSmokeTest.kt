package io.chronoscore

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import io.chronoscore.engine.GameAction
import io.chronoscore.engine.GameEngine
import androidx.compose.runtime.Composable
import io.chronoscore.model.BuiltInPresets
import io.chronoscore.model.CounterTarget
import io.chronoscore.model.DayNight
import io.chronoscore.model.Profile
import io.chronoscore.model.SessionSummary
import io.chronoscore.ui.App
import io.chronoscore.ui.dialogs.CommanderDialog
import io.chronoscore.ui.dialogs.CountersAllSeatsDialog
import io.chronoscore.ui.dialogs.OptionsDialog
import io.chronoscore.ui.dialogs.StartDialog
import io.chronoscore.ui.dialogs.StatsDialog
import io.chronoscore.ui.dialogs.EditAllSeatsDialog
import io.chronoscore.model.HandCalc
import io.chronoscore.ui.dialogs.HandDialog
import io.chronoscore.ui.dialogs.HistoryDialog
import io.chronoscore.ui.dialogs.KeypadDialog
import io.chronoscore.ui.dialogs.LibraryDialog
import io.chronoscore.ui.dialogs.MenuDialog
import io.chronoscore.ui.dialogs.NewGameDialog
import io.chronoscore.ui.dialogs.AppearanceDialog
import io.chronoscore.ui.dialogs.PlayerEditorDialog
import io.chronoscore.ui.dialogs.UtilitiesDialog
import io.chronoscore.ui.theme.ChronoscoreTheme
import io.chronoscore.viewmodel.SeatStyle
import io.chronoscore.viewmodel.ThemeMode
import io.chronoscore.viewmodel.GameViewModel
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Renders the real App offscreen and drives it with pointer events.
 * PNGs land in build/render-smoke/ for eyeballing layouts.
 */
class RenderSmokeTest {

    private val outDir = File("build/render-smoke").apply { mkdirs() }

    private fun viewModel(players: Int) = GameViewModel(
        repository = null,
        ioDispatcher = Dispatchers.Unconfined,
        engine = GameEngine(),
    ).also { vm ->
        vm.dispatch(GameAction.NewGame(players, 40))
        val first = vm.session.value.players.first().id
        vm.dispatch(GameAction.AddSubCounter(first, "Poison"))
        vm.dispatch(GameAction.AddSubCounter(first, "Commander"))
    }

    /** A view model with no database, for rendering. */
    private fun offlineVm() = GameViewModel(null, Dispatchers.Unconfined, GameEngine())

    private fun ImageComposeScene.snapshot(name: String) {
        val bytes = render().encodeToData(EncodedImageFormat.PNG)!!.bytes
        File(outDir, "$name.png").writeBytes(bytes)
    }

    @Test
    fun rendersEveryLayout() {
        for ((w, h, tag) in listOf(Triple(420, 860, "phone"), Triple(1280, 800, "desktop"))) {
            for (players in listOf(1, 2, 3, 4, 6, 8)) {
                val vm = viewModel(players)
                ImageComposeScene(w, h, Density(1.5f)) { App(vm) }.use { it.snapshot("${tag}_$players") }
            }
        }
    }

    /** A 4-seat table mid-game, named like the UI kit mockups. */
    private fun kitTable(style: SeatStyle = SeatStyle.NEUTRAL) = viewModel(4).also { vm ->
        vm.setSeatStyle(style)
        val ids = vm.session.value.players.map { it.id }
        listOf("Mara", "Jonas", "Priya", "Theo").forEachIndexed { i, n -> vm.dispatch(GameAction.RenamePlayer(ids[i], n)) }
        vm.dispatch(GameAction.SetGameName("Commander"))
        vm.dispatch(GameAction.Adjust(ids[1], CounterTarget.Primary, -7))
        vm.dispatch(GameAction.NextRound)
        vm.dispatch(GameAction.Adjust(ids[2], CounterTarget.Primary, -22))
        vm.dispatch(GameAction.AddSubCounter(ids[3], "Tax"))
        vm.dispatch(GameAction.Adjust(ids[0], CounterTarget.Sub(vm.session.value.players[0].subCounters[1].id), 7))
        vm.roll(20)
    }

    @Test
    fun rendersKitTables() {
        for (style in SeatStyle.entries) {
            val vm = kitTable(style)
            vm.pickFirstPlayer()
            ImageComposeScene(420, 880, Density(1.5f)) { App(vm) }.use { it.snapshot("kit_table_${style.name.lowercase()}") }
            ImageComposeScene(1280, 800, Density(1.5f)) { App(vm) }.use { it.snapshot("kit_desktop_${style.name.lowercase()}") }
        }
    }

    @Test
    fun rendersDialogs() {
        val vm = kitTable()
        val session = vm.session.value
        val summaries = listOf(
            SessionSummary(session.id, "Commander", 2, 1_700_000_000_000, 1_700_000_900_000, session.players.map { it.name }, 3),
            SessionSummary("b", "Commander", 5, 1_699_000_000_000, 1_699_000_500_000, listOf("Mara", "Jonas"), 41),
            SessionSummary("c", "Catan", 9, 1_698_000_000_000, 1_698_000_800_000, listOf("Ana", "Ben", "Cy"), 77),
            SessionSummary("d", null, 1, 1_697_000_000_000, 1_697_000_100_000, listOf("Player 1"), 2),
        )
        val noop: (GameAction) -> Unit = {}
        val dialogs: Map<String, @Composable () -> Unit> = mapOf(
            "keypad" to { KeypadDialog("Mara", "#2563EB", "Score", 40, false, 4, {}, { _, _, _ -> }) },
            "utilities" to { UtilitiesDialog(vm.utility.value, { _, _ -> }, {}, {}, {}, {}) },
            "log" to { HistoryDialog(session, {}, {}, {}) },
            "menu" to {
                MenuDialog(session, listOf("Commander", "Catan"), noop, {}, {}, {})
            },
            "start" to { StartDialog(session, summaries[0], {}, {}, {}, quickStarts = BuiltInPresets.all.take(3)) },
            "new_game" to { NewGameDialog(session, listOf("Commander", "Catan"), BuiltInPresets.all, {}, {}) },
            "new_game_players" to { NewGameDialog(session, listOf("Commander"), BuiltInPresets.all, {}, {}, initialStep = 1) },
            "new_game_preset" to {
                NewGameDialog(session, listOf("Catan"), BuiltInPresets.all, {}, {}, initialPreset = BuiltInPresets.all.first { it.name == "Catan" })
            },
            // A saved preset opened with one more seat than it stores: shows the edited state.
            "new_game_preset_edited" to {
                val mine = BuiltInPresets.all.first { it.name == "Catan" }.copy(id = "mine", name = "Catan night")
                NewGameDialog(session, listOf("Catan"), BuiltInPresets.all + mine, {}, {}, initialPreset = mine.copy(playerCount = 5))
            },
            "appearance" to { AppearanceDialog(ThemeMode.DARK, SeatStyle.NEUTRAL, {}, {}, {}) },
            "edit_all" to { EditAllSeatsDialog(session, noop) {} },
            "counters_all" to { CountersAllSeatsDialog(session, noop) {} },
            "library" to { LibraryDialog(session, summaries, {}, {}, {}, {}) },
            "editor" to { PlayerEditorDialog(session.players[0], true, false, noop, {}, {}) },
        )
        for ((name, dialog) in dialogs) {
            ImageComposeScene(720, 1500, Density(1.5f)) {
                ChronoscoreTheme(ThemeMode.DARK) { dialog() }
            }.use { scene ->
                scene.render(0)
                scene.snapshot("dialog_$name")
            }
        }
    }

    /** A Commander pod using the 1.1 options: turns, seat timers, elimination and MTG extras. */
    private fun commanderTable() = offlineVm().also { vm ->
        val preset = BuiltInPresets.all.first { it.name == "Commander" }
        vm.dispatch(
            GameAction.NewGame(4, 40, "Commander", preset.counters, preset.options.copy(seatTimers = true, matchClock = true, turnLimitSeconds = 120)),
        )
        val ids = vm.session.value.players.map { it.id }
        listOf("Mara", "Jonas", "Priya", "Theo").forEachIndexed { i, n -> vm.dispatch(GameAction.RenamePlayer(ids[i], n)) }
        vm.dispatch(GameAction.Adjust(ids[1], CounterTarget.Commander(ids[0]), 9))
        vm.dispatch(GameAction.Adjust(ids[2], CounterTarget.Primary, -40))
        vm.dispatch(GameAction.SetMonarch(ids[3]))
        vm.dispatch(GameAction.NextTurn)
        vm.dispatch(GameAction.SetDayNight(DayNight.DAY))
    }

    @Test
    fun rendersMatchFeatures() {
        val vm = commanderTable()
        ImageComposeScene(420, 880, Density(1.5f)) { App(vm) }.use { it.snapshot("feature_commander_phone") }
        ImageComposeScene(1280, 800, Density(1.5f)) { App(vm) }.use { it.snapshot("feature_commander_desktop") }

        // Last seat standing: the banner names the winner.
        val ids = vm.session.value.players.map { it.id }
        vm.dispatch(GameAction.SetConceded(ids[1], true))
        vm.dispatch(GameAction.SetConceded(ids[3], true))
        ImageComposeScene(420, 880, Density(1.5f)) { App(vm) }.use { it.snapshot("feature_winner_phone") }

        val hearts = offlineVm().also { h ->
            val preset = BuiltInPresets.all.first { it.name == "Hearts" }
            h.dispatch(GameAction.NewGame(4, 0, "Hearts", preset.counters, preset.options.copy(turnTracking = true)))
            val p = h.session.value.players.map { it.id }
            listOf("Ana", "Ben", "Cy", "Dee").forEachIndexed { i, n -> h.dispatch(GameAction.RenamePlayer(p[i], n)) }
            listOf(listOf(4, 9, 0, 13), listOf(13, 2, 6, 5), listOf(0, 11, 8, 7)).forEachIndexed { r, row ->
                row.forEachIndexed { i, pts -> h.dispatch(GameAction.SetRoundPoints(p[i], r + 1, pts)) }
                h.dispatch(GameAction.NextRound)
            }
        }
        ImageComposeScene(420, 880, Density(1.5f)) { App(hearts) }.use { it.snapshot("feature_sheet_phone") }
        ImageComposeScene(1280, 800, Density(1.5f)) { App(hearts) }.use { it.snapshot("feature_sheet_desktop") }
        // Timer mode: a two-seat chess clock and an eight-seat board game timer.
        listOf("Chess clock" to 2, "Board game timer" to 8).forEach { (name, seats) ->
            val timer = offlineVm().also { t ->
                val preset = BuiltInPresets.all.first { it.name == name }
                t.dispatch(GameAction.NewGame(seats, 0, null, preset.counters, preset.options))
                t.dispatch(GameAction.AdjustClock(t.session.value.players[1].id, -95_000))
                t.dispatch(GameAction.NextTurn)
            }
            val tag = name.lowercase().replace(' ', '_')
            ImageComposeScene(420, 880, Density(1.5f)) { App(timer) }.use { it.snapshot("feature_${tag}_phone") }
            ImageComposeScene(1280, 800, Density(1.5f)) { App(timer) }.use { it.snapshot("feature_${tag}_desktop") }
        }

        // Seats turned in 30° steps keep their angle on a phone and still fill their cells.
        val angled = offlineVm().also { a ->
            a.dispatch(GameAction.NewGame(4, 20))
            a.session.value.players.forEachIndexed { i, p -> a.dispatch(GameAction.RotateSeat(p.id, 30 * (i + 1))) }
        }
        ImageComposeScene(420, 880, Density(1.5f)) { App(angled) }.use { it.snapshot("feature_rotation_phone") }
        ImageComposeScene(1280, 800, Density(1.5f)) { App(angled) }.use { it.snapshot("feature_rotation_desktop") }
        val tiltedCounters = viewModel(4).also { a ->
            a.session.value.players.forEach { p -> a.dispatch(GameAction.RotateSeat(p.id, 30)) }
        }
        ImageComposeScene(420, 880, Density(1.5f)) { App(tiltedCounters) }.use { it.snapshot("feature_rotation_30_phone") }
        // A 410 dp-wide phone, at its own pixel density.
        ImageComposeScene(920, 2000, Density(2.25f)) { App(tiltedCounters) }.use { it.snapshot("feature_rotation_30_phone_large") }
    }

    /** A new match from a built-in preset, by name. */
    private fun presetTable(name: String) = offlineVm().also { vm ->
        val preset = BuiltInPresets.all.first { it.name == name }
        vm.dispatch(GameAction.NewGame(preset.playerCount, preset.startingScore, preset.gameName, preset.counters, preset.options, preset.teamSize))
    }

    @Test
    fun rendersDartsAndTeams() {
        val x01 = presetTable("Darts 501").also { vm ->
            val ids = vm.session.value.players.map { it.id }
            vm.dispatch(GameAction.RenamePlayer(ids[0], "Mara"))
            vm.dispatch(GameAction.RenamePlayer(ids[1], "Jonas"))
            listOf(20 to 3, 20 to 3, 20 to 1, 19 to 3, 5 to 1, 1 to 1, 20 to 3, 20 to 3, 20 to 3).forEach { (n, m) ->
                vm.dispatch(GameAction.ThrowDart(n, m))
            }
            vm.dispatch(GameAction.ThrowDart(20, 3))
        }
        ImageComposeScene(420, 880, Density(1.5f)) { App(x01) }.use { it.snapshot("feature_darts_501_phone") }
        ImageComposeScene(1280, 800, Density(1.5f)) { App(x01) }.use { it.snapshot("feature_darts_501_desktop") }

        val cricket = presetTable("Cricket").also { vm ->
            listOf(20 to 3, 20 to 2, 19 to 1, 18 to 3, 0 to 1, 25 to 2, 17 to 3, 16 to 1).forEach { (n, m) ->
                vm.dispatch(GameAction.ThrowDart(n, m))
            }
        }
        ImageComposeScene(420, 880, Density(1.5f)) { App(cricket) }.use { it.snapshot("feature_darts_cricket_phone") }

        val scopone = presetTable("Scopone").also { vm ->
            val ids = vm.session.value.players.map { it.id }
            listOf(listOf(3, 1), listOf(2, 4), listOf(4, 2)).forEachIndexed { r, row ->
                row.forEachIndexed { i, pts -> vm.dispatch(GameAction.SetRoundPoints(ids[i], r + 1, pts)) }
                vm.dispatch(GameAction.NextRound)
            }
        }
        ImageComposeScene(420, 880, Density(1.5f)) { App(scopone) }.use { it.snapshot("feature_teams_phone") }
    }

    @Test
    fun rendersFeatureDialogs() {
        val vm = commanderTable()
        val session = vm.session.value
        val noop: (GameAction) -> Unit = {}
        val profiles = listOf(Profile("a", "Mara", "#2563EB"), Profile("b", "Jonas", "#E11D48"))
        val history = listOf(session, session.copy(id = "x", gameName = "Catan"))
        val dialogs: Map<String, @Composable () -> Unit> = mapOf(
            "options" to { OptionsDialog(session.options, {}, {}) },
            "commander" to { CommanderDialog(session, session.players[1].id, false, noop, {}) },
            "stats" to { StatsDialog(history, profiles, {}, {}) },
            "menu_presets" to { MenuDialog(session, listOf("Commander"), noop, {}, {}, {}) },
            "editor_extras" to {
                PlayerEditorDialog(
                    session.players[0], true, false, noop, {}, {},
                    options = session.options, isMonarch = true, profiles = profiles,
                )
            },
            "library_backup" to { LibraryDialog(session, emptyList(), {}, {}, {}, {}, filesAvailable = true) },
            "options_darts" to { OptionsDialog(BuiltInPresets.all.first { it.name == "Darts 501" }.options, {}, {}) },
            "hand_scopa" to { HandDialog(presetTable("Scopone").session.value.players, 3, HandCalc.SCOPA, {}, {}, {}) },
            "hand_burraco" to { HandDialog(presetTable("Burraco").session.value.players, 1, HandCalc.BURRACO, {}, {}, {}) },
            "hand_scala40" to { HandDialog(presetTable("Scala 40").session.value.players, 2, HandCalc.SCALA40, {}, {}, {}) },
            "hand_bocce" to { HandDialog(presetTable("Bocce · Raffa").session.value.players, 5, HandCalc.BOCCE, {}, {}, {}) },
            "editor_team" to {
                val team = presetTable("Scopone").session.value.players[0]
                PlayerEditorDialog(team, true, false, noop, {}, {}, profiles = profiles)
            },
        )
        for ((name, dialog) in dialogs) {
            ImageComposeScene(720, 2600, Density(1.5f)) {
                ChronoscoreTheme(ThemeMode.DARK) { dialog() }
            }.use { scene ->
                scene.render(0)
                scene.snapshot("dialog_$name")
            }
        }
    }

    @Test
    fun tapAndHoldAdjustScore() {
        val vm = viewModel(1)
        // A single seat fills the scene; its right half increments, left half decrements.
        ImageComposeScene(600, 600, Density(1f)) { App(vm) }.use { scene ->
            // Hold detection uses real coroutine delays, so frames are pumped in real time.
            val start = System.nanoTime()
            fun frame() = scene.render(System.nanoTime() - start)
            fun press(x: Float, holdMs: Long) {
                val at = Offset(x, 300f)
                scene.sendPointerEvent(PointerEventType.Press, at)
                frame()
                val end = System.currentTimeMillis() + holdMs
                while (System.currentTimeMillis() < end) {
                    Thread.sleep(16)
                    frame()
                }
                scene.sendPointerEvent(PointerEventType.Release, at)
                frame()
            }

            val player = { vm.session.value.players.single() }
            press(500f, 50)
            assertEquals(41, player().primaryScore, "tap right → +1")
            press(100f, 50)
            assertEquals(40, player().primaryScore, "tap left → −1")

            press(500f, 1_000)
            val afterHold = player().primaryScore
            check(afterHold in 43..50) { "1s hold should repeat a handful of times, got ${afterHold - 40}" }
            assertEquals(afterHold - 40, vm.session.value.historyLog.last().delta)
            scene.snapshot("after_hold")

            vm.dispatch(GameAction.Undo)
            assertEquals(40, player().primaryScore, "one undo reverts the whole hold")
        }
    }

    @Test
    fun slantedSeatIsTappableToItsCorners() {
        val vm = viewModel(1)
        val id = vm.session.value.players.single().id
        vm.dispatch(GameAction.RotateSeat(id, 30))
        ImageComposeScene(600, 600, Density(1f)) { App(vm) }.use { scene ->
            scene.render()
            fun tap(x: Float, y: Float) {
                scene.sendPointerEvent(PointerEventType.Press, Offset(x, y))
                scene.render()
                scene.sendPointerEvent(PointerEventType.Release, Offset(x, y))
                scene.render()
            }
            // Turned 30° clockwise, the player's right points to the lower right. The cell's
            // corners lie outside the slanted card but still split into − and +.
            val score = { vm.session.value.players.single().primaryScore }
            val centreY = 300f
            tap(560f, centreY + 120f)
            assertEquals(41, score(), "lower right → +1")
            tap(40f, centreY - 120f)
            assertEquals(40, score(), "upper left → −1")
            scene.snapshot("slanted_single")
        }
    }
}
