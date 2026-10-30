package io.chronoscore.ui.dialogs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.chronoscore.engine.GameAction
import io.chronoscore.model.GamePreset
import io.chronoscore.model.GameSession
import io.chronoscore.model.SessionSummary
import io.chronoscore.model.seatsLabel
import io.chronoscore.ui.formatDateTime
import io.chronoscore.ui.theme.ChronoIcons
import io.chronoscore.ui.theme.ChronoShapes
import io.chronoscore.ui.theme.ChronoTheme

/** One quick-start tile: tap starts it, long-press opens it in the setup. */
private class QuickStart(val title: String, val detail: String, val start: () -> Unit, val customize: () -> Unit)

/**
 * Home, shown once at launch: continue the last match, start a recent game or preset in one
 * tap with the same players, set up a custom game, or pick a saved match.
 */
@Composable
fun StartDialog(
    session: GameSession,
    summary: SessionSummary?,
    onContinue: () -> Unit,
    onNewGame: () -> Unit,
    onSavedMatches: () -> Unit,
    quickStarts: List<GamePreset> = emptyList(),
    onQuickStart: (GameAction.NewGame) -> Unit = {},
    onCustomize: (GamePreset?) -> Unit = { onNewGame() },
) {
    val c = ChronoTheme.colors
    // Dismissing keeps the match on the table, same as Continue.
    DialogFrame("Chronoscore", onContinue, maxWidth = 420.dp) {
        SectionLabel("Last match", topGap = 0.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(ChronoShapes.dialog)
                .background(c.raised)
                .border(1.dp, c.outline, ChronoShapes.dialog)
                .clickable(onClick = onContinue)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(session.gameName ?: "No game", style = ChronoTheme.type.body, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Note(session.players.joinToString(", ") { it.name })
            Note(
                "Round ${session.round}" +
                    (summary?.let { " · last played ${formatDateTime(it.updatedAt)}" } ?: ""),
            )
        }
        Pill(
            "Continue", Modifier.fillMaxWidth(), style = PillStyle.PRIMARY,
            leadingIcon = ChronoIcons.Play, trailingIcon = ChronoIcons.ArrowRight, textAlign = TextAlign.Start,
            onClick = onContinue,
        )

        SectionLabel("Quick start")
        val seats = session.players.size
        val tiles = listOf(
            QuickStart(
                session.gameName?.let { "$it again" } ?: "Same game again",
                "$seats " + (if (seats == 1) "player" else "players") + " · same setup",
                start = { onQuickStart(session.rematch()) },
                customize = { onCustomize(null) },
            ),
        ) + quickStarts.map { preset ->
            QuickStart(preset.name, presetLine(preset), { onQuickStart(preset.newGameAction()) }, { onCustomize(preset) })
        }
        tiles.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { QuickTile(it, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Note("Tap to start with the players at the table. Long-press to change the setup first.")

        NavRow("Custom game…", ChronoIcons.Plus, onClick = onNewGame)
        NavRow("Saved matches", ChronoIcons.Library, onClick = onSavedMatches)
        Note("A new game doesn't delete anything: this match stays in Saved matches.")
    }
}

private fun presetLine(preset: GamePreset): String =
    preset.seatsLabel() + if (preset.options.darts == null) " · start ${preset.startingScore}" else ""

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickTile(tile: QuickStart, modifier: Modifier) {
    val c = ChronoTheme.colors
    Column(
        modifier
            .heightIn(min = 64.dp)
            .clip(ChronoShapes.control)
            .background(c.surface)
            .border(1.dp, c.outline, ChronoShapes.control)
            .combinedClickable(
                onClickLabel = "Start ${tile.title}", onLongClickLabel = "Change setup",
                onLongClick = tile.customize, onClick = tile.start,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            tile.title, style = ChronoTheme.type.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp), color = c.text,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(tile.detail, style = ChronoTheme.type.body.copy(fontSize = 12.sp), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
