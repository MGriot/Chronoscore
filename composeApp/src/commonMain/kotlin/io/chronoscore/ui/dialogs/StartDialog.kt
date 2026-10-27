package io.chronoscore.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.chronoscore.model.GameSession
import io.chronoscore.model.SessionSummary
import io.chronoscore.ui.formatDateTime
import io.chronoscore.ui.theme.ChronoIcons
import io.chronoscore.ui.theme.ChronoShapes
import io.chronoscore.ui.theme.ChronoTheme

/** Shown once at launch: continue the last match, set up a new one, or pick a saved one. */
@Composable
fun StartDialog(
    session: GameSession,
    summary: SessionSummary?,
    onContinue: () -> Unit,
    onNewGame: () -> Unit,
    onSavedMatches: () -> Unit,
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
        Pill(
            "New game", Modifier.fillMaxWidth(),
            leadingIcon = ChronoIcons.Plus, trailingIcon = ChronoIcons.ArrowRight, textAlign = TextAlign.Start,
            onClick = onNewGame,
        )
        Pill(
            "Saved matches", Modifier.fillMaxWidth(),
            leadingIcon = ChronoIcons.Library, trailingIcon = ChronoIcons.ArrowRight, textAlign = TextAlign.Start,
            onClick = onSavedMatches,
        )
        Note("A new game doesn't delete anything: this match stays in Saved matches.")
    }
}
