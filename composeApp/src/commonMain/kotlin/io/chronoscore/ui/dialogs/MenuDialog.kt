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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

/**
 * Menu (UI kit · Menu & all seats), short enough for one phone screen: this match's game and
 * tools, then rows to New game, Saved matches, stats and Appearance. Reset scores sits apart
 * at the bottom, since it is the one action here that discards data.
 */
@Composable
fun MenuDialog(
    session: GameSession,
    knownGames: List<String>,
    dispatch: (GameAction) -> Unit,
    onCountersAllSeats: () -> Unit,
    onOpenLibrary: () -> Unit,
    onDismiss: () -> Unit,
    onNewGame: () -> Unit = {},
    onMatchOptions: () -> Unit = {},
    onStats: () -> Unit = {},
    onAppearance: () -> Unit = {},
) {
    var currentGame by remember { mutableStateOf(session.gameName.orEmpty()) }
    var currentGameChanged by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var seatsAdded by remember { mutableIntStateOf(0) }

    DialogFrame("Menu", onDismiss, maxWidth = 420.dp) {
        SectionLabel("This match", topGap = 0.dp)
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
        NavRow("Match options · ${session.options.shortSummary()}", ChronoIcons.Options, onClick = onMatchOptions)
        NavRow("Counters on every seat", ChronoIcons.Plus, onClick = onCountersAllSeats)
        val seats = session.players.size
        val full = seats >= Limits.MAX_PLAYERS
        Pill(
            if (full) "Table full · $seats of ${Limits.MAX_PLAYERS}" else "Add seat · $seats of ${Limits.MAX_PLAYERS}",
            Modifier.fillMaxWidth(), leadingIcon = ChronoIcons.Person, enabled = !full,
        ) {
            dispatch(GameAction.AddPlayer)
            seatsAdded++
        }
        // The Menu covers the board, so say what the tap did.
        if (seatsAdded > 0) {
            Note(
                (if (seatsAdded == 1) "Added \"${session.players.last().name}\"." else "Added $seatsAdded seats.") +
                    " Close the menu to see it on the board; tap a seat there to rename it.",
            )
        }

        SectionLabel("Chronoscore")
        NavRow("New game", ChronoIcons.Play, onClick = onNewGame)
        NavRow("Saved matches", ChronoIcons.Library, onClick = onOpenLibrary)
        NavRow("Players & stats", ChronoIcons.Person, onClick = onStats)
        NavRow("Appearance", ChronoIcons.Palette, onClick = onAppearance)

        Spacer(Modifier.height(12.dp))
        Pill(
            if (confirmReset) "Tap to confirm" else "Reset scores", Modifier.fillMaxWidth(),
            style = if (confirmReset) PillStyle.DESTRUCTIVE else PillStyle.SECONDARY,
        ) {
            if (confirmReset) {
                dispatch(GameAction.ResetScores)
                onDismiss()
            } else confirmReset = true
        }
    }
}

/** Seat style and theme; device settings, not part of the match. */
@Composable
fun AppearanceDialog(
    themeMode: ThemeMode,
    seatStyle: SeatStyle,
    onThemeMode: (ThemeMode) -> Unit,
    onSeatStyle: (SeatStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    DialogFrame("Appearance", onDismiss, maxWidth = 420.dp) {
        SectionLabel("Seat style", topGap = 0.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SeatStylePreview(SeatStyle.NEUTRAL, seatStyle == SeatStyle.NEUTRAL, Modifier.weight(1f)) { onSeatStyle(SeatStyle.NEUTRAL) }
            SeatStylePreview(SeatStyle.COLORED, seatStyle == SeatStyle.COLORED, Modifier.weight(1f)) { onSeatStyle(SeatStyle.COLORED) }
        }
        SectionLabel("Theme")
        Segmented(
            options = listOf("System", "Dark", "Light"),
            selectedIndex = themeMode.ordinal,
            onSelect = { onThemeMode(ThemeMode.entries[it]) },
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
