package ai.anya.companion.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Anya brand-aligned icons (circular message bubble matches desktop mark). */
public object AnyaIcons {
    public val ChatCircle: ImageVector
        get() {
            if (_chatCircle != null) return _chatCircle!!
            _chatCircle = ImageVector.Builder(
                name = "Anya.ChatCircle",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    // Circular bubble + soft tail (aligned with desktop brand mark).
                    moveTo(12f, 3.2f)
                    curveTo(7.31f, 3.2f, 3.5f, 7.01f, 3.5f, 11.7f)
                    curveTo(3.5f, 14.55f, 4.95f, 17.08f, 7.18f, 18.58f)
                    lineTo(5.42f, 21.72f)
                    curveTo(5.1f, 22.29f, 5.74f, 22.9f, 6.33f, 22.55f)
                    lineTo(10.55f, 20.12f)
                    curveTo(11.02f, 20.2f, 11.5f, 20.25f, 12f, 20.25f)
                    curveTo(16.69f, 20.25f, 20.5f, 16.44f, 20.5f, 11.75f)
                    curveTo(20.5f, 7.06f, 16.69f, 3.2f, 12f, 3.2f)
                    close()
                }
            }.build()
            return _chatCircle!!
        }

    public val ChatCircleOutline: ImageVector
        get() {
            if (_chatCircleOutline != null) return _chatCircleOutline!!
            _chatCircleOutline = ImageVector.Builder(
                name = "Anya.ChatCircleOutline",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(12f, 4.1f)
                    curveTo(7.86f, 4.1f, 4.5f, 7.46f, 4.5f, 11.6f)
                    curveTo(4.5f, 14.1f, 5.75f, 16.32f, 7.7f, 17.62f)
                    lineTo(6.35f, 20.2f)
                    curveTo(6.18f, 20.52f, 6.52f, 20.86f, 6.85f, 20.68f)
                    lineTo(10.2f, 18.82f)
                    curveTo(10.78f, 18.95f, 11.38f, 19.02f, 12f, 19.02f)
                    curveTo(16.14f, 19.02f, 19.5f, 15.66f, 19.5f, 11.52f)
                    curveTo(19.5f, 7.38f, 16.14f, 4.1f, 12f, 4.1f)
                    close()
                }
            }.build()
            return _chatCircleOutline!!
        }

    /** Four-point star matching desktop composer send icon (filled for mobile clarity). */
    public val StarFour: ImageVector
        get() {
            if (_starFour != null) return _starFour!!
            _starFour = ImageVector.Builder(
                name = "Anya.StarFour",
                defaultWidth = 16.dp,
                defaultHeight = 16.dp,
                viewportWidth = 16f,
                viewportHeight = 16f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(8f, 2.25f)
                    lineTo(9.35f, 6.15f)
                    lineTo(13.25f, 7.5f)
                    lineTo(9.35f, 8.85f)
                    lineTo(8f, 12.75f)
                    lineTo(6.65f, 8.85f)
                    lineTo(2.75f, 7.5f)
                    lineTo(6.65f, 6.15f)
                    close()
                }
            }.build()
            return _starFour!!
        }

    /** Lucide `shield` — desktop approvalIcon "auto" tier (same stroke style as the two below). */
    public val Shield: ImageVector
        get() {
            if (_shield != null) return _shield!!
            _shield = ImageVector.Builder(
                name = "Anya.Shield",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    // Transcribed 1:1 from lucide `shield` (desktop uses the same icon).
                    moveTo(20f, 13f)
                    curveToRelative(0f, 5f, -3.5f, 7.5f, -7.66f, 8.95f)
                    arcToRelative(1f, 1f, 0f, false, true, -0.67f, -0.01f)
                    curveTo(7.5f, 20.5f, 4f, 18f, 4f, 13f)
                    verticalLineTo(6f)
                    arcToRelative(1f, 1f, 0f, false, true, 1f, -1f)
                    curveToRelative(2f, 0f, 4.5f, -1.2f, 6.24f, -2.72f)
                    arcToRelative(1.17f, 1.17f, 0f, false, true, 1.52f, 0f)
                    curveTo(14.51f, 3.81f, 17f, 5f, 19f, 5f)
                    arcToRelative(1f, 1f, 0f, false, true, 1f, 1f)
                    close()
                }
            }.build()
            return _shield!!
        }

    /** Lucide `shield-question-mark` — desktop approvalIcon "ask" tier. */
    public val ShieldQuestion: ImageVector
        get() {
            if (_shieldQuestion != null) return _shieldQuestion!!
            _shieldQuestion = ImageVector.Builder(
                name = "Anya.ShieldQuestion",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(20f, 13f)
                    curveToRelative(0f, 5f, -3.5f, 7.5f, -7.66f, 8.95f)
                    arcToRelative(1f, 1f, 0f, false, true, -0.67f, -0.01f)
                    curveTo(7.5f, 20.5f, 4f, 18f, 4f, 13f)
                    verticalLineTo(6f)
                    arcToRelative(1f, 1f, 0f, false, true, 1f, -1f)
                    curveToRelative(2f, 0f, 4.5f, -1.2f, 6.24f, -2.72f)
                    arcToRelative(1.17f, 1.17f, 0f, false, true, 1.52f, 0f)
                    curveTo(14.51f, 3.81f, 17f, 5f, 19f, 5f)
                    arcToRelative(1f, 1f, 0f, false, true, 1f, 1f)
                    close()
                }
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(9.1f, 9f)
                    arcToRelative(3f, 3f, 0f, false, true, 5.82f, 1f)
                    curveToRelative(0f, 2f, -3f, 3f, -3f, 3f)
                }
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(12f, 17f)
                    horizontalLineToRelative(0.01f)
                }
            }.build()
            return _shieldQuestion!!
        }

    /** Lucide `shield-off` — desktop approvalIcon "alwaysAllow" tier. */
    public val ShieldOff: ImageVector
        get() {
            if (_shieldOff != null) return _shieldOff!!
            _shieldOff = ImageVector.Builder(
                name = "Anya.ShieldOff",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveToRelative(2f, 2f)
                    lineToRelative(20f, 20f)
                }
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(5f, 5f)
                    arcToRelative(1f, 1f, 0f, false, false, -1f, 1f)
                    verticalLineToRelative(7f)
                    curveToRelative(0f, 5f, 3.5f, 7.5f, 7.67f, 8.94f)
                    arcToRelative(1f, 1f, 0f, false, false, 0.67f, 0.01f)
                    curveToRelative(2.35f, -0.82f, 4.48f, -1.97f, 5.9f, -3.71f)
                }
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(9.309f, 3.652f)
                    arcTo(12.252f, 12.252f, 0f, false, false, 11.24f, 2.28f)
                    arcToRelative(1.17f, 1.17f, 0f, false, true, 1.52f, 0f)
                    curveTo(14.51f, 3.81f, 17f, 5f, 19f, 5f)
                    arcToRelative(1f, 1f, 0f, false, true, 1f, 1f)
                    verticalLineToRelative(7f)
                    arcToRelative(9.784f, 9.784f, 0f, false, true, -0.08f, 1.264f)
                }
            }.build()
            return _shieldOff!!
        }

    private var _chatCircle: ImageVector? = null
    private var _chatCircleOutline: ImageVector? = null
    private var _starFour: ImageVector? = null
    private var _shield: ImageVector? = null
    private var _shieldQuestion: ImageVector? = null
    private var _shieldOff: ImageVector? = null
}
