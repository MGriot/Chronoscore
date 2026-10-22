package io.chronoscore.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.chronoscore.model.Limits
import io.chronoscore.model.Player
import io.chronoscore.model.TimeBonus
import io.chronoscore.model.TimeControl
import io.chronoscore.ui.formatDuration
import io.chronoscore.ui.theme.ChronoIcons
import io.chronoscore.ui.theme.ChronoTheme
import io.chronoscore.viewmodel.SeatStyle

/** Below this a running clock turns red. */
private const val LOW_TIME_MS = 10_000L

/** Table-wide state for timer mode's clock tiles. */
class ClockTiles(
    val control: TimeControl,
    /** The clock is paused (or not started yet); a tap on the active tile starts it. */
    val paused: Boolean,
    /** Ends the active seat's turn, or starts the clock while paused. */
    val onTap: () -> Unit,
)

/**
 * A seat in timer mode: the bank's time left in large figures. Tapping the active tile ends
 * the turn, like pressing a chess clock; other tiles ignore taps so a stray touch can't pass.
 */
@Composable
fun ClockTile(
    player: Player,
    angle: Int,
    highlighted: Boolean,
    style: SeatStyle,
    status: SeatStatus,
    callbacks: SeatCallbacks,
    tiles: ClockTiles,
    modifier: Modifier = Modifier,
) {
    val c = ChronoTheme.colors
    val p = seatPalette(player, style)
    val millis = status.clockMillis ?: 0
    val low = status.active && !tiles.paused && millis < LOW_TIME_MS
    val tappable = status.active && !status.out && !status.winner

    BoxWithConstraints(modifier.rotatedToFit(angle)) {
        val compact = maxWidth < COMPACT_WIDTH
        val ring = when {
            low -> c.destructive
            highlighted || status.active || status.winner -> p.ring
            else -> null
        }
        Column(
            Modifier
                .fillMaxSize()
                .alpha(if (status.out) OUT_ALPHA else if (status.active || status.winner) 1f else 0.8f)
                .clip(SeatShape)
                .background(p.fill)
                .then(if (ring != null) Modifier.border(4.dp, ring, SeatShape) else Modifier)
                .then(
                    if (tappable) Modifier.clickable(onClickLabel = if (tiles.paused) "Start clock" else "End turn", onClick = tiles.onTap)
                    else Modifier,
                ),
        ) {
            ClockHeader(player, p, compact, highlighted, status, callbacks)
            AutoSizeText(
                formatDuration(millis),
                if (low && style == SeatStyle.NEUTRAL) c.negative else p.text,
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = if (compact) 8.dp else 20.dp),
            )
            val hint = when {
                status.out -> "OUT OF TIME"
                tappable && tiles.paused -> "TAP TO START"
                tappable -> "TAP TO END TURN"
                else -> null
            }
            // Narrow tiles keep only the hint, so the caption never gets cut off.
            val bonus = tiles.control.bonusLabel()
            val caption = if (compact) hint ?: bonus.orEmpty() else listOfNotNull(hint, bonus).joinToString(" · ")
            Text(
                caption,
                style = ChronoTheme.type.label.copy(fontSize = if (compact) 10.sp else 12.sp, letterSpacing = 1.sp),
                color = p.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = if (compact) 8.dp else 12.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClockHeader(
    player: Player,
    p: SeatPalette,
    compact: Boolean,
    highlighted: Boolean,
    status: SeatStatus,
    callbacks: SeatCallbacks,
) {
    // The clock is the tile's figure, so the status tags leave the time label out.
    val tags = status.copy(timeLabel = null)
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 2.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 4.dp, height = 18.dp).background(p.bar))
        Spacer(Modifier.width(8.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                player.name.uppercase(),
                style = ChronoTheme.type.label.copy(fontWeight = FontWeight.SemiBold, letterSpacing = if (compact) 0.5.sp else 1.sp),
                color = p.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).clickable(onClick = callbacks.onEdit),
            )
            if (highlighted) SeatTag(if (compact) "1ST" else "FIRST", p)
            if (!compact) StatusTags(tags, p, compact = false)
        }
        if (!compact) HeaderIcon(ChronoIcons.Rotate, "Rotate ${Limits.ROTATION_STEP}°", p.muted, callbacks.onRotate)
        HeaderIcon(ChronoIcons.Edit, "Edit seat", p.muted, callbacks.onEdit)
    }
    if (compact) {
        FlowRow(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            StatusTags(tags, p, compact = true)
        }
    }
}

/** "+5s" for an increment, "5s delay" for a delay; null without a bonus. */
fun TimeControl.bonusLabel(): String? = when (bonus) {
    TimeBonus.NONE -> null
    TimeBonus.INCREMENT -> "+${bonusSeconds}s"
    TimeBonus.DELAY -> "${bonusSeconds}s delay"
}
