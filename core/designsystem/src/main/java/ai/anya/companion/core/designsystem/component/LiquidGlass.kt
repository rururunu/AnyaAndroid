package ai.anya.companion.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ai.anya.companion.core.designsystem.haptic.rememberAnyaHaptics
import ai.anya.companion.core.designsystem.theme.AnyaColors
import ai.anya.companion.core.designsystem.theme.AnyaSpace
import ai.anya.companion.core.designsystem.theme.LocalDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Shared liquid-glass tokens. Every glass surface (chips, bottom bar, selected
 * pill) reads its tint / blur / refraction values from here so the whole app
 * refracts consistently and the numbers live in exactly one place.
 */
internal object AnyaGlassTokens {
    // The bar is the *flat* pane of glass: content behind it stays largely
    // legible, so the tint is nearly clear and the blur is only a soft veil.
    const val SurfaceAlphaLight = 0.10f
    const val SurfaceAlphaDark = 0.14f

    val ChipBlur = 4.dp
    val ChipRefractionHeight = 8.dp
    val ChipRefractionAmount = 12.dp

    val BarBlur = 3.dp
    val BarRefractionHeight = 12.dp
    val BarRefractionAmount = 16.dp
    val BarShadow = 8.dp
    val BarPadding = 6.dp
    val ItemSpacing = 4.dp

    // The selected pill is the *raised* glass puck. It reads as raised because
    // the content behind it is smeared out (blur) rather than because it is a
    // brighter white; the tint stays low so the blur difference carries the
    // selection instead of a flat overlay.
    val PillBlur = 16.dp
    val PillRefractionHeight = 14.dp
    val PillRefractionAmount = 26.dp
    const val PillTintLightAlpha = 0.20f
    const val PillTintDarkAlpha = 0.10f
    const val SelectAnimMs = 320

    // The segmented control is *not* a copy of the bar's recipe. The bar always has
    // list content behind it, so a 3dp veil reads as glass there; the segment sits
    // over the page's flat white far more often, and at 3dp nothing happened at all.
    // It therefore gets a much stronger blur plus a tint that contrasts with the
    // page instead of matching it.
    val SegmentHeight = 44.dp
    val SegmentPadding = 3.dp
    val SegmentBlur = 12.dp
    const val SegmentTrackTintAlpha = 0.05f

    // FAB: pure lens. It draws no surface at all — only the refraction and the
    // highlight rim make it visible.
    val FabBlur = 18.dp
    val FabRefractionHeight = 14.dp
    val FabRefractionAmount = 22.dp
    val FabShadow = 12.dp

    // A modal sheet is the largest pane in the app and has to stay readable over busy
    // content, so it takes the library's pane-sized effects (12dp blur, 24/32 lens,
    // chromatic aberration) plus a heavier tint. The tint is a *lifted* surface rather
    // than plain `surface`: a `surface` veil over a `surface` page differs by ~1/255 and
    // reads as no sheet at all.
    val SheetBlur = 12.dp
    val SheetRefractionHeight = 24.dp
    val SheetRefractionAmount = 32.dp
    const val SheetTintAlpha = 0.58f
    const val SheetTintLift = 0.10f

    // Secondary controls sitting *on* a glass sheet: same pane effects, thinner veil, so
    // the control reads as a lighter pane on the sheet rather than a flat outlined box.
    const val SheetControlTintAlpha = 0.45f
}

/** Translucent surface tint that keeps glass content readable in both themes. */
@Composable
internal fun liquidGlassSurfaceColor(): Color {
    val alpha = if (LocalDarkTheme.current) {
        AnyaGlassTokens.SurfaceAlphaDark
    } else {
        AnyaGlassTokens.SurfaceAlphaLight
    }
    return MaterialTheme.colorScheme.surface.copy(alpha = alpha)
}

/** Capsule liquid-glass chip (top-bar actions such as search / connection). */
@Composable
public fun AnyaGlassChip(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val density = LocalDensity.current
    val surface = liquidGlassSurfaceColor()
    Row(
        modifier = modifier
            .height(height)
            .defaultMinSize(minWidth = height)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    vibrancy()
                    blur(with(density) { AnyaGlassTokens.ChipBlur.toPx() })
                    lens(
                        with(density) { AnyaGlassTokens.ChipRefractionHeight.toPx() },
                        with(density) { AnyaGlassTokens.ChipRefractionAmount.toPx() },
                    )
                },
                highlight = { Highlight.Default },
                onDrawSurface = { drawRect(surface) },
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        content = content,
    )
}

/**
 * The [AnyaGlassChip] recipe as a plain modifier: translucent tint, blur, the
 * refraction lens and the highlight rim. For hosts that already own a container and
 * only want to swap a flat fill for glass — the chat composer panel and its round
 * controls do exactly that, so they refract the list scrolling behind them.
 *
 * The defaults are the 32dp-chip numbers; a larger pane wants the library's own
 * pane-sized values (see the composer). [chromaticAberration] turns on the library's
 * edge-fringing pass, the strongest cue that a surface is glass rather than a tint.
 *
 * The caller must clip to [shape] itself: `drawBackdrop`'s `shape` rounds the
 * refracted content but not the tint drawn by `onDrawSurface`, so an unclipped host
 * shows square corners (the same reason the nav bar pins its own clip).
 */
@Composable
public fun Modifier.anyaGlassSurface(
    backdrop: Backdrop,
    shape: Shape,
    // Overridable because the default composes `colorScheme.surface`, and a host whose
    // backdrop base *is* that surface (the chat composer) ends up 1/255 off the canvas,
    // i.e. an invisible pane. Such hosts must pass a tone that actually differs.
    surfaceTint: Color = liquidGlassSurfaceColor(),
    blurRadius: Dp = AnyaGlassTokens.ChipBlur,
    refractionHeight: Dp = AnyaGlassTokens.ChipRefractionHeight,
    refractionAmount: Dp = AnyaGlassTokens.ChipRefractionAmount,
    chromaticAberration: Boolean = false,
): Modifier {
    val density = LocalDensity.current
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(with(density) { blurRadius.toPx() })
            lens(
                with(density) { refractionHeight.toPx() },
                with(density) { refractionAmount.toPx() },
                chromaticAberration = chromaticAberration,
            )
        },
        highlight = { Highlight.Default },
        onDrawSurface = { drawRect(surfaceTint) },
    )
}

/**
 * Modal sheet as a *same-window* glass overlay.
 *
 * [androidx.compose.material3.ModalBottomSheet] runs in its own window, and
 * `drawBackdrop` can only sample a layer recorded in the same window — so glass placed
 * inside a Material sheet renders as a flat tint, with no blur and no refraction.
 * Hosting the sheet inside the host's own composition instead (this component, handed
 * the page's backdrop) is what lets it actually refract and blur what is behind it.
 *
 * Dismiss: scrim tap, dragging down past a third of the panel, or whatever the caller
 * wires up (close button, back). [visible] is owned by the host and drives the enter /
 * exit animation.
 */
@Composable
public fun AnyaGlassBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(if (visible) 1f else 0f) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var panelHeightPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(visible) {
        if (visible) {
            dragOffset = 0f
            progress.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.85f))
        } else {
            progress.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.85f))
        }
    }
    if (!visible && progress.value <= 0.001f) return

    // iOS 26 sheets sit inset on every side and float, so the panel rounds all four
    // corners and keeps a gap instead of resting flush on the bottom edge.
    val shape = RoundedCornerShape(28.dp)
    val lifted = lerp(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.onBackground,
        AnyaGlassTokens.SheetTintLift,
    )
    val tint = lifted.copy(alpha = AnyaGlassTokens.SheetTintAlpha)
    val density = LocalDensity.current
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val floatBottom = bottomInset + 8.dp
    val floatBottomPx = with(density) { floatBottom.toPx() }

    Box(modifier = modifier.fillMaxSize()) {
        // Scrim: dims the page, takes the tap-to-dismiss, and swallows drags so the pager
        // behind cannot scroll while the sheet is open.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value }
                .background(Color.Black.copy(alpha = 0.26f))
                .pointerInput(Unit) { detectTapGestures { onDismissRequest() } }
                .pointerInput(Unit) { detectDragGestures { change, _ -> change.consume() } },
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .padding(bottom = floatBottom)
                .graphicsLayer {
                    // Clear the screen on the way out: the panel's own height plus the gap
                    // it floats above the bottom edge.
                    translationY = dragOffset + (1f - progress.value) * (size.height + floatBottomPx)
                }
                .onGloballyPositioned { panelHeightPx = it.size.height.toFloat() }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragOffset > panelHeightPx / 3f) {
                                onDismissRequest()
                            } else {
                                scope.launch {
                                    animate(dragOffset, 0f) { value, _ -> dragOffset = value }
                                }
                            }
                        },
                        onVerticalDrag = { change, delta ->
                            change.consume()
                            dragOffset = (dragOffset + delta).coerceAtLeast(0f)
                        },
                    )
                }
                .clip(shape)
                .anyaGlassSurface(
                    backdrop = backdrop,
                    shape = shape,
                    surfaceTint = tint,
                    blurRadius = AnyaGlassTokens.SheetBlur,
                    refractionHeight = AnyaGlassTokens.SheetRefractionHeight,
                    refractionAmount = AnyaGlassTokens.SheetRefractionAmount,
                    chromaticAberration = true,
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
                )
            }
            // The navigation-bar inset is already part of the panel's bottom padding above;
            // a spacer here would leave a dead band inside the glass.
            content()
        }
    }
}

/** Connection status chip rendered as liquid glass. */
@Composable
public fun AnyaGlassConnectionChip(
    backdrop: Backdrop,
    label: String,
    tone: AnyaStatusTone,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val dotColor = when (tone) {
        AnyaStatusTone.Success -> AnyaColors.Success
        AnyaStatusTone.Danger -> AnyaColors.Danger
        AnyaStatusTone.Info -> AnyaColors.Info
        AnyaStatusTone.Warning -> AnyaColors.Warning
        AnyaStatusTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    AnyaGlassChip(backdrop = backdrop, modifier = modifier, onClick = onClick) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** Circular icon-only liquid-glass chip for top-bar actions. */
@Composable
public fun AnyaGlassIconChip(
    backdrop: Backdrop,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
) {
    AnyaGlassChip(backdrop = backdrop, modifier = modifier, height = height, onClick = onClick) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size((height.value * 0.56f).dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Floating liquid-glass bottom navigation bar: renders the content scrolling
 * behind it through a refraction lens + blur + vibrancy.
 */
@Composable
public fun AnyaGlassBottomNavBar(
    items: List<AnyaBottomNavItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    // Continuous selection position in item units (1.5 = half-way between the second
    // and third item). Null falls back to animating between whole indices.
    pillProgress: Float? = null,
    // Invoked with -1 / +1 when the bar itself is swiped towards the previous / next item.
    onSwipeStep: ((Int) -> Unit)? = null,
) {
    val haptics = rememberAnyaHaptics()
    val density = LocalDensity.current
    val surface = liquidGlassSurfaceColor()
    val swipeThresholdPx = with(density) { 40.dp.toPx() }
    val dragTotal = remember { mutableFloatStateOf(0f) }
    // The bar itself is a capsule and the pill sits exactly BarPadding inside it,
    // so a capsule pill keeps the two radii concentric instead of the mismatched
    // corners a fixed 22.dp radius produced.
    val pillShape = CircleShape
    val pillTint = Color.White.copy(
        alpha = if (LocalDarkTheme.current) {
            AnyaGlassTokens.PillTintDarkAlpha
        } else {
            AnyaGlassTokens.PillTintLightAlpha
        },
    )
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AnyaSpace.Xxl)
            // The bar itself is swipeable: a horizontal drag or fling anywhere on it
            // steps to the previous / next item, so switching does not require
            // aiming at a particular item.
            .draggable(
                state = rememberDraggableState { delta -> dragTotal.value += delta },
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity ->
                    val step = when {
                        // A quick flick wins over distance, so a short fast swipe works.
                        velocity <= -900f -> 1
                        velocity >= 900f -> -1
                        dragTotal.value <= -swipeThresholdPx -> 1
                        dragTotal.value >= swipeThresholdPx -> -1
                        else -> 0
                    }
                    dragTotal.value = 0f
                    if (step != 0) {
                        onSwipeStep?.invoke(step)
                    }
                },
            )
            .defaultMinSize(minHeight = 64.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    vibrancy()
                    blur(with(density) { AnyaGlassTokens.BarBlur.toPx() })
                    lens(
                        with(density) { AnyaGlassTokens.BarRefractionHeight.toPx() },
                        with(density) { AnyaGlassTokens.BarRefractionAmount.toPx() },
                    )
                },
                highlight = { Highlight.Default },
                shadow = { Shadow(radius = AnyaGlassTokens.BarShadow) },
                onDrawSurface = { drawRect(surface) },
            )
            .padding(AnyaGlassTokens.BarPadding),
    ) {
        val spacing = AnyaGlassTokens.ItemSpacing
        val itemCount = items.size.coerceAtLeast(1)
        val itemWidth = (maxWidth - spacing * (itemCount - 1)) / itemCount
        val selectedIndex = items.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
        val animatedIndex by animateFloatAsState(
            targetValue = selectedIndex.toFloat(),
            animationSpec = tween(AnyaGlassTokens.SelectAnimMs, easing = FastOutSlowInEasing),
            label = "navPillIndex",
        )
        // When the host supplies live pager progress the pill tracks it directly, so
        // it glides through the whole page transition instead of jumping on settle.
        val pillIndex = (pillProgress ?: animatedIndex)
            .coerceIn(0f, (itemCount - 1).toFloat())

        // The selected pill is its own glass puck layered over the bar so the
        // selection itself refracts the content behind the bar, and slides
        // between items instead of cross-fading two separate rectangles.
        Box(modifier = Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .width(itemWidth)
                    .fillMaxHeight()
                    // Slide in the *draw* phase. A layout-phase offset moves the drawn
                    // puck but leaves the backdrop's offscreen sampling window where it
                    // was laid out, which is what produced the hard-edged square
                    // artefact while the selection moved.
                    .graphicsLayer {
                        translationX = (itemWidth + spacing).toPx() * pillIndex
                    }
                    // Clip to the pill's own bounds. drawBackdrop's shape alone did not
                    // round the tint to the bar's curvature, so the rounding is pinned
                    // here to the composable's size instead.
                    .clip(pillShape)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { pillShape },
                        effects = {
                            vibrancy()
                            blur(with(density) { AnyaGlassTokens.PillBlur.toPx() })
                            lens(
                                with(density) { AnyaGlassTokens.PillRefractionHeight.toPx() },
                                with(density) { AnyaGlassTokens.PillRefractionAmount.toPx() },
                            )
                        },
                        highlight = { Highlight.Default },
                        onDrawSurface = { drawRect(pillTint) },
                    ),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val selected = item.id == selectedId
                val icon = if (selected) item.selectedIcon ?: item.icon else item.icon
                val activeColor = MaterialTheme.colorScheme.onBackground
                val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant
                val contentColor by animateColorAsState(
                    targetValue = if (selected) activeColor else inactiveColor,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    label = "navContent",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        // Round the touch feedback to the same capsule as the selection;
                        // an unclipped clickable shows a square press ripple.
                        .clip(pillShape)
                        .clickable {
                            if (!selected) {
                                haptics.linearTick()
                            }
                            onSelect(item.id)
                        }
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        androidx.compose.material3.Icon(
                            imageVector = icon,
                            contentDescription = item.label,
                            modifier = Modifier.size(24.dp),
                            tint = contentColor,
                        )
                        if (item.badge > 0) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(AnyaColors.Danger)
                                    .align(Alignment.TopEnd),
                            )
                        }
                    }
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/**
 * Segmented control rendered as liquid glass: the track is a flat pane that
 * refracts the page scrolling under it, and the selected thumb is a raised glass
 * puck that slides with [selectedProgress] (e.g. the pager's page + offset).
 */
@Composable
public fun AnyaGlassSegmentedControl(
    backdrop: Backdrop,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedProgress: Float? = null,
) {
    val haptics = rememberAnyaHaptics()
    val density = LocalDensity.current
    val dark = LocalDarkTheme.current
    val count = options.size.coerceAtLeast(1)
    val safeIndex = selectedIndex.coerceIn(0, count - 1)
    val progress = selectedProgress?.coerceIn(0f, (count - 1).toFloat()) ?: safeIndex.toFloat()
    val labelIndex = progress.roundToInt().coerceIn(0, count - 1)
    // Deliberately not `liquidGlassSurfaceColor()`: that is white, and white glass
    // over the page's white background is invisible. `onSurface` is dark in the
    // light theme and light in the dark theme, so this veil contrasts in both.
    val trackTint = MaterialTheme.colorScheme.onSurface.copy(
        alpha = AnyaGlassTokens.SegmentTrackTintAlpha,
    )
    // Same thumb as the bottom bar's selected pill.
    val thumbTint = Color.White.copy(
        alpha = if (dark) {
            AnyaGlassTokens.PillTintDarkAlpha
        } else {
            AnyaGlassTokens.PillTintLightAlpha
        },
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(AnyaGlassTokens.SegmentHeight)
            .clip(CircleShape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    vibrancy()
                    blur(with(density) { AnyaGlassTokens.SegmentBlur.toPx() })
                    lens(
                        with(density) { AnyaGlassTokens.BarRefractionHeight.toPx() },
                        with(density) { AnyaGlassTokens.BarRefractionAmount.toPx() },
                    )
                },
                highlight = { Highlight.Default },
                shadow = { Shadow(radius = AnyaGlassTokens.BarShadow) },
                onDrawSurface = { drawRect(trackTint) },
            )
            // A hairline edge. The track is white-on-white as soon as a card scrolls
            // behind it, and the library's Highlight is a *light* rim — it cannot
            // separate white glass from a white card. This darker stroke can.
            .border(
                width = 0.75.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                shape = CircleShape,
            )
            .padding(AnyaGlassTokens.SegmentPadding),
    ) {
        val thumbWidth = maxWidth / count

        // Thumb slides in the draw phase, same reason as the bottom-nav pill: a
        // layout-phase offset leaves the backdrop sampling window behind.
        Box(modifier = Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .width(thumbWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        translationX = thumbWidth.toPx() * progress
                    }
                    .clip(CircleShape)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { CircleShape },
                        effects = {
                            vibrancy()
                            blur(with(density) { AnyaGlassTokens.PillBlur.toPx() })
                            lens(
                                with(density) { AnyaGlassTokens.PillRefractionHeight.toPx() },
                                with(density) { AnyaGlassTokens.PillRefractionAmount.toPx() },
                            )
                        },
                        highlight = { Highlight.Default },
                        onDrawSurface = { drawRect(thumbTint) },
                    )
                    .border(
                        width = 0.75.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                        shape = CircleShape,
                    ),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
        ) {
            options.forEachIndexed { index, label ->
                val selected = index == labelIndex
                val content by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
                    label = "segmentFg",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable {
                            if (index != safeIndex) {
                                haptics.linearTick()
                            }
                            onSelect(index)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = content,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/**
 * Circular liquid-glass primary action: a tinted puck that refracts the page
 * behind it instead of an opaque floating button.
 */
@Composable
public fun AnyaGlassFloatingActionButton(
    backdrop: Backdrop,
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAnyaHaptics()
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    vibrancy()
                    blur(with(density) { AnyaGlassTokens.FabBlur.toPx() })
                    lens(
                        with(density) { AnyaGlassTokens.FabRefractionHeight.toPx() },
                        with(density) { AnyaGlassTokens.FabRefractionAmount.toPx() },
                    )
                },
                highlight = { Highlight.Default },
                shadow = { Shadow(radius = AnyaGlassTokens.FabShadow) },
                // No surface drawn: the FAB is a pure lens with no base colour.
                onDrawSurface = { },
            )
            .clickable {
                haptics.buttonPress()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            // The puck is nearly clear now, so the glyph has to carry the contrast:
            // onPrimary would vanish against a light glass surface.
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
