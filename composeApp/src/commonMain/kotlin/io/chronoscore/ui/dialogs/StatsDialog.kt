package io.chronoscore.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import io.chronoscore.engine.PlayerStats
import io.chronoscore.engine.computeStats
import io.chronoscore.model.GameSession
import io.chronoscore.model.Profile
import io.chronoscore.ui.formatDuration
import io.chronoscore.ui.theme.ChronoIcons
import io.chronoscore.ui.theme.ChronoShapes
import io.chronoscore.ui.theme.ChronoTheme
import io.chronoscore.ui.theme.parseHex
import kotlin.math.roundToInt

/**
 * Players & stats across saved matches, optionally for one game. Wins count matches with a
 * result (a win rule, or everyone else marked out). Saved players can be removed here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsDialog(
    sessions: List<GameSession>,
    profiles: List<Profile>,
    onDeleteProfile: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = ChronoTheme.colors
    var game by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    val games = sessions.mapNotNull { it.gameName }.distinctBy { it.lowercase() }
    val stats = computeStats(sessions, profiles, game)
    val inScope = sessions.count { game == null || it.gameName.equals(game, ignoreCase = true) }

    DialogFrame("Players & stats", onDismiss, maxWidth = 480.dp) {
        if (games.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("All games", selected = game == null) { game = null }
                games.forEach { g -> Pill(g, selected = game.equals(g, ignoreCase = true)) { game = g } }
            }
        }
        Note("$inScope ${if (inScope == 1) "match" else "matches"} · wins count matches with a result.")

        if (stats.isEmpty()) Note("No matches yet.")
        stats.forEach { s -> StatsRow(s) }

        if (profiles.isNotEmpty()) {
            SectionLabel("Saved players", Modifier.padding(top = 8.dp))
            Note("Pick a saved player from a seat's editor so stats follow them between matches.")
            profiles.forEach { profile ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(width = 3.dp, height = 18.dp).background(parseHex(profile.colorHex)))
                    Text(profile.name, style = ChronoTheme.type.body, color = c.text, modifier = Modifier.weight(1f))
                    if (confirmDelete == profile.id) {
                        Pill("Remove", style = PillStyle.DESTRUCTIVE) {
                            onDeleteProfile(profile.id)
                            confirmDelete = null
                        }
                    } else {
                        IconTap(ChronoIcons.Trash, "Remove ${profile.name}", tint = c.muted) { confirmDelete = profile.id }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsRow(s: PlayerStats) {
    val c = ChronoTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(ChronoShapes.control).background(c.surface)
            .border(1.dp, c.outline, ChronoShapes.control).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(width = 3.dp, height = 32.dp).background(parseHex(s.colorHex)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                s.name + if (s.isProfile) "" else "  ·  by name",
                style = ChronoTheme.type.body.copy(fontWeight = FontWeight.Medium), color = c.text,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val avg = (s.averageScore * 10).roundToInt() / 10.0
            val time = if (s.timeMillis > 0) " · ${formatDuration(s.timeMillis)} on turns" else ""
            Text(
                "${s.played} played · avg $avg · best ${s.bestScore}$time",
                style = ChronoTheme.type.body.copy(fontSize = 13.sp), color = c.muted,
                maxLines = 2,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${s.wins}", style = ChronoTheme.type.counter.copy(fontSize = 20.sp), color = c.text,
                textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 40.dp),
            )
            Text(
                s.winRate?.let { "${(it * 100).roundToInt()}% wins" } ?: "wins",
                style = ChronoTheme.type.body.copy(fontSize = 12.sp), color = c.muted,
            )
        }
    }
}
