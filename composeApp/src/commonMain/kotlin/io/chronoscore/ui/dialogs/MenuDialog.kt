package io.chronoscore.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.chronoscore.engine.GameAction
import io.chronoscore.model.Accents
import io.chronoscore.model.BuiltInPresets
import io.chronoscore.model.GamePreset
import io.chronoscore.model.GameSession
import io.chronoscore.model.Limits
import io.chronoscore.ui.theme.ChronoIcons
import io.chronoscore.ui.theme.ChronoShapes
import io.chronoscore.ui.theme.ChronoTheme
import io.chronoscore.ui.theme.parseHex
import io.chronoscore.ui.theme.seatFill
import io.chronoscore.viewmodel.SeatStyle
import io.chronoscore.viewmodel.ThemeMode

private val ScorePresets = listOf(20, 30, 40)
private val TeamSizes = listOf(1, 2, 3)

/** Menu (UI kit · Menu & all seats): current game, saved matches, appearance, new game. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MenuDialog(
    session: GameSession,
    themeMode: ThemeMode,
    seatStyle: SeatStyle,
    knownGames: List<String>,
    dispatch: (GameAction) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onSeatStyle: (SeatStyle) -> Unit,
    onEditAllSeats: () -> Unit,
    onCountersAllSeats: () -> Unit,
    onOpenLibrary: () -> Unit,
    onDismiss: () -> Unit,
    presets: List<GamePreset> = emptyList(),
    onMatchOptions: () -> Unit = {},
    onStats: () -> Unit = {},
    onSavePreset: (GamePreset) -> Unit = {},
    onDeletePreset: (String) -> Unit = {},
    /** Only the New game section, for the start screen. */
    newGameOnly: Boolean = false,
) {
    var playerCount by remember { mutableIntStateOf(session.players.size) }
    var startingScore by remember { mutableIntStateOf(session.startingScore) }
    var customScore by remember { mutableStateOf(if (session.startingScore in ScorePresets) "" else "${session.startingScore}") }
    var currentGame by remember { mutableStateOf(session.gameName.orEmpty()) }
    var currentGameChanged by remember { mutableStateOf(false) }
    var gameName by remember { mutableStateOf(session.gameName.orEmpty()) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmNewGame by remember { mutableStateOf(false) }
    var presetId by remember { mutableStateOf<String?>(null) }
    // Null keeps each seat's current counters; a preset sets them for every seat.
    var counters by remember { mutableStateOf<List<String>?>(null) }
    var newOptions by remember { mutableStateOf(session.options) }
    var showOptions by remember { mutableStateOf(false) }
    var presetSaved by remember { mutableStateOf(false) }
    var teamSize by remember { mutableIntStateOf(session.players.first().members.size.coerceAtLeast(1)) }

    DialogFrame(if (newGameOnly) "New game" else "Menu", onDismiss, maxWidth = 420.dp) {
        if (!newGameOnly) {
            SectionLabel("Current game")
            // Applies to the match on the table right away; the New game section below is separate.
            GamePicker(
                value = currentGame,
                onValueChange = {
                    currentGame = it
                    currentGameChanged = true
                    dispatch(GameAction.SetGameName(it.ifBlank { null }))
                },
                knownGames = knownGames,
                label = "This match's game",
            )
            if (currentGameChanged) {
                val name = currentGame.trim()
                Note(
                    if (name.isEmpty()) "Saved. This match is now filed under \"No game\"."
                    else "Saved. This match is now filed under \"$name\" in Saved matches.",
                )
            }
            Pill(
                "Edit all seats", Modifier.fillMaxWidth(), style = PillStyle.PRIMARY,
                leadingIcon = ChronoIcons.Grid, trailingIcon = ChronoIcons.ArrowRight, textAlign = TextAlign.Start,
                onClick = onEditAllSeats,
            )
            Pill(
                "Counters on every seat", Modifier.fillMaxWidth(),
                leadingIcon = ChronoIcons.Plus, trailingIcon = ChronoIcons.ArrowRight, textAlign = TextAlign.Start,
                onClick = onCountersAllSeats,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Add seat", Modifier.weight(1f), enabled = session.players.size < Limits.MAX_PLAYERS) {
                    dispatch(GameAction.AddPlayer)
                }
                Pill(
                    if (confirmReset) "Tap to confirm" else "Reset scores", Modifier.weight(1f),
                    style = if (confirmReset) PillStyle.DESTRUCTIVE else PillStyle.SECONDARY,
                ) {
                    if (confirmReset) {
                        dispatch(GameAction.ResetScores)
                        onDismiss()
                    } else confirmReset = true
                }
            }
            Pill(
                "Match options · ${session.options.summary()}", Modifier.fillMaxWidth(),
                leadingIcon = ChronoIcons.Options, trailingIcon = ChronoIcons.ArrowRight, textAlign = TextAlign.Start,
                onClick = onMatchOptions,
            )
            Pill(
                "Saved matches", Modifier.fillMaxWidth(),
                leadingIcon = ChronoIcons.Library, trailingIcon = ChronoIcons.ArrowRight, textAlign = TextAlign.Start,
                onClick = onOpenLibrary,
            )
            Pill(
                "Players & stats", Modifier.fillMaxWidth(),
                leadingIcon = ChronoIcons.Person, trailingIcon = ChronoIcons.ArrowRight, textAlign = TextAlign.Start,
                onClick = onStats,
            )

            SectionLabel("Appearance", Modifier.padding(top = 8.dp))
            Text("Seat style", style = ChronoTheme.type.body, color = ChronoTheme.colors.text)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeatStylePreview(SeatStyle.NEUTRAL, seatStyle == SeatStyle.NEUTRAL, Modifier.weight(1f)) { onSeatStyle(SeatStyle.NEUTRAL) }
                SeatStylePreview(SeatStyle.COLORED, seatStyle == SeatStyle.COLORED, Modifier.weight(1f)) { onSeatStyle(SeatStyle.COLORED) }
            }
            Text("Theme", style = ChronoTheme.type.body, color = ChronoTheme.colors.text)
            Segmented(
                options = listOf("System", "Dark", "Light"),
                selectedIndex = themeMode.ordinal,
                onSelect = { onThemeMode(ThemeMode.entries[it]) },
            )

            SectionLabel("New game", Modifier.padding(top = 8.dp))
        }
        if (presets.isNotEmpty()) {
            Text("Preset", style = ChronoTheme.type.body, color = ChronoTheme.colors.text)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { preset ->
                    Pill(preset.name, selected = preset.id == presetId) {
                        presetId = preset.id
                        playerCount = preset.playerCount
                        startingScore = preset.startingScore
                        customScore = if (preset.startingScore in ScorePresets) "" else "${preset.startingScore}"
                        gameName = preset.gameName.orEmpty()
                        counters = preset.counters
                        newOptions = preset.options
                        teamSize = preset.teamSize ?: 1
                        presetSaved = false
                    }
                }
            }
            presets.firstOrNull { it.id == presetId }?.let { preset ->
                Note(
                    (preset.teamSize?.takeIf { it > 1 }?.let { "${preset.playerCount} teams of $it" } ?: "${preset.playerCount} players") +
                        " · start ${preset.startingScore}" +
                        (if (preset.counters.isEmpty()) "" else " · " + preset.counters.joinToString(", ")) +
                        " · " + preset.options.summary(),
                )
                if (!BuiltInPresets.isBuiltIn(preset)) {
                    Pill("Delete this preset", Modifier.fillMaxWidth()) {
                        onDeletePreset(preset.id)
                        presetId = null
                    }
                }
            }
        }
        GamePicker(gameName, { gameName = it; confirmNewGame = false }, knownGames, label = "Game for the new match (optional)")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (teamSize > 1) "Teams" else "Players", style = ChronoTheme.type.body, color = ChronoTheme.colors.text, modifier = Modifier.weight(1f))
            Stepper(
                "$playerCount", onDecrement = { playerCount-- }, onIncrement = { playerCount++ },
                decrementEnabled = playerCount > Limits.MIN_PLAYERS, incrementEnabled = playerCount < Limits.MAX_PLAYERS,
            )
        }
        // A team seat shares one score between its members (Scopone, Burraco in pairs, bocce teams).
        Segmented(
            options = TeamSizes.map { if (it == 1) "One per seat" else "Teams of $it" },
            selectedIndex = TeamSizes.indexOf(teamSize).coerceAtLeast(0),
            onSelect = { teamSize = TeamSizes[it]; presetSaved = false },
        )
        if (newOptions.darts != null) {
            // The engine takes the start from the darts game (X01 start, otherwise 0).
            Note("The darts game sets the starting score.")
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScorePresets.forEach { s ->
                    Pill("Start $s", Modifier.weight(1f), selected = customScore.isEmpty() && startingScore == s) {
                        startingScore = s; customScore = ""
                    }
                }
            }
            ChronoTextField(
                value = customScore,
                onValueChange = { v ->
                    customScore = v.filter(Char::isDigit).take(5)
                    customScore.toIntOrNull()?.let { startingScore = it }
                },
                placeholder = "Custom starting score",
                keyboardType = KeyboardType.Number,
                showCounter = false,
            )
        }
        Pill(
            "Options · ${newOptions.summary()}", Modifier.fillMaxWidth(),
            leadingIcon = ChronoIcons.Options, trailingIcon = if (showOptions) ChronoIcons.Minus else ChronoIcons.Plus,
            textAlign = TextAlign.Start,
        ) { showOptions = !showOptions }
        if (showOptions) OptionsEditor(newOptions) { newOptions = it; presetSaved = false }
        // Two taps, like Reset: a new game clears the table (the current match stays saved).
        Pill(
            if (confirmNewGame) "Tap again to start" else "Start new game", Modifier.fillMaxWidth(),
            style = PillStyle.PRIMARY,
        ) {
            if (confirmNewGame) {
                dispatch(GameAction.NewGame(playerCount, startingScore, gameName.ifBlank { null }, counters, newOptions, teamSize))
                onDismiss()
            } else confirmNewGame = true
        }
        Pill(if (presetSaved) "Saved as a preset" else "Save as preset", Modifier.fillMaxWidth(), enabled = !presetSaved) {
            onSavePreset(
                GamePreset(
                    id = "",
                    name = gameName.trim().ifEmpty { "My preset" },
                    gameName = gameName.trim().ifEmpty { null },
                    playerCount = playerCount,
                    startingScore = startingScore,
                    counters = counters ?: session.players.first().subCounters.map { it.name },
                    options = newOptions,
                    teamSize = teamSize.takeIf { it > 1 },
                ),
            )
            presetSaved = true
        }
        Note(
            "Starting a new game doesn't delete anything: the current match" +
                (session.gameName?.let { " ($it)" } ?: "") + " stays in Saved matches, so you can continue it later.",
        )
    }
}

/** Mini 2×2 table preview for the seat style choice. */
@Composable
private fun SeatStylePreview(style: SeatStyle, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = ChronoTheme.colors
    val accents = listOf(Accents.BLUE, Accents.ROSE, Accents.GREEN, Accents.AMBER).map(::parseHex)
    Column(
        modifier
            .clip(ChronoShapes.dialog)
            .background(c.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.text else c.outline, ChronoShapes.dialog)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        accents.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { accent ->
                    Row(
                        Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(4.dp))
                            .background(if (style == SeatStyle.COLORED) seatFill(accent) else c.raised),
                    ) {
                        Box(Modifier.width(3.dp).height(26.dp).background(if (style == SeatStyle.COLORED) Color.Transparent else accent))
                    }
                }
            }
        }
        Spacer(Modifier.size(2.dp))
        Text(
            if (style == SeatStyle.NEUTRAL) "Neutral" else "Colored",
            style = ChronoTheme.type.body.copy(fontSize = 14.sp), color = c.text,
        )
    }
}
