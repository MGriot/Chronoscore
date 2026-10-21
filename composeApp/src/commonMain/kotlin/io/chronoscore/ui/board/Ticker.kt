package io.chronoscore.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.chronoscore.engine.nowMillis
import kotlinx.coroutines.delay

private const val TICK_MS = 250L

/** Wall-clock time that recomposes a few times a second while [running]; frozen otherwise. */
@Composable
fun rememberNow(running: Boolean): Long {
    var now by remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(running) {
        now = nowMillis()
        while (running) {
            delay(TICK_MS)
            now = nowMillis()
        }
    }
    return now
}
