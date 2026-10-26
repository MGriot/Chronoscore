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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.chronoscore.engine.GameAction
import io.chronoscore.model.Accents
import io.chronoscore.model.Limits
import io.chronoscore.model.MatchOptions
import io.chronoscore.model.Player
import io.chronoscore.model.Profile
import io.chronoscore.model.SubCounterPresets
import io.chronoscore.model.TeamMember
import io.chronoscore.ui.formatDuration
import io.chronoscore.ui.theme.ChronoIcons
import io.chronoscore.ui.theme.ChronoShapes
import io.chronoscore.ui.theme.ChronoTheme
import io.chronoscore.ui.theme.parseHex

private const val CLOCK_STEP_MS = 60_000L

/** Edit one seat: name, color, counters, direct entry, rotation, removal. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerEditorDialog(
    player: Player,
    canRemove: Boolean,
    rotated: Boolean,
    dispatch: (GameAction) -> Unit,
    onSetScore: () -> Unit,
    onDismiss: () -> Unit,
    options: MatchOptions = MatchOptions(),
    isActive: Boolean = false,
    isMonarch: Boolean = false,
    isInitiative: Boolean = false,
    profiles: List<Profile> = emptyList(),
    onSaveProfile: () -> Unit = {},
) {
    var name by remember(player.id) { mutableStateOf(player.name) }
    var customCounter by remember { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf(false) }
    val c = ChronoTheme.colors

    val commitName = { if (name != player.name) dispatch(GameAction.RenamePlayer(player.id, name)) }
    val close = {
        commitName()
        onDismiss()
    }

    DialogFrame("Edit seat", close, rotated = rotated, maxWidth = 420.dp) {
        ChronoTextField(value = name, onValueChange = { name = it }, label = "Name", maxLength = Limits.MAX_NAME_LENGTH)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Set score", Modifier.weight(1f), leadingIcon = ChronoIcons.Keypad) {
                commitName()
                onSetScore()
            }
            Pill("Flip 180°", Modifier.weight(1f), leadingIcon = ChronoIcons.Rotate) {
                dispatch(GameAction.RotateSeat(player.id, 180))
                close()
            }
        }
        Row(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Angle ${player.rotation}°", style = ChronoTheme.type.body, color = c.text, modifier = Modifier.weight(1f))
            Pill("−${Limits.ROTATION_STEP}°", Modifier.weight(1f)) { dispatch(GameAction.RotateSeat(player.id, -Limits.ROTATION_STEP)) }
            Pill("+${Limits.ROTATION_STEP}°", Modifier.weight(1f)) { dispatch(GameAction.RotateSeat(player.id, Limits.ROTATION_STEP)) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (options.turnTracking) {
                Pill(if (isActive) "Their turn" else "Give turn", Modifier.weight(1f), enabled = !isActive) {
                    dispatch(GameAction.SetActivePlayer(player.id))
                    close()
                }
            }
            Pill(
                if (player.conceded) "Back in" else "Mark as out", Modifier.weight(1f),
                selected = player.conceded,
            ) { dispatch(GameAction.SetConceded(player.id, !player.conceded)) }
        }
        player.clockMillis?.let { bank ->
            // Timer mode: penalties, handicaps, or reviving a seat that ran out by mistake.
            Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Clock " + formatDuration(bank), style = ChronoTheme.type.counter.copy(fontSize = 15.sp),
                    color = if (bank <= 0) c.negative else c.text, modifier = Modifier.weight(1f),
                )
                Pill("−1 min", Modifier.weight(1f)) { dispatch(GameAction.AdjustClock(player.id, -CLOCK_STEP_MS)) }
                Pill("+1 min", Modifier.weight(1f)) { dispatch(GameAction.AdjustClock(player.id, CLOCK_STEP_MS)) }
            }
        }
        if (options.mtgExtras) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Monarch", Modifier.weight(1f), selected = isMonarch) {
                    dispatch(GameAction.SetMonarch(if (isMonarch) null else player.id))
                }
                Pill("Initiative", Modifier.weight(1f), selected = isInitiative) {
                    dispatch(GameAction.SetInitiative(if (isInitiative) null else player.id))
                }
            }
        }

        if (player.isTeam) TeamEditor(player, profiles, dispatch)
        else SavedPlayer(player, name, profiles, dispatch, commitName, onSaveProfile) { name = it }

        SectionLabel("Seat color")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Accents.all.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { hex -> Swatch(hex, hex == player.colorHex, Modifier.weight(1f)) { dispatch(GameAction.SetColor(player.id, hex)) } }
                }
            }
        }
        Note("Selected swatch takes a ring, so it doesn't depend on hue.")

        SectionLabel("Counters · ${player.subCounters.size} / ${Limits.MAX_SUB_COUNTERS}")
        player.subCounters.forEach { counter ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(counter.name, style = ChronoTheme.type.body, color = c.text, modifier = Modifier.weight(1f))
                Text("${counter.value}", style = ChronoTheme.type.counter.copy(fontSize = 17.sp), color = c.text)
                IconTap(ChronoIcons.Trash, "Remove ${counter.name}", tint = c.muted) {
                    dispatch(GameAction.RemoveSubCounter(player.id, counter.id))
                }
            }
        }
        if (player.subCounters.size < Limits.MAX_SUB_COUNTERS) {
            val existing = player.subCounters.map { it.name.lowercase() }.toSet()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SubCounterPresets.filter { it.lowercase() !in existing }.forEach { preset ->
                    Pill(preset) { dispatch(GameAction.AddSubCounter(player.id, preset)) }
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChronoTextField(
                    value = customCounter,
                    onValueChange = { customCounter = it },
                    placeholder = "Custom counter",
                    maxLength = Limits.MAX_NAME_LENGTH,
                    showCounter = false,
                    modifier = Modifier.weight(1f),
                )
                Pill("Add", enabled = customCounter.isNotBlank()) {
                    dispatch(GameAction.AddSubCounter(player.id, customCounter))
                    customCounter = ""
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canRemove) {
                Pill(
                    if (confirmRemove) "Tap to confirm" else "Remove seat", Modifier.weight(1f),
                    style = if (confirmRemove) PillStyle.DESTRUCTIVE else PillStyle.SECONDARY,
                ) {
                    if (confirmRemove) {
                        dispatch(GameAction.RemovePlayer(player.id))
                        onDismiss()
                    } else confirmRemove = true
                }
            }
            Pill("Done", Modifier.weight(1f), style = PillStyle.PRIMARY, onClick = close)
        }
    }
}

/** A single-player seat: link it to a saved player, or turn it into a team. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedPlayer(
    player: Player,
    name: String,
    profiles: List<Profile>,
    dispatch: (GameAction) -> Unit,
    commitName: () -> Unit,
    onSaveProfile: () -> Unit,
    onName: (String) -> Unit,
) {
    SectionLabel("Saved player")
    val linked = profiles.firstOrNull { it.id == player.profileId }
    if (linked != null) {
        Note("Stats for this seat count toward ${linked.name}.")
        Pill("Unlink from ${linked.name}", Modifier.fillMaxWidth()) { dispatch(GameAction.SetProfile(player.id, null)) }
    } else {
        Pill("Save ${name.trim().ifEmpty { player.name }} as a player", Modifier.fillMaxWidth(), leadingIcon = ChronoIcons.Person) {
            commitName()
            onSaveProfile()
        }
    }
    val others = profiles.filter { it.id != player.profileId }
    if (others.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            others.forEach { profile ->
                Pill(profile.name, accent = parseHex(profile.colorHex)) {
                    onName(profile.name)
                    dispatch(GameAction.SetProfile(player.id, profile))
                }
            }
        }
    }

    Pill("Make this seat a team", Modifier.fillMaxWidth(), leadingIcon = ChronoIcons.Plus) {
        commitName()
        // The current player becomes the first member; the seat name becomes the team name.
        dispatch(GameAction.SetTeamMembers(player.id, listOf(TeamMember(name.trim().ifEmpty { player.name }, player.profileId))))
        dispatch(GameAction.SetProfile(player.id, null))
    }
}

/** A team seat: its members share the score, and stats count for each of them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeamEditor(player: Player, profiles: List<Profile>, dispatch: (GameAction) -> Unit) {
    val c = ChronoTheme.colors
    var newMember by remember(player.id) { mutableStateOf("") }
    val set = { members: List<TeamMember> -> dispatch(GameAction.SetTeamMembers(player.id, members)) }

    SectionLabel("Team · ${player.members.size} / ${Limits.MAX_TEAM_MEMBERS}")
    player.members.forEachIndexed { index, member ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(member.name, style = ChronoTheme.type.body, color = c.text, modifier = Modifier.weight(1f))
            if (member.profileId != null) Text("saved", style = ChronoTheme.type.body.copy(fontSize = 13.sp), color = c.muted)
            IconTap(ChronoIcons.Trash, "Remove ${member.name}", tint = c.muted) {
                set(player.members.filterIndexed { i, _ -> i != index })
            }
        }
    }
    if (player.members.size < Limits.MAX_TEAM_MEMBERS) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChronoTextField(
                value = newMember,
                onValueChange = { newMember = it },
                placeholder = "Add a member",
                maxLength = Limits.MAX_NAME_LENGTH,
                showCounter = false,
                modifier = Modifier.weight(1f),
            )
            Pill("Add", enabled = newMember.isNotBlank()) {
                set(player.members + TeamMember(newMember))
                newMember = ""
            }
        }
        val linked = player.members.mapNotNull { it.profileId }.toSet()
        val others = profiles.filter { it.id !in linked }
        if (others.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                others.forEach { profile ->
                    Pill(profile.name, accent = parseHex(profile.colorHex)) { set(player.members + TeamMember(profile.name, profile.id)) }
                }
            }
        }
    }
    Note("Members share this seat's score. Stats count the result for each of them. Remove every member to make it a single seat again.")
}

/** Rounded color swatch; the selected one takes a text-colored ring with a gap. */
@Composable
private fun Swatch(hex: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = ChronoTheme.colors
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(ChronoShapes.control)
            .border(2.dp, if (selected) c.text else androidx.compose.ui.graphics.Color.Transparent, ChronoShapes.control)
            .padding(4.dp)
            .clickable(onClickLabel = "Color $hex", onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(4.dp)).background(parseHex(hex)))
    }
}
