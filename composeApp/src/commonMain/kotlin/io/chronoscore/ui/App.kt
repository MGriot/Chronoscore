package io.chronoscore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.chronoscore.data.matchCsv
import io.chronoscore.engine.BulkMode
import io.chronoscore.engine.GameAction
import io.chronoscore.engine.Outcome
import io.chronoscore.engine.nowMillis
import io.chronoscore.engine.outcome
import io.chronoscore.model.CounterTarget
import io.chronoscore.model.GameSession
import io.chronoscore.model.Limits
import io.chronoscore.model.Player
import io.chronoscore.ui.board.ClockTiles
import io.chronoscore.ui.board.PlayerGrid
import io.chronoscore.ui.board.ResultBanner
import io.chronoscore.ui.board.RoundSheet
import io.chronoscore.ui.board.SeatCallbacks
import io.chronoscore.ui.board.SeatStatus
import io.chronoscore.ui.board.TurnBar
import io.chronoscore.ui.board.gridFor
import io.chronoscore.ui.board.rememberNow
import io.chronoscore.ui.board.roundPoints
import io.chronoscore.ui.board.showsTurnBar
import io.chronoscore.ui.dialogs.CommanderDialog
import io.chronoscore.ui.dialogs.CountersAllSeatsDialog
import io.chronoscore.ui.dialogs.EditAllSeatsDialog
import io.chronoscore.ui.dialogs.HistoryDialog
import io.chronoscore.ui.dialogs.IconTap
import io.chronoscore.ui.dialogs.KeypadDialog
import io.chronoscore.ui.dialogs.LibraryDialog
import io.chronoscore.ui.dialogs.MenuDialog
import io.chronoscore.ui.dialogs.OptionsDialog
import io.chronoscore.ui.dialogs.PlayerEditorDialog
import io.chronoscore.ui.dialogs.StatsDialog
import io.chronoscore.ui.dialogs.UtilitiesDialog
import io.chronoscore.ui.dialogs.knownGames
import io.chronoscore.ui.theme.ChronoIcons
import io.chronoscore.ui.theme.ChronoShapes
import io.chronoscore.ui.theme.ChronoTheme
import io.chronoscore.ui.theme.ChronoscoreTheme
import io.chronoscore.viewmodel.GameViewModel

private sealed interface OpenDialog {
    data object None : OpenDialog
    data object Utilities : OpenDialog
    data object History : OpenDialog
    data object Menu : OpenDialog
    data object EditAllSeats : OpenDialog
    data object CountersAllSeats : OpenDialog
    data object Library : OpenDialog
    data object Options : OpenDialog
    data object Stats : OpenDialog
    data class Keypad(val playerId: String, val target: CounterTarget, val rotated: Boolean) : OpenDialog
    data class Editor(val playerId: String, val rotated: Boolean) : OpenDialog
    data class Commander(val playerId: String, val rotated: Boolean) : OpenDialog
    data class RoundPoints(val playerId: String, val round: Int) : OpenDialog
}

@Composable
fun App(viewModel: GameViewModel, files: FileBridge = NoFileBridge) {
    val session by viewModel.session.collectAsState()
    val utility by viewModel.utility.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val seatStyle by viewModel.seatStyle.collectAsState()
    val library by viewModel.library.collectAsState()
    val presets by viewModel.presets.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val allSessions by viewModel.allSessions.collectAsState()
    var dialog by remember { mutableStateOf<OpenDialog>(OpenDialog.None) }
    var libraryNotice by remember { mutableStateOf<String?>(null) }
    val dispatch = viewModel::dispatch
    val now = rememberNow(session.timing.isRunning)
    val outcome = remember(session) { session.outcome() }

    // Timer mode: knock the active seat out as its bank hits zero (the engine re-checks the clock).
    val timedOut = session.options.timeControl?.outOnTimeout == true && session.timing.isRunning &&
        session.activePlayerId?.let(session::player)?.let { session.liveClockMillis(it, now) }?.let { it <= 0 } == true
    LaunchedEffect(timedOut) { if (timedOut) dispatch(GameAction.TimeOut) }

    // The library list is refreshed whenever a dialog that shows saved games opens.
    LaunchedEffect(dialog) {
        if (dialog == OpenDialog.Menu || dialog == OpenDialog.Library) viewModel.refreshLibrary()
        if (dialog == OpenDialog.Stats) viewModel.refreshStats()
        if (dialog != OpenDialog.Library) libraryNotice = null
    }

    ChronoscoreTheme(themeMode) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(ChronoTheme.colors.background).safeDrawingPadding(),
        ) {
            val strip = @Composable {
                CenterStrip(
                    session = session,
                    outcome = outcome,
                    now = now,
                    dispatch = dispatch,
                    onDice = { dialog = OpenDialog.Utilities },
                    onEditAll = { dialog = OpenDialog.EditAllSeats },
                    onLog = { dialog = OpenDialog.History },
                    onMenu = { dialog = OpenDialog.Menu },
                )
            }

            if (session.options.sheetMode) {
                Column(Modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    strip()
                    RoundSheet(
                        session = session,
                        outcome = outcome,
                        onCell = { id, round -> dialog = OpenDialog.RoundPoints(id, round) },
                        onNextRound = { dispatch(GameAction.NextRound) },
                        onEditSeat = { id -> dialog = OpenDialog.Editor(id, rotated = false) },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            } else {
                val grid = gridFor(session.players.size, isLandscape = maxWidth > maxHeight)
                PlayerGrid(
                    players = session.players,
                    grid = grid,
                    style = seatStyle,
                    highlightedPlayerId = utility.highlightedPlayerId,
                    lastEntry = session.historyLog.lastOrNull(),
                    callbacksFor = { player ->
                        // Dialogs face whoever sits on the far side when the card points away.
                        val rotated = grid.angleOf(player.seatIndex, player) in 91..269
                        SeatCallbacks(
                            onAdjust = { target, delta -> dispatch(GameAction.Adjust(player.id, target, delta)) },
                            onOpenKeypad = { target -> dialog = OpenDialog.Keypad(player.id, target, rotated) },
                            onRotate = { dispatch(GameAction.RotateSeat(player.id, Limits.ROTATION_STEP)) },
                            onEdit = { dialog = OpenDialog.Editor(player.id, rotated) },
                            onOpenCommander = { dialog = OpenDialog.Commander(player.id, rotated) },
                        )
                    },
                    statusFor = { player -> seatStatus(session, outcome, player, now) },
                    centerStrip = strip,
                    modifier = Modifier.fillMaxSize(),
                    clock = session.options.timeControl?.let { control ->
                        val paused = !session.timing.isRunning
                        ClockTiles(control, paused) {
                            dispatch(if (paused) GameAction.ToggleClock else GameAction.NextTurn)
                        }
                    },
                )
            }
        }

        val dismiss = { dialog = OpenDialog.None }
        when (val d = dialog) {
            OpenDialog.None -> Unit
            OpenDialog.Utilities -> UtilitiesDialog(
                state = utility,
                onRoll = viewModel::roll,
                onFlipCoin = viewModel::flipCoin,
                onPickFirst = viewModel::pickFirstPlayer,
                onShuffleOrder = viewModel::shuffleTurnOrder,
                onDismiss = dismiss,
                showDayNight = session.options.mtgExtras,
                dayNight = session.dayNight,
                onDayNight = { dispatch(GameAction.SetDayNight(it)) },
            )
            OpenDialog.History -> HistoryDialog(
                session = session,
                onUndo = { dispatch(GameAction.Undo) },
                onNextRound = { dispatch(GameAction.NextRound) },
                onOpenLibrary = { dialog = OpenDialog.Library },
                onDismiss = dismiss,
            )
            OpenDialog.Menu -> MenuDialog(
                session = session,
                themeMode = themeMode,
                seatStyle = seatStyle,
                knownGames = knownGames(library),
                dispatch = dispatch,
                onThemeMode = viewModel::setThemeMode,
                onSeatStyle = viewModel::setSeatStyle,
                onEditAllSeats = { dialog = OpenDialog.EditAllSeats },
                onCountersAllSeats = { dialog = OpenDialog.CountersAllSeats },
                onOpenLibrary = { dialog = OpenDialog.Library },
                onDismiss = dismiss,
                presets = presets,
                onMatchOptions = { dialog = OpenDialog.Options },
                onStats = { dialog = OpenDialog.Stats },
                onSavePreset = viewModel::savePreset,
                onDeletePreset = viewModel::deletePreset,
            )
            OpenDialog.Options -> OptionsDialog(
                options = session.options,
                onChange = { dispatch(GameAction.SetOptions(it)) },
                onDismiss = dismiss,
            )
            OpenDialog.Stats -> StatsDialog(
                sessions = allSessions,
                profiles = profiles,
                onDeleteProfile = viewModel::deleteProfile,
                onDismiss = dismiss,
            )
            OpenDialog.EditAllSeats -> EditAllSeatsDialog(session, dispatch, dismiss)
            OpenDialog.CountersAllSeats -> CountersAllSeatsDialog(session, dispatch, dismiss)
            OpenDialog.Library -> LibraryDialog(
                current = session,
                summaries = library,
                onSetGame = { dispatch(GameAction.SetGameName(it)) },
                onOpen = viewModel::openSession,
                onDelete = viewModel::deleteSession,
                onDismiss = dismiss,
                filesAvailable = files.available,
                onExportBackup = {
                    viewModel.exportBackup { text ->
                        files.saveText("chronoscore-backup-${isoDate(nowMillis())}.json", "application/json", text) { ok ->
                            libraryNotice = if (ok) "Backup saved." else "Backup not saved."
                        }
                    }
                },
                onImportBackup = {
                    files.openText(listOf("application/json", "text/plain", "application/octet-stream")) { text ->
                        if (text == null) libraryNotice = "Nothing imported."
                        else viewModel.importBackup(text) { libraryNotice = it }
                    }
                },
                onExportCsv = {
                    val name = (session.gameName ?: "match").filter { it.isLetterOrDigit() || it == ' ' || it == '-' }.trim()
                        .replace(' ', '-').lowercase().ifEmpty { "match" }
                    files.saveText("$name-${isoDate(session.timestamp)}.csv", "text/csv", matchCsv(session, ::formatDateTime)) { ok ->
                        libraryNotice = if (ok) "Match saved as CSV." else "CSV not saved."
                    }
                },
                notice = libraryNotice,
                onStats = { dialog = OpenDialog.Stats },
            )
            is OpenDialog.Keypad -> {
                val player = session.player(d.playerId)
                val current = player?.valueOf(d.target)
                if (player == null || current == null) {
                    LaunchedEffect(Unit) { dialog = OpenDialog.None }
                } else {
                    val counterName = (d.target as? CounterTarget.Sub)
                        ?.let { t -> player.subCounters.firstOrNull { it.id == t.subCounterId }?.name }
                    KeypadDialog(
                        playerName = player.name,
                        playerColor = player.colorHex,
                        targetLabel = counterName ?: "Score",
                        current = current,
                        rotated = d.rotated,
                        seatCount = session.players.size,
                        onDismiss = dismiss,
                        onApply = { allSeats, mode, value ->
                            when {
                                allSeats -> dispatch(
                                    GameAction.BulkEdit(session.players.map { it.id }, counterName, mode, value),
                                )
                                mode == BulkMode.SET -> dispatch(GameAction.SetValue(player.id, d.target, value))
                                else -> dispatch(GameAction.Adjust(player.id, d.target, value))
                            }
                        },
                    )
                }
            }
            is OpenDialog.RoundPoints -> {
                val player = session.player(d.playerId)
                if (player == null) {
                    LaunchedEffect(Unit) { dialog = OpenDialog.None }
                } else {
                    val current = session.roundPoints()[d.round to d.playerId] ?: 0
                    KeypadDialog(
                        playerName = player.name,
                        playerColor = player.colorHex,
                        targetLabel = "Round ${d.round}",
                        current = current,
                        rotated = false,
                        seatCount = 1,
                        onDismiss = dismiss,
                        onApply = { _, mode, value ->
                            val points = if (mode == BulkMode.SET) value else current + value
                            dispatch(GameAction.SetRoundPoints(player.id, d.round, points))
                        },
                    )
                }
            }
            is OpenDialog.Editor -> {
                val player = session.player(d.playerId)
                if (player == null) {
                    LaunchedEffect(Unit) { dialog = OpenDialog.None }
                } else {
                    PlayerEditorDialog(
                        player = player,
                        canRemove = session.players.size > 1,
                        rotated = d.rotated,
                        dispatch = dispatch,
                        onSetScore = { dialog = OpenDialog.Keypad(player.id, CounterTarget.Primary, d.rotated) },
                        onDismiss = dismiss,
                        options = session.options,
                        isActive = session.activePlayerId == player.id,
                        isMonarch = session.monarchId == player.id,
                        isInitiative = session.initiativeId == player.id,
                        profiles = profiles,
                        onSaveProfile = { viewModel.saveProfileFromSeat(player.id) },
                    )
                }
            }
            is OpenDialog.Commander -> CommanderDialog(session, d.playerId, d.rotated, dispatch, dismiss)
        }
    }
}

/** What a seat shows for the optional features that are on. */
private fun seatStatus(session: GameSession, outcome: Outcome, player: Player, now: Long): SeatStatus {
    val o = session.options
    val active = o.turnTracking && player.id == session.activePlayerId
    val clock = session.turnClock(player, now)
    // Seat timers show the seat's total time on turns; timer mode and the turn limit show the turn clock.
    val timeLabel = when {
        o.timeControl == null && o.seatTimers ->
            formatDuration(player.timeMillis + if (active) session.timing.liveTurnMillis(now) else 0)
        o.timeControl != null || o.turnLimitSeconds != null -> clock?.let { formatDuration(it.millis) }
        else -> null
    }
    return SeatStatus(
        active = active,
        out = player.id in outcome.eliminated,
        winner = player.id in outcome.winners,
        monarch = o.mtgExtras && session.monarchId == player.id,
        initiative = o.mtgExtras && session.initiativeId == player.id,
        timeLabel = timeLabel,
        overtime = clock?.overtime == true,
        clockMillis = session.liveClockMillis(player, now),
        commanderDamage = if (o.mtgExtras) player.commanderDamage.values.maxOrNull() ?: 0 else null,
    )
}

/** Center line: result banner, turn bar and hub, each only when there is something to show. */
@Composable
private fun CenterStrip(
    session: GameSession,
    outcome: Outcome,
    now: Long,
    dispatch: (GameAction) -> Unit,
    onDice: () -> Unit,
    onEditAll: () -> Unit,
    onLog: () -> Unit,
    onMenu: () -> Unit,
) {
    Column {
        ResultBanner(session, outcome)
        if (session.showsTurnBar()) {
            TurnBar(
                session = session,
                now = now,
                onPrevious = { dispatch(GameAction.PreviousTurn) },
                onNext = { dispatch(GameAction.NextTurn) },
                onToggleClock = { dispatch(GameAction.ToggleClock) },
            )
        }
        CenterHub(
            gameName = session.gameName,
            canUndo = session.historyLog.isNotEmpty(),
            onUndo = { dispatch(GameAction.Undo) },
            onDice = onDice,
            onEditAll = onEditAll,
            onLog = onLog,
            onMenu = onMenu,
        )
    }
}

/** Floating hub pill on the table's center line (UI kit · Controls · Center hub). */
@Composable
private fun CenterHub(
    gameName: String?,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onDice: () -> Unit,
    onEditAll: () -> Unit,
    onLog: () -> Unit,
    onMenu: () -> Unit,
) {
    val c = ChronoTheme.colors
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
        // The five buttons need ~250 dp; the name only joins them when it can't push one off.
        val showName = gameName != null && maxWidth >= 400.dp
        Row(
            Modifier
                .clip(ChronoShapes.hub)
                .background(c.surface)
                .border(1.dp, c.outline, ChronoShapes.hub)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The game this match is filed under, so a rename shows on the table.
            if (showName) {
                Text(
                    gameName!!.uppercase(),
                    style = ChronoTheme.type.label.copy(fontSize = 11.sp, letterSpacing = 1.sp),
                    color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp).padding(start = 8.dp, end = 4.dp).clickable(onClick = onMenu),
                )
            }
            IconTap(ChronoIcons.Undo, "Undo", enabled = canUndo, onClick = onUndo)
            IconTap(ChronoIcons.Dice, "Dice & utilities", onClick = onDice)
            IconTap(ChronoIcons.Grid, "Edit all seats", onClick = onEditAll)
            IconTap(ChronoIcons.Log, "Match log", onClick = onLog)
            IconTap(ChronoIcons.Menu, "Menu", onClick = onMenu)
        }
    }
}
