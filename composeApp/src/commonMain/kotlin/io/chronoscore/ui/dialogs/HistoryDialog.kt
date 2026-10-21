package io.chronoscore.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.chronoscore.model.CounterTarget
import io.chronoscore.model.GameSession
import io.chronoscore.ui.formatDateTime
import io.chronoscore.ui.theme.ChronoIcons
import io.chronoscore.ui.theme.ChronoTheme
import io.chronoscore.ui.theme.parseHex

/** Match log (UI kit · Dialogs): round-by-round transactions with single-step undo. */
@Composable
fun HistoryDialog(
    session: GameSession,
    onUndo: () -> Unit,
    onNextRound: () -> Unit,
    onOpenLibrary: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = ChronoTheme.colors
    DialogFrame("Match log · Round ${session.round}", onDismiss, maxWidth = 440.dp) {
        Note("${session.gameName ?: "Match"} · started ${formatDateTime(session.timestamp)}")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Undo", Modifier.weight(1f), enabled = session.historyLog.isNotEmpty(), leadingIcon = ChronoIcons.Undo, onClick = onUndo)
            Pill("Next round →", Modifier.weight(1f), style = PillStyle.PRIMARY, onClick = onNextRound)
        }

        if (session.historyLog.isEmpty()) {
            Note("No score changes yet. Tap a seat's left or right half to count.")
        }

        val players = session.players.associateBy { it.id }
        session.historyLog
            .groupBy { it.round }
            .entries
            .sortedByDescending { it.key }
            .forEach { (round, entries) ->
                SectionLabel("Round $round", Modifier.padding(top = 4.dp))
                Column {
                    entries.asReversed().forEachIndexed { i, e ->
                        if (i > 0) Hairline()
                        val player = players[e.playerId]
                        val counter = when (val t = e.target) {
                            CounterTarget.Primary -> "Score"
                            is CounterTarget.Sub -> player?.subCounters?.firstOrNull { it.id == t.subCounterId }?.name ?: "Counter"
                            is CounterTarget.Commander -> "Cmdr · " + (players[t.fromPlayerId]?.name ?: "?")
                        }
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(width = 3.dp, height = 18.dp).background(player?.colorHex?.let(::parseHex) ?: c.outline))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                player?.name ?: "?", style = ChronoTheme.type.body.copy(fontWeight = FontWeight.Medium),
                                color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                            )
                            Text(
                                counter, style = ChronoTheme.type.body.copy(fontSize = 13.sp), color = c.muted,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                            )
                            Text(
                                (if (e.delta > 0) "+" else "−") + kotlin.math.abs(e.delta),
                                style = ChronoTheme.type.counter.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                                color = if (e.delta > 0) c.positive else c.negative,
                                textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 52.dp),
                            )
                            Text(
                                "${e.resultingValue}", style = ChronoTheme.type.counter.copy(fontSize = 15.sp),
                                color = c.text, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 52.dp),
                            )
                        }
                    }
                }
            }

        Pill("Saved matches", Modifier.fillMaxWidth(), leadingIcon = ChronoIcons.Library, trailingIcon = ChronoIcons.ArrowRight, textAlign = TextAlign.Start, onClick = onOpenLibrary)
    }
}
