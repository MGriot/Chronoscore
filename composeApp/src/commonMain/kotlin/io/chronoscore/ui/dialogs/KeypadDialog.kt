package io.chronoscore.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.chronoscore.engine.BulkMode
import io.chronoscore.engine.Entry
import io.chronoscore.ui.formatScore
import io.chronoscore.ui.theme.ChronoIcons
import io.chronoscore.ui.theme.ChronoShapes
import io.chronoscore.ui.theme.ChronoTheme
import io.chronoscore.ui.theme.parseHex

private const val MAX_LENGTH = 40

/**
 * Direct entry (UI kit · Dialogs). Sets or adds/subtracts on this seat, or — with the
 * "All seats" scope — on every seat at once (logged as one undoable batch).
 */
@Composable
fun KeypadDialog(
    playerName: String,
    playerColor: String,
    targetLabel: String,
    current: Int,
    rotated: Boolean,
    seatCount: Int,
    onDismiss: () -> Unit,
    onApply: (allSeats: Boolean, mode: BulkMode, value: Int) -> Unit,
) {
    var allSeats by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val c = ChronoTheme.colors

    val entry = Entry.parse(text)
    val preview = entry?.next(current)

    DialogFrame("$playerName · $targetLabel", onDismiss, rotated = rotated, scrollable = true, maxWidth = 400.dp) {
        if (seatCount > 1) {
            Segmented(
                options = listOf("Only $playerName", "All seats"),
                selectedIndex = if (allSeats) 1 else 0,
                onSelect = { allSeats = it == 1 },
                // Only the seat gets its colour dot; "All seats" needs none.
                dotColors = listOf(parseHex(playerColor)),
            )
        }

        EquationLine(text)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(ChronoShapes.control).background(c.surface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (allSeats && entry?.relative != true) "All seats" else formatScore(current),
                style = ChronoTheme.type.counter.copy(fontSize = 22.sp), color = c.muted,
            )
            Icon(ChronoIcons.ArrowRight, null, Modifier.padding(horizontal = 10.dp).size(18.dp), tint = c.muted)
            if (preview == null) {
                Text(
                    "no result yet", style = ChronoTheme.type.body, color = c.muted.copy(alpha = 0.7f),
                    textAlign = TextAlign.End, modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    formatScore(preview),
                    style = ChronoTheme.type.score.copy(fontSize = 48.sp, lineHeight = 52.sp),
                    color = c.text, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.weight(1f),
                )
            }
        }

        EquationKeys(text, entry) { text = it }

        Pill("Apply", Modifier.fillMaxWidth(), style = PillStyle.PRIMARY, enabled = entry != null) {
            val e = entry ?: return@Pill
            onApply(allSeats, if (e.relative) BulkMode.ADD else BulkMode.SET, e.value)
            onDismiss()
        }
    }
}

/** The equation as typed, e.g. "12+5×3"; a hint while empty. */
@Composable
internal fun EquationLine(text: String) {
    val c = ChronoTheme.colors
    Text(
        text.ifEmpty { "12+5×3 · or +5 / −3 to change" },
        style = ChronoTheme.type.counter.copy(fontSize = 20.sp),
        color = if (text.isEmpty()) c.muted.copy(alpha = 0.7f) else c.text,
        textAlign = TextAlign.End, maxLines = 1,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    )
}

/**
 * Calculator pad. A plain equation sets the value; starting with + or − changes the current
 * value instead. "=" folds the equation into its result.
 */
@Composable
internal fun EquationKeys(text: String, entry: Entry?, onText: (String) -> Unit) {
    val rows = listOf(
        listOf("C" to 1, "(" to 1, ")" to 1, "⌫" to 1),
        listOf("7" to 1, "8" to 1, "9" to 1, "÷" to 1),
        listOf("4" to 1, "5" to 1, "6" to 1, "×" to 1),
        listOf("1" to 1, "2" to 1, "3" to 1, "−" to 1),
        listOf("0" to 2, "=" to 1, "+" to 1),
    )
    rows.forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (key, weight) ->
                Key(key, enabled = key != "=" || entry != null, Modifier.weight(weight.toFloat())) {
                    when (key) {
                        "C" -> onText("")
                        "⌫" -> onText(text.dropLast(1))
                        "=" -> entry?.let { onText(it.folded()) }
                        else -> if (text.length < MAX_LENGTH) onText(text + key)
                    }
                }
            }
        }
    }
}

@Composable
private fun Key(key: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = ChronoTheme.colors
    Box(
        modifier
            .height(56.dp)
            .clip(ChronoShapes.control)
            .background(c.surface)
            .border(1.dp, c.outline, ChronoShapes.control)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val color = if (enabled) c.text else c.muted.copy(alpha = 0.4f)
        if (key == "⌫") {
            Icon(ChronoIcons.Backspace, "Backspace", Modifier.size(22.dp), tint = color)
        } else {
            Text(key, style = ChronoTheme.type.counter.copy(fontSize = 22.sp), color = color)
        }
    }
}
