package ai.anya.companion.feature.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min

/* Port of desktop imageEditReference.ts / useGeneratedImagePaint.ts. */

/** Brush radius as a fraction of the shorter image edge. */
internal enum class BrushSize(val label: String, val radius: Float) {
    Fine("细", 0.018f),
    Medium("中", 0.045f),
    Bold("粗", 0.085f),
}

/** One freehand stroke in normalized image coordinates (0–1). */
internal data class NormalizedStroke(val radius: Float, val points: List<Offset>)

internal data class NormalizedRect(val x: Float, val y: Float, val w: Float, val h: Float)

internal fun hasUsableStrokes(strokes: List<NormalizedStroke>): Boolean =
    strokes.any { it.points.isNotEmpty() && it.radius > 0f }

private fun clamp01(v: Float) = v.coerceIn(0f, 1f)

private fun normalizeRect(x: Float, y: Float, w: Float, h: Float): NormalizedRect? {
    val left = clamp01(x)
    val top = clamp01(y)
    val right = clamp01(x + w)
    val bottom = clamp01(y + h)
    val nw = right - left
    val nh = bottom - top
    if (nw < 0.02f || nh < 0.02f) return null
    return NormalizedRect(left, top, nw, nh)
}

/** Bounding box of the strokes including brush padding. */
internal fun strokeBounds(strokes: List<NormalizedStroke>): NormalizedRect? {
    var minX = 1f
    var minY = 1f
    var maxX = 0f
    var maxY = 0f
    var any = false
    for (stroke in strokes) {
        val pad = max(0f, stroke.radius)
        for (p in stroke.points) {
            any = true
            minX = min(minX, p.x - pad)
            minY = min(minY, p.y - pad)
            maxX = max(maxX, p.x + pad)
            maxY = max(maxY, p.y + pad)
        }
    }
    if (!any) return null
    return normalizeRect(minX, minY, maxX - minX, maxY - minY)
}

/** Desktop `describeRegionPlacement` → the Chinese label the draft prompt uses. */
internal fun describeRegionPlacement(bounds: NormalizedRect): String {
    val cx = bounds.x + bounds.w / 2f
    val cy = bounds.y + bounds.h / 2f
    val vertical = when {
        cy < 0.33f -> "upper"
        cy > 0.66f -> "lower"
        else -> "middle"
    }
    val horizontal = when {
        cx < 0.33f -> "left"
        cx > 0.66f -> "right"
        else -> "center"
    }
    return when {
        vertical == "middle" && horizontal == "center" -> "中间"
        horizontal == "center" -> if (vertical == "upper") "上方" else "下方"
        vertical == "middle" -> if (horizontal == "left") "左侧" else "右侧"
        vertical == "upper" -> if (horizontal == "left") "左上" else "右上"
        else -> if (horizontal == "left") "左下" else "右下"
    }
}

/** Rect the image occupies inside [viewport] when shown with ContentScale.Fit. */
internal fun fitRect(imageW: Int, imageH: Int, viewport: Size): Rect {
    if (imageW <= 0 || imageH <= 0 || viewport.width <= 0f || viewport.height <= 0f) return Rect.Zero
    val scale = min(viewport.width / imageW, viewport.height / imageH)
    val w = imageW * scale
    val h = imageH * scale
    val left = (viewport.width - w) / 2f
    val top = (viewport.height - h) / 2f
    return Rect(left, top, left + w, top + h)
}

/** Reads only the dimensions of an image file. */
internal fun imageSizeOf(path: String): Pair<Int, Int>? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, opts)
    if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
    return opts.outWidth to opts.outHeight
}

/**
 * Transparent layer over the fitted image that records freehand strokes and paints
 * them as a translucent highlight while the user is still marking.
 */
@Composable
internal fun ImagePaintOverlay(
    imageWidth: Int,
    imageHeight: Int,
    brush: BrushSize,
    strokes: MutableList<NormalizedStroke>,
    modifier: Modifier = Modifier,
) {
    var viewport by remember { mutableStateOf(Size.Zero) }
    val livePoints = remember { mutableStateListOf<Offset>() }
    fun toNormalized(p: Offset): Offset? {
        val rect = fitRect(imageWidth, imageHeight, viewport)
        if (rect.width <= 0f || rect.height <= 0f) return null
        return Offset(clamp01((p.x - rect.left) / rect.width), clamp01((p.y - rect.top) / rect.height))
    }
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(brush) {
                detectTapGestures { pos ->
                    toNormalized(pos)?.let { strokes += NormalizedStroke(brush.radius, listOf(it)) }
                }
            }
            .pointerInput(brush) {
                detectDragGestures(
                    onDragStart = { pos ->
                        livePoints.clear()
                        toNormalized(pos)?.let(livePoints::add)
                    },
                    onDrag = { change, _ -> toNormalized(change.position)?.let(livePoints::add) },
                    onDragEnd = {
                        if (livePoints.isNotEmpty()) strokes += NormalizedStroke(brush.radius, livePoints.toList())
                        livePoints.clear()
                    },
                    onDragCancel = { livePoints.clear() },
                )
            },
    ) {
        viewport = size
        val rect = fitRect(imageWidth, imageHeight, size)
        if (rect.width <= 0f) return@Canvas
        val shortEdge = min(rect.width, rect.height)
        fun drawStroke(stroke: NormalizedStroke, alpha: Float) {
            val radiusPx = max(2f, stroke.radius * shortEdge)
            val color = Color(0xFFFFD166).copy(alpha = alpha)
            if (stroke.points.size == 1) {
                val p = stroke.points.first()
                drawCircle(color, radiusPx, Offset(rect.left + p.x * rect.width, rect.top + p.y * rect.height))
                return
            }
            val path = Path()
            stroke.points.forEachIndexed { i, p ->
                val x = rect.left + p.x * rect.width
                val y = rect.top + p.y * rect.height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = radiusPx * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        strokes.forEach { drawStroke(it, 0.45f) }
        if (livePoints.isNotEmpty()) drawStroke(NormalizedStroke(brush.radius, livePoints.toList()), 0.55f)
    }
}

private const val MAX_PREVIEW_EDGE = 768

/**
 * Desktop `buildStrokeHighlightReferenceDataUrl`: dim everything outside the strokes and
 * lift the painted area so the model sees a spotlight. Returned as a JPEG data URL sized
 * for the WebSocket message.
 */
internal fun buildStrokeSpotlightDataUrl(localPath: String, strokes: List<NormalizedStroke>): String? {
    if (!hasUsableStrokes(strokes)) return null
    val source = BitmapFactory.decodeFile(localPath) ?: return null
    val scale = min(1f, MAX_PREVIEW_EDGE.toFloat() / max(source.width, source.height))
    val width = max(1, (source.width * scale).toInt())
    val height = max(1, (source.height * scale).toInt())
    val base = if (scale < 1f) Bitmap.createScaledBitmap(source, width, height, true) else source

    val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    AndroidCanvas(mask).apply {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = Paint.Style.STROKE
        }
        val shortEdge = min(width, height).toFloat()
        for (stroke in strokes) {
            if (stroke.points.isEmpty()) continue
            val radiusPx = max(2f, stroke.radius * shortEdge)
            if (stroke.points.size == 1) {
                val p = stroke.points.first()
                drawCircle(p.x * width, p.y * height, radiusPx, paint.apply { style = Paint.Style.FILL })
                paint.style = Paint.Style.STROKE
                continue
            }
            paint.strokeWidth = radiusPx * 2f
            val path = AndroidPath()
            stroke.points.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(p.x * width, p.y * height) else path.lineTo(p.x * width, p.y * height)
            }
            drawPath(path, paint)
        }
    }

    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(out)
    canvas.drawBitmap(base, 0f, 0f, null)

    // Soft brighten inside the painted region.
    val lift = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    AndroidCanvas(lift).apply {
        drawColor(android.graphics.Color.argb((0.18f * 255).toInt(), 255, 255, 255))
        drawBitmap(mask, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
    }
    canvas.drawBitmap(lift, 0f, 0f, null)

    // Dim outside the painted mask.
    val dim = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    AndroidCanvas(dim).apply {
        drawColor(android.graphics.Color.argb((0.52f * 255).toInt(), 0, 0, 0))
        drawBitmap(mask, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) })
    }
    canvas.drawBitmap(dim, 0f, 0f, null)

    val bytes = ByteArrayOutputStream().use { stream ->
        out.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        stream.toByteArray()
    }
    if (base !== source) base.recycle()
    source.recycle()
    mask.recycle()
    lift.recycle()
    dim.recycle()
    out.recycle()
    return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
}

/** True when the desktop can open this path directly (absolute Windows / POSIX path). */
internal fun isDesktopAbsolutePath(path: String): Boolean {
    val p = path.trim().removePrefix("path:")
    return Regex("^[a-zA-Z]:[\\\\/]").containsMatchIn(p) || p.startsWith("/") || p.startsWith("\\\\")
}

internal fun localFileExists(path: String?): Boolean = !path.isNullOrBlank() && File(path).isFile
