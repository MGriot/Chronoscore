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
    val ChevronLeft = icon("ChevronLeft", "M15 5l-7 7 7 7")
    val ChevronRight = icon("ChevronRight", "M9 5l7 7-7 7")
    val Play = icon("Play", "M8 5.5v13l10.5-6.5z")
    val Pause = icon("Pause", "M8.5 5.5v13", "M15.5 5.5v13")
    val Timer = icon("Timer", "M12 21a8 8 0 1 1 0-16a8 8 0 1 1 0 16z", "M12 9v4l2.5 1.5", "M10 2.5h4")
    val Options = icon("Options", "M4 6h16", "M4 12h16", "M4 18h16", dots = listOf(9f to 6f, 15f to 12f, 7f to 18f))
    val Trophy = icon("Trophy", "M8 4h8v5a4 4 0 0 1-8 0z", "M8 6H5a3 3 0 0 0 3 4", "M16 6h3a3 3 0 0 1-3 4", "M12 13v4", "M8.5 20h7")
    val Export = icon("Export", "M12 15V4", "M7.5 8.5L12 4l4.5 4.5", "M5 14v5h14v-5")
    val Import = icon("Import", "M12 4v11", "M7.5 10.5L12 15l4.5-4.5", "M5 14v5h14v-5")
    val Sheet = icon("Sheet", "M4 5h16v14H4z", "M4 10h16", "M10 5v14", "M15 5v14")
    val Person = icon("Person", "M12 12a4 4 0 1 1 0-8a4 4 0 1 1 0 8z", "M4.5 20a7.5 7.5 0 0 1 15 0")
    val Check = icon("Check", "M5 12.5l4.5 4.5L19 7.5")
    val Backspace = icon("Backspace", "M9 5h11v14H9l-6-7z", "M12.5 9.5l5 5", "M17.5 9.5l-5 5")
    val Sun = icon(
        "Sun", "M12 16a4 4 0 1 1 0-8a4 4 0 1 1 0 8z",
        "M12 2.5v2", "M12 19.5v2", "M2.5 12h2", "M19.5 12h2", "M5.3 5.3l1.4 1.4", "M17.3 17.3l1.4 1.4", "M5.3 18.7l1.4-1.4", "M17.3 6.7l1.4-1.4",
    )
    val Moon = icon("Moon", "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z")
    val Crown = icon("Crown", "M4 8l4 4 4-7 4 7 4-4-1.5 10h-13z")
    val More = icon("More", dots = listOf(6f to 12f, 12f to 12f, 18f to 12f))
    val Palette = icon("Palette", "M12 3a9 9 0 1 0 0 18c1.5 0 2-1 2-2s-1-1.5-1-2.5 1-1.5 2-1.5h2a4 4 0 0 0 4-4c0-4.5-4-8-9-8z", dots = listOf(7.5f to 11f, 10f to 7f, 15f to 7.5f))

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
