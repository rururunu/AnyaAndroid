package ai.anya.companion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.delay

/** The animated part of launch. The platform starting window uses the matching static eyes. */
@Composable
internal fun MascotSplash(darkTheme: Boolean, onFinished: () -> Unit) {
    val blink = remember { Animatable(1f) }
    val look = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(180)
        blink.animateTo(0.08f, tween(110))
        blink.animateTo(1f, tween(150))
        look.animateTo(-1f, tween(300, easing = FastOutSlowInEasing))
        delay(140)
        look.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        delay(120)
        look.animateTo(0f, tween(250, easing = FastOutSlowInEasing))
        blink.animateTo(0.08f, tween(100))
        blink.animateTo(1f, tween(140))
        onFinished()
    }

    val background = if (darkTheme) Color(0xFF171717) else Color.White
    val eyeColor = if (darkTheme) Color(0xFFF8F8F8) else Color(0xFF242424)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(background)
            drawEyes(eyeColor, blink.value, look.value)
        }
    }
}

private fun DrawScope.drawEyes(color: Color, blink: Float, look: Float) {
    val density = this.density
    val eyeWidth = 16f * density
    val eyeHeight = 40f * density * blink
    val spacing = 46f * density
    val shift = look * 8f * density
    val center = Offset(size.width / 2f, size.height / 2f)
    for (side in listOf(-1f, 1f)) {
        drawRoundRect(
            color = color,
            topLeft = Offset(center.x + side * spacing / 2f - eyeWidth / 2f + shift, center.y - eyeHeight / 2f),
            size = Size(eyeWidth, eyeHeight),
            cornerRadius = CornerRadius(eyeWidth / 2f),
        )
    }
}
