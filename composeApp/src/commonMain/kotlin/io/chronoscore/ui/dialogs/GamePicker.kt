package io.chronoscore.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import io.chronoscore.model.Limits

private const val MAX_SUGGESTIONS = 6

/**
 * Optional game name for a match, as a combobox: once the field has been focused, games played
 * before that match the text show as suggestions below it. A name that already matches a known
 * game shows no chip, so the field and a chip never repeat the same text. Matches with the same
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
    // Stays on after focus leaves, so a tap on a suggestion still lands.
    var engaged by remember { mutableStateOf(false) }
    val query = value.trim()
    val suggestions = knownGames
        .filter { !it.equals(query, ignoreCase = true) && it.contains(query, ignoreCase = true) }
        .take(MAX_SUGGESTIONS)
    val exact = knownGames.any { it.equals(query, ignoreCase = true) }
    Column(
        modifier.onFocusChanged { if (it.hasFocus) engaged = true },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChronoTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            placeholder = "e.g. Catan, Commander",
            maxLength = Limits.MAX_GAME_NAME_LENGTH,
            showCounter = false,
        )
        if (engaged && !exact && suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { game -> Pill(game) { onValueChange(game) } }
            }
        }
    }
}
