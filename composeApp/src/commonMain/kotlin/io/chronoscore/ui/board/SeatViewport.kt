package io.chronoscore.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
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
import io.chronoscore.ui.formatScore
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
/** Seats this wide get bigger − / + glyphs and a tinted minus half. */
private val WIDE_WIDTH = 400.dp
/** Narrowest full counter chip: two 30 dp steppers and the label between them. */
private val MIN_CHIP_WIDTH = 108.dp
/** Share of the seat height the score always keeps. */
private const val SCORE_SHARE = 0.55f
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
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()

    val counterCount = player.subCounters.size + if (status.commanderDamage != null) 1 else 0
    val hasCounters = counterCount > 0
    // A slanted card narrower than this cuts off the name or the mini chips.
    val minTiltedWidth = maxOf(TILTED_NAME_WIDTH, MINI_CHIP_WIDTH * counterCount + 12.dp)
    val tilted = isTilted(angle)

    RotatedSeat(
        angle,
        modifier.hoverable(hover),
        fill = p.fill,
        alpha = if (status.out) OUT_ALPHA else 1f,
        ring = if (highlighted || status.active || status.winner) p.ring else null,
        // A slanted card gets the shape that leaves the largest score under its name line.
        quality = { w, h ->
            val score = min((w - TILTED_SCORE_PAD * 2) / (2 * 0.68f), (h - NAME_LINE - if (hasCounters) 36.dp else 0.dp) * 0.78f).value
            if (w < minTiltedWidth) score * (w / minTiltedWidth) else score
        },
        backdrop = {
            // Primary score: the half to the player's left decrements, the right half increments.
            // This layer covers the whole cell, so a slanted seat is tappable to its corners.
            if (status.out) {
                // Out seats only open the editor.
                Box(Modifier.fillMaxSize().pointerInput(callbacks) { detectTapGestures(onLongPress = { callbacks.onEdit() }) })
            } else {
                val tint = p.text.copy(alpha = 0.06f)
                val wide = !tilted && maxWidth >= WIDE_WIDTH
                Row(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            // Wide seats tint the minus half faintly, so the split reads at a glance.
                            .then(if (wide) Modifier.background(p.text.copy(alpha = 0.03f)) else Modifier)
                            .repeatingPress(tint) { callbacks.onAdjust(CounterTarget.Primary, -it) },
                    )
                    Box(Modifier.weight(1f).fillMaxHeight().repeatingPress(tint) { callbacks.onAdjust(CounterTarget.Primary, it) })
                }
            }
        },
        corners = { (first, second) ->
            CornerIcon(first) { HeaderIcon(ChronoIcons.Edit, "Edit seat", p.muted, callbacks.onEdit) }
            CornerIcon(second) { HeaderIcon(ChronoIcons.Rotate, "Rotate ${Limits.ROTATION_STEP}°", p.muted, callbacks.onRotate) }
        },
    ) { corners ->
        val compact = maxWidth < COMPACT_WIDTH
        val wide = !tilted && maxWidth >= WIDE_WIDTH
        // The score keeps at least SCORE_SHARE of the seat; counters that don't fit collapse to mini chips.
        val headerHeight = MinTouch + if (compact && status.hasTags(highlighted)) 24.dp else 0.dp
        val counterBudget = maxHeight * (1f - SCORE_SHARE) - headerHeight
        // Full chips need room for − value +; narrower grid cells get the mini chips instead.
        val cellTooNarrow = compact && (maxWidth - 18.dp) / 2 < MIN_CHIP_WIDTH
        val mini = tilted || cellTooNarrow || counterRowHeight(player, status, compact, maxWidth) > counterBudget
        Column(Modifier.fillMaxSize()) {
            SeatHeader(player, p, compact, highlighted, status, lastEntry, callbacks, buttons = corners == null)

            // The score and its − / + marks only draw: taps fall through to the halves underneath.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (!status.out) {
                    EdgeIcon(ChronoIcons.Minus, p.muted, compact || tilted, wide, Modifier.align(Alignment.CenterStart), tilted)
                    EdgeIcon(ChronoIcons.Plus, p.muted, compact || tilted, wide, Modifier.align(Alignment.CenterEnd), tilted)
                }
                AutoSizeText(
                    formatScore(player.primaryScore), p.text,
                    Modifier.fillMaxSize().padding(
                        horizontal = when {
                            tilted -> TILTED_SCORE_PAD
                            compact -> 30.dp
                            wide -> 64.dp
                            else -> 52.dp
                        },
                    ),
                    // A slanted card is smaller than its neighbours, so it doesn't hold their scores down.
                    tableKey = if (tilted) null else player.id,
                )
            }

            if (mini) MiniCounterStrip(player, p, status, callbacks)
            else CounterRow(player, p, compact, status, callbacks, showAdd = hovered)
        }
    }
}

/**
 * A seat card turned by [angle] in its cell. The cell itself carries the card's [fill], [ring]
 * and [alpha], so a slanted seat still fills its cell instead of floating in it.
 *
 * - [backdrop] is turned with the card but sized to cover the whole cell (the tap halves).
 * - [content] is the turned card itself, the largest that fits: at right angles the cell's own
 *   shape, slanted the shape [quality] (in dp) rates best.
 * - A slanted card leaves two opposite corners of the cell free; when they have room for a
 *   button, [content] gets them and [corners] draws there, upright.
 */
@Composable
internal fun RotatedSeat(
    angle: Int,
    modifier: Modifier,
    fill: Color,
    alpha: Float,
    ring: Color?,
    quality: (Dp, Dp) -> Float,
    backdrop: @Composable BoxWithConstraintsScope.() -> Unit = {},
    corners: @Composable BoxScope.(Pair<CellCorner, CellCorner>) -> Unit = {},
    content: @Composable BoxWithConstraintsScope.(Pair<CellCorner, CellCorner>?) -> Unit,
) {
    BoxWithConstraints(modifier.alpha(alpha).clip(SeatShape).background(fill)) {
        if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight) {
            BoxWithConstraints(Modifier.matchParentSize(), content = backdrop)
            BoxWithConstraints { content(null) }
            return@BoxWithConstraints
        }
        val w = maxWidth.value
        val h = maxHeight.value
        val tilted = isTilted(angle)
        val (bw, bh) = if (tilted) bestTiltedSize(w, h, angle) { x, y -> quality(x.dp, y.dp) } else fittedSize(w, h, angle)
        val (cw, ch) = coverSize(w, h, angle)
        val free = if (tilted) freeCorners(w, h, angle, bw, bh, (MinTouch + CORNER_INSET * 2).value) else null
        // requiredSize centres a child that is larger than the cell; the cell's clip trims it.
        BoxWithConstraints(Modifier.align(Alignment.Center).requiredSize(cw.dp, ch.dp).rotate(angle.toFloat()), content = backdrop)
        BoxWithConstraints(Modifier.align(Alignment.Center).requiredSize(bw.dp, bh.dp).rotate(angle.toFloat())) { content(free) }
        if (free != null) corners(free)
        if (ring != null) Box(Modifier.matchParentSize().border(4.dp, ring, SeatShape))
    }
}

private val CORNER_INSET = 2.dp
/** Header height of a slanted seat whose buttons moved to the corners. */
internal val NAME_LINE = 30.dp
/** Side padding of a slanted seat's score, room for its − / + marks. */
private val TILTED_SCORE_PAD = 24.dp
/** Width a slanted seat's name line needs to show a short name in full. */
private val TILTED_NAME_WIDTH = 90.dp
/** Width of one "PSN 0" mini chip and its gap. */
private val MINI_CHIP_WIDTH = 66.dp

/** Places a header button upright in [corner] of a slanted seat's cell. */
@Composable
internal fun BoxScope.CornerIcon(corner: CellCorner, content: @Composable () -> Unit) {
    val alignment = when (corner) {
        CellCorner.TOP_LEFT -> AbsoluteAlignment.TopLeft
        CellCorner.TOP_RIGHT -> AbsoluteAlignment.TopRight
        CellCorner.BOTTOM_RIGHT -> AbsoluteAlignment.BottomRight
        CellCorner.BOTTOM_LEFT -> AbsoluteAlignment.BottomLeft
    }
    Box(Modifier.align(alignment).padding(CORNER_INSET)) { content() }
}

/** Height the full counter chips need, to decide whether they fit under the score. */
private fun counterRowHeight(player: Player, status: SeatStatus, compact: Boolean, width: Dp): Dp {
    val items = player.subCounters.size + if (status.commanderDamage != null) 1 else 0
    if (items == 0) return 0.dp
    val perRow = if (compact) 2 else ((width - 16.dp) / 150.dp).toInt().coerceAtLeast(1)
    val rows = (items + perRow - 1) / perRow
    return (MinTouch + 4.dp) * rows + 6.dp * (rows - 1) + if (compact) 12.dp else 16.dp
}

/** Color bar, name, status tags and header buttons; shared by score seats and clock tiles. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SeatHeader(
    player: Player,
    p: SeatPalette,
    compact: Boolean,
    highlighted: Boolean,
    status: SeatStatus,
    lastEntry: ScoreHistoryEntry?,
    callbacks: SeatCallbacks,
    showKeypad: Boolean = true,
    /** False when a slanted seat shows its buttons in the cell's corners instead. */
    buttons: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = if (buttons) 2.dp else 12.dp, top = 2.dp)
            // Without buttons the line only holds the name, so it gives its height to the score.
            .then(if (buttons) Modifier else Modifier.heightIn(min = NAME_LINE)),
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
            if (player.isTeam && !compact) {
                Spacer(Modifier.width(6.dp))
                Text(
                    player.members.joinToString(" · ") { it.name },
                    style = ChronoTheme.type.label.copy(fontSize = 11.sp, letterSpacing = 0.sp),
                    color = p.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).clickable(onClick = callbacks.onEdit),
                )
            }
            RunningDelta(lastEntry, p)
            if (!compact) {
                if (highlighted) SeatTag("FIRST", p)
                StatusTags(status, p, compact = false)
            }
        }
        if (buttons) {
            if (!compact) {
                if (showKeypad) HeaderIcon(ChronoIcons.Keypad, "Enter value", p.muted) { callbacks.onOpenKeypad(CounterTarget.Primary) }
                HeaderIcon(ChronoIcons.Rotate, "Rotate ${Limits.ROTATION_STEP}°", p.muted, callbacks.onRotate)
            }
            HeaderIcon(ChronoIcons.Edit, "Edit seat", p.muted, callbacks.onEdit)
        }
    }
    // Narrow seats keep the whole header for the name: badges and the timer get their own
    // line, on one row that never wraps, with the timer at its end.
    if (compact && status.hasTags(highlighted)) {
        Row(
            Modifier.fillMaxWidth().height(24.dp).padding(start = 18.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                if (highlighted) SeatTag("1ST", p, leading = false)
                StatusTags(status.copy(timeLabel = null), p, compact = true, leading = !highlighted)
            }
            status.timeLabel?.let { SeatTimer(it, status.overtime, p) }
        }
    }
}

private fun SeatStatus.hasTags(highlighted: Boolean): Boolean =
    highlighted || winner || out || active || monarch || initiative || timeLabel != null

/** Status badges; [leading] puts a gap before the first one (it follows the name). */
@Composable
internal fun StatusTags(status: SeatStatus, p: SeatPalette, compact: Boolean, leading: Boolean = true) {
    var gap = leading
    @Composable
    fun tag(text: String, filled: Boolean = true) {
        SeatTag(text, p, filled, leading = gap)
        gap = true
    }
    if (status.winner) tag(if (compact) "WIN" else "WINNER")
    if (status.out) tag("OUT", filled = false)
    if (status.active && !status.winner) tag("TURN")
    // An out seat's Monarch or Initiative no longer matters, and only crowds the line.
    if (status.monarch && !status.out) tag(if (compact) "MON" else "MONARCH", filled = false)
    if (status.initiative && !status.out) tag(if (compact) "INI" else "INITIATIVE", filled = false)
    status.timeLabel?.let { SeatTimer(it, status.overtime, p) }
}

@Composable
internal fun SeatTag(text: String, p: SeatPalette, filled: Boolean = true, leading: Boolean = true) {
    if (leading) Spacer(Modifier.width(6.dp))
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

/**
 * Counter chips under the score, centred. Compact seats use an equal-cell two-column grid.
 * "+ Counter" only shows while the seat is hovered (desktop); the seat editor adds counters too.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CounterRow(
    player: Player,
    p: SeatPalette,
    compact: Boolean,
    status: SeatStatus,
    callbacks: SeatCallbacks,
    showAdd: Boolean,
) {
    val canAdd = !compact && player.subCounters.size < Limits.MAX_SUB_COUNTERS
    if (player.subCounters.isEmpty() && !canAdd && status.commanderDamage == null) return
    if (player.subCounters.isEmpty() && status.commanderDamage == null && !showAdd) return
    if (compact) {
        val chips: List<@Composable (Modifier) -> Unit> = buildList {
            status.commanderDamage?.let { d -> add { m: Modifier -> CommanderChip(d, p, compact = true, callbacks.onOpenCommander, m) } }
            player.subCounters.forEach { counter -> add { m: Modifier -> SubCounterChip(counter, p, compact = true, callbacks, m) } }
        }
        Column(Modifier.fillMaxWidth().padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            chips.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { chip -> chip(Modifier.weight(1f)) }
                    if (row.size == 1 && chips.size > 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        return
    }
    FlowRow(
        Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        status.commanderDamage?.let { CommanderChip(it, p, compact = false, callbacks.onOpenCommander) }
        player.subCounters.forEach { counter -> SubCounterChip(counter, p, compact = false, callbacks) }
        if (canAdd && showAdd) AddCounterChip(p, callbacks.onEdit)
    }
}

/** One row of small "PSN 0" chips, for seats too short for full counters; a tap opens the keypad. */
@Composable
private fun MiniCounterStrip(player: Player, p: SeatPalette, status: SeatStatus, callbacks: SeatCallbacks) {
    if (player.subCounters.isEmpty() && status.commanderDamage == null) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        status.commanderDamage?.let { MiniChip("CMDR", it, p, callbacks.onOpenCommander) }
        player.subCounters.forEach { counter ->
            MiniChip(counter.shortLabel(), counter.value, p) { callbacks.onOpenKeypad(CounterTarget.Sub(counter.id)) }
        }
    }
}

@Composable
private fun MiniChip(label: String, value: Int, p: SeatPalette, onClick: () -> Unit) {
    Row(
        Modifier.height(28.dp).clip(RoundedCornerShape(5.dp)).background(p.chipFill)
            .border(1.dp, p.outline, RoundedCornerShape(5.dp))
            .clickable(onClickLabel = label, onClick = onClick).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = ChronoTheme.type.label.copy(fontSize = 9.sp, letterSpacing = 0.5.sp), color = p.muted, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        Text("$value", style = ChronoTheme.type.counter.copy(fontSize = 13.sp), color = p.text, maxLines = 1)
    }
}

/** Short counter label for narrow chips: CMDR, PSN, or the first four letters. */
internal fun SubCounter.shortLabel(): String = when (name.trim().lowercase()) {
    "commander", "commander damage" -> "CMDR"
    "poison" -> "PSN"
    "energy" -> "NRG"
    "experience" -> "EXP"
    "treasure" -> "TRSR"
    else -> name.trim().uppercase().take(4)
}

/**
 * Score sizes measured across the table: each seat reports the largest two-digit size its box
 * holds, and every seat uses the smallest, so equal scores look equal on every seat.
 */
internal class TableScoreSize {
    private val capacities = mutableStateMapOf<String, Dp>()
    val cap: Dp? get() = capacities.values.minOrNull()
    fun report(key: String, capacity: Dp) {
        if (capacities[key] != capacity) capacities[key] = capacity
    }
    fun remove(key: String) {
        capacities.remove(key)
    }
}

internal val LocalTableScoreSize = staticCompositionLocalOf<TableScoreSize?> { null }

/** Display-size figures that fill the box: a score, or a clock in timer mode. */
@Composable
internal fun AutoSizeText(text: String, color: Color, modifier: Modifier, tableKey: String? = null) {
    val table = if (tableKey != null) LocalTableScoreSize.current else null
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // Inter Tight Bold figures run ~0.62em wide at display sizes; keep a little headroom.
        val byWidth: Dp = maxWidth / (text.length.coerceAtLeast(2) * 0.68f)
        val byHeight: Dp = maxHeight * 0.78f
        val capacity = min(maxWidth / (2 * 0.68f), byHeight)
        if (table != null && tableKey != null) {
            SideEffect { table.report(tableKey, capacity) }
            DisposableEffect(tableKey) { onDispose { table.remove(tableKey) } }
        }
        val fitted = min(byWidth, byHeight)
        val size = with(LocalDensity.current) { min(fitted, table?.cap ?: fitted).coerceAtLeast(16.dp).toSp() }
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
private fun EdgeIcon(icon: ImageVector, tint: Color, compact: Boolean, wide: Boolean, modifier: Modifier, tight: Boolean = false) {
    val size = when {
        compact -> 20.dp
        wide -> 32.dp
        else -> 24.dp
    }
    Icon(icon, null, modifier.padding(horizontal = if (tight) 2.dp else if (compact) 8.dp else 16.dp).size(size), tint = tint)
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
private fun SubCounterChip(counter: SubCounter, p: SeatPalette, compact: Boolean, callbacks: SeatCallbacks, modifier: Modifier = Modifier) {
    val side = if (compact) 30.dp else 36.dp
    val target = CounterTarget.Sub(counter.id)
    val tint = p.text.copy(alpha = 0.08f)
    Row(
        modifier.height(MinTouch + 4.dp).clip(ChronoShapes.control).background(p.chipFill)
            .border(1.dp, p.outline, ChronoShapes.control),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(side).fillMaxHeight().repeatingPress(tint) { callbacks.onAdjust(target, -it) },
            contentAlignment = Alignment.Center,
        ) { Icon(ChronoIcons.Minus, "Decrease ${counter.name}", Modifier.size(16.dp), tint = p.muted) }
        Column(
            Modifier.then(if (compact) Modifier.weight(1f) else Modifier.widthIn(min = 36.dp, max = 96.dp))
                .clickable { callbacks.onOpenKeypad(target) }.padding(horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (compact) counter.shortLabel() else counter.name.uppercase(),
                style = ChronoTheme.type.label.copy(fontSize = 9.sp, letterSpacing = 0.5.sp),
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

/** Commander damage summary, same height as a counter chip; the chevron says it opens the tracker. */
@Composable
private fun CommanderChip(damage: Int, p: SeatPalette, compact: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.height(MinTouch + 4.dp).clip(ChronoShapes.control).background(p.chipFill)
            .border(1.dp, p.outline, ChronoShapes.control)
            .clickable(onClickLabel = "Commander damage", onClick = onClick)
            .padding(start = if (compact) 8.dp else 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.then(if (compact) Modifier.weight(1f) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("CMDR", style = ChronoTheme.type.label.copy(fontSize = 9.sp, letterSpacing = 0.5.sp), color = p.muted, maxLines = 1)
            Text("$damage", style = ChronoTheme.type.counter.copy(fontSize = 17.sp, lineHeight = 18.sp), color = p.text)
        }
        Icon(ChronoIcons.ChevronRight, null, Modifier.size(16.dp), tint = p.muted)
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
