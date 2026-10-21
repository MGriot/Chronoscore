package io.chronoscore.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.chronoscore.model.DayNight
import io.chronoscore.random.CoinSide
import io.chronoscore.random.TableRng
import io.chronoscore.ui.theme.ChronoShapes
import io.chronoscore.ui.theme.ChronoTheme
import io.chronoscore.viewmodel.UtilityResult
import io.chronoscore.viewmodel.UtilityState

/** Table utilities (UI kit · Dialogs): result first, then dice, custom dX, coin and pickers. */
@Composable
fun UtilitiesDialog(
    state: UtilityState,
    onRoll: (sides: Int, count: Int) -> Unit,
    onFlipCoin: () -> Unit,
    onPickFirst: () -> Unit,
    onShuffleOrder: () -> Unit,
    onDismiss: () -> Unit,
    /** MTG extras: shows the Day / Night switch. */
    showDayNight: Boolean = false,
    dayNight: DayNight? = null,
    onDayNight: (DayNight?) -> Unit = {},
) {
    var sides by remember { mutableIntStateOf((state.last as? UtilityResult.Dice)?.sides ?: 20) }
    var count by remember { mutableIntStateOf(1) }
    var custom by remember { mutableStateOf("") }

    DialogFrame("Table utilities", onDismiss, maxWidth = 420.dp) {
        ResultPanel(state.last)

        SectionLabel("Select dice")
        TableRng.STANDARD_DICE.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { d ->
                    val selected = sides == d && custom.isEmpty()
                    Pill(
                        "d$d", Modifier.weight(1f),
                        style = if (selected) PillStyle.PRIMARY else PillStyle.SECONDARY,
                    ) { sides = d; custom = ""; onRoll(d, count) }
                }
            }
        }

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val customSides = custom.toIntOrNull()
            val valid = customSides != null && customSides in TableRng.MIN_SIDES..TableRng.MAX_SIDES
            ChronoTextField(
                value = custom,
                onValueChange = { v -> custom = v.filter(Char::isDigit).take(4) },
                label = "Custom dX",
                placeholder = "e.g. 100",
                keyboardType = KeyboardType.Number,
                showCounter = false,
                modifier = Modifier.weight(1f),
            )
            Pill("Roll", style = PillStyle.PRIMARY, enabled = valid || custom.isEmpty()) {
                if (valid) sides = customSides!!
                onRoll(sides, count)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dice per roll", style = ChronoTheme.type.body, color = ChronoTheme.colors.text, modifier = Modifier.weight(1f))
            Stepper(
                "$count", onDecrement = { count-- }, onIncrement = { count++ },
                decrementEnabled = count > 1, incrementEnabled = count < 6,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Flip coin", Modifier.weight(1f), onClick = onFlipCoin)
            Pill("First player", Modifier.weight(1f), onClick = onPickFirst)
            Pill("Turn order", Modifier.weight(1f), onClick = onShuffleOrder)
        }

        if (showDayNight) {
            SectionLabel("Day / Night")
            Segmented(
                options = listOf("Off", "Day", "Night"),
                selectedIndex = when (dayNight) {
                    null -> 0
                    DayNight.DAY -> 1
                    DayNight.NIGHT -> 2
                },
                onSelect = { onDayNight(listOf(null, DayNight.DAY, DayNight.NIGHT)[it]) },
            )
        }

        if (state.log.size > 1) {
            SectionLabel("Recent")
            state.log.drop(1).forEach { r -> Note(r.describe()) }
        }
    }
}

@Composable
private fun ResultPanel(result: UtilityResult?) {
    val c = ChronoTheme.colors
    val (big, caption) = when (result) {
        null -> "—" to "Pick a die to roll"
        is UtilityResult.Dice -> result.total.toString() to
            if (result.values.size > 1) "${result.values.joinToString(" + ")}  ·  ${result.values.size}d${result.sides}"
            else "Rolled d${result.sides}"
        is UtilityResult.Coin -> (if (result.side == CoinSide.HEADS) "Heads" else "Tails") to "Coin flip"
        is UtilityResult.FirstPlayer -> result.playerName to "Goes first"
        is UtilityResult.TurnOrder -> result.playerNames.joinToString(" → ") to "Turn order"
    }
    val size = when {
        big.length <= 4 -> 72.sp
        big.length <= 10 -> 44.sp
        else -> 22.sp
    }
    Column(
        Modifier.fillMaxWidth().clip(ChronoShapes.control).background(c.surface).padding(vertical = 18.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            big, style = ChronoTheme.type.score.copy(fontSize = size, lineHeight = size),
            color = c.text, textAlign = TextAlign.Center,
        )
        Text(caption, style = ChronoTheme.type.body.copy(fontSize = 14.sp), color = c.muted, textAlign = TextAlign.Center)
    }
}

private fun UtilityResult.describe(): String = when (this) {
    is UtilityResult.Dice -> "${values.size}d$sides → ${values.joinToString(", ")}" + if (values.size > 1) " = $total" else ""
    is UtilityResult.Coin -> "Coin → ${side.name.lowercase().replaceFirstChar { it.uppercase() }}"
    is UtilityResult.FirstPlayer -> "First player → $playerName"
    is UtilityResult.TurnOrder -> "Turn order → ${playerNames.joinToString(", ")}"
}
