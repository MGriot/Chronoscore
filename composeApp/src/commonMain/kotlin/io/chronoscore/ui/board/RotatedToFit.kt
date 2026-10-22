package io.chronoscore.ui.board

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Turns the content by [degrees] and sizes it so the turned card still fits the cell it was
 * given: 90° swaps width and height, and in-between angles get the largest card whose
 * rotated outline stays inside. Touches are mapped through the rotation, so taps still land.
 */
fun Modifier.rotatedToFit(degrees: Int): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.placeWithLayer(0, 0) { rotationZ = degrees.toFloat() } }
    }
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val (w, h) = fittedSize(width.toFloat(), height.toFloat(), degrees)
    val placeable = measurable.measure(Constraints.fixed(w.roundToInt().coerceAtLeast(0), h.roundToInt().coerceAtLeast(0)))
    layout(width, height) {
        placeable.placeWithLayer((width - placeable.width) / 2, (height - placeable.height) / 2) {
            rotationZ = degrees.toFloat()
        }
    }
}

/** Width and height of the largest card that, turned by [degrees], fits a [width] × [height] cell. */
internal fun fittedSize(width: Float, height: Float, degrees: Int): Pair<Float, Float> {
    val rad = (((degrees % 360) + 360) % 360) * PI / 180
    val c = abs(cos(rad)).toFloat()
    val s = abs(sin(rad)).toFloat()
    if (s < 1e-3f) return width to height
    if (c < 1e-3f) return height to width

    // Keep the shape of the nearer right-angle orientation, scaled down until it fits.
    val (bw, bh) = if (c >= s) width to height else height to width
    val k = min(width / (bw * c + bh * s), height / (bw * s + bh * c))
    val fallback = bw * k to bh * k

    // The card that touches all four sides, when one exists and is roomier.
    val denom = c * c - s * s
    if (abs(denom) < 1e-3f) return fallback
    val ew = (width * c - height * s) / denom
    val eh = (height * c - width * s) / denom
    return if (ew > 0 && eh > 0 && ew * eh > fallback.first * fallback.second) ew to eh else fallback
}
