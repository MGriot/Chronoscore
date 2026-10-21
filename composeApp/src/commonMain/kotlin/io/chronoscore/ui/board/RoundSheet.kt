package io.chronoscore.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.chronoscore.engine.Outcome
import io.chronoscore.model.GameSession
import io.chronoscore.ui.dialogs.Pill
import io.chronoscore.ui.dialogs.PillStyle
import io.chronoscore.ui.theme.ChronoShapes
import io.chronoscore.ui.theme.ChronoTheme
import io.chronoscore.ui.theme.parseHex

/** Points each seat scored in each round, from the score changes logged in that round. */
fun GameSession.roundPoints(): Map<Pair<Int, String>, Int> =
    historyLog.filter { it.subCounterId == null }
        .groupBy { it.round to it.playerId }
        .mapValues { (_, entries) -> entries.sumOf { it.delta } }

/**
 * Scorepad layout (Round sheet option): one column per seat, one row per round, totals at
 * the bottom. Tapping a cell enters that seat's points for the round.
 */
@Composable
fun RoundSheet(
    session: GameSession,
    outcome: Outcome,
    onCell: (playerId: String, round: Int) -> Unit,
    onNextRound: () -> Unit,
    onEditSeat: (playerId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ChronoTheme.colors
    val points = session.roundPoints()
    val players = session.players.sortedBy { it.seatIndex }
    val listState = rememberLazyListState()
    LaunchedEffect(session.round) { listState.animateScrollToItem((session.round - 1).coerceAtLeast(0)) }

    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(c.surface).border(1.dp, c.outline, RoundedCornerShape(10.dp)),
    ) {
        // Header: seat names with their accent; the active seat and winners are marked.
        SheetRow(Modifier.heightIn(min = 52.dp)) {
            RoundCell("RND")
            players.forEach { p ->
                val active = p.id == session.activePlayerId
                Column(
                    Modifier.weight(1f).alpha(if (p.id in outcome.eliminated) 0.45f else 1f)
                        .clickable(onClickLabel = "Edit ${p.name}") { onEditSeat(p.id) }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.fillMaxWidth().height(if (active) 4.dp else 3.dp).background(parseHex(p.colorHex)))
                    Text(
                        p.name.uppercase(),
                        style = ChronoTheme.type.label.copy(fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium),
                        color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    val tag = when (p.id) {
                        in outcome.winners -> "WINNER"
                        in outcome.eliminated -> "OUT"
                        else -> if (active) "TURN" else null
                    }
                    if (tag != null) Text(tag, style = ChronoTheme.type.label.copy(fontSize = 9.sp), color = c.muted)
                }
            }
        }
        Divider()

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState) {
            items((1..session.round).toList(), key = { it }) { round ->
                val current = round == session.round
                SheetRow(
                    Modifier.heightIn(min = 44.dp)
                        .then(if (current) Modifier.background(c.raised.copy(alpha = 0.5f)) else Modifier),
                ) {
                    RoundCell("$round")
                    players.forEach { p ->
                        val value = points[round to p.id]
                        Box(
                            Modifier.weight(1f).heightIn(min = 44.dp)
                                .clickable(onClickLabel = "Points for ${p.name}, round $round") { onCell(p.id, round) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                value?.let(::signed) ?: if (current) "+" else "–",
                                style = ChronoTheme.type.counter.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
                                color = if (value == null) c.muted.copy(alpha = 0.6f) else c.text,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                Divider()
            }
        }

        Divider()
        SheetRow(Modifier.heightIn(min = 56.dp)) {
            RoundCell("TOTAL")
            players.forEach { p ->
                Text(
                    signed(p.primaryScore, plus = false),
                    style = ChronoTheme.type.score.copy(fontSize = 24.sp, lineHeight = 26.sp),
                    color = c.text, textAlign = TextAlign.Center, maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(8.dp)) {
            Pill("Next round →", Modifier.fillMaxWidth(), style = PillStyle.PRIMARY, onClick = onNextRound)
        }
    }
}

@Composable
private fun SheetRow(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
private fun RoundCell(text: String) {
    Text(
        text, style = ChronoTheme.type.label.copy(fontSize = 10.sp), color = ChronoTheme.colors.muted,
        textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.width(40.dp),
    )
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(ChronoTheme.colors.outline))
}

private fun signed(value: Int, plus: Boolean = true): String = when {
    value < 0 -> "−${-value}"
    plus && value > 0 -> "+$value"
    else -> "$value"
}
