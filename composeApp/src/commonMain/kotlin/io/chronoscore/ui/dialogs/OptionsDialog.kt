package io.chronoscore.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.chronoscore.engine.sanitized
import io.chronoscore.model.MatchOptions
import io.chronoscore.model.WinMode
import io.chronoscore.ui.theme.ChronoTheme

private val TurnLimits = listOf(null, 30, 60, 120, 300)
private val TurnLimitLabels = listOf("Off", "30s", "1m", "2m", "5m")

/** Match options for the match on the table; every change applies immediately. */
@Composable
fun OptionsDialog(options: MatchOptions, onChange: (MatchOptions) -> Unit, onDismiss: () -> Unit) {
    DialogFrame("Match options", onDismiss, maxWidth = 440.dp) {
        OptionsEditor(options, onChange)
        Note("Everything here is optional. With all of it off, the table works like a plain counter.")
    }
}

/** Toggles for the optional features; shared by Match options and New game. */
@Composable
fun OptionsEditor(options: MatchOptions, onChange: (MatchOptions) -> Unit) {
    val set = { o: MatchOptions -> onChange(o.sanitized()) }

    SectionLabel("Turns & time")
    SwitchRow("Turn tracker", "Highlights whose turn it is. Previous and Next pass the turn; after the last seat the round advances.", options.turnTracking) {
        set(options.copy(turnTracking = it))
    }
    SwitchRow("Match clock", "Total time for the match, with pause and resume.", options.matchClock) {
        set(options.copy(matchClock = it))
    }
    SwitchRow(
        "Seat timers", "Each seat's time on its own turns, like a chess clock.", options.seatTimers,
        enabled = options.turnTracking,
    ) { set(options.copy(seatTimers = it)) }
    Text(
        "Turn limit", style = ChronoTheme.type.body,
        color = if (options.turnTracking) ChronoTheme.colors.text else ChronoTheme.colors.muted,
    )
    Segmented(
        options = TurnLimitLabels,
        selectedIndex = TurnLimits.indexOf(options.turnLimitSeconds).coerceAtLeast(0),
        onSelect = { if (options.turnTracking) set(options.copy(turnLimitSeconds = TurnLimits[it])) },
    )
    if (!options.turnTracking) Note("Seat timers and the turn limit need the turn tracker.")

    SectionLabel("Winning", Modifier.padding(top = 8.dp))
    val rule = options.winRule
    Segmented(
        options = listOf("None", "Target", "Elimination"),
        selectedIndex = rule.mode.ordinal,
        onSelect = { i ->
            val mode = WinMode.entries[i]
            val target = when {
                mode == rule.mode -> rule.target
                mode == WinMode.TARGET_SCORE -> 10
                else -> 0
            }
            set(options.copy(winRule = rule.copy(mode = mode, target = target)))
        },
    )
    when (rule.mode) {
        WinMode.NONE -> Note("No automatic result. A seat can still be marked out from its editor.")
        WinMode.TARGET_SCORE -> {
            NumberField("Target score", rule.target) { set(options.copy(winRule = rule.copy(target = it))) }
            Segmented(
                options = listOf("Highest wins", "Lowest wins"),
                selectedIndex = if (rule.lowestWins) 1 else 0,
                onSelect = { set(options.copy(winRule = rule.copy(lowestWins = it == 1))) },
            )
            Note("The match ends when a seat reaches the target.")
        }
        WinMode.ELIMINATION -> {
            NumberField("Out at or below", rule.target) { set(options.copy(winRule = rule.copy(target = it))) }
            Note("Seats at or below this score are out. The last seat standing wins.")
        }
    }

    SectionLabel("Layout & games", Modifier.padding(top = 8.dp))
    SwitchRow("Round sheet", "A scorepad: enter each seat's points per round, totals add up.", options.sheetMode) {
        set(options.copy(sheetMode = it))
    }
    SwitchRow(
        "Magic: The Gathering extras",
        "Commander damage per opponent, Monarch, Initiative and Day/Night. With elimination on, 21 commander damage or 10 poison knocks a seat out.",
        options.mtgExtras,
    ) { set(options.copy(mtgExtras = it)) }
}

/** Short description of the options that are on, e.g. "Turns · Clock · Target 10". */
fun MatchOptions.summary(): String {
    val parts = buildList {
        if (turnTracking) add("Turns")
        if (matchClock) add("Clock")
        if (seatTimers) add("Seat timers")
        turnLimitSeconds?.let { add(if (it % 60 == 0) "${it / 60}m limit" else "${it}s limit") }
        when (winRule.mode) {
            WinMode.TARGET_SCORE -> add("Target ${winRule.target}" + if (winRule.lowestWins) " (low wins)" else "")
            WinMode.ELIMINATION -> add("Elimination")
            WinMode.NONE -> Unit
        }
        if (sheetMode) add("Round sheet")
        if (mtgExtras) add("MTG")
    }
    return if (parts.isEmpty()) "Off" else parts.joinToString(" · ")
}

/** Title and explanation on the left, a switch on the right. */
@Composable
fun SwitchRow(title: String, note: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val c = ChronoTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = ChronoTheme.type.body, color = if (enabled) c.text else c.muted)
            if (note != null) Text(note, style = ChronoTheme.type.body.copy(fontSize = 13.sp), color = c.muted)
        }
        ChronoSwitch(
            state = if (checked && enabled) SwitchState.ON else SwitchState.OFF,
            onToggle = { if (enabled) onChange(!checked) },
            description = title,
        )
    }
}

/** Whole-number field that accepts a leading minus; applies as soon as the text parses. */
@Composable
private fun NumberField(label: String, value: Int, onValue: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    ChronoTextField(
        value = text,
        onValueChange = { v ->
            val cleaned = v.filterIndexed { i, ch -> ch.isDigit() || (i == 0 && ch == '-') }.take(6)
            text = cleaned
            cleaned.toIntOrNull()?.let(onValue)
        },
        label = label,
        keyboardType = KeyboardType.Number,
        showCounter = false,
        modifier = Modifier.fillMaxWidth(),
    )
}
