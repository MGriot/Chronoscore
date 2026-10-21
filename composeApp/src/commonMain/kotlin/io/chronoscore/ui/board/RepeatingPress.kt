package io.chronoscore.ui.board

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput

private const val HOLD_DELAY_MS = 400L
private const val SLOW_INTERVAL_MS = 150L
private const val FAST_INTERVAL_MS = 60L
private const val FAST_AFTER_MS = 1_000L
private const val FAST_STEP = 5

/** Wraps the result of waitForUpOrCancellation so a timeout (null) is distinguishable. */
private class Release(val change: PointerInputChange?)

/**
 * Tap → onStep(1). Press & hold → repeats onStep(1) every 150 ms, then onStep(5)
 * every 60 ms after ~1 s for large modifications. [pressedTint] is drawn while held.
 */
fun Modifier.repeatingPress(pressedTint: Color, onStep: (Int) -> Unit): Modifier = composed {
    val step by rememberUpdatedState(onStep)
    var pressed by remember { mutableStateOf(false) }
    this
        .drawBehind { if (pressed) drawRect(pressedTint) }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown().consume()
                pressed = true
                try {
                    val early = withTimeoutOrNull(HOLD_DELAY_MS) { Release(waitForUpOrCancellation()) }
                    if (early != null) {
                        if (early.change != null) step(1)
                        return@awaitEachGesture
                    }
                    var held = HOLD_DELAY_MS
                    while (true) {
                        val fast = held >= FAST_AFTER_MS
                        step(if (fast) FAST_STEP else 1)
                        val interval = if (fast) FAST_INTERVAL_MS else SLOW_INTERVAL_MS
                        val released = withTimeoutOrNull(interval) { Release(waitForUpOrCancellation()) }
                        if (released != null) break
                        held += interval
                    }
                } finally {
                    pressed = false
                }
            }
        }
}
