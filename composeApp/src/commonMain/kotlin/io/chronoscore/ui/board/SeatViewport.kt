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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import io.chronoscore.engine.nowMillis
import io.chronoscore.model.CounterTarget
import io.chronoscore.model.Limits
import io.chronoscore.model.Player
import io.chronoscore.model.ScoreHistoryEntry
import io.chronoscore.model.SubCounter
import io.chronoscore.ui.theme.ChronoIcons
import io.chronoscore.ui.theme.ChronoShapes
import io.chronoscore.ui.theme.ChronoTheme
import io.chronoscore.ui.theme.MinTouch
import io.chronoscore.ui.theme.parseHex
import io.chronoscore.ui.theme.seatFill
import io.chronoscore.viewmodel.SeatStyle
import kotlinx.coroutines.delay

class SeatCallbacks(
    val onAdjust: (CounterTarget, Int) -> Unit,
    val onOpenKeypad: (CounterTarget) -> Unit,
    /** Turns the seat one rotation step clockwise. */
    val onRotate: () -> Unit,
    val onEdit: () -> Unit,
    val onOpenCommander: () -> Unit = {},
)

/** Optional-feature state shown on a seat; the default shows nothing extra (1.0 look). */
@Immutable
data class SeatStatus(
    /** It is this seat's turn (turn tracker). */
    val active: Boolean = false,
    /** Out by concession or the win rule; the seat is dimmed but still editable. */
    val out: Boolean = false,
    val winner: Boolean = false,
    val monarch: Boolean = false,
    val initiative: Boolean = false,
    /** Seat timer text, e.g. "4:12"; null when seat timers are off. */
    val timeLabel: String? = null,
    /** The active seat ran past the turn limit. */
    val overtime: Boolean = false,
    /** Timer mode: time left in this seat's bank (negative in overtime); null when off. */
    val clockMillis: Long? = null,
    /** MTG extras: highest commander damage taken from one commander; null when off. */
    val commanderDamage: Int? = null,
)

/** Colors inside one seat; the Colored style swaps the zinc card for the seat's accent. */
@Immutable
internal data class SeatPalette(
    val fill: Color,
    val text: Color,
    val muted: Color,
    val outline: Color,
    val bar: Color,
    val chipFill: Color,
    val ring: Color,
    val tagFill: Color,
    val tagText: Color,
)

internal val SeatShape = RoundedCornerShape(10.dp)
internal val COMPACT_WIDTH = 220.dp
internal const val OUT_ALPHA = 0.45f

@Composable
internal fun seatPalette(player: Player, style: SeatStyle): SeatPalette {
    val accent = parseHex(player.colorHex)
    val c = ChronoTheme.colors
    return if (style == SeatStyle.COLORED) {
        SeatPalette(
            fill = seatFill(accent), text = Color.White, muted = Color.White.copy(alpha = 0.75f),
            outline = Color.White.copy(alpha = 0.35f), bar = Color.White, chipFill = Color.Black.copy(alpha = 0.12f),
            ring = Color.White, tagFill = Color.White, tagText = seatFill(accent),
        )
    } else {
        SeatPalette(
            fill = c.surface, text = c.text, muted = c.muted, outline = c.outline, bar = accent,
            chipFill = Color.Transparent, ring = accent, tagFill = accent, tagText = Color.White,
        )
    }
}

/** How long the running delta stays beside the name before folding into the log. */
private const val DELTA_VISIBLE_MS = 1_500L

/**
 * One player's viewport (UI kit · Seat viewport). Left half subtracts, right half adds; the
 * whole card turns 180° when [flipped]. [lastEntry] is the newest log entry if it belongs to
 * this seat's score — shown as a running delta beside the name until the pause ends.
 * The card is turned by [angle] degrees and shrunk as needed to stay inside its cell.
 */
@Composable
fun SeatViewport(
    player: Player,
    angle: Int,
    highlighted: Boolean,
    style: SeatStyle,
    lastEntry: ScoreHistoryEntry?,
    callbacks: SeatCallbacks,
    modifier: Modifier = Modifier,
    status: SeatStatus = SeatStatus(),
) {
    val p = seatPalette(player, style)

    BoxWithConstraints(modifier.rotatedToFit(angle)) {
        val compact = maxWidth < COMPACT_WIDTH
        val ring = highlighted || status.active || status.winner
        Column(
            Modifier
                .fillMaxSize()
                .alpha(if (status.out) OUT_ALPHA else 1f)
                .clip(SeatShape)
                .background(p.fill)
                .then(if (ring) Modifier.border(4.dp, p.ring, SeatShape) else Modifier),
        ) {
            SeatHeader(player, p, compact, highlighted, status, lastEntry, callbacks)

            // Primary score: left half decrements, right half increments.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val tint = p.text.copy(alpha = 0.06f)
                Row(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .repeatingPress(tint) { callbacks.onAdjust(CounterTarget.Primary, -it) },
                        contentAlignment = Alignment.CenterStart,
                    ) { EdgeIcon(ChronoIcons.Minus, p.muted, compact) }
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .repeatingPress(tint) { callbacks.onAdjust(CounterTarget.Primary, it) },
                        contentAlignment = Alignment.CenterEnd,
                    ) { EdgeIcon(ChronoIcons.Plus, p.muted, compact) }
                }
                AutoSizeText(
                    player.primaryScore.let { if (it < 0) "−${-it}" else it.toString() }, p.text,
                    Modifier.fillMaxSize().padding(horizontal = if (compact) 30.dp else 52.dp),
                )
            }

            CounterRow(player, p, compact, status, callbacks)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeatHeader(
    player: Player,
    p: SeatPalette,
    compact: Boolean,
    highlighted: Boolean,
    status: SeatStatus,
    lastEntry: ScoreHistoryEntry?,
    callbacks: SeatCallbacks,
) {
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
            RunningDelta(lastEntry, p)
            if (highlighted) SeatTag(if (compact) "1ST" else "FIRST", p)
            if (!compact) StatusTags(status, p, compact = false)
        }
        if (!compact) {
            HeaderIcon(ChronoIcons.Keypad, "Enter value", p.muted) { callbacks.onOpenKeypad(CounterTarget.Primary) }
            HeaderIcon(ChronoIcons.Rotate, "Rotate ${Limits.ROTATION_STEP}°", p.muted, callbacks.onRotate)
        }
        HeaderIcon(ChronoIcons.Edit, "Edit seat", p.muted, callbacks.onEdit)
    }
    // Narrow seats keep the name readable by moving status tags to their own line.
    if (compact && status.hasTags) {
        FlowRow(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            StatusTags(status, p, compact = true)
        }
    }
}

private val SeatStatus.hasTags: Boolean
    get() = winner || out || active || monarch || initiative || timeLabel != null

@Composable
internal fun StatusTags(status: SeatStatus, p: SeatPalette, compact: Boolean) {
    if (status.winner) SeatTag(if (compact) "WIN" else "WINNER", p)
    if (status.out) SeatTag("OUT", p, filled = false)
    if (status.active && !status.winner) SeatTag("TURN", p)
    if (status.monarch) SeatTag(if (compact) "MON" else "MONARCH", p, filled = false)
    if (status.initiative) SeatTag(if (compact) "INI" else "INITIATIVE", p, filled = false)
    status.timeLabel?.let { SeatTimer(it, status.overtime, p) }
}

@Composable
internal fun SeatTag(text: String, p: SeatPalette, filled: Boolean = true) {
    Spacer(Modifier.width(6.dp))
    Text(
        text,
        style = ChronoTheme.type.label.copy(fontSize = 10.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Bold),
        color = if (filled) p.tagText else p.text,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.clip(RoundedCornerShape(4.dp))
            .then(if (filled) Modifier.background(p.tagFill) else Modifier.border(1.dp, p.outline, RoundedCornerShape(4.dp)))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

/** Seat timer; turns into a rose chip once the turn limit is exceeded. */
@Composable
private fun SeatTimer(text: String, overtime: Boolean, p: SeatPalette) {
    Spacer(Modifier.width(6.dp))
    Text(
        text,
        style = ChronoTheme.type.counter.copy(fontSize = 12.sp),
        color = if (overtime) Color.White else p.muted,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.clip(RoundedCornerShape(4.dp))
            .then(if (overtime) Modifier.background(ChronoTheme.colors.destructive) else Modifier)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Composable
private fun RunningDelta(entry: ScoreHistoryEntry?, p: SeatPalette) {
    var visible by remember(entry?.id, entry?.timestamp) {
        mutableStateOf(entry != null && nowMillis() - entry.timestamp < DELTA_VISIBLE_MS)
    }
    LaunchedEffect(entry?.id, entry?.timestamp) {
        if (entry != null && visible) {
            delay((DELTA_VISIBLE_MS - (nowMillis() - entry.timestamp)).coerceAtLeast(0))
            visible = false
        }
    }
    if (visible && entry != null) {
        Spacer(Modifier.width(8.dp))
        Text(
            (if (entry.delta > 0) "+" else "−") + kotlin.math.abs(entry.delta),
            style = ChronoTheme.type.counter.copy(fontSize = 12.sp),
            color = p.text,
            modifier = Modifier.clip(RoundedCornerShape(4.dp))
                .background(if (p.chipFill == Color.Transparent) ChronoTheme.colors.raised else Color.Black.copy(alpha = 0.25f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CounterRow(player: Player, p: SeatPalette, compact: Boolean, status: SeatStatus, callbacks: SeatCallbacks) {
    val showAdd = !compact && player.subCounters.size < Limits.MAX_SUB_COUNTERS
    if (player.subCounters.isEmpty() && !showAdd && status.commanderDamage == null) return
    FlowRow(
        Modifier.fillMaxWidth().padding(if (compact) 6.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        status.commanderDamage?.let { CommanderChip(it, p, compact, callbacks.onOpenCommander) }
        player.subCounters.forEach { counter -> SubCounterChip(counter, p, compact, callbacks) }
        if (showAdd) AddCounterChip(p, callbacks.onEdit)
    }
}

/** Display-size figures that fill the box: a score, or a clock in timer mode. */
@Composable
internal fun AutoSizeText(text: String, color: Color, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // Inter Tight Bold figures run ~0.62em wide at display sizes; keep a little headroom.
        val byWidth: Dp = maxWidth / (text.length.coerceAtLeast(2) * 0.68f)
        val byHeight: Dp = maxHeight * 0.78f
        val size = with(LocalDensity.current) { min(byWidth, byHeight).coerceAtLeast(16.dp).toSp() }
        Text(
            text,
            style = ChronoTheme.type.score.copy(fontSize = size, lineHeight = size),
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun EdgeIcon(icon: ImageVector, tint: Color, compact: Boolean) {
    Icon(icon, null, Modifier.padding(horizontal = if (compact) 8.dp else 14.dp).size(if (compact) 20.dp else 24.dp), tint = tint)
}

@Composable
internal fun HeaderIcon(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(MinTouch).clip(ChronoShapes.control).clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(20.dp), tint = tint)
    }
}

@Composable
private fun SubCounterChip(counter: SubCounter, p: SeatPalette, compact: Boolean, callbacks: SeatCallbacks) {
    val side = if (compact) 30.dp else 36.dp
    val target = CounterTarget.Sub(counter.id)
    val tint = p.text.copy(alpha = 0.08f)
    Row(
        Modifier.height(MinTouch + 4.dp).clip(ChronoShapes.control).background(p.chipFill)
            .border(1.dp, p.outline, ChronoShapes.control),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(side).fillMaxHeight().repeatingPress(tint) { callbacks.onAdjust(target, -it) },
            contentAlignment = Alignment.Center,
        ) { Icon(ChronoIcons.Minus, "Decrease ${counter.name}", Modifier.size(16.dp), tint = p.muted) }
        Column(
            Modifier.clickable { callbacks.onOpenKeypad(target) }.padding(horizontal = 2.dp)
                .widthIn(min = 36.dp, max = if (compact) 60.dp else 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                counter.name.uppercase(), style = ChronoTheme.type.label.copy(fontSize = 9.sp, letterSpacing = 0.5.sp),
                color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(counter.value.toString(), style = ChronoTheme.type.counter.copy(fontSize = 17.sp, lineHeight = 18.sp), color = p.text)
        }
        Box(
            Modifier.width(side).fillMaxHeight().repeatingPress(tint) { callbacks.onAdjust(target, it) },
            contentAlignment = Alignment.Center,
        ) { Icon(ChronoIcons.Plus, "Increase ${counter.name}", Modifier.size(16.dp), tint = p.muted) }
    }
}

/** Commander damage summary; opens the per-opponent tracker. */
@Composable
private fun CommanderChip(damage: Int, p: SeatPalette, compact: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.height(MinTouch + 4.dp).clip(ChronoShapes.control).background(p.chipFill)
            .border(1.dp, p.outline, ChronoShapes.control)
            .clickable(onClickLabel = "Commander damage", onClick = onClick)
            .padding(horizontal = if (compact) 8.dp else 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("CMDR", style = ChronoTheme.type.label.copy(fontSize = 9.sp, letterSpacing = 0.5.sp), color = p.muted, maxLines = 1)
        Text("$damage", style = ChronoTheme.type.counter.copy(fontSize = 17.sp, lineHeight = 18.sp), color = p.text)
    }
}

@Composable
private fun AddCounterChip(p: SeatPalette, onClick: () -> Unit) {
    val density = LocalDensity.current
    Row(
        Modifier
            .heightIn(min = MinTouch + 4.dp)
            .clip(ChronoShapes.control)
            .drawBehind {
                val stroke = with(density) { 1.dp.toPx() }
                drawRoundRect(
                    color = p.outline,
                    cornerRadius = CornerRadius(with(density) { 6.dp.toPx() }),
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))),
                )
            }
            .clickable(onClickLabel = "Add counter", onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ChronoIcons.Plus, null, Modifier.size(14.dp), tint = p.muted)
        Spacer(Modifier.width(8.dp))
        Text("COUNTER", style = ChronoTheme.type.label, color = p.muted)
    }
}
