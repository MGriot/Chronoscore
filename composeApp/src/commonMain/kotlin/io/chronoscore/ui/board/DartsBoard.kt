package io.chronoscore.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.chronoscore.engine.BULL
import io.chronoscore.engine.CRICKET_CLOSED
import io.chronoscore.engine.CricketTargets
import io.chronoscore.engine.DARTS_PER_VISIT
import io.chronoscore.engine.DartsState
import io.chronoscore.engine.Outcome
import io.chronoscore.engine.checkout
import io.chronoscore.engine.dartsState
import io.chronoscore.engine.label
import io.chronoscore.engine.targetFor
import io.chronoscore.model.DartsGame
import io.chronoscore.model.DartsRules
import io.chronoscore.model.GameSession
import io.chronoscore.model.Player
import io.chronoscore.ui.dialogs.Segmented
import io.chronoscore.ui.theme.ChronoIcons
import io.chronoscore.ui.theme.ChronoTheme
import io.chronoscore.ui.theme.parseHex

/**
 * Darts mode: seat scores on top, the cricket marks when playing cricket, the visit in
 * progress, and a keypad that enters one dart at a time.
 */
@Composable
fun DartsBoard(
    session: GameSession,
    outcome: Outcome,
    onThrow: (segment: Int, multiplier: Int) -> Unit,
    onUndo: () -> Unit,
    onEditSeat: (playerId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ChronoTheme.colors
    val rules = session.options.darts ?: return
    val state = session.dartsState()
    val players = session.players.sortedBy { it.seatIndex }
    val shape = RoundedCornerShape(10.dp)

    val cricket = rules.game == DartsGame.CRICKET
    val scroll = rememberScrollState()

    Column(modifier.clip(shape).background(c.surface).border(1.dp, c.outline, shape)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                // At most four cards a row, so scores stay readable on a phone with 5–8 players.
                // Cricket keeps every card in one row over its mark column, after the numbers' column.
                val perRow = if (cricket) players.size else SEATS_PER_ROW
                Column(Modifier.fillMaxWidth().padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    players.chunked(perRow).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (cricket) Box(Modifier.width(CRICKET_NUMBER_WIDTH - 4.dp))
                            row.forEach { p ->
                                SeatCard(session, state, rules, outcome, p, Modifier.weight(1f), compact = cricket) { onEditSeat(p.id) }
                            }
                            // Keep a short last row's cards the same width as the rows above.
                            if (players.size > perRow) repeat(perRow - row.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
                if (cricket) CricketMarks(state, players)
                VisitRow(session, state)
                if (!cricket) RecentVisits(session, state)
            }
            // A fade at the bottom edge says the marks go on below the keypad's top.
            if (scroll.canScrollForward) {
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(28.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, c.surface))),
                )
            }
        }
        DartKeypad(
            enabled = !outcome.finished,
            target = session.activePlayerId?.let { state.targetFor(it, rules) },
            canUndo = session.darts.isNotEmpty(),
            onThrow = onThrow,
            onUndo = onUndo,
            // Thrown-dart entry wants big keys; cricket needs the height for its marks.
            keyHeight = if (cricket) 44.dp else 56.dp,
        )
    }
}

private val CRICKET_NUMBER_WIDTH = 44.dp

/** The last three finished visits, newest first, under the visit in progress. */
@Composable
private fun RecentVisits(session: GameSession, state: DartsState) {
    val c = ChronoTheme.colors
    val recent = state.visits.filter { it.closed }.takeLast(3).asReversed()
    if (recent.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("LAST VISITS", style = ChronoTheme.type.label.copy(fontSize = 10.sp), color = c.muted)
        recent.forEach { visit ->
            Row(Modifier.fillMaxWidth().heightIn(min = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    session.player(visit.playerId)?.name.orEmpty(), style = ChronoTheme.type.body.copy(fontSize = 13.sp),
                    color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Text(
                    visit.darts.joinToString("  ") { it.label() }, style = ChronoTheme.type.counter.copy(fontSize = 13.sp),
                    color = c.muted, maxLines = 1,
                )
                Text(
                    if (visit.bust) "BUST" else "${visit.darts.sumOf { it.points }}",
                    style = ChronoTheme.type.counter.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    color = if (visit.bust) c.negative else c.text, textAlign = TextAlign.End, modifier = Modifier.width(48.dp),
                )
            }
        }
    }
}

private const val SEATS_PER_ROW = 4

@Composable
private fun SeatCard(
    session: GameSession,
    state: DartsState,
    rules: DartsRules,
    outcome: Outcome,
    player: Player,
    modifier: Modifier,
    compact: Boolean,
    onEdit: () -> Unit,
) {
    val c = ChronoTheme.colors
    val active = player.id == session.activePlayerId && !outcome.finished
    val seat = state.seats[player.id]
    val accent = parseHex(player.colorHex)
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) c.raised else c.surface)
            .border(if (active) 2.dp else 1.dp, if (active) accent else c.outline, RoundedCornerShape(8.dp))
            .alpha(if (player.id in outcome.eliminated) 0.45f else 1f)
            .clickable(onClickLabel = "Edit ${player.name}", onClick = onEdit)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(accent))
        Text(
            player.name.uppercase(),
            style = ChronoTheme.type.label.copy(fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium),
            color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
        )
        if (player.isTeam) {
            Text(
                player.members.joinToString(" · ") { it.name },
                style = ChronoTheme.type.label.copy(fontSize = 9.sp, letterSpacing = 0.sp),
                color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "${player.primaryScore}",
            style = ChronoTheme.type.score.copy(fontSize = if (compact) 26.sp else 34.sp, lineHeight = if (compact) 28.sp else 38.sp),
            color = c.text, maxLines = 1,
        )
        val note = when {
            player.id in outcome.winners -> "WINNER"
            seat == null -> null
            rules.game == DartsGame.X01 && !seat.opened -> "DOUBLE TO START"
            rules.game == DartsGame.X01 && active -> {
                val left = DARTS_PER_VISIT - (state.openVisit?.takeIf { it.playerId == player.id }?.darts?.size ?: 0)
                checkout(player.primaryScore, left, rules.doubleOut)?.joinToString(" ")
            }
            rules.game == DartsGame.AROUND_THE_CLOCK -> seat.next?.let { "NEXT " + if (it == BULL) "BULL" else "$it" }
            rules.game == DartsGame.SHANGHAI -> state.targetFor(player.id, rules)?.let { "ROUND $it / ${rules.rounds}" }
            else -> null
        }
        if (!compact || note != null) {
            Text(
                note ?: if (active) "TURN" else " ",
                style = ChronoTheme.type.label.copy(fontSize = 10.sp), color = c.muted, maxLines = 1,
            )
        }
    }
}

/** Cricket: one row per number, marks drawn as / X Ⓧ; a number closed by everyone is dimmed. */
@Composable
private fun CricketMarks(state: DartsState, players: List<Player>) {
    val c = ChronoTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        CricketTargets.forEach { number ->
            val dead = players.all { state.seats[it.id]?.closed(number) == true }
            // Same spacing as the cards above, so each mark sits under its player.
            Row(
                Modifier.fillMaxWidth().height(22.dp).alpha(if (dead) 0.4f else 1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (number == BULL) "BULL" else "$number",
                    style = ChronoTheme.type.label.copy(fontSize = 11.sp), color = c.muted,
                    textAlign = TextAlign.Center, modifier = Modifier.width(CRICKET_NUMBER_WIDTH - 4.dp),
                )
                players.forEach { p ->
                    Text(
                        markGlyph(state.seats[p.id]?.marks?.get(number) ?: 0),
                        style = ChronoTheme.type.counter.copy(fontSize = 16.sp, lineHeight = 18.sp),
                        color = c.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.outline))
        }
    }
}

private fun markGlyph(marks: Int) = when (marks.coerceAtMost(CRICKET_CLOSED)) {
    0 -> ""
    1 -> "/"
    2 -> "X"
    else -> "Ⓧ"
}

/** The visit in progress (or the last one): three dart slots and the visit total. */
@Composable
private fun VisitRow(session: GameSession, state: DartsState) {
    val c = ChronoTheme.colors
    val visit = state.visits.lastOrNull()
    val shown = visit?.takeIf { !it.closed || it.playerId != session.activePlayerId }
    val name = shown?.let { session.player(it.playerId)?.name } ?: session.activePlayerId?.let(session::player)?.name ?: return
    Row(
        Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("VISIT", style = ChronoTheme.type.label.copy(fontSize = 10.sp), color = c.muted, maxLines = 1)
            Text(
                name, style = ChronoTheme.type.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = c.text,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        repeat(DARTS_PER_VISIT) { i ->
            val dart = shown?.darts?.getOrNull(i)
            Box(
                Modifier.width(46.dp).heightIn(min = 34.dp).clip(RoundedCornerShape(6.dp))
                    .background(if (dart == null) c.surface else c.raised)
                    .border(1.dp, c.outline, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(dart?.label() ?: "–", style = ChronoTheme.type.counter.copy(fontSize = 15.sp), color = if (dart == null) c.muted else c.text)
            }
        }
        Text(
            when {
                shown == null -> " "
                shown.bust -> "BUST"
                else -> "${shown.darts.sumOf { it.points }}"
            },
            style = ChronoTheme.type.counter.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
            color = if (shown?.bust == true) c.negative else c.text,
            textAlign = TextAlign.Start, modifier = Modifier.width(44.dp),
        )
    }
}

/** Single / Double / Treble, then the number. The ring goes back to Single after each dart. */
@Composable
private fun DartKeypad(
    enabled: Boolean,
    target: Int?,
    canUndo: Boolean,
    onThrow: (Int, Int) -> Unit,
    onUndo: () -> Unit,
    keyHeight: Dp,
) {
    var ring by remember { mutableIntStateOf(1) }
    val c = ChronoTheme.colors
    val throwDart = { segment: Int ->
        onThrow(segment, if (segment == BULL) ring.coerceAtMost(2) else ring)
        ring = 1
    }
    Column(
        Modifier.fillMaxWidth().background(c.background).padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Segmented(
            options = listOf("Single", "Double", "Treble"),
            selectedIndex = ring - 1,
            onSelect = { ring = it + 1 },
        )
        // Six keys a row keeps the board above the keypad readable on a phone.
        (1..18).chunked(6).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { n -> DartKey("$n", Modifier.weight(1f), keyHeight, selected = n == target, enabled = enabled) { throwDart(n) } }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(19, 20).forEach { n -> DartKey("$n", Modifier.weight(1f), keyHeight, selected = n == target, enabled = enabled) { throwDart(n) } }
            DartKey(
                if (ring >= 2) "Bull" else "25", Modifier.weight(1f), keyHeight,
                selected = target == BULL, enabled = enabled && ring <= 2,
            ) { throwDart(BULL) }
            DartKey("Miss", Modifier.weight(1f), keyHeight, enabled = enabled) { throwDart(0) }
            // Undo erases a dart, so it is outlined, set apart from Miss, and not styled like a score.
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.weight(1.5f).heightIn(min = keyHeight).clip(RoundedCornerShape(8.dp))
                    .border(1.dp, c.outline, RoundedCornerShape(8.dp))
                    .clickable(enabled = canUndo, onClickLabel = "Undo last dart", onClick = onUndo),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ChronoIcons.Undo, "Undo last dart", Modifier.size(22.dp), tint = if (canUndo) c.muted else c.muted.copy(alpha = 0.35f))
            }
        }
    }
}

@Composable
private fun DartKey(text: String, modifier: Modifier, height: Dp, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val c = ChronoTheme.colors
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .heightIn(min = height)
            .clip(shape)
            .background(if (selected) c.primary else c.surface)
            .border(1.dp, c.outline, shape)
            .clickable(enabled = enabled, onClickLabel = text, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = ChronoTheme.type.counter.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
            color = when {
                !enabled -> c.muted.copy(alpha = 0.45f)
                selected -> c.onPrimary
                else -> c.text
            },
            maxLines = 1,
        )
    }
}
