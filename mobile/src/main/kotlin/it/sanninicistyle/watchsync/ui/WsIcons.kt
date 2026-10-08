package it.sanninicistyle.watchsync.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Stroke icons drawn for WatchSync on a 24-unit grid, matching the design. */
object WsIcons {
    private fun icon(name: String, strokeWidth: Float, vararg paths: String, filled: Boolean = false) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { data ->
                addPath(
                    pathData = addPathNodes(data),
                    fill = if (filled) SolidColor(Color.Black) else null,
                    stroke = if (filled) null else SolidColor(Color.Black),
                    strokeLineWidth = strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Info = icon("Info", 2f, "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0z", "M12 11v5.5", "M12 7.6v.1")
    val Dnd = icon("Dnd", 2f, "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0z", "M7.5 12h9")
    val Moon = icon("Moon", 2f, "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z")
    val Watch = icon("Watch", 1.8f, "M18 12a6 6 0 1 1-12 0a6 6 0 1 1 12 0z", "M9 6.8l1-3.3h4l1 3.3M9 17.2l1 3.3h4l1-3.3", "M12 12V9.5")
    val ArrowForward = icon("ArrowForward", 2.2f, "M5 12h14M13 6l6 6-6 6")
    val Back = icon("Back", 2f, "M15 6l-6 6 6 6")
    val Check = icon("Check", 3f, "M5 12l4 4 10-10")
    val Snooze = icon("Snooze", 2f, "M20 13a8 8 0 1 1-16 0a8 8 0 1 1 16 0z", "M12 9v4l2.5 2.5M9 2h6")
    val Stop = icon("Stop", 0f, "M9 6h6a3 3 0 0 1 3 3v6a3 3 0 0 1-3 3H9a3 3 0 0 1-3-3V9a3 3 0 0 1 3-3z", filled = true)
    val Language = icon("Language", 1.8f, "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0z", "M3 12h18", "M12 3a14 14 0 0 1 0 18a14 14 0 0 1 0-18z")
    val Settings = icon(
        "Settings", 1.8f,
        "M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0z",
        "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z",
    )
}
