package io.chronoscore.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.chronoscore.model.Player
import io.chronoscore.model.ScoreHistoryEntry
import io.chronoscore.viewmodel.SeatStyle

/**
 * Lays out seat cards per [gridFor] with 4 px gutters, inserting [centerStrip] on the boundary
 * between the flipped and upright halves so the hub never covers a seat.
 */
@Composable
fun PlayerGrid(
    players: List<Player>,
    grid: SeatGrid,
    style: SeatStyle,
    highlightedPlayerId: String?,
    lastEntry: ScoreHistoryEntry?,
    callbacksFor: (Player) -> SeatCallbacks,
    centerStrip: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        grid.rowsOfSeats().forEachIndexed { rowIndex, seats ->
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                seats.forEach { seat ->
                    val player = players[seat]
                    key(player.id) {
                        SeatViewport(
                            player = player,
                            flipped = grid.defaultFlipped(seat) != player.isFlipped,
                            highlighted = player.id == highlightedPlayerId,
                            style = style,
                            lastEntry = lastEntry?.takeIf { it.playerId == player.id && it.subCounterId == null },
                            callbacks = callbacksFor(player),
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
            if (rowIndex == grid.stripAfterRow) centerStrip()
        }
    }
}
