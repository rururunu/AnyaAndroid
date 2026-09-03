package ai.anya.companion.core.designsystem.component

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ai.anya.companion.core.designsystem.haptic.rememberAnyaHaptics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Facial state, roughly mirroring an agent turn (same set as the desktop mascot):
 * idle (blinks, wanders) / thinking (eyes drift up) / working (eyes scan) /
 * talking (eyes pulse) / waiting (wide round eyes) / done (happy squint) / error (> < eyes).
 */
public enum class MascotState { Idle, Thinking, Working, Talking, Waiting, Done, Error }

/** Items the mascot can carry; callers map app state (e.g. chat mode) onto these. */
public enum class MascotTool { Phone, Wrench, Clipboard, Brush }

private enum class EyeKind { Pill, Round, Smile, Ouch }

private fun MascotState.eyeKind(): EyeKind = when (this) {
    MascotState.Idle, MascotState.Thinking, MascotState.Working, MascotState.Talking -> EyeKind.Pill
    MascotState.Waiting -> EyeKind.Round
    MascotState.Done -> EyeKind.Smile
    MascotState.Error -> EyeKind.Ouch
}

/* SVG design space: the desktop asset is authored in a 512 grid and shown through viewBox 64 76 384 384. */
private const val VIEW_X = 64f
private const val VIEW_Y = 76f
private const val VIEW_SIZE = 384f
private const val MAX_DX = 30f
private const val MAX_DY = 22f
private const val GAZE_SOFTNESS_DP = 140f

private const val TURN_MS = 600
private const val SETTLE_MS = 450L
private const val MIN_GAP_MS = 1400L
private const val PLEASED_MS = 1800L
private const val WIGGLE_MS = 720

private val EYE_L = Offset(208f, 290f)
private val EYE_R = Offset(304f, 290f)

/**
 * The blob mascot's face: two pill eyes that look at [lookAt] (window coordinates) or the
 * finger while it rests on the face, blink and wander when idle, change expression with
 * [state] (turning around to swap faces), and carry [tool] behind the lower right.
 * Tapping an [interactive] face makes it beam for a moment.
 */
@Composable
public fun MascotFace(
    modifier: Modifier = Modifier,
    state: MascotState = MascotState.Idle,
    tool: MascotTool? = null,
    lookAt: Offset? = null,
    followPointer: Boolean = true,
    interactive: Boolean = false,
    turnOnChange: Boolean = true,
    busy: Boolean = false,
    bodyColor: Color = MaterialTheme.colorScheme.onBackground,
    faceColor: Color = MaterialTheme.colorScheme.background,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = rememberAnyaHaptics()
    val reduceMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    val scope = rememberCoroutineScope()

    /* ---- expression + turn ---- */
    var shownState by remember { mutableStateOf(state) }
    val turn = remember { Animatable(0f) }
    var turning by remember { mutableStateOf(false) }
    var lastTurnEnd by remember { mutableLongStateOf(0L) }
    val latestState by rememberUpdatedState(state)

    LaunchedEffect(turnOnChange, reduceMotion) {
        while (isActive) {
            snapshotFlow { latestState != shownState }.first { it }
            if (!turnOnChange || reduceMotion) {
                shownState = latestState
                continue
            }
            // Agent turns flip state quickly (tool start/stop, chunks); a change must hold
            // for SETTLE_MS and turns are spaced by MIN_GAP_MS or the face would flicker.
            val sinceLast = System.currentTimeMillis() - lastTurnEnd
            delay(maxOf(SETTLE_MS, MIN_GAP_MS - sinceLast))
            if (latestState == shownState) continue // flipped back before it settled
            turning = true
            turn.snapTo(0f)
            val swap = launch {
                delay(TURN_MS / 2L)
                shownState = latestState // new face appears while turned away
            }
            turn.animateTo(1f, tween(TURN_MS, easing = LinearEasing))
            swap.cancel()
            shownState = latestState
            turning = false
            lastTurnEnd = System.currentTimeMillis()
        }
    }

    /* ---- click: beam for a moment ---- */
    var pleased by remember { mutableStateOf(false) }
    val wiggle = remember { Animatable(1f) }
    var pleasedJob by remember { mutableStateOf<Job?>(null) }
    val tilt by animateFloatAsState(
        targetValue = if (pleased && !reduceMotion) -6f else 0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "mascotTilt",
    )

    val visualState = when {
        pleased -> MascotState.Done
        busy && shownState == MascotState.Idle -> MascotState.Working
        else -> shownState
    }

    /* ---- eye variant cross-fade ---- */
    val morph = remember { Animatable(1f) }
    var prevKind by remember { mutableStateOf(visualState.eyeKind()) }
    var curKind by remember { mutableStateOf(visualState.eyeKind()) }
    LaunchedEffect(visualState) {
        val next = visualState.eyeKind()
        if (next == curKind) return@LaunchedEffect
        prevKind = curKind
        curKind = next
        if (reduceMotion) {
            morph.snapTo(1f)
        } else {
            morph.snapTo(0f)
            morph.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 700f))
        }
    }

    /* ---- blink ---- */
    val blink = remember { Animatable(1f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        delay(1200)
        while (isActive) {
            blink.animateTo(0.08f, tween(70, easing = LinearEasing))
            blink.animateTo(1f, tween(90, easing = LinearEasing))
            if (Random.nextFloat() < 0.2f) {
                delay(160)
                blink.animateTo(0.08f, tween(70, easing = LinearEasing))
                blink.animateTo(1f, tween(90, easing = LinearEasing))
            }
            delay(2400 + Random.nextLong(2800))
        }
    }

    /* ---- gaze ---- */
    var bounds by remember { mutableStateOf<Rect?>(null) }
    var pointer by remember { mutableStateOf<Offset?>(null) }
    val gaze = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val softnessPx = with(density) { GAZE_SOFTNESS_DP.dp.toPx() }
    val target: Offset? = if (pleased) null else lookAt ?: pointer
    LaunchedEffect(target, bounds, pleased, visualState) {
        val rect = bounds
        if (target == null || rect == null) {
            if (target == null && !pleased && visualState == MascotState.Idle && !reduceMotion) {
                // Nobody to look at: wander a little now and then, like glancing around.
                while (isActive) {
                    delay(2600 + Random.nextLong(3200))
                    val wander = Offset(
                        (Random.nextFloat() * 2f - 1f) * MAX_DX * 0.6f,
                        (Random.nextFloat() * 2f - 1f) * MAX_DY * 0.5f,
                    )
                    gaze.animateTo(wander, tween(520, easing = FastOutSlowInEasing))
                    delay(900 + Random.nextLong(1400))
                    gaze.animateTo(Offset.Zero, tween(460, easing = FastOutSlowInEasing))
                }
            } else {
                gaze.animateTo(Offset.Zero, tween(220, easing = FastOutSlowInEasing))
            }
            return@LaunchedEffect
        }
        val dx = target.x - rect.center.x
        val dy = target.y - rect.center.y
        val dist = hypot(dx, dy)
        val next = if (dist < 1f) {
            Offset.Zero
        } else {
            // Soft saturation: near targets move the eyes a little, far ones up to the max.
            val k = dist / (dist + softnessPx)
            Offset(dx / dist * k * MAX_DX, dy / dist * k * MAX_DY)
        }
        gaze.animateTo(next, tween(180, easing = FastOutSlowInEasing))
    }

    /* ---- carried tool: pop out / in ---- */
    var shownTool by remember { mutableStateOf(tool) }
    val toolPop = remember { Animatable(if (tool != null) 1f else 0f) }
    LaunchedEffect(tool, reduceMotion) {
        if (tool == shownTool) {
            if (tool != null && toolPop.value < 1f) toolPop.snapTo(1f)
            return@LaunchedEffect
        }
        if (reduceMotion) {
            shownTool = tool
            toolPop.snapTo(if (tool != null) 1f else 0f)
            return@LaunchedEffect
        }
        if (shownTool != null) toolPop.animateTo(0f, tween(160, easing = FastOutSlowInEasing))
        shownTool = tool
        if (tool != null) toolPop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 600f))
    }

    /* ---- shared clock for the looping animations (only while something loops) ---- */
    val clock = remember { mutableLongStateOf(0L) }
    val needsClock = !reduceMotion &&
        (visualState != MascotState.Idle || shownTool != null || turning || wiggle.isRunning)
    LaunchedEffect(needsClock) {
        if (!needsClock) return@LaunchedEffect
        while (isActive) {
            withFrameMillis { clock.longValue = it }
        }
    }

    val paths = remember { MascotPaths() }

    fun handleClick() {
        onClick?.invoke()
        if (!interactive) return
        haptic.mascotPleased()
        pleased = true
        pleasedJob?.cancel()
        pleasedJob = scope.launch {
            delay(PLEASED_MS)
            pleased = false
        }
        if (!reduceMotion) {
            scope.launch {
                wiggle.snapTo(0f)
                wiggle.animateTo(1f, tween(WIGGLE_MS, easing = LinearEasing))
            }
        }
    }

    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics {
            this.contentDescription = contentDescription
            if (interactive || onClick != null) role = Role.Image
        }
    } else {
        Modifier
    }
    val clickModifier = if (interactive || onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { handleClick() },
        )
    } else {
        Modifier
    }
    val followModifier = if (followPointer) {
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull()
                    val rect = bounds
                    if (change == null || rect == null) continue
                    pointer = if (change.pressed) {
                        Offset(rect.left + change.position.x, rect.top + change.position.y)
                    } else {
                        null
                    }
                }
            }
        }
    } else {
        Modifier
    }

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .then(semanticsModifier)
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                bounds = Rect(pos, Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
            }
            .then(followModifier)
            .then(clickModifier),
    ) {
        val now = clock.longValue
        val unit = min(size.width, size.height) / VIEW_SIZE
        val originX = (size.width - VIEW_SIZE * unit) / 2f
        val originY = (size.height - VIEW_SIZE * unit) / 2f

        // Wiggle (click) keyframes: rotate / scale around the body centre.
        val w = wiggle.value
        val (wigRot, wigSx, wigSy) = if (w >= 1f) {
            Triple(0f, 1f, 1f)
        } else {
            keyframes3(
                w,
                floatArrayOf(0f, 0.18f, 0.42f, 0.68f, 1f),
                floatArrayOf(0f, -7f, 6f, -3f, 0f),
                floatArrayOf(1f, 1.05f, 0.97f, 1.01f, 1f),
                floatArrayOf(1f, 0.95f, 1.03f, 0.99f, 1f),
            )
        }

        // Turn-around keyframes: body bounces, the face slides to the side and back.
        val p = if (turning) turn.value else 0f
        val (bodyTy, bodySx, bodySy) = if (!turning) {
            Triple(0f, 1f, 1f)
        } else {
            keyframes3(
                p,
                floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f),
                floatArrayOf(0f, -8f, -11f, -8f, 0f),
                floatArrayOf(1f, 0.97f, 1f, 0.97f, 1f),
                floatArrayOf(1f, 1.02f, 1f, 1.02f, 1f),
            )
        }
        var faceTx = 0f
        var faceSx = 1f
        var faceAlpha = 1f
        if (turning) {
            // Face = point on a sphere: x = R*sin(a), width = cos(a); hidden on the back side.
            val a: Float
            val dir: Float
            when {
                p <= 1f / 3f -> { a = p * 3f * 90f; dir = 1f }
                p >= 2f / 3f -> { a = (1f - p) * 3f * 90f; dir = -1f }
                else -> { a = 90f; dir = 0f }
            }
            val rad = a * PI.toFloat() / 180f
            faceTx = dir * 150f * sin(rad)
            faceSx = cos(rad).coerceAtLeast(0f)
            faceAlpha = when {
                a <= 45f -> 1f
                a >= 75f -> 0f
                else -> 1f - (a - 45f) / 30f
            }
        }

        withTransform({
            translate(originX, originY)
            scale(unit, unit, pivot = Offset.Zero)
            translate(-VIEW_X, -VIEW_Y)
            rotate(tilt + wigRot, pivot = Offset(256f, 300f))
            scale(wigSx, wigSy, pivot = Offset(256f, 300f))
        }) {
            withTransform({
                translate(0f, bodyTy)
                scale(bodySx, bodySy, pivot = Offset(256f, 268f))
            }) {
                val activeTool = shownTool
                val pop = toolPop.value
                if (activeTool != null && pop > 0.01f) {
                    // Sway as if being waved: rotate -4..5deg over 3.2s.
                    val bob = -4f + 9f * easeInOutLoop(now, 3200)
                    drawTool(paths, activeTool, pop, bob, bodyColor, faceColor)
                }

                drawPath(paths.body, bodyColor)

                withTransform({
                    translate(faceTx, 0f)
                    scale(faceSx, 1f, pivot = Offset(256f, 290f))
                }) {
                    if (faceAlpha > 0.01f) {
                        val stateOffset = when (visualState) {
                            MascotState.Thinking -> {
                                val t = easeInOutLoop(now, 3600)
                                Offset(28f + 8f * t, -30f + 8f * t)
                            }
                            MascotState.Working -> {
                                val t = easeInOutLoop(now, 1600)
                                Offset(-34f + 68f * t, 14f)
                            }
                            MascotState.Error -> {
                                val tri = ((now % 200L) / 100f).let { if (it > 1f) 2f - it else it }
                                Offset(-2f + 4f * tri, 0f)
                            }
                            else -> Offset.Zero
                        }
                        val eyes = gaze.value + stateOffset
                        val squint = if (visualState == MascotState.Thinking || visualState == MascotState.Working) 0.62f else 1f
                        val talkL: Float
                        val talkR: Float
                        if (visualState == MascotState.Talking) {
                            talkL = talkScale((now % 420L) / 420f)
                            talkR = talkScale(((now + 200L) % 420L) / 420f)
                        } else {
                            talkL = 1f
                            talkR = 1f
                        }
                        val face = faceColor.copy(alpha = faceColor.alpha * faceAlpha)
                        val glint = bodyColor.copy(alpha = bodyColor.alpha * faceAlpha)
                        val m = morph.value
                        translate(eyes.x, eyes.y) {
                            drawEye(paths, EYE_L, left = true, curKind, prevKind, m, blink.value * squint * talkL, face, glint)
                            drawEye(paths, EYE_R, left = false, curKind, prevKind, m, blink.value * squint * talkR, face, glint)
                        }
                    }
                }
            }
        }
    }
}

/* ---------------- drawing helpers ---------------- */

private fun easeInOutLoop(nowMs: Long, periodMs: Int): Float {
    val phase = (nowMs % periodMs) / periodMs.toFloat()
    return 0.5f - 0.5f * cos(2f * PI.toFloat() * phase)
}

/** Talking pulse: 0->1, 0.3->0.42, 0.55->1.12, 0.8->0.7, 1->1. */
private fun talkScale(t: Float): Float {
    val ks = floatArrayOf(0f, 0.3f, 0.55f, 0.8f, 1f)
    val vs = floatArrayOf(1f, 0.42f, 1.12f, 0.7f, 1f)
    return piecewise(t, ks, vs)
}

private fun piecewise(t: Float, keys: FloatArray, values: FloatArray): Float {
    if (t <= keys.first()) return values.first()
    if (t >= keys.last()) return values.last()
    for (i in 1 until keys.size) {
        if (t <= keys[i]) {
            val span = keys[i] - keys[i - 1]
            val local = if (span <= 0f) 1f else (t - keys[i - 1]) / span
            val eased = 0.5f - 0.5f * cos(local * PI.toFloat())
            return values[i - 1] + (values[i] - values[i - 1]) * eased
        }
    }
    return values.last()
}

private fun keyframes3(
    t: Float,
    keys: FloatArray,
    a: FloatArray,
    b: FloatArray,
    c: FloatArray,
): Triple<Float, Float, Float> = Triple(piecewise(t, keys, a), piecewise(t, keys, b), piecewise(t, keys, c))

private fun DrawScope.drawEye(
    paths: MascotPaths,
    center: Offset,
    left: Boolean,
    kind: EyeKind,
    prevKind: EyeKind,
    morph: Float,
    pillScaleY: Float,
    face: Color,
    glint: Color,
) {
    translate(center.x, center.y) {
        if (morph < 1f && prevKind != kind) {
            val fade = 1f - morph
            drawEyeVariant(paths, prevKind, left, pillScaleY, face.copy(alpha = face.alpha * fade), glint.copy(alpha = glint.alpha * fade), 1f)
        }
        val grow = if (kind == EyeKind.Pill) 1f else 0.6f + 0.4f * morph
        drawEyeVariant(paths, kind, left, pillScaleY, face.copy(alpha = face.alpha * morph), glint.copy(alpha = glint.alpha * morph), grow)
    }
}

private fun DrawScope.drawEyeVariant(
    paths: MascotPaths,
    kind: EyeKind,
    left: Boolean,
    pillScaleY: Float,
    face: Color,
    glint: Color,
    grow: Float,
) {
    when (kind) {
        EyeKind.Pill -> {
            val h = 92f * pillScaleY.coerceAtLeast(0.04f)
            drawRoundRect(
                color = face,
                topLeft = Offset(-17f, -h / 2f),
                size = Size(34f, h),
                cornerRadius = CornerRadius(17f, 17f),
            )
        }
        EyeKind.Round -> scale(grow, grow, pivot = Offset.Zero) {
            drawCircle(face, radius = 25f, center = Offset.Zero)
            drawCircle(glint, radius = 7f, center = Offset(-9f, -10f))
        }
        EyeKind.Smile -> scale(grow, grow, pivot = Offset.Zero) {
            drawPath(paths.smile, face, style = Stroke(width = 18f, cap = StrokeCap.Round))
        }
        EyeKind.Ouch -> scale(grow, grow, pivot = Offset.Zero) {
            drawPath(
                if (left) paths.ouchLeft else paths.ouchRight,
                face,
                style = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/** Props are flat body-coloured silhouettes with a page-coloured halo separating them from the body. */
private fun DrawScope.drawTool(
    paths: MascotPaths,
    tool: MascotTool,
    pop: Float,
    bobDeg: Float,
    body: Color,
    face: Color,
) {
    val anchor: Offset
    val rotation: Float
    when (tool) {
        MascotTool.Phone -> { anchor = Offset(440f, 262f); rotation = 16f }
        MascotTool.Wrench -> { anchor = Offset(424f, 352f); rotation = 42f }
        MascotTool.Clipboard -> { anchor = Offset(438f, 366f); rotation = -12f }
        MascotTool.Brush -> { anchor = Offset(432f, 336f); rotation = 44f }
    }
    val alpha = pop.coerceIn(0f, 1f)
    val objColor = body.copy(alpha = body.alpha * alpha)
    val haloColor = face.copy(alpha = face.alpha * alpha)
    val halo = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val line = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val scaleAmt = 0.2f + 0.8f * pop

    fun DrawScope.obj(path: Path) {
        drawPath(path, haloColor, style = halo)
        drawPath(path, objColor, style = Fill)
    }
    fun DrawScope.objRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
        drawRoundRect(haloColor, Offset(x, y), Size(w, h), CornerRadius(r, r), style = halo)
        drawRoundRect(objColor, Offset(x, y), Size(w, h), CornerRadius(r, r))
    }
    fun DrawScope.dotRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
        drawRoundRect(haloColor, Offset(x, y), Size(w, h), CornerRadius(r, r))
    }
    fun DrawScope.lineRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
        drawRoundRect(haloColor, Offset(x, y), Size(w, h), CornerRadius(r, r), style = line)
    }

    withTransform({
        // Pop in/out around the anchor; sway from the lower-left of the prop like a held handle.
        scale(scaleAmt, scaleAmt, pivot = anchor)
        rotate(bobDeg, pivot = anchor + Offset(-20f, 40f))
        translate(anchor.x, anchor.y)
        rotate(rotation, pivot = Offset.Zero)
    }) {
        when (tool) {
            MascotTool.Phone -> {
                objRect(-32f, -60f, 64f, 120f, 16f)
                dotRect(-22f, -44f, 44f, 72f, 6f)
                drawCircle(haloColor, radius = 5f, center = Offset(0f, 44f))
                drawPath(paths.phoneRings, objColor, style = Stroke(width = 8f, cap = StrokeCap.Round))
            }
            MascotTool.Wrench -> {
                objRect(-14f, -40f, 28f, 150f, 14f)
                drawCircle(haloColor, radius = 34f, center = Offset(0f, -62f), style = halo)
                drawCircle(objColor, radius = 34f, center = Offset(0f, -62f))
                drawPath(paths.wrenchNotch, haloColor)
            }
            MascotTool.Clipboard -> {
                objRect(-52f, -66f, 104f, 132f, 14f)
                dotRect(-22f, -78f, 44f, 26f, 9f)
                lineRect(-32f, -34f, 18f, 18f, 5f)
                drawPath(paths.check1, haloColor, style = line)
                drawLine(haloColor, Offset(-2f, -25f), Offset(32f, -25f), strokeWidth = 7f, cap = StrokeCap.Round)
                lineRect(-32f, 0f, 18f, 18f, 5f)
                drawPath(paths.check2, haloColor, style = line)
                drawLine(haloColor, Offset(-2f, 9f), Offset(32f, 9f), strokeWidth = 7f, cap = StrokeCap.Round)
                lineRect(-32f, 34f, 18f, 18f, 5f)
                drawLine(haloColor, Offset(-2f, 43f), Offset(32f, 43f), strokeWidth = 7f, cap = StrokeCap.Round)
            }
            MascotTool.Brush -> {
                objRect(-12f, -20f, 24f, 150f, 12f)
                objRect(-17f, -52f, 34f, 34f, 6f)
                obj(paths.brushHead)
                drawPath(paths.paintDrop, haloColor)
            }
        }
    }
}

/** Geometry ported from the desktop SVG, built once per instance. */
private class MascotPaths {
    val body: Path = Path().apply {
        moveTo(256f, 108f)
        cubicTo(356f, 108f, 428f, 164f, 428f, 272f)
        cubicTo(428f, 388f, 381f, 428f, 256f, 428f)
        cubicTo(131f, 428f, 84f, 388f, 84f, 272f)
        cubicTo(84f, 164f, 156f, 108f, 256f, 108f)
        close()
    }

    /** M-26 10 A26 26 0 0 1 26 10 — an upside-down U (happy squint). */
    val smile: Path = Path().apply {
        arcTo(Rect(-26f, -16f, 26f, 36f), 180f, 180f, forceMoveTo = true)
    }

    val ouchLeft: Path = Path().apply {
        moveTo(-14f, -18f); lineTo(14f, 0f); lineTo(-14f, 18f)
    }
    val ouchRight: Path = Path().apply {
        moveTo(14f, -18f); lineTo(-14f, 0f); lineTo(14f, 18f)
    }

    /** M46 -30 a34 34 0 0 1 0 60  M60 -46 a52 52 0 0 1 0 92 */
    val phoneRings: Path = Path().apply {
        arcTo(Rect(30f - 34f, -34f, 30f + 34f, 34f), -61.93f, 123.86f, forceMoveTo = true)
        arcTo(Rect(35.75f - 52f, -52f, 35.75f + 52f, 52f), -62.19f, 124.38f, forceMoveTo = true)
    }

    /** M-12 -104 h24 v34 a12 12 0 0 1 -24 0 z */
    val wrenchNotch: Path = Path().apply {
        moveTo(-12f, -104f)
        lineTo(12f, -104f)
        lineTo(12f, -70f)
        arcTo(Rect(-12f, -82f, 12f, -58f), 0f, 180f, forceMoveTo = false)
        close()
    }

    val check1: Path = Path().apply {
        moveTo(-29f, -25f); lineTo(-24f, -20f); lineTo(-15f, -30f)
    }
    val check2: Path = Path().apply {
        moveTo(-29f, 9f); lineTo(-24f, 14f); lineTo(-15f, 4f)
    }

    /** M-17 -52 L-24 -96 Q0 -118 24 -96 L17 -52 Z */
    val brushHead: Path = Path().apply {
        moveTo(-17f, -52f)
        lineTo(-24f, -96f)
        quadraticTo(0f, -118f, 24f, -96f)
        lineTo(17f, -52f)
        close()
    }

    /** M-36 -112 c0 -8 8 -22 8 -22 s8 14 8 22 a8 8 0 0 1 -16 0 z */
    val paintDrop: Path = Path().apply {
        moveTo(-36f, -112f)
        cubicTo(-36f, -120f, -28f, -134f, -28f, -134f)
        cubicTo(-28f, -134f, -20f, -120f, -20f, -112f)
        arcTo(Rect(-36f, -120f, -20f, -104f), 0f, 180f, forceMoveTo = false)
        close()
    }
}

/** Convenience for callers that only know the chat run state. */
public fun mascotStateFor(streaming: Boolean, waiting: Boolean, error: Boolean): MascotState = when {
    error -> MascotState.Error
    waiting -> MascotState.Waiting
    streaming -> MascotState.Talking
    else -> MascotState.Idle
}
