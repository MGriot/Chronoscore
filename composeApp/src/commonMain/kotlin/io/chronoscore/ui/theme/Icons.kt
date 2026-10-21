package io.chronoscore.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Line icons on a 24 grid with a 1.75 stroke (UI kit · Foundations · Icons). Tint with Icon(). */
object ChronoIcons {
    val Undo = icon("Undo", "M9 14L4 9l5-5", "M4 9h10.5a5.5 5.5 0 0 1 0 11H11")
    val Dice = icon(
        "Dice", "M6 3h12a3 3 0 0 1 3 3v12a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V6a3 3 0 0 1 3-3z",
        dots = listOf(8.5f to 8.5f, 15.5f to 8.5f, 12f to 12f, 8.5f to 15.5f, 15.5f to 15.5f),
    )
    val Log = icon(
        "Log", "M9 6h11", "M9 12h11", "M9 18h11",
        dots = listOf(4.5f to 6f, 4.5f to 12f, 4.5f to 18f),
    )
    val Menu = icon(
        "Menu", "M3 7h9", "M18 7h3", "M3 17h3", "M12 17h9",
        "M18 7a3 3 0 1 1-6 0a3 3 0 1 1 6 0z", "M12 17a3 3 0 1 1-6 0a3 3 0 1 1 6 0z",
    )
    val Rotate = icon("Rotate", "M20 12a8 8 0 1 1-2.34-5.66L20 8.5", "M20 3.5v5h-5")
    val Keypad = icon("Keypad", "M4 9h16", "M4 15h16", "M10 3L8 21", "M16 3l-2 18")
    val Edit = icon("Edit", "M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4z", "M14.5 5.5l3 3")
    val Plus = icon("Plus", "M12 5v14", "M5 12h14")
    val Minus = icon("Minus", "M5 12h14")
    val Close = icon("Close", "M18 6L6 18", "M6 6l12 12")
    val Grid = icon(
        "Grid", "M4 5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1z",
        "M14 5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1h-4a1 1 0 0 1-1-1z",
        "M4 15a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1z",
        "M14 15a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1h-4a1 1 0 0 1-1-1z",
    )
    val ArrowRight = icon("ArrowRight", "M5 12h14", "M13 6l6 6-6 6")
    val Library = icon("Library", "M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18z", "M12 7.5V12l3 2")
    val Trash = icon("Trash", "M4 7h16", "M10 11v6", "M14 11v6", "M6 7l1 13h10l1-13", "M9 7V4h6v3")
    val Check = icon("Check", "M5 12.5l4.5 4.5L19 7.5")

    private fun icon(name: String, vararg paths: String, dots: List<Pair<Float, Float>> = emptyList()): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.75f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            dots.forEach { (x, y) ->
                addPath(
                    pathData = addPathNodes("M${x - 1.1f} ${y}a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0-2.2 0z"),
                    fill = SolidColor(Color.Black),
                )
            }
        }.build()
}
