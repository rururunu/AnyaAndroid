package ai.anya.companion.feature.pairing

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.anya.companion.core.designsystem.component.AnyaPrimaryButton
import ai.anya.companion.core.designsystem.component.AnyaScreen
import ai.anya.companion.core.designsystem.component.AnyaSurfaceCard
import ai.anya.companion.core.designsystem.component.AnyaTopBar
import ai.anya.companion.core.designsystem.haptic.rememberAnyaHaptics
import ai.anya.companion.core.designsystem.theme.AnyaColors
import ai.anya.companion.core.designsystem.theme.AnyaSpace

@Composable
public fun PairingRoute(
    onPaired: () -> Unit,
    initialPairUri: String? = null,
    viewModel: PairingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(initialPairUri) {
        if (!initialPairUri.isNullOrBlank()) {
            viewModel.applyPairLink(initialPairUri)
        }
    }
    LaunchedEffect(state.paired, state.error) {
        if (state.paired && state.error == null) onPaired()
    }
    PairingScreen(
        state = state,
        onHostChange = viewModel::onHostChange,
        onPortChange = viewModel::onPortChange,
        onTokenChange = viewModel::onTokenChange,
        onScanResult = { viewModel.applyPairLink(it, autoSubmit = true) },
        onSubmit = viewModel::submit,
        onCameraDenied = viewModel::onCameraPermissionDenied,
    )
}

@Composable
public fun PairingScreen(
    state: PairingUiState,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onScanResult: (String) -> Unit,
    onSubmit: () -> Unit,
    onCameraDenied: () -> Unit = {},
) {
    val context = LocalContext.current
    val haptic = rememberAnyaHaptics()
    var scanning by remember { mutableStateOf(false) }
    var manualOpen by remember {
        mutableStateOf(state.host.isNotBlank() || state.token.isNotBlank())
    }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            scanning = true
        } else {
            haptic.reject()
            onCameraDenied()
            manualOpen = true
        }
    }

    LaunchedEffect(state.error) {
        if (state.error != null) haptic.reject()
    }
    LaunchedEffect(state.info) {
        if (!state.info.isNullOrBlank() && state.error == null) haptic.tick()
    }
    LaunchedEffect(state.token, state.host, state.info) {
        if (state.token.isNotBlank() && (state.host.isBlank() || state.info?.contains("主机") == true)) {
            manualOpen = true
        }
    }

    if (scanning) {
        QrScanScreen(
            onResult = { payload ->
                scanning = false
                haptic.confirm()
                onScanResult(payload)
            },
            onCancel = { scanning = false },
        )
        return
    }

    AnyaScreen(
        topBar = {
            AnyaTopBar(
                title = "连接桌面",
                subtitle = "扫码或手动填写配对信息",
                showBrand = true,
            )
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AnyaSpace.Screen, vertical = AnyaSpace.Lg),
                verticalArrangement = Arrangement.spacedBy(AnyaSpace.Xl),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm)) {
                    Text(
                        text = "配对手机",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "在桌面工作台打开「连接手机」，扫描二维码即可连接。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Surface(
                    onClick = {
                        if (state.isSubmitting) return@Surface
                        haptic.tick()
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            scanning = true
                        } else {
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    },
                    enabled = !state.isSubmitting,
                    shape = RoundedCornerShape(AnyaSpace.CardRadius),
                    color = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Md),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QrCode2,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "扫描二维码",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "推荐 · 识别后自动连接",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                            )
                        }
                    }
                }

                if (state.error != null) {
                    FeedbackBanner(
                        text = state.error,
                        danger = true,
                    )
                } else if (!state.info.isNullOrBlank()) {
                    FeedbackBanner(
                        text = state.info.orEmpty(),
                        danger = false,
                    )
                }

                Surface(
                    onClick = { manualOpen = !manualOpen },
                    shape = RoundedCornerShape(AnyaSpace.CardRadius),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "手动填写",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = if (manualOpen) {
                                Icons.Rounded.ExpandLess
                            } else {
                                Icons.Rounded.ExpandMore
                            },
                            contentDescription = if (manualOpen) "收起" else "展开",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                AnimatedVisibility(visible = manualOpen) {
                    AnyaSurfaceCard {
                        AnyaField(
                            value = state.host,
                            onValueChange = onHostChange,
                            label = "主机 / 公网域名",
                        )
                        AnyaField(
                            value = state.port,
                            onValueChange = onPortChange,
                            label = "端口",
                        )
                        AnyaField(
                            value = state.token,
                            onValueChange = onTokenChange,
                            label = "配对令牌",
                        )
                        AnyaPrimaryButton(
                            text = if (state.isSubmitting) "连接中…" else "配对并连接",
                            onClick = {
                                haptic.tick()
                                onSubmit()
                            },
                            enabled = !state.isSubmitting,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(AnyaSpace.Xxxl))
            }

            if (state.isSubmitting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Lg),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = if (state.host.isNotBlank()) {
                                "正在连接 ${state.host}…"
                            } else {
                                "正在配对并连接…"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedbackBanner(text: String, danger: Boolean) {
    val bg = if (danger) {
        AnyaColors.Danger.copy(alpha = 0.12f)
    } else {
        AnyaColors.Info.copy(alpha = 0.12f)
    }
    val fg = if (danger) AnyaColors.Danger else AnyaColors.Info
    Surface(
        shape = RoundedCornerShape(AnyaSpace.ControlRadius),
        color = bg,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = text,
            color = fg,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun AnyaField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(AnyaSpace.ControlRadius),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.background,
            unfocusedContainerColor = MaterialTheme.colorScheme.background,
        ),
    )
}
