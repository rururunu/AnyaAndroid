package ai.anya.companion.feature.settings

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PhonelinkErase
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import ai.anya.companion.core.designsystem.component.AnyaBrandMark
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.anya.companion.core.designsystem.component.AnyaOverlayScaffold
import ai.anya.companion.core.designsystem.component.AnyaPrimaryButton
import ai.anya.companion.core.designsystem.component.AnyaSecondaryButton
import ai.anya.companion.core.designsystem.haptic.rememberAnyaHaptics
import ai.anya.companion.core.designsystem.theme.AnyaColors
import ai.anya.companion.core.designsystem.theme.AnyaSpace
import ai.anya.companion.core.domain.repository.ConnectionState
import ai.anya.companion.core.model.protocol.DeviceCredential
import ai.anya.companion.core.model.protocol.HostDisplayName
import ai.anya.companion.core.model.settings.AppLanguage

@Composable
public fun SettingsTabContent(
    state: SettingsUiState,
    onOpenConnection: () -> Unit,
    onOpenGeneral: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    SettingsHub(
        connectionState = state.connectionState,
        availableUpdateVersion = state.availableUpdateVersion,
        onOpenConnection = onOpenConnection,
        onOpenGeneral = onOpenGeneral,
        onOpenAbout = onOpenAbout,
    )
}

@Composable
public fun ConnectionSettingsRoute(
    onBack: () -> Unit,
    onAddDevice: () -> Unit,
    onRepairDevice: (deviceId: String) -> Unit,
    onUnpairLast: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AnyaOverlayScaffold(
        onBack = onBack,
        title = stringResource(R.string.settings_connection),
    ) { padding ->
        ConnectionSettingsPage(
            state = state,
            onDisconnect = viewModel::disconnect,
            onConnect = viewModel::connect,
            onSwitchDevice = viewModel::switchDevice,
            onRenameDevice = viewModel::renameDevice,
            onRepairDevice = onRepairDevice,
            onRemoveDevice = { deviceId ->
                val last = state.pairedDevices.size <= 1
                viewModel.removeDevice(deviceId)
                if (last) onUnpairLast()
            },
            onAddDevice = onAddDevice,
            onGoPair = onAddDevice,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
public fun GeneralSettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    AnyaOverlayScaffold(
        onBack = onBack,
        title = stringResource(R.string.settings_general),
    ) { padding ->
        GeneralSettingsPage(
            language = state.language,
            onLanguageChange = { language ->
                viewModel.setLanguage(language)
                (context as? Activity)?.recreate()
            },
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
public fun AboutSettingsRoute(
    onBack: () -> Unit,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.onInstallPermissionReturned()
    }
    LaunchedEffect(state.needsInstallPermission) {
        if (state.needsInstallPermission) {
            permissionLauncher.launch(viewModel.installPermissionIntent())
        }
    }
    AnyaOverlayScaffold(
        onBack = onBack,
        title = stringResource(R.string.settings_about),
    ) { padding ->
        AboutSettingsPage(
            state = state,
            onCheck = viewModel::check,
            onInstall = viewModel::install,
            onSnooze = viewModel::snooze,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun SettingsHub(
    connectionState: ConnectionState,
    availableUpdateVersion: String?,
    onOpenConnection: () -> Unit,
    onOpenGeneral: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val haptic = rememberAnyaHaptics()
    val connectionSummary = when (connectionState) {
        ConnectionState.Connected -> stringResource(R.string.settings_status_connected)
        ConnectionState.Connecting, ConnectionState.Reconnecting ->
            stringResource(R.string.settings_status_connecting)
        ConnectionState.Error -> stringResource(R.string.settings_status_error)
        ConnectionState.Disconnected -> stringResource(R.string.settings_status_disconnected)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AnyaSpace.Screen, vertical = AnyaSpace.Sm),
    ) {
        SettingsMenuRow(
            icon = Icons.Rounded.Link,
            title = stringResource(R.string.settings_connection),
            subtitle = connectionSummary,
            onClick = {
                haptic.linearTick()
                onOpenConnection()
            },
        )
        SettingsMenuRow(
            icon = Icons.Rounded.Language,
            title = stringResource(R.string.settings_general),
            subtitle = stringResource(R.string.settings_general_subtitle),
            onClick = {
                haptic.linearTick()
                onOpenGeneral()
            },
        )
        SettingsMenuRow(
            icon = Icons.Rounded.Info,
            title = stringResource(R.string.settings_about),
            subtitle = if (availableUpdateVersion != null) {
                stringResource(R.string.settings_about_update_available, availableUpdateVersion)
            } else {
                stringResource(R.string.settings_about_subtitle)
            },
            showBadge = availableUpdateVersion != null,
            onClick = {
                haptic.linearTick()
                onOpenAbout()
            },
        )
    }
}

@Composable
private fun ConnectionSettingsPage(
    state: SettingsUiState,
    onDisconnect: () -> Unit,
    onConnect: () -> Unit,
    onSwitchDevice: (String) -> Unit,
    onRenameDevice: (String, String) -> Unit,
    onRepairDevice: (String) -> Unit,
    onRemoveDevice: (String) -> Unit,
    onAddDevice: () -> Unit,
    onGoPair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberAnyaHaptics()
    var actionsFor by remember { mutableStateOf<DeviceCredential?>(null) }
    var renameFor by remember { mutableStateOf<DeviceCredential?>(null) }
    var unpairFor by remember { mutableStateOf<DeviceCredential?>(null) }
    val statusTitle = when (state.connectionState) {
        ConnectionState.Connected -> stringResource(R.string.settings_status_connected)
        ConnectionState.Connecting, ConnectionState.Reconnecting ->
            stringResource(R.string.settings_status_connecting)
        ConnectionState.Error -> stringResource(R.string.settings_status_error)
        ConnectionState.Disconnected -> stringResource(R.string.settings_status_disconnected)
    }
    val statusHint = when (state.connectionState) {
        ConnectionState.Connected -> stringResource(R.string.settings_hint_connected)
        ConnectionState.Connecting, ConnectionState.Reconnecting ->
            stringResource(R.string.settings_hint_connecting)
        ConnectionState.Error -> stringResource(R.string.settings_hint_error)
        ConnectionState.Disconnected -> stringResource(R.string.settings_hint_disconnected)
    }
    val toneColor = when (state.connectionState) {
        ConnectionState.Connected -> AnyaColors.Success
        ConnectionState.Error -> AnyaColors.Danger
        ConnectionState.Connecting, ConnectionState.Reconnecting -> AnyaColors.Info
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val canConnect = state.credential != null &&
        state.connectionState != ConnectionState.Connected &&
        state.connectionState != ConnectionState.Connecting &&
        state.connectionState != ConnectionState.Reconnecting
    val canDisconnect = state.connectionState == ConnectionState.Connected ||
        state.connectionState == ConnectionState.Connecting ||
        state.connectionState == ConnectionState.Reconnecting
    val credential = state.credential
    val devices = state.pairedDevices

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsMenuRow(
            icon = if (state.connectionState == ConnectionState.Connected) {
                Icons.Rounded.Link
            } else {
                Icons.Rounded.LinkOff
            },
            title = statusTitle,
            subtitle = if (credential != null) {
                "${credential.resolvedDisplayName()} · $statusHint"
            } else {
                statusHint
            },
            trailing = {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(toneColor),
                )
            },
        )

        SettingsSectionLabel(stringResource(R.string.settings_devices))
        if (devices.isEmpty()) {
            SettingsMenuRow(
                icon = Icons.Rounded.PhonelinkErase,
                title = stringResource(R.string.settings_not_paired),
                subtitle = stringResource(R.string.settings_not_paired_hint),
            )
        } else {
            devices.forEach { device ->
                val active = device.deviceId == credential?.deviceId
                SettingsDeviceRow(
                    device = device,
                    active = active,
                    connected = active && state.connectionState == ConnectionState.Connected,
                    onClick = {
                        haptic.linearTick()
                        if (!active) onSwitchDevice(device.deviceId)
                    },
                    onMore = {
                        haptic.tick()
                        actionsFor = device
                    },
                )
            }
        }

        SettingsActionRow(
            icon = Icons.Rounded.Add,
            title = stringResource(R.string.settings_add_device),
            onClick = {
                haptic.buttonPress()
                onAddDevice()
            },
        )

        if (credential != null) {
            SettingsInfoRow(
                label = stringResource(R.string.settings_host),
                value = credential.host,
            )
            SettingsInfoRow(
                label = stringResource(R.string.settings_port),
                value = credential.port.toString(),
            )
        }

        Spacer(modifier = Modifier.height(AnyaSpace.Lg))

        if (canConnect) {
            SettingsActionRow(
                icon = Icons.Rounded.Refresh,
                title = stringResource(R.string.settings_reconnect),
                onClick = {
                    haptic.buttonPress()
                    onConnect()
                },
            )
        }
        if (canDisconnect) {
            SettingsActionRow(
                icon = Icons.Rounded.LinkOff,
                title = stringResource(R.string.settings_disconnect),
                onClick = {
                    haptic.buttonPress()
                    onDisconnect()
                },
            )
        }
        if (devices.isEmpty()) {
            SettingsActionRow(
                icon = Icons.Rounded.Link,
                title = stringResource(R.string.settings_go_pair),
                onClick = {
                    haptic.buttonPress()
                    onGoPair()
                },
            )
        }
    }

    actionsFor?.let { device ->
        DeviceActionsSheet(
            device = device,
            onRename = {
                actionsFor = null
                renameFor = device
            },
            onRepair = {
                actionsFor = null
                onRepairDevice(device.deviceId)
            },
            onUnpair = {
                actionsFor = null
                unpairFor = device
            },
            onDismiss = { actionsFor = null },
        )
    }
    renameFor?.let { device ->
        RenameDeviceSheet(
            device = device,
            onConfirm = { name ->
                onRenameDevice(device.deviceId, name)
                renameFor = null
            },
            onDismiss = { renameFor = null },
        )
    }
    unpairFor?.let { device ->
        UnpairConfirmSheet(
            hostName = device.resolvedDisplayName(),
            onConfirm = {
                unpairFor = null
                onRemoveDevice(device.deviceId)
            },
            onDismiss = { unpairFor = null },
        )
    }
}

@Composable
private fun SettingsDeviceRow(
    device: DeviceCredential,
    active: Boolean,
    connected: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Lg),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (active) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Computer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = device.resolvedDisplayName(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    connected -> stringResource(R.string.settings_device_connected)
                    active -> stringResource(R.string.settings_device_current)
                    else -> device.host
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (connected) AnyaColors.Success else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (active) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onMore) {
            Icon(
                imageVector = Icons.Rounded.MoreHoriz,
                contentDescription = stringResource(R.string.settings_device_actions),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceActionsSheet(
    device: DeviceCredential,
    onRename: () -> Unit,
    onRepair: () -> Unit,
    onUnpair: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = rememberAnyaHaptics()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AnyaSpace.Screen)
                .padding(bottom = AnyaSpace.Xxl),
            verticalArrangement = Arrangement.spacedBy(AnyaSpace.Xs),
        ) {
            Text(
                text = device.resolvedDisplayName(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = device.host,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(AnyaSpace.Sm))
            SettingsActionRow(
                icon = Icons.Rounded.Edit,
                title = stringResource(R.string.settings_rename_device),
                onClick = {
                    haptic.tick()
                    onRename()
                },
            )
            SettingsActionRow(
                icon = Icons.Rounded.QrCode2,
                title = stringResource(R.string.settings_repair_device),
                onClick = {
                    haptic.buttonPress()
                    onRepair()
                },
            )
            SettingsActionRow(
                icon = Icons.Rounded.PhonelinkErase,
                title = stringResource(R.string.settings_unpair),
                destructive = true,
                onClick = {
                    haptic.tick()
                    onUnpair()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenameDeviceSheet(
    device: DeviceCredential,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = rememberAnyaHaptics()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(device.resolvedDisplayName()) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AnyaSpace.Screen)
                .padding(bottom = AnyaSpace.Xxl),
            verticalArrangement = Arrangement.spacedBy(AnyaSpace.Md),
        ) {
            Text(
                text = stringResource(R.string.settings_rename_device),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = HostDisplayName.sanitize(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_device_name)) },
                supportingText = {
                    Text(
                        stringResource(
                            R.string.settings_device_name_hint,
                            name.length,
                            HostDisplayName.MAX_LENGTH,
                        ),
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(AnyaSpace.ControlRadius),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
            AnyaPrimaryButton(
                text = stringResource(R.string.settings_save_name),
                onClick = {
                    haptic.confirm()
                    onConfirm(name)
                },
            )
            AnyaSecondaryButton(
                text = stringResource(R.string.settings_cancel),
                onClick = {
                    haptic.tick()
                    onDismiss()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun HostSwitcherSheet(
    devices: List<DeviceCredential>,
    activeDeviceId: String?,
    connectionState: ConnectionState,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = rememberAnyaHaptics()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AnyaSpace.Screen)
                .padding(bottom = AnyaSpace.Xxl),
            verticalArrangement = Arrangement.spacedBy(AnyaSpace.Xs),
        ) {
            Text(
                text = stringResource(R.string.settings_switch_host),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.settings_switch_host_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(AnyaSpace.Sm))
            devices.forEach { device ->
                val active = device.deviceId == activeDeviceId
                SettingsSelectRow(
                    title = device.resolvedDisplayName(),
                    subtitle = if (active && connectionState == ConnectionState.Connected) {
                        stringResource(R.string.settings_device_connected)
                    } else {
                        device.host
                    },
                    selected = active,
                    onSelect = {
                        haptic.linearTick()
                        if (!active) onSelect(device.deviceId)
                        onDismiss()
                    },
                )
            }
            Spacer(modifier = Modifier.height(AnyaSpace.Sm))
            SettingsActionRow(
                icon = Icons.Rounded.Add,
                title = stringResource(R.string.settings_add_device),
                onClick = {
                    haptic.buttonPress()
                    onAdd()
                },
            )
        }
    }
}

@Composable
private fun GeneralSettingsPage(
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberAnyaHaptics()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSectionLabel(stringResource(R.string.settings_language))
        SettingsSelectRow(
            title = stringResource(R.string.settings_language_system),
            subtitle = stringResource(R.string.settings_language_system_hint),
            selected = language == AppLanguage.System,
            onSelect = {
                haptic.tick()
                if (language != AppLanguage.System) onLanguageChange(AppLanguage.System)
            },
        )
        SettingsSelectRow(
            title = stringResource(R.string.settings_language_chinese),
            selected = language == AppLanguage.Chinese,
            onSelect = {
                haptic.tick()
                if (language != AppLanguage.Chinese) onLanguageChange(AppLanguage.Chinese)
            },
        )
        SettingsSelectRow(
            title = stringResource(R.string.settings_language_english),
            selected = language == AppLanguage.English,
            onSelect = {
                haptic.tick()
                if (language != AppLanguage.English) onLanguageChange(AppLanguage.English)
            },
        )
    }
}

@Composable
private fun AboutSettingsPage(
    state: AboutUiState,
    onCheck: () -> Unit,
    onInstall: () -> Unit,
    onSnooze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberAnyaHaptics()
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1400)
            copied = false
        }
    }
    val busy = state.status == AboutUpdateStatus.Checking ||
        state.status == AboutUpdateStatus.Downloading
    val showUpdateDetails = state.latestVersion.isNotEmpty() &&
        state.status != AboutUpdateStatus.Idle &&
        state.status != AboutUpdateStatus.Checking &&
        state.status != AboutUpdateStatus.UpToDate
    val updateLabel = when (state.status) {
        AboutUpdateStatus.Error -> if (showUpdateDetails) {
            stringResource(R.string.about_update_retry)
        } else {
            stringResource(R.string.about_update_check)
        }
        AboutUpdateStatus.Ready -> stringResource(R.string.about_update_now)
        AboutUpdateStatus.Available, AboutUpdateStatus.Downloading ->
            stringResource(R.string.about_update_now)
        else -> stringResource(R.string.about_update_check)
    }
    val updateValue = when (state.status) {
        AboutUpdateStatus.Idle -> ""
        AboutUpdateStatus.Checking -> stringResource(R.string.about_update_checking)
        AboutUpdateStatus.Downloading -> {
            val percent = if (state.totalBytes > 0) {
                "${((state.downloadedBytes * 100) / state.totalBytes).coerceIn(0, 100)}%"
            } else {
                stringResource(R.string.about_update_downloading)
            }
            if (state.metered) {
                "$percent · ${stringResource(R.string.about_update_metered)}"
            } else {
                percent
            }
        }
        AboutUpdateStatus.Ready -> stringResource(R.string.about_update_tap_install)
        AboutUpdateStatus.Available -> stringResource(R.string.about_update_tap_install)
        AboutUpdateStatus.UpToDate -> stringResource(R.string.about_update_up_to_date)
        AboutUpdateStatus.Error -> aboutUpdateErrorText(state.errorMessage)
    }
    val latestValue = buildString {
        append(state.latestVersion)
        if (state.latestSizeBytes > 0) {
            append(" · ")
            append(formatFileSize(state.latestSizeBytes))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(AnyaSpace.Md))
        AnyaBrandMark(size = 120.dp, clipped = false)
        Spacer(modifier = Modifier.height(AnyaSpace.Lg))
        Text(
            text = state.appName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.about_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = AnyaSpace.Sm),
        )
        Text(
            text = stringResource(R.string.about_version_label, state.version),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = AnyaSpace.Sm),
        )

        Spacer(modifier = Modifier.height(AnyaSpace.Xxl))
        Column(modifier = Modifier.fillMaxWidth()) {
            SettingsSectionLabel(stringResource(R.string.about_application))
            SettingsInfoRow(
                label = stringResource(R.string.about_app_name),
                value = state.appName,
            )
            SettingsInfoRow(
                label = stringResource(R.string.about_version),
                value = state.version,
            )
            SettingsInfoRow(
                label = stringResource(R.string.about_identifier),
                value = if (copied) stringResource(R.string.about_copied) else state.identifier,
                onClick = {
                    clipboard.setText(AnnotatedString(state.identifier))
                    copied = true
                },
            )
            SettingsInfoRow(
                label = stringResource(R.string.about_runtime),
                value = stringResource(R.string.about_runtime_value),
            )
            if (showUpdateDetails) {
                SettingsInfoRow(
                    label = stringResource(R.string.about_update_latest),
                    value = latestValue,
                    emphasize = true,
                )
                if (state.latestNotes.isNotBlank()) {
                    Text(
                        text = state.latestNotes.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = AnyaSpace.Sm),
                    )
                }
                SettingsInfoRow(
                    label = updateLabel,
                    value = updateValue,
                    emphasize = true,
                    maxLines = 1,
                    onClick = if (busy) {
                        null
                    } else {
                        {
                            haptic.confirm()
                            onInstall()
                        }
                    },
                )
                if (state.status != AboutUpdateStatus.Downloading) {
                    SettingsInfoRow(
                        label = stringResource(R.string.about_update_snooze),
                        value = "",
                        maxLines = 1,
                        onClick = {
                            haptic.buttonPress()
                            onSnooze()
                        },
                    )
                }
            } else {
                SettingsInfoRow(
                    label = updateLabel,
                    value = updateValue,
                    maxLines = 1,
                    onClick = if (busy) {
                        null
                    } else {
                        {
                            haptic.buttonPress()
                            onCheck()
                        }
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(AnyaSpace.Lg))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AnyaSpace.Md),
            horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Md),
        ) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Column {
                Text(
                    text = stringResource(R.string.about_privacy),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.about_privacy_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(AnyaSpace.Xxl))
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = AnyaSpace.Xs, bottom = AnyaSpace.Xs),
    )
}

@Composable
private fun SettingsMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    showBadge: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Lg),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (showBadge) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (showBadge) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(AnyaColors.Danger),
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun SettingsInfoRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    maxLines: Int = 2,
    emphasize: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Lg),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (emphasize) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (emphasize) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            textAlign = TextAlign.End,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SettingsSelectRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AnyaSpace.ControlRadius))
            .background(
                if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                else Color.Transparent,
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = AnyaSpace.Sm, vertical = AnyaSpace.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Md),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val tint = if (destructive) AnyaColors.Danger else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Lg),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (destructive) AnyaColors.Danger.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = tint,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnpairConfirmSheet(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    hostName: String? = null,
) {
    val haptics = rememberAnyaHaptics()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AnyaSpace.Screen)
                .padding(bottom = AnyaSpace.Xxl),
            verticalArrangement = Arrangement.spacedBy(AnyaSpace.Md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PhonelinkErase,
                    contentDescription = null,
                    tint = AnyaColors.Danger,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = stringResource(R.string.settings_unpair_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = if (hostName.isNullOrBlank()) {
                    stringResource(R.string.settings_unpair_body)
                } else {
                    stringResource(R.string.settings_unpair_body_named, hostName)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnyaPrimaryButton(
                text = stringResource(R.string.settings_unpair_confirm),
                onClick = {
                    haptics.confirm()
                    onConfirm()
                },
            )
            AnyaSecondaryButton(
                text = stringResource(R.string.settings_cancel),
                onClick = {
                    haptics.tick()
                    onDismiss()
                },
            )
            Spacer(modifier = Modifier.height(AnyaSpace.Sm))
        }
    }
}

@Composable
private fun aboutUpdateErrorText(code: String?): String = when (code) {
    "no-release" -> stringResource(R.string.about_update_no_release)
    "size-mismatch" -> stringResource(R.string.about_update_size_mismatch)
    "package-mismatch" -> stringResource(R.string.about_update_package_mismatch)
    "signature-mismatch" -> stringResource(R.string.about_update_signature_mismatch)
    "empty-apk" -> stringResource(R.string.about_update_empty_apk)
    "checksum-mismatch" -> stringResource(R.string.about_update_checksum_mismatch)
    else -> stringResource(R.string.about_update_error, code.orEmpty())
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb)
    return String.format(java.util.Locale.US, "%.1f MB", kb / 1024.0)
}
