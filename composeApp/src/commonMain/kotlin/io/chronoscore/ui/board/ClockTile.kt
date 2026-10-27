package io.chronoscore.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.chronoscore.model.Player
import io.chronoscore.model.TimeBonus
import io.chronoscore.model.TimeControl
import io.chronoscore.ui.formatDuration
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

    RotatedSeat(angle, modifier) {
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
            // The clock is the tile's figure, so the tags leave the time label out.
            SeatHeader(player, p, compact, highlighted, status.copy(timeLabel = null), lastEntry = null, callbacks, showKeypad = false)
            AutoSizeText(
                formatDuration(millis),
                if (low && style == SeatStyle.NEUTRAL) c.negative else p.text,
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = if (compact) 8.dp else 20.dp),
            )
            // An increment is per seat, so it sits right under every clock; a delay is
            // table-wide and shows once, in the turn bar.
            if (tiles.control.bonus == TimeBonus.INCREMENT) {
                Text(
                    tiles.control.bonusLabel().orEmpty(),
                    style = ChronoTheme.type.counter.copy(fontSize = 12.sp),
                    color = p.muted,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            val hint = when {
                status.out -> "OUT OF TIME"
                tappable && tiles.paused -> "TAP TO START"
                tappable -> "TAP TO END TURN"
                else -> null
            }
            Text(
                hint.orEmpty(),
                style = ChronoTheme.type.label.copy(fontSize = if (compact) 10.sp else 12.sp, letterSpacing = 1.sp),
                // The hint is the tile's only instruction, so the active tile shows it in full colour.
                color = if (tappable) p.text else p.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = if (compact) 8.dp else 12.dp),
            )
        }
    }
}

/** "+5s" for an increment, "5s delay" for a delay; null without a bonus. */
fun TimeControl.bonusLabel(): String? = when (bonus) {
    TimeBonus.NONE -> null
    TimeBonus.INCREMENT -> "+${bonusSeconds}s"
    TimeBonus.DELAY -> "${bonusSeconds}s delay"
}
