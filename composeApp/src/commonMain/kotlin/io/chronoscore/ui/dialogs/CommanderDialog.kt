package io.chronoscore.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.chronoscore.engine.GameAction
import io.chronoscore.engine.GameEngine
import io.chronoscore.model.CounterTarget
import io.chronoscore.model.GameSession
import io.chronoscore.model.Limits
import io.chronoscore.ui.theme.ChronoTheme
import io.chronoscore.ui.theme.parseHex

/**
 * Commander damage a seat has taken, one row per opposing commander (MTG extras).
 * Each change also moves the seat's life by the same amount, as one undo step.
 */
@Composable
fun CommanderDialog(
    session: GameSession,
    playerId: String,
    rotated: Boolean,
    dispatch: (GameAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val player = session.player(playerId) ?: return
    val c = ChronoTheme.colors
    DialogFrame("${player.name} · Commander damage", onDismiss, rotated = rotated, maxWidth = 420.dp) {
        Note("Damage taken from each commander. It also comes off ${player.name}'s life; ${Limits.COMMANDER_LETHAL} from one commander is lethal.")
        val opponents = session.players.filter { it.id != playerId }
        if (opponents.isEmpty()) Note("Add another seat to track commander damage.")
        opponents.forEach { opponent ->
            val target = CounterTarget.Commander(opponent.id)
            val damage = player.commanderDamage[opponent.id] ?: 0
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(width = 3.dp, height = 20.dp).background(parseHex(opponent.colorHex)))
                Spacer(Modifier.width(2.dp))
                Text(
                    opponent.name, style = ChronoTheme.type.body,
                    color = if (damage >= Limits.COMMANDER_LETHAL) c.negative else c.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Stepper(
                    "$damage",
                    onDecrement = { dispatch(GameAction.Adjust(playerId, target, -1)) },
                    onIncrement = { dispatch(GameAction.Adjust(playerId, target, 1)) },
                    decrementEnabled = damage > 0,
                    incrementEnabled = damage < GameEngine.MAX_COMMANDER_DAMAGE,
                )
            }
        }
        Pill("Done", Modifier.fillMaxWidth(), style = PillStyle.PRIMARY, onClick = onDismiss)
    }
}
