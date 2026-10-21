package io.chronoscore.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

/** Menu (UI kit · Menu & all seats): current game, saved matches, appearance, new game. */
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
) {
    var playerCount by remember { mutableIntStateOf(session.players.size) }
    var startingScore by remember { mutableIntStateOf(session.startingScore) }
    var customScore by remember { mutableStateOf(if (session.startingScore in ScorePresets) "" else "${session.startingScore}") }
    var gameName by remember { mutableStateOf(session.gameName.orEmpty()) }
    var confirmReset by remember { mutableStateOf(false) }

    DialogFrame("Menu", onDismiss, maxWidth = 420.dp) {
        SectionLabel("Current game")
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
            "Saved matches", Modifier.fillMaxWidth(),
            leadingIcon = ChronoIcons.Library, trailingIcon = ChronoIcons.ArrowRight, textAlign = TextAlign.Start,
            onClick = onOpenLibrary,
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
        GamePicker(gameName, { gameName = it }, knownGames)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Players", style = ChronoTheme.type.body, color = ChronoTheme.colors.text, modifier = Modifier.weight(1f))
            Stepper(
                "$playerCount", onDecrement = { playerCount-- }, onIncrement = { playerCount++ },
                decrementEnabled = playerCount > Limits.MIN_PLAYERS, incrementEnabled = playerCount < Limits.MAX_PLAYERS,
            )
        }
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
        Pill("Start new game", Modifier.fillMaxWidth()) {
            dispatch(GameAction.NewGame(playerCount, startingScore, gameName.ifBlank { null }))
            onDismiss()
        }
        Note("The current match stays in Saved matches, so you can continue it later.")
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
