package io.chronoscore.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.chronoscore.model.Limits

/**
 * Optional game name for a match, with chips for games played before. Matches with the same
 * game are grouped together in Saved matches; leaving it empty files the match under "No game".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GamePicker(
    value: String,
    onValueChange: (String) -> Unit,
    knownGames: List<String>,
    modifier: Modifier = Modifier,
    label: String = "Game (optional)",
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChronoTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            placeholder = "e.g. Catan, Commander",
            maxLength = Limits.MAX_GAME_NAME_LENGTH,
            showCounter = false,
        )
        if (knownGames.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                knownGames.forEach { game ->
                    val selected = value.trim().equals(game, ignoreCase = true)
                    Pill(game, selected = selected) { onValueChange(if (selected) "" else game) }
                }
            }
        }
    }
}
