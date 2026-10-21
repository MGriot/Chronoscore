package io.chronoscore.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.chronoscore.model.GameSession
import io.chronoscore.model.SessionSummary
import io.chronoscore.ui.formatDateTime
import io.chronoscore.ui.theme.ChronoIcons
import io.chronoscore.ui.theme.ChronoShapes
import io.chronoscore.ui.theme.ChronoTheme

/** Distinct game names, most recently played first. */
fun knownGames(summaries: List<SessionSummary>): List<String> =
    summaries.mapNotNull { it.gameName }.distinctBy { it.lowercase() }

/**
 * Saved matches grouped by game. Each match keeps its own players, counters and log;
 * Continue switches the table to it. The current match can be filed under a game here.
 */
@Composable
fun LibraryDialog(
    current: GameSession,
    summaries: List<SessionSummary>,
    onSetGame: (String?) -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
    filesAvailable: Boolean = false,
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    onExportCsv: () -> Unit = {},
    /** Result of the last export or import, shown under the buttons. */
    notice: String? = null,
    onStats: () -> Unit = {},
) {
    val c = ChronoTheme.colors
    var game by remember { mutableStateOf(current.gameName.orEmpty()) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    val games = knownGames(summaries)

    DialogFrame("Saved matches", onDismiss, maxWidth = 460.dp) {
        GamePicker(
            value = game,
            onValueChange = { game = it; onSetGame(it.ifBlank { null }) },
            knownGames = games,
            label = "This match belongs to",
        )
        Pill(
            "Players & stats", Modifier.fillMaxWidth(),
            leadingIcon = ChronoIcons.Person, trailingIcon = ChronoIcons.ArrowRight, textAlign = TextAlign.Start,
            onClick = onStats,
        )
        if (filesAvailable) {
            SectionLabel("Backup", Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Export all", Modifier.weight(1f), leadingIcon = ChronoIcons.Export, onClick = onExportBackup)
                Pill("Import", Modifier.weight(1f), leadingIcon = ChronoIcons.Import, onClick = onImportBackup)
            }
            Pill("This match as CSV", Modifier.fillMaxWidth(), leadingIcon = ChronoIcons.Sheet, onClick = onExportCsv)
            Note(notice ?: "Backups are a file you keep: every match, preset and saved player. Nothing is uploaded.")
        }

        // Show the current match under its (possibly just edited) game.
        val rows = summaries.map { if (it.id == current.id) it.copy(gameName = game.trim().ifEmpty { null }) else it }
        val groups = rows.groupBy { it.gameName?.lowercase() }.values
            .sortedByDescending { group -> group.maxOf { it.updatedAt } }

        if (rows.isEmpty()) Note("No saved matches yet.")

        groups.forEach { group ->
            val name = group.first().gameName
            SectionLabel(
                (name ?: "No game") + "  ·  ${group.size} " + if (group.size == 1) "match" else "matches",
                Modifier.padding(top = 8.dp),
            )
            group.forEach { s ->
                MatchRow(
                    summary = s,
                    isCurrent = s.id == current.id,
                    confirming = confirmDelete == s.id,
                    onOpen = { onOpen(s.id); onDismiss() },
                    onDelete = {
                        if (confirmDelete == s.id) {
                            onDelete(s.id)
                            confirmDelete = null
                        } else confirmDelete = s.id
                    },
                )
            }
        }
    }
}

@Composable
private fun MatchRow(
    summary: SessionSummary,
    isCurrent: Boolean,
    confirming: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = ChronoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ChronoShapes.control)
            .background(c.surface)
            .border(if (isCurrent) 1.5.dp else 1.dp, if (isCurrent) c.text else c.outline, ChronoShapes.control)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                formatDateTime(summary.createdAt),
                style = ChronoTheme.type.body.copy(fontWeight = FontWeight.Medium), color = c.text,
            )
            val players = summary.playerNames.size
            Text(
                "$players ${if (players == 1) "player" else "players"} · Round ${summary.round} · ${summary.entryCount} changes",
                style = ChronoTheme.type.body.copy(fontSize = 13.sp), color = c.muted,
            )
            Text(
                summary.playerNames.joinToString(" · "),
                style = ChronoTheme.type.body.copy(fontSize = 13.sp), color = c.muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrent) {
            Text(
                "CURRENT", style = ChronoTheme.type.label.copy(fontSize = 11.sp), color = c.onPrimary,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(c.primary).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        } else {
            Pill("Continue", style = PillStyle.PRIMARY, onClick = onOpen)
            if (confirming) Pill("Delete", style = PillStyle.DESTRUCTIVE, onClick = onDelete)
            else IconTap(ChronoIcons.Trash, "Delete match", tint = c.muted, onClick = onDelete)
        }
    }
}
