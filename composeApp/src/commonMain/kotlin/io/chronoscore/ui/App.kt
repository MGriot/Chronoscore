package io.chronoscore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.unit.dp
import io.chronoscore.engine.BulkMode
import io.chronoscore.engine.GameAction
import io.chronoscore.model.CounterTarget
import io.chronoscore.ui.board.PlayerGrid
import io.chronoscore.ui.board.SeatCallbacks
import io.chronoscore.ui.board.gridFor
import io.chronoscore.ui.dialogs.CountersAllSeatsDialog
import io.chronoscore.ui.dialogs.EditAllSeatsDialog
import io.chronoscore.ui.dialogs.HistoryDialog
import io.chronoscore.ui.dialogs.IconTap
import io.chronoscore.ui.dialogs.KeypadDialog
import io.chronoscore.ui.dialogs.LibraryDialog
import io.chronoscore.ui.dialogs.MenuDialog
import io.chronoscore.ui.dialogs.PlayerEditorDialog
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
    data class Keypad(val playerId: String, val target: CounterTarget, val rotated: Boolean) : OpenDialog
    data class Editor(val playerId: String, val rotated: Boolean) : OpenDialog
}

@Composable
fun App(viewModel: GameViewModel) {
    val session by viewModel.session.collectAsState()
    val utility by viewModel.utility.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val seatStyle by viewModel.seatStyle.collectAsState()
    val library by viewModel.library.collectAsState()
    var dialog by remember { mutableStateOf<OpenDialog>(OpenDialog.None) }
    val dispatch = viewModel::dispatch

    // The library list is refreshed whenever a dialog that shows saved games opens.
    LaunchedEffect(dialog) {
        if (dialog == OpenDialog.Menu || dialog == OpenDialog.Library) viewModel.refreshLibrary()
    }

    ChronoscoreTheme(themeMode) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(ChronoTheme.colors.background).safeDrawingPadding(),
        ) {
            val grid = gridFor(session.players.size, isLandscape = maxWidth > maxHeight)

            PlayerGrid(
                players = session.players,
                grid = grid,
                style = seatStyle,
                highlightedPlayerId = utility.highlightedPlayerId,
                lastEntry = session.historyLog.lastOrNull(),
                callbacksFor = { player ->
                    val rotated = grid.defaultFlipped(player.seatIndex) != player.isFlipped
                    SeatCallbacks(
                        onAdjust = { target, delta -> dispatch(GameAction.Adjust(player.id, target, delta)) },
                        onOpenKeypad = { target -> dialog = OpenDialog.Keypad(player.id, target, rotated) },
                        onToggleFlip = { dispatch(GameAction.ToggleFlip(player.id)) },
                        onEdit = { dialog = OpenDialog.Editor(player.id, rotated) },
                    )
                },
                centerStrip = {
                    CenterHub(
                        canUndo = session.historyLog.isNotEmpty(),
                        onUndo = { dispatch(GameAction.Undo) },
                        onDice = { dialog = OpenDialog.Utilities },
                        onEditAll = { dialog = OpenDialog.EditAllSeats },
                        onLog = { dialog = OpenDialog.History },
                        onMenu = { dialog = OpenDialog.Menu },
                    )
                },
                modifier = Modifier.fillMaxSize(),
            )
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
                    )
                }
            }
        }
    }
}

/** Floating hub pill on the table's center line (UI kit · Controls · Center hub). */
@Composable
private fun CenterHub(
    canUndo: Boolean,
    onUndo: () -> Unit,
    onDice: () -> Unit,
    onEditAll: () -> Unit,
    onLog: () -> Unit,
    onMenu: () -> Unit,
) {
    val c = ChronoTheme.colors
    Box(Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .clip(ChronoShapes.hub)
                .background(c.surface)
                .border(1.dp, c.outline, ChronoShapes.hub)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTap(ChronoIcons.Undo, "Undo", enabled = canUndo, onClick = onUndo)
            IconTap(ChronoIcons.Dice, "Dice & utilities", onClick = onDice)
            IconTap(ChronoIcons.Grid, "Edit all seats", onClick = onEditAll)
            IconTap(ChronoIcons.Log, "Match log", onClick = onLog)
            IconTap(ChronoIcons.Menu, "Menu", onClick = onMenu)
        }
    }
}
