package io.chronoscore.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.chronoscore.engine.Outcome
import io.chronoscore.model.DayNight
import io.chronoscore.model.GameSession
import io.chronoscore.ui.dialogs.IconTap
import io.chronoscore.ui.dialogs.Pill
import io.chronoscore.ui.dialogs.PillStyle
import io.chronoscore.ui.formatDuration
import io.chronoscore.ui.theme.ChronoIcons
import io.chronoscore.ui.theme.ChronoShapes
import io.chronoscore.ui.theme.ChronoTheme
import io.chronoscore.ui.theme.MinTouch
import io.chronoscore.ui.theme.parseHex

/** True when the session shows anything in the turn bar. */
fun GameSession.showsTurnBar(): Boolean =
    options.turnTracking || options.matchClock || (options.mtgExtras && dayNight != null)

/**
 * Turn controls on the center line: Previous · whose turn (with turn time or countdown) ·
 * match clock with pause · Next. Parts appear only for the options that are on.
 */
@Composable
fun TurnBar(
    session: GameSession,
    now: Long,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleClock: () -> Unit,
) {
    val c = ChronoTheme.colors
    val o = session.options
    val active = session.activePlayerId?.let(session::player)
    val timing = session.timing
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
        val narrow = maxWidth < 360.dp
        Row(
            Modifier.widthIn(max = 520.dp).fillMaxWidth()
                .clip(ChronoShapes.hub).background(c.surface).border(1.dp, c.outline, ChronoShapes.hub)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // In darts, Undo on the keypad takes the turn back with the last dart.
            if (o.turnTracking && o.darts == null) IconTap(ChronoIcons.ChevronLeft, "Previous turn", onClick = onPrevious)
            Row(Modifier.weight(1f).heightIn(min = MinTouch), verticalAlignment = Alignment.CenterVertically) {
                if (o.turnTracking && active != null) {
                    Box(Modifier.size(width = 4.dp, height = 22.dp).background(parseHex(active.colorHex)))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            active.name, style = ChronoTheme.type.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                            color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        val clock = session.turnClock(active, now)
                        val detail = (if (narrow) "R" else "Round ") + session.round +
                            if (o.usesClock && clock != null) " · " + formatDuration(clock.millis) else ""
                        Text(
                            detail, style = ChronoTheme.type.counter.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                            color = if (clock?.overtime == true) c.negative else c.muted,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (o.mtgExtras && session.dayNight != null) {
                    Chip(if (session.dayNight == DayNight.DAY) (if (narrow) "D" else "DAY") else (if (narrow) "N" else "NIGHT"))
                    Spacer(Modifier.width(6.dp))
                }
                if (o.usesClock) ClockButton(o.matchClock, timing.liveMatchMillis(now), timing.isRunning, onToggleClock)
            }
            if (o.turnTracking) IconTap(ChronoIcons.ChevronRight, "Next turn", onClick = onNext)
        }
    }
}

/** Pause / resume, with the match time beside it when the match clock is on. */
@Composable
private fun ClockButton(showTime: Boolean, matchMillis: Long, running: Boolean, onClick: () -> Unit) {
    val c = ChronoTheme.colors
    Row(
        Modifier.heightIn(min = MinTouch).clip(ChronoShapes.control)
            .clickable(onClickLabel = if (running) "Pause clock" else "Resume clock", onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (running) ChronoIcons.Pause else ChronoIcons.Play, null, Modifier.size(18.dp), tint = if (running) c.muted else c.text)
        if (showTime) {
            Spacer(Modifier.width(6.dp))
            Text(formatDuration(matchMillis), style = ChronoTheme.type.counter.copy(fontSize = 15.sp), color = if (running) c.text else c.muted)
        } else if (!running) {
            Spacer(Modifier.width(6.dp))
            Text("PAUSED", style = ChronoTheme.type.label.copy(fontSize = 10.sp), color = c.muted)
        }
    }
}

@Composable
private fun Chip(text: String) {
    val c = ChronoTheme.colors
    Text(
        text, style = ChronoTheme.type.label.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
        color = c.text, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).border(1.dp, c.outline, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

/**
 * "Mara wins" (or a draw) once the win rule decides the match. It takes the turn bar's place,
 * with Rematch (same setup and players) and New game, so a finished match isn't a dead end.
 */
@Composable
fun ResultBanner(session: GameSession, outcome: Outcome, onRematch: () -> Unit, onNewGame: () -> Unit) {
    if (!outcome.finished) return
    val c = ChronoTheme.colors
    val winners = outcome.winners.mapNotNull(session::player)
    val text = when (winners.size) {
        0 -> "Draw · nobody left standing"
        1 -> "${winners[0].name} wins"
        else -> winners.joinToString(" & ") { it.name } + " tie"
    }
    val accent = winners.singleOrNull()?.colorHex?.let(::parseHex)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
        val narrow = maxWidth < 360.dp
        Row(
            Modifier.widthIn(max = 520.dp).fillMaxWidth()
                .clip(ChronoShapes.hub).background(c.surface).border(1.dp, accent ?: c.outline, ChronoShapes.hub)
                .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(ChronoIcons.Trophy, null, Modifier.size(20.dp), tint = accent ?: c.text)
            Spacer(Modifier.width(8.dp))
            Text(
                text, style = ChronoTheme.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.text,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Pill(if (narrow) "New" else "New game", onClick = onNewGame)
            Spacer(Modifier.width(4.dp))
            Pill("Rematch", style = PillStyle.PRIMARY, onClick = onRematch)
        }
    }
}
