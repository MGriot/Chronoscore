package io.chronoscore.ui.dialogs

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.chronoscore.ui.theme.ChronoIcons
import io.chronoscore.ui.theme.ChronoShapes
import io.chronoscore.ui.theme.ChronoTheme
import io.chronoscore.ui.theme.MinTouch

/**
 * One frame for all dialogs: 10 px radius, 1 px outline, uppercase title, 44 px close.
 * [rotated] turns the whole dialog 180° to face a player sitting across the table.
 */
@Composable
fun DialogFrame(
    title: String,
    onDismiss: () -> Unit,
    rotated: Boolean = false,
    scrollable: Boolean = true,
    maxWidth: Dp = 440.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val c = ChronoTheme.colors
        Column(
            Modifier
                .padding(16.dp)
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .rotate(if (rotated) 180f else 0f)
                .clip(ChronoShapes.dialog)
                .background(c.background)
                .border(1.dp, c.outline, ChronoShapes.dialog),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title.uppercase(), style = ChronoTheme.type.label, color = c.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                IconTap(ChronoIcons.Close, "Close", tint = c.muted, onClick = onDismiss)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.outline))
            Column(
                Modifier
                    .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

/** 44 px square icon button. */
@Composable
fun IconTap(
    icon: ImageVector,
    description: String,
    tint: Color = ChronoTheme.colors.text,
    enabled: Boolean = true,
    iconSize: Dp = 22.dp,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(MinTouch).clip(ChronoShapes.control)
            .clickable(enabled = enabled, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(iconSize), tint = if (enabled) tint else tint.copy(alpha = 0.35f))
    }
}

enum class PillStyle { PRIMARY, SECONDARY, DESTRUCTIVE }

/**
 * Primary (white fill) · Secondary (surface + outline) · Destructive (rose, used for the
 * confirm state of destructive actions) · disabled. [selected] draws a text-colored ring.
 */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.SECONDARY,
    selected: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    accent: Color? = null,
    textAlign: TextAlign = TextAlign.Center,
    onClick: () -> Unit,
) {
    val c = ChronoTheme.colors
    val (bg, fg) = when {
        !enabled -> c.surface to c.muted.copy(alpha = 0.45f)
        style == PillStyle.PRIMARY -> c.primary to c.onPrimary
        style == PillStyle.DESTRUCTIVE -> c.destructive to Color.White
        else -> c.surface to c.text
    }
    val border = when {
        selected -> c.text
        style == PillStyle.SECONDARY || !enabled -> c.outline
        else -> bg
    }
    Row(
        modifier
            .heightIn(min = MinTouch)
            .clip(ChronoShapes.control)
            .background(bg)
            .border(if (selected) 1.5.dp else 1.dp, border, ChronoShapes.control)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (textAlign == TextAlign.Start) Arrangement.Start else Arrangement.Center,
    ) {
        if (accent != null) {
            Box(Modifier.size(width = 3.dp, height = 16.dp).background(accent))
            Spacer(Modifier.width(8.dp))
        }
        if (leadingIcon != null) {
            Icon(leadingIcon, null, Modifier.size(18.dp), tint = fg)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text, style = ChronoTheme.type.body, color = fg, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = textAlign,
            modifier = if (textAlign == TextAlign.Start) Modifier.weight(1f, fill = true) else Modifier,
        )
        if (trailingIcon != null) {
            Spacer(Modifier.width(10.dp))
            Icon(trailingIcon, null, Modifier.size(18.dp), tint = fg)
        }
    }
}

/** Segmented control: outlined track, the selected segment fills with the primary color. */
@Composable
fun Segmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    dotColors: List<Color>? = null,
) {
    val c = ChronoTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch + 4.dp)
            .clip(ChronoShapes.control)
            .background(c.surface)
            .border(1.dp, c.outline, ChronoShapes.control)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = MinTouch - 4.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (selected) c.primary else Color.Transparent)
                    .clickable { onSelect(i) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dotColors?.getOrNull(i)?.let { dot ->
                    Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    label, style = ChronoTheme.type.body.copy(fontSize = 14.sp),
                    color = if (selected) c.onPrimary else c.muted, maxLines = 1,
                )
            }
        }
    }
}

/** Outlined − value + stepper. */
@Composable
fun Stepper(
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    decrementEnabled: Boolean = true,
    incrementEnabled: Boolean = true,
) {
    val c = ChronoTheme.colors
    Row(
        Modifier.clip(ChronoShapes.control).background(c.surface).border(1.dp, c.outline, ChronoShapes.control),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTap(ChronoIcons.Minus, "Decrease", enabled = decrementEnabled, iconSize = 18.dp, onClick = onDecrement)
        Text(
            value, style = ChronoTheme.type.counter, color = c.text,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 32.dp),
        )
        IconTap(ChronoIcons.Plus, "Increase", enabled = incrementEnabled, iconSize = 18.dp, onClick = onIncrement)
    }
}

/** Label above, surface field, optional "n / max" counter below. */
@Composable
fun ChronoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    maxLength: Int? = null,
    showCounter: Boolean = maxLength != null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val c = ChronoTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) Text(label, style = ChronoTheme.type.body.copy(fontSize = 13.sp), color = c.muted)
        BasicTextField(
            value = value,
            onValueChange = { v -> onValueChange(if (maxLength != null) v.take(maxLength) else v) },
            singleLine = true,
            textStyle = ChronoTheme.type.body.copy(color = c.text, fontSize = 16.sp),
            cursorBrush = SolidColor(c.text),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = MinTouch + 2.dp)
                        .clip(ChronoShapes.control)
                        .background(c.surface)
                        .border(1.dp, c.outline, ChronoShapes.control)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) Text(placeholder, style = ChronoTheme.type.body.copy(fontSize = 16.sp), color = c.muted.copy(alpha = 0.7f))
                    inner()
                }
            },
        )
        if (showCounter && maxLength != null) {
            Text(
                "${value.length} / $maxLength", style = ChronoTheme.type.body.copy(fontSize = 12.sp),
                color = c.muted, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

enum class SwitchState { OFF, PARTIAL, ON }

/** On / off switch; PARTIAL shows a dash (counter is on some seats only). */
@Composable
fun ChronoSwitch(state: SwitchState, onToggle: () -> Unit, description: String) {
    val c = ChronoTheme.colors
    val knobX by animateDpAsState(
        when (state) {
            SwitchState.OFF -> 4.dp
            SwitchState.PARTIAL -> 15.dp
            SwitchState.ON -> 26.dp
        },
    )
    Box(
        Modifier.size(MinTouch + 12.dp, MinTouch).clip(ChronoShapes.control).clickable(onClickLabel = description, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 52.dp, height = 28.dp)
                .clip(CircleShape)
                .background(if (state == SwitchState.ON) c.primary else c.raised)
                .border(1.dp, c.outline, CircleShape),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier.offset(x = knobX).size(22.dp).clip(CircleShape)
                    .background(if (state == SwitchState.ON) c.onPrimary else c.muted),
                contentAlignment = Alignment.Center,
            ) {
                if (state == SwitchState.PARTIAL) Box(Modifier.size(width = 10.dp, height = 2.dp).background(c.raised))
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = ChronoTheme.type.label, color = ChronoTheme.colors.muted, modifier = modifier)
}

@Composable
fun Note(text: String) {
    Text(text, style = ChronoTheme.type.body.copy(fontSize = 13.sp), color = ChronoTheme.colors.muted)
}

/** Full-width divider row used between list items. */
@Composable
fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(ChronoTheme.colors.outline))
}
