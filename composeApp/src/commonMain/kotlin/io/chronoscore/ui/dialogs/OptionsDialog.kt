package io.chronoscore.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.chronoscore.engine.sanitized
import io.chronoscore.model.DartsGame
import io.chronoscore.model.DartsRules
import io.chronoscore.model.HandCalc
import io.chronoscore.model.MatchOptions
import io.chronoscore.model.TimeBonus
import io.chronoscore.model.TimeControl
import io.chronoscore.model.WinMode
import io.chronoscore.ui.board.bonusLabel
import io.chronoscore.ui.theme.ChronoIcons
import io.chronoscore.ui.theme.ChronoTheme

private val TurnLimits = listOf(null, 30, 60, 120, 300)
private val TurnLimitLabels = listOf("Off", "30s", "1m", "2m", "5m")

private val BankMinutes = listOf(1, 3, 5, 10, 15, 30, 60)
private val BonusSeconds = listOf(2, 5, 10, 30)

/**
 * Match options; every change applies immediately. The same screen edits the match on the
 * table ("Match options") and the setup of a new game, with a different [title].
 */
@Composable
fun OptionsDialog(
    options: MatchOptions,
    onChange: (MatchOptions) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Match options",
) {
    DialogFrame(title, onDismiss, maxWidth = 440.dp, footer = { Pill("Done", Modifier.fillMaxWidth(), style = PillStyle.PRIMARY, onClick = onDismiss) }) {
        OptionsEditor(options, onChange)
        Note("Everything here is optional. With all of it off, the table works like a plain counter.")
    }
}

/** Toggles for the optional features; shared by Match options and New game. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OptionsEditor(options: MatchOptions, onChange: (MatchOptions) -> Unit) {
    val set = { o: MatchOptions -> onChange(o.sanitized()) }

    SectionLabel("Turns & time", topGap = 0.dp)
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

    TimerModeEditor(options, set)

    DartsEditor(options, set)
    // Darts decide the result themselves.
    if (options.darts != null) return

    SectionLabel("Winning")
    val rule = options.winRule
    Segmented(
        options = listOf("None", "Target", "Low out", "High out"),
        selectedIndex = rule.mode.ordinal,
        onSelect = { i ->
            val mode = WinMode.entries[i]
            val target = when {
                mode == rule.mode -> rule.target
                mode == WinMode.TARGET_SCORE -> 10
                mode == WinMode.ELIMINATION_ABOVE -> 101
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
        WinMode.ELIMINATION_ABOVE -> {
            NumberField("Out at or above", rule.target) { set(options.copy(winRule = rule.copy(target = it))) }
            Note("Penalty points: seats at or above this score are out (Scala 40 plays to 101). The last seat standing wins.")
        }
    }

    SectionLabel("Game extras")
    SwitchRow(
        "Magic: The Gathering extras",
        "Commander damage per opponent, Monarch, Initiative and Day/Night. With elimination on, 21 commander damage or 10 poison knocks a seat out.",
        options.mtgExtras,
    ) { set(options.copy(mtgExtras = it)) }

    SectionLabel("Layout")
    SwitchRow("Round sheet", "A scorepad: enter each seat's points per round, totals add up.", options.sheetMode) {
        set(options.copy(sheetMode = it))
    }
    if (options.sheetMode) {
        Text("Hand calculator", style = ChronoTheme.type.body, color = ChronoTheme.colors.text)
        val calcs = listOf<HandCalc?>(null) + HandCalc.entries
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            calcs.forEach { calc ->
                Pill(calc?.title ?: "Off", selected = options.handCalculator == calc) { set(options.copy(handCalculator = calc)) }
            }
        }
        Note("Tapping a round works out the hand's points from what happened in it: scope and settebello, burrachi, card points, bocce or penalties.")
    }
}

/** Short description of the options that are on, e.g. "Turns · Clock · Target 10". */
fun MatchOptions.summary(): String {
    val parts = summaryParts()
    return if (parts.isEmpty()) "Off" else parts.joinToString(" · ")
}

/** "3 on", or "Off": fits on a menu row where the full summary would be cut. */
fun MatchOptions.shortSummary(): String = summaryParts().size.let { if (it == 0) "Off" else "$it on" }

private fun MatchOptions.summaryParts(): List<String> =
    buildList {
        if (turnTracking) add("Turns")
        if (matchClock) add("Clock")
        if (seatTimers) add("Seat timers")
        turnLimitSeconds?.let { add(if (it % 60 == 0) "${it / 60}m limit" else "${it}s limit") }
        timeControl?.let { add("Clock " + formatBank(it.bankSeconds) + (it.bonusLabel()?.let { b -> " $b" } ?: "")) }
        when (winRule.mode) {
            WinMode.TARGET_SCORE -> add("Target ${winRule.target}" + if (winRule.lowestWins) " (low wins)" else "")
            WinMode.ELIMINATION -> add("Elimination")
            WinMode.ELIMINATION_ABOVE -> add("Out at ${winRule.target}")
            WinMode.NONE -> Unit
        }
        darts?.let { d ->
            add(
                when (d.game) {
                    DartsGame.X01 -> "${d.start}" + if (d.doubleOut) " double out" else ""
                    DartsGame.CRICKET -> "Cricket"
                    DartsGame.AROUND_THE_CLOCK -> "Around the Clock"
                    DartsGame.SHANGHAI -> "Shanghai ${d.rounds}"
                },
            )
        }
        if (sheetMode) add(handCalculator?.let { "${it.title} sheet" } ?: "Round sheet")
        if (mtgExtras) add("MTG")
    }

/**
 * Timer mode: a chess clock for every seat. Turning it on also turns on the turn tracker,
 * since the clock follows the active seat.
 */
@Composable
private fun TimerModeEditor(options: MatchOptions, set: (MatchOptions) -> Unit) {
    val control = options.timeControl
    SwitchRow(
        "Timer mode", "Each seat counts down its own time bank, like a chess clock. Tap your seat to end your turn.",
        control != null,
    ) { on -> set(options.copy(turnTracking = options.turnTracking || on, timeControl = if (on) TimeControl() else null)) }
    if (control == null) return
    val update = { t: TimeControl -> set(options.copy(timeControl = t)) }

    Text("Time per seat", style = ChronoTheme.type.body, color = ChronoTheme.colors.text)
    Segmented(
        options = BankMinutes.map { "${it}m" },
        selectedIndex = BankMinutes.indexOf(control.bankSeconds / 60).coerceAtLeast(0),
        onSelect = { update(control.copy(bankSeconds = BankMinutes[it] * 60)) },
    )
    Text("Bonus per turn", style = ChronoTheme.type.body, color = ChronoTheme.colors.text)
    Segmented(
        options = listOf("None", "Increment", "Delay"),
        selectedIndex = control.bonus.ordinal,
        onSelect = { i ->
            val bonus = TimeBonus.entries[i]
            update(control.copy(bonus = bonus, bonusSeconds = if (bonus == TimeBonus.NONE) 0 else control.bonusSeconds.takeIf { it > 0 } ?: 5))
        },
    )
    if (control.bonus != TimeBonus.NONE) {
        Segmented(
            options = BonusSeconds.map { "${it}s" },
            selectedIndex = BonusSeconds.indexOf(control.bonusSeconds).coerceAtLeast(0),
            onSelect = { update(control.copy(bonusSeconds = BonusSeconds[it])) },
        )
    }
    Text("When time runs out", style = ChronoTheme.type.body, color = ChronoTheme.colors.text)
    Segmented(
        options = listOf("Seat is out", "Overtime"),
        selectedIndex = if (control.outOnTimeout) 0 else 1,
        onSelect = { update(control.copy(outOnTimeout = it == 0)) },
    )
    Note(
        when (control.bonus) {
            TimeBonus.NONE -> "The clock starts paused; tap the first seat to start."
            TimeBonus.INCREMENT -> "Each seat gains the bonus after every turn it finishes in time."
            TimeBonus.DELAY -> "The first seconds of each turn are free; only time past the delay comes off the bank."
        } + if (control.outOnTimeout) " A seat that runs out is out, and the last seat left wins."
        else " A seat that runs out keeps playing, with its overtime shown in red.",
    )
}

/** Darts mode: the game, and its rules. Changing them starts the scores over. */
@Composable
private fun DartsEditor(options: MatchOptions, set: (MatchOptions) -> Unit) {
    val darts = options.darts
    var picking by remember { mutableStateOf(false) }
    SectionLabel("Darts")
    val current = darts?.let { d -> DartsGames.first { it.first == d.game }.second } ?: "Off"
    Pill(
        "Darts game · $current", Modifier.fillMaxWidth(), textAlign = TextAlign.Start,
        trailingIcon = if (picking) ChronoIcons.ChevronUp else ChronoIcons.ArrowRight,
    ) { picking = !picking }
    if (picking) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf<Pair<DartsGame?, String>>(null to "Off") + DartsGames).forEach { (game, label) ->
                Pill(label, Modifier.fillMaxWidth(), selected = darts?.game == game, textAlign = TextAlign.Start) {
                    set(options.copy(darts = game?.let { (darts ?: DartsRules()).copy(game = it) }))
                    picking = false
                }
            }
        }
    }
    if (darts == null) {
        Note("Enter every dart on a board keypad: X01 with double-out, Cricket, Around the Clock or Shanghai.")
        return
    }
    val update = { d: DartsRules -> set(options.copy(darts = d)) }
    when (darts.game) {
        DartsGame.X01 -> {
            Segmented(
                options = X01Starts.map { "$it" },
                selectedIndex = X01Starts.indexOf(darts.start).coerceAtLeast(0),
                onSelect = { update(darts.copy(start = X01Starts[it])) },
            )
            SwitchRow("Double out", "The last dart must be a double or the bull's eye.", darts.doubleOut) { update(darts.copy(doubleOut = it)) }
            SwitchRow("Double in", "Scoring starts with a double.", darts.doubleIn) { update(darts.copy(doubleIn = it)) }
        }
        DartsGame.SHANGHAI -> Segmented(
            options = ShanghaiRounds.map { "$it rounds" },
            selectedIndex = ShanghaiRounds.indexOf(darts.rounds).coerceAtLeast(0),
            onSelect = { update(darts.copy(rounds = ShanghaiRounds[it])) },
        )
        else -> Unit
    }
    Note(
        when (darts.game) {
            DartsGame.X01 -> "Count down to exactly 0. Going below 0 (or to 1 with double out) is a bust: the visit doesn't count."
            DartsGame.CRICKET -> "Three marks close a number (a treble does it in one). Extra marks score while an opponent still has it open."
            DartsGame.AROUND_THE_CLOCK -> "Hit 1 to 20 in order, then the bull. Any ring counts as one hit."
            DartsGame.SHANGHAI -> "Round n aims at n; doubles and trebles multiply. Single, double and treble of it in one visit wins outright."
        } + " Changing the darts settings starts the scores over.",
    )
}

private val DartsGames = listOf(
    DartsGame.X01 to "X01",
    DartsGame.CRICKET to "Cricket",
    DartsGame.AROUND_THE_CLOCK to "Around the Clock",
    DartsGame.SHANGHAI to "Shanghai",
)
private val X01Starts = listOf(301, 501, 701)
private val ShanghaiRounds = listOf(7, 20)

private fun formatBank(seconds: Int): String =
    if (seconds % 60 == 0) "${seconds / 60}m" else "${seconds / 60}m${seconds % 60}s"

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
    // Follow outside changes (another preset picked), but leave text being typed ("-", "") alone.
    if (text.toIntOrNull()?.let { it != value } == true) text = value.toString()
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
