package io.chronoscore.ui.dialogs

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.chronoscore.engine.BRISCOLA_TOTAL
import io.chronoscore.engine.BurracoSide
import io.chronoscore.engine.ScopaSide
import io.chronoscore.engine.boccePoints
import io.chronoscore.engine.briscolaHands
import io.chronoscore.engine.burracoPoints
import io.chronoscore.engine.scala40Penalty
import io.chronoscore.engine.scopaPoints
import io.chronoscore.model.HandCalc
import io.chronoscore.model.Player
import io.chronoscore.ui.formatScore
import io.chronoscore.ui.theme.ChronoTheme
import io.chronoscore.ui.theme.parseHex

private val BoccePoints = (1..6).toList()

/**
 * Round sheet with a hand calculator: enter what happened in the hand, check the points
 * each seat gets, and apply them to [round] in one step.
 */
@Composable
fun HandDialog(
    players: List<Player>,
    round: Int,
    calc: HandCalc,
    onApply: (Map<String, Int>) -> Unit,
    onManual: () -> Unit,
    onDismiss: () -> Unit,
) {
    val seats = players.sortedBy { it.seatIndex }
    DialogFrame("${calc.title} · hand $round", onDismiss, maxWidth = 460.dp) {
        val points: List<Int>? = when (calc) {
            HandCalc.SCOPA -> scopaInputs(seats)
            HandCalc.BURRACO -> burracoInputs(seats)
            HandCalc.BRISCOLA -> briscolaInputs(seats)
            HandCalc.BOCCE -> bocceInputs(seats)
            HandCalc.SCALA40 -> scala40Inputs(seats)
        }

        if (points != null) {
            SectionLabel("Points this hand")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                seats.forEachIndexed { i, p ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(p.name, style = ChronoTheme.type.label.copy(fontSize = 11.sp), color = parseHex(p.colorHex), maxLines = 1)
                        Text(
                            formatScore(points[i], plus = true), style = ChronoTheme.type.counter.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                            color = ChronoTheme.colors.text, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Type points", Modifier.weight(1f), onClick = onManual)
            Pill("Apply", Modifier.weight(1f), style = PillStyle.PRIMARY, enabled = points != null) {
                if (points != null) onApply(seats.mapIndexed { i, p -> p.id to points[i] }.toMap())
            }
        }
    }
}

val HandCalc.title: String
    get() = when (this) {
        HandCalc.SCOPA -> "Scopa"
        HandCalc.BURRACO -> "Burraco"
        HandCalc.BRISCOLA -> "Briscola"
        HandCalc.BOCCE -> "Bocce"
        HandCalc.SCALA40 -> "Scala 40"
    }

@Composable
private fun scopaInputs(seats: List<Player>): List<Int> {
    val sides = remember { mutableStateOf(List(seats.size) { ScopaSide() }) }
    var settebello by remember { mutableIntStateOf(-1) }
    var primiera by remember { mutableIntStateOf(-1) }
    seats.forEachIndexed { i, p ->
        val side = sides.value[i]
        val set = { s: ScopaSide -> sides.value = sides.value.toMutableList().also { it[i] = s } }
        SeatHeading(p)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IntField("Cards", side.cards, Modifier.weight(1f)) { set(side.copy(cards = it)) }
            IntField("Denari", side.denari, Modifier.weight(1f)) { set(side.copy(denari = it)) }
            IntField("Scope", side.scope, Modifier.weight(1f)) { set(side.copy(scope = it)) }
        }
    }
    Text("Settebello", style = ChronoTheme.type.body, color = ChronoTheme.colors.text)
    SeatChoice(seats, settebello, noneLabel = "—") { settebello = it }
    Text("Primiera", style = ChronoTheme.type.body, color = ChronoTheme.colors.text)
    SeatChoice(seats, primiera, noneLabel = "Tie") { primiera = it }
    Note("Most cards (over 20 of 40) and most denari (over 5) score a point each; a tie scores nobody. Primiera: best card per suit, 7 = 21, 6 = 18, ace = 16, 5 = 15, 4 = 14, 3 = 13, 2 = 12, face cards 10.")
    return scopaPoints(sides.value.mapIndexed { i, s -> s.copy(settebello = i == settebello, primiera = i == primiera) })
}

@Composable
private fun burracoInputs(seats: List<Player>): List<Int> {
    val sides = remember { mutableStateOf(List(seats.size) { BurracoSide() }) }
    var closer by remember { mutableIntStateOf(-1) }
    seats.forEachIndexed { i, p ->
        val side = sides.value[i]
        val set = { s: BurracoSide -> sides.value = sides.value.toMutableList().also { it[i] = s } }
        SeatHeading(p)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IntField("Clean ×200", side.clean, Modifier.weight(1f)) { set(side.copy(clean = it)) }
            IntField("Dirty ×100", side.dirty, Modifier.weight(1f)) { set(side.copy(dirty = it)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IntField("Cards on table", side.table, Modifier.weight(1f)) { set(side.copy(table = it)) }
            IntField("Cards in hand", side.hand, Modifier.weight(1f)) { set(side.copy(hand = it)) }
        }
        SwitchRow("Took the pozzetto", "−100 if not.", side.pozzetto) { set(side.copy(pozzetto = it)) }
    }
    Text("Closed the hand (+100)", style = ChronoTheme.type.body, color = ChronoTheme.colors.text)
    SeatChoice(seats, closer, noneLabel = "Nobody") { closer = it }
    Note("Card values: jolly 30, pinella 20, ace 15, 8 to king 10, 3 to 7 five.")
    return sides.value.mapIndexed { i, s -> burracoPoints(s.copy(closed = i == closer)) }
}

@Composable
private fun briscolaInputs(seats: List<Player>): List<Int>? {
    if (seats.size != 2) {
        Note("The Briscola calculator is for two sides (one per player or per team). Type the points instead.")
        return null
    }
    var first by remember { mutableIntStateOf(60) }
    IntField("${seats[0].name}: card points", first, Modifier.fillMaxWidth(), max = BRISCOLA_TOTAL) { first = it }
    Note("${seats[1].name}: ${BRISCOLA_TOTAL - first.coerceIn(0, BRISCOLA_TOTAL)}. 61 of the 120 wins the hand; 60–60 is a draw.")
    return briscolaHands(first)
}

@Composable
private fun bocceInputs(seats: List<Player>): List<Int> {
    var scorer by remember { mutableIntStateOf(0) }
    var points by remember { mutableIntStateOf(1) }
    Text("Closest to the jack", style = ChronoTheme.type.body, color = ChronoTheme.colors.text)
    SeatChoice(seats, scorer) { scorer = it }
    Text("Bocce closer than the other side's best", style = ChronoTheme.type.body, color = ChronoTheme.colors.text)
    Segmented(BoccePoints.map { "$it" }, BoccePoints.indexOf(points), onSelect = { points = BoccePoints[it] })
    return boccePoints(seats.size, scorer, points)
}

@Composable
private fun scala40Inputs(seats: List<Player>): List<Int> {
    var closer by remember { mutableIntStateOf(0) }
    val opened = remember { mutableStateOf(List(seats.size) { true }) }
    val cards = remember { mutableStateOf(List(seats.size) { 0 }) }
    Text("Closed the hand", style = ChronoTheme.type.body, color = ChronoTheme.colors.text)
    SeatChoice(seats, closer) { closer = it }
    seats.forEachIndexed { i, p ->
        if (i == closer) return@forEachIndexed
        SeatHeading(p)
        SwitchRow("Opened", "Not opened costs a flat 100.", opened.value[i]) { on ->
            opened.value = opened.value.toMutableList().also { it[i] = on }
        }
        if (opened.value[i]) {
            IntField("Cards in hand", cards.value[i], Modifier.fillMaxWidth()) { v ->
                cards.value = cards.value.toMutableList().also { it[i] = v }
            }
        }
    }
    Note("Penalties: ace 11, face cards 10, number cards face value, jolly 25.")
    return seats.indices.map { i -> scala40Penalty(closed = i == closer, opened = opened.value[i], cardsInHand = cards.value[i]) }
}

@Composable
private fun SeatHeading(player: Player) {
    Text(
        player.name.uppercase() + if (player.isTeam) " · " + player.members.joinToString(" & ") { it.name } else "",
        style = ChronoTheme.type.label, color = parseHex(player.colorHex), modifier = Modifier.padding(top = 6.dp),
    )
}

/** One seat, or none with [noneLabel]; -1 means none. */
@Composable
private fun SeatChoice(seats: List<Player>, selected: Int, noneLabel: String? = null, onSelect: (Int) -> Unit) {
    val options = seats.map { it.name } + listOfNotNull(noneLabel)
    Segmented(
        options = options,
        selectedIndex = if (selected < 0) seats.size else selected,
        onSelect = { onSelect(if (it >= seats.size) -1 else it) },
    )
}

/** A whole-number field, 0 when empty. */
@Composable
private fun IntField(label: String, value: Int, modifier: Modifier, max: Int = 9_999, onValue: (Int) -> Unit) {
    var text by remember { mutableStateOf(if (value == 0) "" else "$value") }
    ChronoTextField(
        value = text,
        onValueChange = { v ->
            text = v.filter(Char::isDigit).take(4)
            onValue((text.toIntOrNull() ?: 0).coerceAtMost(max))
        },
        label = label,
        placeholder = "0",
        keyboardType = KeyboardType.Number,
        showCounter = false,
        modifier = modifier,
    )
}
