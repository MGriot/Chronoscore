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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.chronoscore.engine.GameAction
import io.chronoscore.model.Accents
import io.chronoscore.model.BuiltInPresets
import io.chronoscore.model.GamePreset
import io.chronoscore.model.GameSession
import io.chronoscore.model.Limits
import io.chronoscore.model.category
import io.chronoscore.model.sameSetupAs
import io.chronoscore.model.seatsLabel
import io.chronoscore.ui.theme.ChronoIcons
import io.chronoscore.ui.theme.ChronoShapes
import io.chronoscore.ui.theme.ChronoTheme
import io.chronoscore.ui.theme.parseHex

private val ScorePresets = listOf(20, 30, 40)
private val TeamSizes = listOf(1, 2, 3)

/** What a new match starts with, plus the seat names and colors typed in setup (by seat index). */
class NewGameSetup(
    val action: GameAction.NewGame,
    val names: Map<Int, String>,
    val colors: Map<Int, String>,
)

/** The same game, players and options again: what Rematch and the last-match tile start. */
fun GameSession.rematch(): GameAction.NewGame =
    GameAction.NewGame(players.size, startingScore, gameName, counters = null, options = options, teamSize = null)

/** Starts a preset in one tap, with the players from the table. */
fun GamePreset.newGameAction(): GameAction.NewGame =
    GameAction.NewGame(playerCount, startingScore, gameName, counters, options, teamSize ?: 1)

/**
 * New game setup in two steps: 1 · Game (preset, name, starting score, options) and
 * 2 · Players (count or teams, a name and color per seat, prefilled from the table).
 * Start sits in a footer that stays in view and starts in one tap: the current match
 * stays in Saved matches, so there is nothing to confirm.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewGameDialog(
    session: GameSession,
    knownGames: List<String>,
    presets: List<GamePreset>,
    onStart: (NewGameSetup) -> Unit,
    onDismiss: () -> Unit,
    /** Saves (or updates, for a saved preset's id) and returns the preset as stored. */
    onSavePreset: (GamePreset) -> GamePreset = { it },
    onDeletePreset: (String) -> Unit = {},
    initialPreset: GamePreset? = null,
    initialStep: Int = 0,
) {
    val c = ChronoTheme.colors
    var step by remember { mutableIntStateOf(initialStep) }
    var presetId by remember { mutableStateOf(initialPreset?.id) }
    var gameName by remember { mutableStateOf(initialPreset?.gameName ?: session.gameName.orEmpty()) }
    var playerCount by remember { mutableIntStateOf(initialPreset?.playerCount ?: session.players.size) }
    var startingScore by remember { mutableIntStateOf(initialPreset?.startingScore ?: session.startingScore) }
    var custom by remember { mutableStateOf(startingScore !in ScorePresets) }
    var customScore by remember { mutableStateOf(if (startingScore in ScorePresets) "" else "$startingScore") }
    // Null keeps each seat's current counters; a preset sets them for every seat.
    var counters by remember { mutableStateOf(initialPreset?.counters) }
    var options by remember { mutableStateOf(initialPreset?.options ?: session.options) }
    var teamSize by remember {
        mutableIntStateOf(initialPreset?.teamSize ?: session.players.first().members.size.coerceAtLeast(1))
    }
    var showOptions by remember { mutableStateOf(false) }
    // "Saved as preset …", shown until the setup changes again.
    var savedMessage by remember { mutableStateOf<String?>(null) }
    // Only seats the user changed are renamed or recolored after the engine sets the table up.
    val names = remember { mutableStateMapOf<Int, String>() }
    val colors = remember { mutableStateMapOf<Int, String>() }

    fun applyPreset(preset: GamePreset) {
        presetId = preset.id
        playerCount = preset.playerCount
        startingScore = preset.startingScore
        custom = preset.startingScore !in ScorePresets
        customScore = if (custom) "${preset.startingScore}" else ""
        gameName = preset.gameName.orEmpty()
        counters = preset.counters
        options = preset.options
        teamSize = preset.teamSize ?: 1
        savedMessage = null
    }

    /** The setup on screen as a preset; what Save stores and what the selected preset is compared with. */
    fun currentPreset(id: String = "", name: String = gameName.trim().ifEmpty { "My preset" }) = GamePreset(
        id = id,
        name = name,
        gameName = gameName.trim().ifEmpty { null },
        playerCount = playerCount,
        startingScore = startingScore,
        counters = counters ?: session.players.first().subCounters.map { it.name },
        options = options,
        teamSize = teamSize.takeIf { it > 1 },
    )

    val selected = presets.firstOrNull { it.id == presetId }
    val current = currentPreset()
    val edited = selected != null && !selected.sameSetupAs(current)

    fun save(preset: GamePreset, message: (GamePreset) -> String) {
        val saved = onSavePreset(preset)
        presetId = saved.id
        savedMessage = message(saved)
    }

    /** The name the engine gives seat [i]: carried over, or "Team n" / "Player n" for new seats. */
    fun defaultName(i: Int): String {
        val old = session.players.getOrNull(i)
        return when {
            teamSize > 1 && old?.members?.size != teamSize -> "Team ${i + 1}"
            old != null -> old.name
            else -> "Player ${i + 1}"
        }
    }

    fun defaultColor(i: Int) = session.players.getOrNull(i)?.colorHex ?: Accents.all[i % Accents.all.size]

    fun start() {
        onStart(
            NewGameSetup(
                GameAction.NewGame(playerCount, startingScore, gameName.ifBlank { null }, counters, options, teamSize),
                names = names.filterKeys { it < playerCount }.filterValues { it.isNotBlank() },
                colors = colors.filterKeys { it < playerCount },
            ),
        )
        onDismiss()
    }

    DialogFrame(
        "New game", onDismiss, maxWidth = 440.dp,
        footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (step == 0) Pill("Players", Modifier.weight(1f), trailingIcon = ChronoIcons.ArrowRight) { step = 1 }
                Pill("Start new game", Modifier.weight(1f), style = PillStyle.PRIMARY, leadingIcon = ChronoIcons.Play, onClick = ::start)
            }
            val message = savedMessage
            when {
                message != null && !edited -> Text(
                    message, style = ChronoTheme.type.body.copy(fontSize = 14.sp), color = c.muted,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp),
                )
                selected != null && !BuiltInPresets.isBuiltIn(selected) && edited ->
                    Row(Modifier.align(Alignment.CenterHorizontally)) {
                        TextAction("Update \"${selected.name}\"") {
                            save(currentPreset(selected.id, selected.name)) { "Updated preset \"${it.name}\"" }
                        }
                        TextAction("Save as new preset") { save(currentPreset()) { "Saved as preset \"${it.name}\"" } }
                    }
                // Nothing to save: the preset as it is.
                selected != null && !edited -> TextAction("Preset unchanged", Modifier.align(Alignment.CenterHorizontally), enabled = false) {}
                else -> TextAction("Save as new preset", Modifier.align(Alignment.CenterHorizontally)) {
                    save(currentPreset()) { "Saved as preset \"${it.name}\"" }
                }
            }
        },
    ) {
        Segmented(listOf("1 · Game", "2 · Players"), step, onSelect = { step = it })

        if (step == 0) {
            presets.groupBy { it.category() }.entries.sortedBy { it.key.ordinal }.forEach { (category, group) ->
                SectionLabel(category.title)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.forEach { preset -> Pill(preset.name, selected = preset.id == presetId) { applyPreset(preset) } }
                }
                // Right under its own group, so the card shows next to the pill that was tapped.
                if (selected != null && selected in group) {
                    PresetCard(selected, current, edited)
                    if (!BuiltInPresets.isBuiltIn(selected)) {
                        TextAction("Delete preset \"${selected.name}\"") {
                            onDeletePreset(selected.id)
                            presetId = null
                        }
                    }
                }
            }
            if (selected == null) {
                Note("Pick a preset to fill in the game, players, starting score, counters and options. You can change any of them before starting.")
            }

            SectionLabel("Game")
            GamePicker(gameName, { gameName = it }, knownGames, label = "Game for the new match (optional)")
            if (options.darts != null) {
                // The engine takes the start from the darts game (X01 start, otherwise 0).
                Note("The darts game sets the starting score.")
            } else {
                Text("Starting score", style = ChronoTheme.type.body, color = c.text)
                Segmented(
                    options = ScorePresets.map { "$it" } + "Custom",
                    selectedIndex = if (custom) ScorePresets.size else ScorePresets.indexOf(startingScore).coerceAtLeast(0),
                    onSelect = { i ->
                        custom = i == ScorePresets.size
                        if (!custom) {
                            startingScore = ScorePresets[i]
                            customScore = ""
                        }
                    },
                )
                if (custom) {
                    ChronoTextField(
                        value = customScore,
                        onValueChange = { v ->
                            customScore = v.filter(Char::isDigit).take(5)
                            customScore.toIntOrNull()?.let { startingScore = it }
                        },
                        placeholder = "Custom starting score",
                        keyboardType = KeyboardType.Number,
                        showCounter = false,
                    )
                }
            }
            NavRow("Options · ${options.shortSummary()}", ChronoIcons.Options) { showOptions = true }
            Note(
                "Starting a new game doesn't delete anything: the current match" +
                    (session.gameName?.let { " ($it)" } ?: "") + " stays in Saved matches, so you can continue it later.",
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (teamSize > 1) "Teams" else "Players", style = ChronoTheme.type.body, color = c.text, modifier = Modifier.weight(1f))
                Stepper(
                    "$playerCount", onDecrement = { playerCount-- }, onIncrement = { playerCount++ },
                    decrementEnabled = playerCount > Limits.MIN_PLAYERS, incrementEnabled = playerCount < Limits.MAX_PLAYERS,
                )
            }
            // A team seat shares one score between its members (Scopone, Burraco in pairs, bocce teams).
            Segmented(
                options = TeamSizes.map { if (it == 1) "One per seat" else "Teams of $it" },
                selectedIndex = TeamSizes.indexOf(teamSize).coerceAtLeast(0),
                onSelect = { teamSize = TeamSizes[it] },
            )
            SectionLabel(if (teamSize > 1) "Teams" else "Seats")
            repeat(playerCount) { i ->
                val color = colors[i] ?: defaultColor(i)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Tap the dot to move to the next seat color.
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .clickable(onClickLabel = "Change color") {
                                val at = Accents.all.indexOf(color)
                                colors[i] = Accents.all[(at + 1) % Accents.all.size]
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(26.dp).clip(CircleShape).background(parseHex(color)).border(1.dp, c.outline, CircleShape))
                    }
                    ChronoTextField(
                        value = names[i] ?: defaultName(i),
                        onValueChange = { names[i] = it },
                        maxLength = Limits.MAX_NAME_LENGTH,
                        showCounter = false,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Note(
                if (teamSize > 1) "Add team members from each seat's editor once the game starts."
                else "Names, colors and saved players carry over from the table. Tap a dot to change its color.",
            )
        }
    }

    if (showOptions) {
        OptionsDialog(options, { options = it }, { showOptions = false }, title = "New game options")
    }
}

/**
 * What the selected preset sets, with the values on screen. Rows the user changed are marked,
 * so it's clear which settings came from the preset and which apply to this game only.
 */
@Composable
private fun PresetCard(preset: GamePreset, current: GamePreset, edited: Boolean) {
    val c = ChronoTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(ChronoShapes.dialog)
            .background(c.raised)
            .border(1.dp, c.outline, ChronoShapes.dialog)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Preset \"${preset.name}\"", style = ChronoTheme.type.body, color = c.text,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Text(
                when {
                    edited -> "EDITED"
                    BuiltInPresets.isBuiltIn(preset) -> "BUILT-IN"
                    else -> "YOURS"
                },
                style = ChronoTheme.type.label, color = if (edited) c.text else c.muted,
            )
        }
        PresetRow("Game", current.gameName ?: "None", current.gameName != preset.gameName)
        PresetRow("Players", current.seatsLabel(), current.playerCount != preset.playerCount || current.teamSize != preset.teamSize?.takeIf { it > 1 })
        if (current.options.darts == null) {
            PresetRow("Starting score", "${current.startingScore}", current.startingScore != preset.startingScore)
        }
        PresetRow("Counters", current.counters.joinToString().ifEmpty { "None" }, current.counters != preset.counters)
        PresetRow("Options", current.options.summary(), current.options != preset.options)
        Note(
            if (edited) "Changed from the preset. The changes apply to this game only unless you save them below."
            else "Player names and colors aren't part of a preset: they come from the table.",
        )
    }
}

@Composable
private fun PresetRow(label: String, value: String, changed: Boolean) {
    val c = ChronoTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = ChronoTheme.type.body.copy(fontSize = 13.sp), color = c.muted, modifier = Modifier.width(104.dp))
        Text(
            value + if (changed) "  · changed" else "",
            style = ChronoTheme.type.body.copy(fontSize = 13.sp, fontWeight = if (changed) FontWeight.SemiBold else FontWeight.Normal),
            color = c.text, modifier = Modifier.weight(1f),
        )
    }
}
