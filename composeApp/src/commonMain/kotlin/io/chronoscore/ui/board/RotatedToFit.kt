package io.chronoscore.ui.board

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** |cos| and |sin| of [degrees]. */
private fun absCosSin(degrees: Int): Pair<Float, Float> {
    val rad = (((degrees % 360) + 360) % 360) * PI / 180
    return abs(cos(rad)).toFloat() to abs(sin(rad)).toFloat()
}

/** True when [degrees] is not a multiple of 90, so the card sits at a slant in its cell. */
internal fun isTilted(degrees: Int): Boolean = degrees % 90 != 0

/** Width and height of the largest card that, turned by [degrees], fits a [width] × [height] cell. */
internal fun fittedSize(width: Float, height: Float, degrees: Int): Pair<Float, Float> {
    val (c, s) = absCosSin(degrees)
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

/** The largest card [aspect] times wider than tall that, turned by [degrees], fits the cell. */
internal fun fittedSize(width: Float, height: Float, degrees: Int, aspect: Float): Pair<Float, Float> {
    val (c, s) = absCosSin(degrees)
    val k = min(width / (aspect * c + s), height / (aspect * s + c))
    return aspect * k to k
}

/**
 * The tilted card that gives the most room by [quality] (e.g. the score size it allows): the
 * cell's own shape is tried, then a few wider and squarer ones, since a short score in a narrow
 * slanted cell often reads larger on a wide card.
 */
internal fun bestTiltedSize(width: Float, height: Float, degrees: Int, quality: (Float, Float) -> Float): Pair<Float, Float> =
    (listOf(fittedSize(width, height, degrees)) + TiltAspects.map { fittedSize(width, height, degrees, it) })
        .maxBy { (w, h) -> quality(w, h) }

private val TiltAspects = listOf(0.6f, 0.8f, 1f, 1.25f, 1.6f, 2f)

/**
 * Width and height of the smallest card that, turned by [degrees], still covers the whole
 * [width] × [height] cell; its corners stick out and are clipped by the cell.
 */
internal fun coverSize(width: Float, height: Float, degrees: Int): Pair<Float, Float> {
    val (c, s) = absCosSin(degrees)
    return width * c + height * s to width * s + height * c
}

/** Cell corners, in the order of [freeCorners]'s result. */
internal enum class CellCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT }

/**
 * Two opposite cell corners where a [side] × [side] square stays clear of a [boxW] × [boxH]
 * card turned by [degrees] at the cell's centre, or null when no diagonal pair has room.
 */
internal fun freeCorners(width: Float, height: Float, degrees: Int, boxW: Float, boxH: Float, side: Float): Pair<CellCorner, CellCorner>? {
    val rad = degrees * PI / 180
    val c = cos(rad).toFloat()
    val s = sin(rad).toFloat()
    // Card corners on screen, around the cell's centre (y grows downwards, so +degrees is clockwise).
    val card = listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f).map { (u, v) ->
        val x = u * boxW / 2
        val y = v * boxH / 2
        (x * c - y * s) to (x * s + y * c)
    }
    fun square(corner: CellCorner): List<Pair<Float, Float>> {
        val x0 = if (corner == CellCorner.TOP_LEFT || corner == CellCorner.BOTTOM_LEFT) -width / 2 else width / 2 - side
        val y0 = if (corner == CellCorner.TOP_LEFT || corner == CellCorner.TOP_RIGHT) -height / 2 else height / 2 - side
        return listOf(x0 to y0, x0 + side to y0, x0 + side to y0 + side, x0 to y0 + side)
    }
    fun free(corner: CellCorner) = !overlaps(card, square(corner))
    return when {
        free(CellCorner.TOP_RIGHT) && free(CellCorner.BOTTOM_LEFT) -> CellCorner.TOP_RIGHT to CellCorner.BOTTOM_LEFT
        free(CellCorner.TOP_LEFT) && free(CellCorner.BOTTOM_RIGHT) -> CellCorner.BOTTOM_RIGHT to CellCorner.TOP_LEFT
        else -> null
    }
}

/** Separating-axis test for two convex polygons. */
private fun overlaps(a: List<Pair<Float, Float>>, b: List<Pair<Float, Float>>): Boolean {
    for (poly in listOf(a, b)) {
        for (i in poly.indices) {
            val (px, py) = poly[i]
            val (qx, qy) = poly[(i + 1) % poly.size]
            val ax = qy - py
            val ay = px - qx
            val pa = a.map { (x, y) -> x * ax + y * ay }
            val pb = b.map { (x, y) -> x * ax + y * ay }
            if (pa.max() <= pb.min() || pb.max() <= pa.min()) return false
        }
    }
    return true
}
