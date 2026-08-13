package ai.anya.companion.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ai.anya.companion.core.designsystem.haptic.rememberAnyaHaptics
import ai.anya.companion.core.designsystem.theme.AnyaSpace
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * Full-screen page chrome matching the chat detail screen: floating back chip,
 * optional title pill, and a soft progressive blur over scrolling content.
 */
@Composable
public fun AnyaOverlayScaffold(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val density = LocalDensity.current
    val canvas = MaterialTheme.colorScheme.surface
    val hazeState = rememberHazeState()
    val haptic = rememberAnyaHaptics()
    val chipColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)
    val barHeight = 40.dp
    var headerContentPx by remember { mutableStateOf(0) }
    val statusBarDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerContentDp = if (headerContentPx > 0) {
        with(density) { headerContentPx.toDp() }
    } else {
        statusBarDp + barHeight + AnyaSpace.Xs * 2 + 20.dp
    }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(canvas),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
        ) {
            content(
                PaddingValues(
                    start = AnyaSpace.Screen,
                    end = AnyaSpace.Screen,
                    top = headerContentDp + AnyaSpace.Lg,
                    bottom = bottomInset + AnyaSpace.Lg,
                ),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .hazeEffect(state = hazeState) {
                    backgroundColor = canvas
                    tints = listOf(HazeTint(canvas.copy(alpha = 0.46f)))
                    blurRadius = 22.dp
                    noiseFactor = 0.04f
                    progressive = HazeProgressive.verticalGradient(
                        startIntensity = 0.72f,
                        endIntensity = 0f,
                    )
                }
                .background(
                    Brush.verticalGradient(
                        0.00f to canvas.copy(alpha = 0.82f),
                        0.42f to canvas.copy(alpha = 0.38f),
                        0.78f to canvas.copy(alpha = 0.10f),
                        1.00f to Color.Transparent,
                    ),
                )
                .zIndex(3f)
                .onGloballyPositioned { headerContentPx = it.size.height },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = AnyaSpace.Md, vertical = AnyaSpace.Xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
            ) {
                AnyaTopBarIconChip(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    height = barHeight,
                    containerColor = chipColor,
                    onClick = {
                        haptic.buttonPress()
                        onBack()
                    },
                )
                if (!title.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .height(barHeight)
                            .clip(RoundedCornerShape(999.dp))
                            .background(chipColor)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                actions()
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
