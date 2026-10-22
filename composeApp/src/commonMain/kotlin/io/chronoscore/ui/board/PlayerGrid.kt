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
    statusFor: (Player) -> SeatStatus,
    centerStrip: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    /** Timer mode: seats become clock tiles; null shows the usual score seats. */
    clock: ClockTiles? = null,
) {
    Column(modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        grid.rowsOfSeats().forEachIndexed { rowIndex, seats ->
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                seats.forEach { seat ->
                    val player = players[seat]
                    val angle = grid.angleOf(seat, player)
                    val seatModifier = Modifier.weight(1f).fillMaxHeight()
                    key(player.id) {
                        if (clock != null) ClockTile(
                            player = player,
                            angle = angle,
                            highlighted = player.id == highlightedPlayerId,
                            style = style,
                            status = statusFor(player),
                            callbacks = callbacksFor(player),
                            tiles = clock,
                            modifier = seatModifier,
                        ) else SeatViewport(
                            player = player,
                            angle = angle,
                            highlighted = player.id == highlightedPlayerId,
                            style = style,
                            lastEntry = lastEntry?.takeIf { it.playerId == player.id && it.subCounterId == null },
                            callbacks = callbacksFor(player),
                            modifier = seatModifier,
                            status = statusFor(player),
                        )
                    }
                }
            }
            if (rowIndex == grid.stripAfterRow) centerStrip()
        }
    }
}
