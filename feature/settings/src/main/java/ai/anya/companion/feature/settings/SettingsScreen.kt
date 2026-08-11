package ai.anya.companion.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.PhonelinkErase
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.anya.companion.core.designsystem.component.AnyaPrimaryButton
import ai.anya.companion.core.designsystem.component.AnyaSecondaryButton
import ai.anya.companion.core.designsystem.component.AnyaStatusTone
import ai.anya.companion.core.designsystem.component.AnyaSurfaceCard
import ai.anya.companion.core.designsystem.theme.AnyaColors
import ai.anya.companion.core.designsystem.theme.AnyaSpace
import ai.anya.companion.core.domain.repository.ConnectionState

@Composable
public fun SettingsRoute(
    onRePair: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsTabContent(
        state = state,
        onDisconnect = viewModel::disconnect,
        onConnect = viewModel::connect,
        onUnpair = {
            viewModel.unpair()
            onRePair()
        },
    )
}

@Composable
public fun SettingsTabContent(
    state: SettingsUiState,
    onDisconnect: () -> Unit,
    onUnpair: () -> Unit,
    onConnect: () -> Unit = {},
) {
    val tone = when (state.connectionState) {
        ConnectionState.Connected -> AnyaStatusTone.Success
        ConnectionState.Error -> AnyaStatusTone.Danger
        ConnectionState.Connecting, ConnectionState.Reconnecting -> AnyaStatusTone.Info
        else -> AnyaStatusTone.Neutral
    }
    val statusTitle = when (state.connectionState) {
        ConnectionState.Connected -> "已连接"
        ConnectionState.Connecting, ConnectionState.Reconnecting -> "连接中"
        ConnectionState.Error -> "连接失败"
        ConnectionState.Disconnected -> "未连接"
    }
    val statusHint = when (state.connectionState) {
        ConnectionState.Connected -> "与桌面工作台实时同步"
        ConnectionState.Connecting, ConnectionState.Reconnecting -> "正在建立安全通道…"
        ConnectionState.Error -> "请检查桌面端是否在线，或重新配对"
        ConnectionState.Disconnected -> "尚未连上桌面端"
    }
    val toneColor = when (tone) {
        AnyaStatusTone.Success -> AnyaColors.Success
        AnyaStatusTone.Danger -> AnyaColors.Danger
        AnyaStatusTone.Info -> AnyaColors.Info
        AnyaStatusTone.Warning -> AnyaColors.Warning
        AnyaStatusTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val canConnect = state.credential != null &&
        state.connectionState != ConnectionState.Connected &&
        state.connectionState != ConnectionState.Connecting &&
        state.connectionState != ConnectionState.Reconnecting
    val canDisconnect = state.connectionState == ConnectionState.Connected ||
        state.connectionState == ConnectionState.Connecting ||
        state.connectionState == ConnectionState.Reconnecting
    val credential = state.credential

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AnyaSpace.Screen, vertical = AnyaSpace.Lg),
        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Xl),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AnyaSpace.Xs)) {
            Text(
                text = "连接",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "管理与桌面工作台的配对与通道",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnyaSurfaceCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(toneColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (state.connectionState == ConnectionState.Connected) {
                            Icons.Rounded.Link
                        } else {
                            Icons.Rounded.LinkOff
                        },
                        contentDescription = null,
                        tint = toneColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = statusTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = statusHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(toneColor),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm)) {
            Text(
                text = "桌面端",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            )
            AnyaSurfaceCard {
                if (credential == null) {
                    Text(
                        text = "尚未配对",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "扫码或输入配对码，与桌面工作台绑定后即可同步对话与审批。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    SettingsDetailRow(label = "主机", value = credential.host)
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    )
                    SettingsDetailRow(label = "端口", value = credential.port.toString())
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    )
                    SettingsDetailRow(
                        label = "设备 ID",
                        value = credential.deviceId,
                        mono = true,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm)) {
            when {
                canConnect -> AnyaPrimaryButton(text = "重新连接", onClick = onConnect)
                canDisconnect -> AnyaPrimaryButton(text = "断开连接", onClick = onDisconnect)
            }
            if (credential != null) {
                AnyaSecondaryButton(text = "解除配对", onClick = onUnpair)
            } else {
                TextButton(
                    onClick = onUnpair,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhonelinkErase,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("去配对")
                }
            }
        }

        Spacer(modifier = Modifier.height(AnyaSpace.Lg))
        Text(
            text = "Anya Companion",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(AnyaSpace.Md))
    }
}

@Composable
private fun SettingsDetailRow(
    label: String,
    value: String,
    mono: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            maxLines = if (mono) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (mono) {
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    } else {
                        Modifier
                    },
                ),
        )
    }
}
