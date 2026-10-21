package io.chronoscore.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.chronoscore.engine.BulkMode
import io.chronoscore.engine.GameAction
import io.chronoscore.model.GameSession
import io.chronoscore.model.Limits
import io.chronoscore.model.Player
import io.chronoscore.ui.theme.ChronoShapes
import io.chronoscore.ui.theme.ChronoTheme
import io.chronoscore.ui.theme.MinTouch
import io.chronoscore.ui.theme.parseHex

private val Modes = listOf("Set to", "+ Add", "− Sub")

/**
 * Edit all seats (UI kit · Menu & all seats): one score or counter for many seats in one step,
 * with a per-seat preview. Logged as one entry per seat; a single Undo reverts the whole change.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditAllSeatsDialog(
    session: GameSession,
    dispatch: (GameAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = ChronoTheme.colors
    var selected by remember { mutableStateOf(session.players.map { it.id }.toSet()) }
    var counter by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableIntStateOf(0) }
    var setValue by remember { mutableIntStateOf(session.startingScore) }
    var amount by remember { mutableIntStateOf(1) }

    val counterNames = session.players.flatMap { p -> p.subCounters.map { it.name } }
        .distinctBy { it.lowercase() }
    val value = if (mode == 0) setValue else amount

    fun Player.currentValue(): Int? =
        if (counter == null) primaryScore
        else subCounters.firstOrNull { it.name.equals(counter, ignoreCase = true) }?.value

    fun next(current: Int) = when (mode) {
        0 -> setValue
        1 -> current + amount
        else -> current - amount
    }.coerceIn(Limits.MIN_VALUE, Limits.MAX_VALUE)

    val targets = session.players.filter { it.id in selected }
    val affected = targets.count { p -> p.currentValue()?.let { next(it) != it } == true }

    DialogFrame("Edit all seats", onDismiss, maxWidth = 440.dp) {
        SectionLabel("Seats")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val all = selected.size == session.players.size
            Pill("All", selected = all) { selected = if (all) emptySet() else session.players.map { it.id }.toSet() }
            session.players.forEach { p ->
                val on = p.id in selected
                Pill(p.name, selected = on, accent = parseHex(p.colorHex)) {
                    selected = if (on) selected - p.id else selected + p.id
                }
            }
        }

        SectionLabel("What")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Score", selected = counter == null) { counter = null; setValue = session.startingScore }
            counterNames.forEach { name ->
                Pill(name, selected = counter.equals(name, ignoreCase = true)) { counter = name; setValue = 0 }
            }
        }

        Segmented(Modes, mode, onSelect = { mode = it })

        // −5 −1 [value] +1 +5
        Row(
            Modifier.fillMaxWidth().clip(ChronoShapes.control).background(c.surface).border(1.dp, c.outline, ChronoShapes.control),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            fun bump(d: Int) {
                if (mode == 0) setValue = (setValue + d).coerceIn(Limits.MIN_VALUE, Limits.MAX_VALUE)
                else amount = (amount + d).coerceIn(0, Limits.MAX_VALUE)
            }
            StepKey("−5", Modifier.weight(1f)) { bump(-5) }
            StepKey("−1", Modifier.weight(1f)) { bump(-1) }
            Text(
                if (value < 0) "−${-value}" else "$value",
                style = ChronoTheme.type.score.copy(fontSize = 34.sp, lineHeight = 36.sp),
                color = c.text, textAlign = TextAlign.Center, modifier = Modifier.weight(2f),
            )
            StepKey("+1", Modifier.weight(1f)) { bump(1) }
            StepKey("+5", Modifier.weight(1f)) { bump(5) }
        }

        Column {
            targets.forEachIndexed { i, p ->
                if (i > 0) Hairline()
                val current = p.currentValue()
                Row(Modifier.fillMaxWidth().heightIn(min = 42.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 3.dp, height = 18.dp).background(parseHex(p.colorHex)))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        p.name, style = ChronoTheme.type.body.copy(fontWeight = FontWeight.Medium), color = c.text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    if (current == null) {
                        Text("no $counter", style = ChronoTheme.type.body.copy(fontSize = 13.sp), color = c.muted)
                    } else {
                        Text("$current  →", style = ChronoTheme.type.body.copy(fontSize = 14.sp), color = c.muted)
                        Text(
                            "${next(current)}", style = ChronoTheme.type.counter.copy(fontSize = 17.sp), color = c.text,
                            textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 52.dp),
                        )
                    }
                }
            }
        }

        Pill(
            if (affected == 1) "Apply to 1 seat" else "Apply to $affected seats",
            Modifier.fillMaxWidth(), style = PillStyle.PRIMARY, enabled = affected > 0,
        ) {
            dispatch(
                GameAction.BulkEdit(
                    playerIds = targets.map { it.id },
                    counterName = counter,
                    mode = if (mode == 0) BulkMode.SET else BulkMode.ADD,
                    value = when (mode) {
                        0 -> setValue
                        1 -> amount
                        else -> -amount
                    },
                ),
            )
            onDismiss()
        }
        Note("Logged as one entry per seat, and a single Undo reverts the whole change.")
    }
}

@Composable
private fun StepKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = MinTouch + 8.dp).clip(ChronoShapes.control).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = ChronoTheme.type.body, color = ChronoTheme.colors.muted)
    }
}
