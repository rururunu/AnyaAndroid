package ai.anya.companion.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min

/**
 * iOS-style continuous corners (SwiftUI `RoundedRectangle(..., style: .continuous)`).
 * The curve starts further along the edge than a circular arc, so large radii
 * look closer to a squircle than Material's geometric [androidx.compose.foundation.shape.RoundedCornerShape].
 */
@Immutable
public class ContinuousRoundedCornerShape(
    private val radius: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val corner = with(density) { radius.toPx() }
        return Outline.Generic(continuousRoundedRectPath(size, corner))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContinuousRoundedCornerShape) return false
        return radius == other.radius
    }

    override fun hashCode(): Int = radius.hashCode()
}

private fun continuousRoundedRectPath(size: Size, cornerRadius: Float): Path {
    val width = size.width
    val height = size.height
    val radius = cornerRadius.coerceIn(0f, min(width, height) / 2f)
    if (radius < 0.5f) {
        return Path().apply { addRect(Rect(0f, 0f, width, height)) }
    }
    // Apple continuous-corner approximation: the tangent sits past `radius`
    // and the cubic uses a squircle-like handle (longer than 0.55 circular).
    val extend = radius * 1.528665f
    val handle = extend * 0.553426f
    val limit = min(width, height) / 2f
    val edge = extend.coerceAtMost(limit)
    val ctrl = handle.coerceAtMost(edge)

    return Path().apply {
        moveTo(edge, 0f)
        lineTo(width - edge, 0f)
        cubicTo(width - edge + ctrl, 0f, width, edge - ctrl, width, edge)
        lineTo(width, height - edge)
        cubicTo(width, height - edge + ctrl, width - edge + ctrl, height, width - edge, height)
        lineTo(edge, height)
        cubicTo(edge - ctrl, height, 0f, height - edge + ctrl, 0f, height - edge)
        lineTo(0f, edge)
        cubicTo(0f, edge - ctrl, edge - ctrl, 0f, edge, 0f)
        close()
    }
}
