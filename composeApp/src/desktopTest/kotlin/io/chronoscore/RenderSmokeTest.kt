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
import io.chronoscore.ui.dialogs.StatsDialog
import io.chronoscore.ui.dialogs.EditAllSeatsDialog
import io.chronoscore.ui.dialogs.HistoryDialog
import io.chronoscore.ui.dialogs.KeypadDialog
import io.chronoscore.ui.dialogs.LibraryDialog
import io.chronoscore.ui.dialogs.MenuDialog
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
            SessionSummary(session.id, "Commander", "Commander", 2, 1_700_000_000_000, 1_700_000_900_000, session.players.map { it.name }, 3),
            SessionSummary("b", "Commander", "Commander", 5, 1_699_000_000_000, 1_699_000_500_000, listOf("Mara", "Jonas"), 41),
            SessionSummary("c", "Catan", "Catan", 9, 1_698_000_000_000, 1_698_000_800_000, listOf("Ana", "Ben", "Cy"), 77),
            SessionSummary("d", "Match", null, 1, 1_697_000_000_000, 1_697_000_100_000, listOf("Player 1"), 2),
        )
        val noop: (GameAction) -> Unit = {}
        val dialogs: Map<String, @Composable () -> Unit> = mapOf(
            "keypad" to { KeypadDialog("Mara", "#2563EB", "Score", 40, false, 4, {}, { _, _, _ -> }) },
            "utilities" to { UtilitiesDialog(vm.utility.value, { _, _ -> }, {}, {}, {}, {}) },
            "log" to { HistoryDialog(session, {}, {}, {}, {}) },
            "menu" to {
                MenuDialog(session, ThemeMode.DARK, SeatStyle.NEUTRAL, listOf("Commander", "Catan"), noop, {}, {}, {}, {}, {}, {})
            },
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
    private fun commanderTable() = GameViewModel(null, Dispatchers.Unconfined, GameEngine()).also { vm ->
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

        val hearts = GameViewModel(null, Dispatchers.Unconfined, GameEngine()).also { h ->
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
            val timer = GameViewModel(null, Dispatchers.Unconfined, GameEngine()).also { t ->
                val preset = BuiltInPresets.all.first { it.name == name }
                t.dispatch(GameAction.NewGame(seats, 0, null, preset.counters, preset.options))
                t.dispatch(GameAction.AdjustClock(t.session.value.players[1].id, -95_000))
                t.dispatch(GameAction.NextTurn)
            }
            val tag = name.lowercase().replace(' ', '_')
            ImageComposeScene(420, 880, Density(1.5f)) { App(timer) }.use { it.snapshot("feature_${tag}_phone") }
            ImageComposeScene(1280, 800, Density(1.5f)) { App(timer) }.use { it.snapshot("feature_${tag}_desktop") }
        }

        // Seats turned in 30° steps still fit their cells.
        val angled = GameViewModel(null, Dispatchers.Unconfined, GameEngine()).also { a ->
            a.dispatch(GameAction.NewGame(4, 20))
            a.session.value.players.forEachIndexed { i, p -> a.dispatch(GameAction.RotateSeat(p.id, 30 * (i + 1))) }
        }
        ImageComposeScene(420, 880, Density(1.5f)) { App(angled) }.use { it.snapshot("feature_rotation_phone") }
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
            "menu_presets" to {
                MenuDialog(
                    session, ThemeMode.DARK, SeatStyle.NEUTRAL, listOf("Commander"), noop, {}, {}, {}, {}, {}, {},
                    presets = BuiltInPresets.all,
                )
            },
            "editor_extras" to {
                PlayerEditorDialog(
                    session.players[0], true, false, noop, {}, {},
                    options = session.options, isMonarch = true, profiles = profiles,
                )
            },
            "library_backup" to { LibraryDialog(session, emptyList(), {}, {}, {}, {}, filesAvailable = true) },
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
}
