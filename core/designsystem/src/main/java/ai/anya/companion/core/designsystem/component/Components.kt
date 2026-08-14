package ai.anya.companion.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.anya.companion.core.designsystem.R
import ai.anya.companion.core.designsystem.haptic.rememberAnyaHaptics
import ai.anya.companion.core.designsystem.theme.AnyaColors
import ai.anya.companion.core.designsystem.theme.AnyaSpace
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
public fun AnyaBrandMark(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 36.dp,
    clipped: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    // Transparent line-art: invert in dark mode so strokes stay readable.
    val invertMatrix = remember {
        ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }
    Image(
        painter = painterResource(R.drawable.anya_icon),
        contentDescription = "Anya",
        modifier = modifier
            .size(size)
            .then(
                if (clipped) {
                    Modifier.clip(RoundedCornerShape((size.value * 0.28f).dp))
                } else {
                    Modifier
                },
            )
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = {}, onLongClick = onLongClick)
                } else {
                    Modifier
                },
            ),
        contentScale = ContentScale.Fit,
        colorFilter = if (dark) ColorFilter.colorMatrix(invertMatrix) else null,
    )
}

/** Breathing Anya mark used for splash / connecting / submitting states. */
@Composable
public fun AnyaLoadingIndicator(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 88.dp,
    label: String? = stringResource(R.string.loading_please_wait),
) {
    val transition = rememberInfiniteTransition(label = "anyaLoad")
    val scale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "anyaLoadScale",
    )
    val glowAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "anyaLoadGlow",
    )
    val orbit by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
        ),
        label = "anyaLoadOrbit",
    )
    val dotPhases = listOf(0f, 0.33f, 0.66f)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Lg),
    ) {
        Box(
            modifier = Modifier.size(size * 1.35f),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(size * 1.2f)
                    .graphicsLayer { rotationZ = orbit }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)),
            )
            Box(
                modifier = Modifier
                    .size(size * 1.05f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = glowAlpha * 0.06f)),
            )
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            val invertMatrix = remember {
                ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                )
            }
            Image(
                painter = painterResource(R.drawable.anya_icon),
                contentDescription = null,
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(RoundedCornerShape((size.value * 0.22f).dp)),
                contentScale = ContentScale.Fit,
                colorFilter = if (dark) ColorFilter.colorMatrix(invertMatrix) else null,
            )
        }
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dotPhases.forEach { phase ->
                    val dotScale by transition.animateFloat(
                        initialValue = 0.55f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = 900,
                                delayMillis = (phase * 900).toInt(),
                                easing = FastOutSlowInEasing,
                            ),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "anyaLoadDot$phase",
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .graphicsLayer {
                                scaleX = dotScale
                                scaleY = dotScale
                            }
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)),
                    )
                }
            }
        }
    }
}

/** Compact spinner for inline loading slots (file download, search, list headers). */
@Composable
public fun AnyaInlineLoadingMark(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 18.dp,
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        strokeWidth = (size.value * 0.12f).dp.coerceAtLeast(1.5.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun AnyaPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: PullToRefreshState = rememberPullToRefreshState(),
    content: @Composable () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = modifier,
        indicator = {
            AnyaPullRefreshIndicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        content()
    }
}

@Composable
private fun AnyaPullRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!isRefreshing && state.distanceFraction <= 0.02f) return
    val targetScale = if (isRefreshing) 1f else (0.72f + state.distanceFraction * 0.28f).coerceIn(0.72f, 1f)
    val scale by animateFloatAsState(targetScale, label = "pullRefreshScale")
    // Pulling: arrow rotates with drag distance. Refreshing: continuous spin.
    val spinTransition = rememberInfiniteTransition(label = "pullRefreshSpin")
    val spinAngle by spinTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pullRefreshSpinAngle",
    )
    val rotation = if (isRefreshing) spinAngle else (state.distanceFraction * 180f).coerceIn(0f, 180f)
    Surface(
        modifier = modifier
            .padding(top = 10.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = rotation },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (isRefreshing) {
                    stringResource(R.string.pull_refreshing)
                } else {
                    stringResource(R.string.pull_refresh_hint)
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Top-bar icon action matching pill chips (search, etc.). */
@Composable
public fun AnyaTopBarIconChip(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 32.dp,
    containerColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
) {
    Box(
        modifier = modifier
            .height(height)
            .defaultMinSize(minWidth = height)
            .clip(RoundedCornerShape(999.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size((height.value * 0.56f).dp),
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
public fun AnyaBrandRow(
    title: String = "Anya",
    subtitle: String = "Companion",
    modifier: Modifier = Modifier,
    onBrandLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Md),
    ) {
        AnyaBrandMark(size = 40.dp, onLongClick = onBrandLongClick)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 168.dp),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun AnyaTopBar(
    title: String,
    subtitle: String? = null,
    showBrand: Boolean = false,
    brandTitle: String = "Anya",
    onBrandLongClick: (() -> Unit)? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            if (showBrand) {
                AnyaBrandRow(
                    title = brandTitle,
                    subtitle = subtitle ?: "Companion",
                    onBrandLongClick = onBrandLongClick,
                )
            } else {
                Column {
                    Text(text = title, style = MaterialTheme.typography.titleLarge)
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        navigationIcon = {
            if (navigationIcon != null) {
                navigationIcon()
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}

@Composable
public fun AnyaSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** When set (e.g. pager `page + offset`), thumb tracks continuously without spring. */
    selectedProgress: Float? = null,
) {
    val haptics = rememberAnyaHaptics()
    val count = options.size.coerceAtLeast(1)
    val safeIndex = selectedIndex.coerceIn(0, (count - 1).coerceAtLeast(0))
    val progress = selectedProgress?.coerceIn(0f, (count - 1).toFloat()) ?: safeIndex.toFloat()
    val labelIndex = progress.roundToInt().coerceIn(0, count - 1)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(3.dp),
    ) {
        val thumbWidth = maxWidth / count
        val targetOffset = thumbWidth * progress
        val animatedOffset by animateDpAsState(
            targetValue = thumbWidth * safeIndex,
            animationSpec = spring(
                dampingRatio = 0.86f,
                stiffness = 380f,
            ),
            label = "segmentThumb",
        )
        val offsetX = if (selectedProgress != null) targetOffset else animatedOffset
        Box(
            modifier = Modifier
                .offset(x = offsetX)
                .width(thumbWidth)
                .fillMaxHeight()
                .shadow(
                    elevation = 1.dp,
                    shape = RoundedCornerShape(999.dp),
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.04f),
                    spotColor = Color.Black.copy(alpha = 0.06f),
                )
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surface),
        )
        Row(modifier = Modifier.fillMaxSize()) {
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
                        .clip(RoundedCornerShape(999.dp))
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

@Composable
public fun AnyaConnectionChip(
    label: String,
    tone: AnyaStatusTone = AnyaStatusTone.Neutral,
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
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

public data class AnyaBottomNavItem(
    public val id: String,
    public val label: String,
    public val icon: ImageVector,
    public val selectedIcon: ImageVector? = null,
    public val badge: Int = 0,
)

@Composable
public fun AnyaBottomNavBar(
    items: List<AnyaBottomNavItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAnyaHaptics()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = AnyaSpace.Xxl, vertical = AnyaSpace.Md),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = Color.Black.copy(alpha = 0.06f),
                    spotColor = Color.Black.copy(alpha = 0.08f),
                ),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 64.dp)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    val selected = item.id == selectedId
                    val icon = if (selected) item.selectedIcon ?: item.icon else item.icon
                    val activeColor = MaterialTheme.colorScheme.onBackground
                    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant
                    val highlight by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            Color.Transparent
                        },
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        label = "navHighlight",
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (selected) activeColor else inactiveColor,
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        label = "navContent",
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(highlight)
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
}

@Composable
public fun AnyaFloatingActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAnyaHaptics()
    FloatingActionButton(
        onClick = {
            haptics.buttonPress()
            onClick()
        },
        modifier = modifier.size(56.dp),
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.surface,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 6.dp,
            pressedElevation = 8.dp,
        ),
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
public fun AnyaEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AnyaSpace.Xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
        }
        Spacer(modifier = Modifier.height(AnyaSpace.Lg))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(AnyaSpace.Xs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
public fun AnyaMetaRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
public fun AnyaScreen(
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = topBar,
        bottomBar = bottomBar,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            content(padding)
        }
    }
}

@Composable
public fun AnyaSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Xs),
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
public fun AnyaSurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AnyaSpace.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(AnyaSpace.Lg),
            verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
            content = content,
        )
    }
}

@Composable
public fun AnyaStatusCard(
    title: String,
    body: String,
    tone: AnyaStatusTone = AnyaStatusTone.Neutral,
    modifier: Modifier = Modifier,
) {
    val toneColor = when (tone) {
        AnyaStatusTone.Success -> AnyaColors.Success
        AnyaStatusTone.Danger -> AnyaColors.Danger
        AnyaStatusTone.Info -> AnyaColors.Info
        AnyaStatusTone.Warning -> AnyaColors.Warning
        AnyaStatusTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Md),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(toneColor),
        )
        Column(verticalArrangement = Arrangement.spacedBy(AnyaSpace.Xs)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

public enum class AnyaStatusTone {
    Neutral, Success, Danger, Info, Warning,
}

@Composable
public fun AnyaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = rememberAnyaHaptics()
    Button(
        onClick = {
            haptics.buttonPress()
            onClick()
        },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(AnyaSpace.ControlRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
public fun AnyaSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = rememberAnyaHaptics()
    OutlinedButton(
        onClick = {
            haptics.buttonPress()
            onClick()
        },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(AnyaSpace.ControlRadius),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
public fun AnyaHeroHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    markSize: androidx.compose.ui.unit.Dp = 148.dp,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = AnyaSpace.Lg, bottom = AnyaSpace.Md),
        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnyaBrandMark(size = markSize, clipped = true)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
        ) {
            Text(text = title, style = MaterialTheme.typography.displaySmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

public data class AnyaSegmentOption(
    public val id: String,
    public val label: String,
    public val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
public fun AnyaSegmentedControl(
    options: List<AnyaSegmentOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAnyaHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val selected = option.id == selectedId
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.surface
                        else Color.Transparent,
                    )
                    .clickable {
                        if (!selected) {
                            haptics.linearTick()
                        }
                        onSelect(option.id)
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = option.icon,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

