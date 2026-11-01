package app.familyvault.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.familyvault.theme.FV

// Same shapes as design/logo/*.svg and the launcher icon: a house with a play button ("home videos").
private const val HOUSE = "M51.4,31.1 Q54,29 56.6,31.1 L78.6,49.2 Q80,50.4 80,52.3 L80,74 Q80,79 75,79 L33,79 Q28,79 28,74 L28,52.3 Q28,50.4 29.4,49.2 Z"
private const val PLAY = "M49.5,54 L49.5,70 L63.5,62 Z"

/** The mark in one colour with the play button cut out. */
fun appMark(color: Color): ImageVector = ImageVector.Builder("AppMark", 54.dp, 52.dp, 54f, 52f).apply {
    addGroup(translationX = -27f, translationY = -28f)
    addPath(addPathNodes("$HOUSE $PLAY"), fill = SolidColor(color), pathFillType = PathFillType.EvenOdd)
    clearGroup()
}.build()

/** The app icon: white mark on a primary rounded square. */
@Composable
fun AppLogo(size: Dp = 64.dp) {
    val c = FV.colors
    val mark = remember(c.onPrimary) { appMark(c.onPrimary) }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(c.primary),
        contentAlignment = Alignment.Center,
    ) {
        Image(mark, contentDescription = null, modifier = Modifier.size(size * 0.56f))
    }
}
