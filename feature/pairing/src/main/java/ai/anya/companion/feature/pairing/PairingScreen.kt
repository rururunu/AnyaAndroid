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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
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
import ai.anya.companion.core.model.protocol.HostDisplayName

@Composable
public fun PairingRoute(
    onPaired: () -> Unit,
    onBack: (() -> Unit)? = null,
    initialPairUri: String? = null,
    viewModel: PairingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(initialPairUri) {
        if (!initialPairUri.isNullOrBlank()) {
            viewModel.applyPairLink(initialPairUri, autoSubmit = true)
        }
    }
    LaunchedEffect(state.paired, state.error) {
        if (state.paired && state.error == null) onPaired()
    }
    PairingScreen(
        state = state,
        onBack = onBack,
        onHostChange = viewModel::onHostChange,
        onPortChange = viewModel::onPortChange,
        onTokenChange = viewModel::onTokenChange,
        onDisplayNameChange = viewModel::onDisplayNameChange,
        onSchemeChange = viewModel::onSchemeChange,
        onScanResult = { viewModel.applyPairLink(it, autoSubmit = false) },
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
    onDisplayNameChange: (String) -> Unit,
    onSchemeChange: (String) -> Unit,
    onScanResult: (String) -> Unit,
    onSubmit: () -> Unit,
    onCameraDenied: () -> Unit = {},
    onBack: (() -> Unit)? = null,
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
        if (state.token.isNotBlank() && state.host.isBlank()) {
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

    val headline = when {
        state.replaceDeviceId != null -> stringResource(R.string.pairing_title_repair)
        onBack != null -> stringResource(R.string.pairing_title_add)
        else -> stringResource(R.string.pairing_title)
    }

    AnyaScreen(
        topBar = {
            AnyaTopBar(
                title = headline,
                subtitle = stringResource(R.string.pairing_top_subtitle),
                showBrand = true,
                navigationIcon = if (onBack != null) {
                    {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.pairing_close),
                            )
                        }
                    }
                } else {
                    null
                },
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
                        text = headline,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.pairing_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                AnyaField(
                    value = state.displayName,
                    onValueChange = onDisplayNameChange,
                    label = stringResource(R.string.pairing_display_name),
                    supportingText = stringResource(
                        R.string.pairing_display_name_hint,
                        state.displayName.length,
                        HostDisplayName.MAX_LENGTH,
                    ),
                )

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
                                text = stringResource(R.string.pairing_scan),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.pairing_scan_hint),
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
                            text = stringResource(R.string.pairing_manual),
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
                            contentDescription = if (manualOpen) {
                                stringResource(R.string.pairing_collapse)
                            } else {
                                stringResource(R.string.pairing_expand)
                            },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                AnimatedVisibility(visible = manualOpen) {
                    AnyaSurfaceCard {
                        AnyaField(
                            value = state.host,
                            onValueChange = onHostChange,
                            label = stringResource(R.string.pairing_host),
                        )
                        AnyaField(
                            value = state.port,
                            onValueChange = onPortChange,
                            label = stringResource(R.string.pairing_port),
                        )
                        SchemePicker(
                            scheme = state.scheme,
                            onSchemeChange = onSchemeChange,
                            enabled = !state.isSubmitting,
                        )
                        AnyaField(
                            value = state.token,
                            onValueChange = onTokenChange,
                            label = stringResource(R.string.pairing_token),
                        )
                    }
                }

                AnyaPrimaryButton(
                    text = if (state.isSubmitting) {
                        stringResource(R.string.pairing_submitting)
                    } else if (state.replaceDeviceId != null) {
                        stringResource(R.string.pairing_submit_repair)
                    } else {
                        stringResource(R.string.pairing_submit)
                    },
                    onClick = {
                        haptic.tick()
                        onSubmit()
                    },
                    enabled = !state.isSubmitting,
                )

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
                                stringResource(R.string.pairing_connecting_host, state.host)
                            } else {
                                stringResource(R.string.pairing_connecting)
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
private fun SchemePicker(
    scheme: String,
    onSchemeChange: (String) -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm)) {
        Text(
            text = stringResource(R.string.pairing_scheme),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
        ) {
            listOf(
                "ws" to stringResource(R.string.pairing_scheme_lan),
                "wss" to stringResource(R.string.pairing_scheme_wan),
            ).forEach { (value, label) ->
                val selected = scheme.equals(value, ignoreCase = true)
                FilterChip(
                    selected = selected,
                    onClick = { onSchemeChange(value) },
                    enabled = enabled,
                    label = {
                        Text(
                            text = label,
                            modifier = Modifier.widthIn(min = 72.dp),
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.onBackground,
                        selectedLabelColor = MaterialTheme.colorScheme.background,
                    ),
                )
            }
        }
    }
}

@Composable
private fun AnyaField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = supportingText?.let { { Text(it) } },
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
