package ai.anya.companion.feature.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ai.anya.companion.core.designsystem.haptic.rememberAnyaHaptics
import ai.anya.companion.core.designsystem.theme.AnyaSpace
import ai.anya.companion.core.model.session.ChatMessage
import ai.anya.companion.core.model.session.ChatRole
import ai.anya.companion.core.model.session.ContextUsageSnapshot
import kotlin.math.roundToInt

@Composable
internal fun MeterRingButton(
    ratio: Float,
    color: Color,
    onClick: () -> Unit,
    contentDescription: String,
    enabled: Boolean = true,
    size: Dp = 40.dp,
    ringSize: Dp = 22.dp,
) {
    val haptic = rememberAnyaHaptics()
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (enabled) {
                    Modifier.clickable(role = Role.Button) {
                        haptic.buttonPress()
                        onClick()
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        MeterRing(
            ratio = ratio,
            color = if (enabled) color else track,
            track = track,
            size = ringSize,
        )
    }
}

@Composable
internal fun MeterRing(
    ratio: Float,
    color: Color,
    track: Color,
    size: Dp,
    stroke: Dp = 2.5.dp,
) {
    val clamped = ratio.coerceIn(0f, 1f)
    Canvas(modifier = Modifier.size(size)) {
        val strokePx = stroke.toPx()
        val diameter = this.size.minDimension - strokePx
        val topLeft = Offset(strokePx / 2f, strokePx / 2f)
        val arcSize = Size(diameter, diameter)
        drawArc(
            color = track,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
        )
        val sweep = if (clamped <= 0f) 0f else (clamped * 360f).coerceAtLeast(18f)
        if (sweep > 0f) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
internal fun ThinkingEffortPanel(
    options: List<ThinkingOption>,
    selectedId: String,
    accent: Color,
    onSelect: (String) -> Unit,
    enabled: Boolean = true,
) {
    if (options.size <= 1) return
    val haptic = rememberAnyaHaptics()
    val selectedIndex = options.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    var dragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    LaunchedEffect(selectedId, options) {
        if (!dragging) {
            sliderValue = selectedIndex.toFloat()
        }
    }
    val visualIndex = sliderValue.roundToInt().coerceIn(0, options.lastIndex)
    val currentLabel = options.getOrNull(visualIndex)?.label.orEmpty()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "思考强度",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = currentLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { value ->
                if (!enabled) return@Slider
                dragging = true
                sliderValue = value
            },
            onValueChangeFinished = {
                if (!enabled) return@Slider
                dragging = false
                val next = sliderValue.roundToInt().coerceIn(0, options.lastIndex)
                sliderValue = next.toFloat()
                val option = options[next]
                if (option.id != selectedId) {
                    haptic.tick()
                    onSelect(option.id)
                }
            },
            enabled = enabled,
            valueRange = 0f..options.lastIndex.toFloat(),
            steps = (options.size - 2).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
                disabledThumbColor = accent.copy(alpha = 0.4f),
                disabledActiveTrackColor = accent.copy(alpha = 0.4f),
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            options.forEach { option ->
                val active = option.id == options.getOrNull(visualIndex)?.id
                val edge = option.id == options.first().id || option.id == options.last().id
                if (!active && !edge) return@forEach
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun ContextUsageSheet(
    usage: ContextUsageSnapshot,
    rounds: Int,
    steps: Int,
    onDismiss: () -> Unit,
) {
    val percent = usage.percent
    val accent = contextUsageColor(usage.usageRatio)
    val system = usage.systemPromptTokens
    val tools = usage.toolsTokens
    val messages = usage.messageTokens
    val totalParts = (system + tools + messages).coerceAtLeast(1L)
    AnyaBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AnyaSpace.Lg)
                .navigationBarsPadding()
                .padding(bottom = AnyaSpace.Md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "上下文占用",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                )
            }
            Text(
                text = "~${formatCompactTokens(usage.estimatedTokens)} / ${formatCompactTokens(usage.contextWindowTokens)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = AnyaSpace.Md),
            )
            ContextBreakdownBar(
                system = system.toFloat() / totalParts,
                tools = tools.toFloat() / totalParts,
                messages = messages.toFloat() / totalParts,
            )
            Spacer(modifier = Modifier.height(AnyaSpace.Md))
            ContextLegendRow(color = Color(0xFF3B82F6), label = "系统 prompt", tokens = system)
            ContextLegendRow(color = Color(0xFFF59E0B), label = "工具", tokens = tools)
            ContextLegendRow(color = Color(0xFF22C55E), label = "消息", tokens = messages)
            Spacer(modifier = Modifier.height(AnyaSpace.Lg))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            )
            Spacer(modifier = Modifier.height(AnyaSpace.Md))
            Text(
                text = "会话统计",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "$rounds 轮 · $steps 步",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun ContextBreakdownBar(
    system: Float,
    tools: Float,
    messages: Float,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape),
    ) {
        val width = size.width
        var start = 0f
        fun segment(fraction: Float, color: Color) {
            val w = (fraction.coerceAtLeast(0f) * width)
            if (w <= 0f) return
            drawRect(color = color, topLeft = Offset(start, 0f), size = Size(w, size.height))
            start += w
        }
        segment(system, Color(0xFF3B82F6))
        segment(tools, Color(0xFFF59E0B))
        segment(messages, Color(0xFF22C55E))
        if (start < width) {
            drawRect(
                color = Color(0xFFE5E7EB),
                topLeft = Offset(start, 0f),
                size = Size(width - start, size.height),
            )
        }
    }
}

@Composable
private fun ContextLegendRow(color: Color, label: String, tokens: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "~${formatCompactTokens(tokens)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun contextUsageColor(ratio: Float): Color = when {
    ratio >= 0.9f -> Color(0xFFDC2626)
    ratio >= 0.7f -> Color(0xFFF59E0B)
    else -> Color(0xFF3B82F6)
}

internal fun formatCompactTokens(value: Long): String {
    if (value <= 0L) return "0"
    val abs = value.toDouble()
    return when {
        abs >= 1_000_000 -> {
            val scaled = abs / 1_000_000.0
            String.format(java.util.Locale.US, "%.1fM", scaled)
        }
        abs >= 1_000 -> {
            val scaled = abs / 1_000.0
            if (scaled >= 100) String.format(java.util.Locale.US, "%.0fK", scaled)
            else String.format(java.util.Locale.US, "%.1fK", scaled)
        }
        else -> value.toString()
    }
}

internal fun sessionRoundCount(messages: List<ChatMessage>): Int =
    messages.count { it.role == ChatRole.User }

internal fun sessionStepCount(messages: List<ChatMessage>): Int =
    messages.sumOf { it.toolActivities.size }
