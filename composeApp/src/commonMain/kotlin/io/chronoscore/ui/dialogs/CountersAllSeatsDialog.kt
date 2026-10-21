package io.chronoscore.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.chronoscore.engine.GameAction
import io.chronoscore.model.GameSession
import io.chronoscore.model.Limits
import io.chronoscore.model.SubCounterPresets
import io.chronoscore.ui.theme.ChronoTheme

/**
 * Counters on every seat (UI kit · Menu & all seats). Turning a counter on adds it to every
 * seat at 0; seats already holding four counters are skipped and named.
 */
@Composable
fun CountersAllSeatsDialog(
    session: GameSession,
    dispatch: (GameAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = ChronoTheme.colors
    var custom by remember { mutableStateOf("") }
    var skippedNote by remember { mutableStateOf<String?>(null) }
    val seats = session.players.size

    val names = (SubCounterPresets + session.players.flatMap { p -> p.subCounters.map { it.name } })
        .distinctBy { it.lowercase() }

    fun seatsWith(name: String) = session.players.count { p -> p.subCounters.any { it.name.equals(name, ignoreCase = true) } }

    fun enable(name: String) {
        val skipped = session.players.filter { p ->
            p.subCounters.none { it.name.equals(name, ignoreCase = true) } && p.subCounters.size >= Limits.MAX_SUB_COUNTERS
        }
        skippedNote = if (skipped.isEmpty()) null
        else "$name skipped on ${skipped.joinToString { it.name }} — already holding ${Limits.MAX_SUB_COUNTERS} counters."
        dispatch(GameAction.SetCounterOnAllSeats(name, enabled = true))
    }

    DialogFrame("Counters on every seat", onDismiss, maxWidth = 420.dp) {
        Note("Turn a counter on to add it to every seat at its starting value. Per-seat counters stay editable from each seat.")

        Column {
            names.forEachIndexed { i, name ->
                if (i > 0) Hairline()
                val on = seatsWith(name)
                val state = when (on) {
                    0 -> SwitchState.OFF
                    seats -> SwitchState.ON
                    else -> SwitchState.PARTIAL
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, style = ChronoTheme.type.body, color = c.text)
                        Text(
                            if (on == 0) "Off" else "On $on of $seats seats",
                            style = ChronoTheme.type.body.copy(fontSize = 12.sp), color = c.muted,
                        )
                    }
                    ChronoSwitch(state, description = "Toggle $name on every seat", onToggle = {
                        if (state == SwitchState.ON) {
                            skippedNote = null
                            dispatch(GameAction.SetCounterOnAllSeats(name, enabled = false))
                        } else enable(name)
                    })
                }
            }
        }

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChronoTextField(
                value = custom,
                onValueChange = { custom = it },
                label = "Custom counter for all",
                placeholder = "e.g. Rads",
                maxLength = Limits.MAX_NAME_LENGTH,
                showCounter = false,
                modifier = Modifier.weight(1f),
            )
            Pill("Add", enabled = custom.isNotBlank()) {
                enable(custom.trim())
                custom = ""
            }
        }
        skippedNote?.let { Text(it, style = ChronoTheme.type.body.copy(fontSize = 13.sp), color = c.negative) }
        Note("Each seat holds up to ${Limits.MAX_SUB_COUNTERS} counters; seats already full are skipped and named. Turning a counter off removes it, and its log entries, from every seat.")
    }
}
