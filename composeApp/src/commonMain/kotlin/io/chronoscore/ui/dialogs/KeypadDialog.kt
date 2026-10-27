package io.chronoscore.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import io.chronoscore.ui.formatScore
import io.chronoscore.ui.theme.ChronoIcons
import io.chronoscore.ui.theme.ChronoShapes
import io.chronoscore.ui.theme.ChronoTheme
import io.chronoscore.ui.theme.parseHex

private const val MAX_DIGITS = 5
private val Modes = listOf("Set", "+ Add", "− Sub")

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
    var mode by remember { mutableIntStateOf(0) }
    var digits by remember { mutableStateOf("") }
    var negative by remember { mutableStateOf(false) }
    val c = ChronoTheme.colors

    val entered = digits.toIntOrNull()?.let { if (negative && mode == 0) -it else it }
    val preview = when {
        entered == null -> null
        mode == 0 -> entered
        mode == 1 -> current + entered
        else -> current - entered
    }

    DialogFrame("$playerName · $targetLabel", onDismiss, rotated = rotated, scrollable = true, maxWidth = 400.dp) {
        if (seatCount > 1) {
            Segmented(
                options = listOf("Only $playerName", "All seats"),
                selectedIndex = if (allSeats) 1 else 0,
                onSelect = { allSeats = it == 1 },
                dotColors = listOf(parseHex(playerColor), c.text),
            )
        }

        Row(
            Modifier.fillMaxWidth().clip(ChronoShapes.control).background(c.surface).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                if (allSeats && mode == 0) "all →" else "$current →",
                style = ChronoTheme.type.body, color = c.muted, modifier = Modifier.padding(bottom = 6.dp),
            )
            Text(
                formatScore(preview ?: current),
                style = ChronoTheme.type.score.copy(fontSize = 48.sp, lineHeight = 52.sp),
                color = if (preview == null) c.muted else c.text,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }

        Segmented(Modes, mode, onSelect = { mode = it; if (it != 0) negative = false })

        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("±", "0", "⌫"))
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    val enabled = key != "±" || mode == 0
                    Key(key, enabled, Modifier.weight(1f)) {
                        when (key) {
                            "⌫" -> digits = digits.dropLast(1)
                            "±" -> negative = !negative
                            else -> if (digits.length < MAX_DIGITS) digits = (digits + key).trimStart('0').ifEmpty { "0" }
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Clear", Modifier.weight(1f)) { digits = ""; negative = false }
            Pill("Apply", Modifier.weight(1f), style = PillStyle.PRIMARY, enabled = entered != null) {
                val n = entered ?: return@Pill
                when (mode) {
                    0 -> onApply(allSeats, BulkMode.SET, n)
                    1 -> onApply(allSeats, BulkMode.ADD, n)
                    else -> onApply(allSeats, BulkMode.ADD, -n)
                }
                onDismiss()
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
            Icon(ChronoIcons.Close, "Backspace", Modifier.size(20.dp), tint = color)
        } else {
            Text(key, style = ChronoTheme.type.counter.copy(fontSize = 22.sp), color = color)
        }
    }
}
