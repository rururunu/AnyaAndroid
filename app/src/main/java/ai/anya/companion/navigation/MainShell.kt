package ai.anya.companion.navigation

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.anya.companion.R
import androidx.compose.foundation.layout.padding as layoutPadding
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.anya.companion.core.designsystem.component.AnyaBottomNavBar
import ai.anya.companion.core.designsystem.component.AnyaBottomNavItem
import ai.anya.companion.core.designsystem.component.AnyaConnectionChip
import ai.anya.companion.core.designsystem.component.AnyaFloatingActionButton
import ai.anya.companion.core.designsystem.component.AnyaMetaRow
import ai.anya.companion.core.designsystem.component.AnyaPrimaryButton
import ai.anya.companion.core.designsystem.component.AnyaScreen
import ai.anya.companion.core.designsystem.component.AnyaSecondaryButton
import ai.anya.companion.core.designsystem.component.AnyaStatusTone
import ai.anya.companion.core.designsystem.component.AnyaSurfaceCard
import ai.anya.companion.core.designsystem.component.AnyaTopBar
import ai.anya.companion.core.designsystem.component.AnyaTopBarIconChip
import ai.anya.companion.core.designsystem.haptic.rememberAnyaHaptics
import ai.anya.companion.core.designsystem.icon.AnyaIcons
import ai.anya.companion.core.designsystem.theme.AnyaSpace
import ai.anya.companion.core.domain.repository.ConnectionState
import ai.anya.companion.core.model.protocol.DeviceCredential
import ai.anya.companion.feature.approval.ApprovalTabContent
import ai.anya.companion.feature.approval.ApprovalViewModel
import ai.anya.companion.feature.sessions.SessionSearchPanel
import ai.anya.companion.feature.sessions.SessionsAskContent
import ai.anya.companion.feature.sessions.SessionsSegmentHeader
import ai.anya.companion.feature.sessions.SessionsViewModel
import ai.anya.companion.feature.sessions.SessionsWorkspaceContent
import ai.anya.companion.feature.settings.SettingsTabContent
import ai.anya.companion.feature.settings.SettingsViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlin.math.roundToInt

/** Continuous pager pages: Ask → Workspace → Inbox → Settings. */
private object MainPage {
    const val Ask = 0
    const val Workspace = 1
    const val Approvals = 2
    const val Settings = 3
    const val Count = 4

    fun bottomNavId(page: Int): String = when (page) {
        Ask, Workspace -> "conversations"
        Approvals -> "approvals"
        else -> "settings"
    }

    fun fromBottomNavId(id: String, currentPage: Int): Int = when (id) {
        "conversations" -> if (currentPage == Workspace) Workspace else Ask
        "approvals" -> Approvals
        else -> Settings
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainRoute(
    onOpenSession: (sessionId: String, messageId: String?) -> Unit,
    onNewSession: (workspaceId: String?) -> Unit,
    onOpenConnectionSettings: () -> Unit,
    onOpenGeneralSettings: () -> Unit,
    onOpenAboutSettings: () -> Unit,
    onRePair: () -> Unit,
    sessionsViewModel: SessionsViewModel = hiltViewModel(),
    approvalViewModel: ApprovalViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val haptics = rememberAnyaHaptics()
    val pagerState = rememberPagerState(
        initialPage = MainPage.Ask,
        pageCount = { MainPage.Count },
    )
    var showConnectionPanel by rememberSaveable { mutableStateOf(false) }
    var showSearchPanel by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val searchSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var suppressNextSettleHaptic by rememberSaveable { mutableStateOf(false) }

    val sessionsState by sessionsViewModel.state.collectAsStateWithLifecycle()
    val searchState by sessionsViewModel.searchState.collectAsStateWithLifecycle()
    val approvalState by approvalViewModel.state.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()

    val currentPage = pagerState.currentPage
    val absolutePage = currentPage + pagerState.currentPageOffsetFraction
    val selectedNavId = MainPage.bottomNavId(
        absolutePage.roundToInt().coerceIn(0, MainPage.Count - 1),
    )
    val topSubtitle = when (currentPage) {
        MainPage.Approvals -> stringResource(R.string.nav_subtitle_inbox)
        MainPage.Settings -> stringResource(R.string.nav_subtitle_settings)
        else -> stringResource(R.string.nav_subtitle_chats)
    }
    val segmentProgress = absolutePage.coerceIn(0f, 1f)
    val showSegmentHeader = absolutePage < 2f
    val segmentHeaderAlpha = when {
        absolutePage <= 1f -> 1f
        absolutePage >= 2f -> 0f
        else -> (2f - absolutePage).coerceIn(0f, 1f)
    }

    val tone = when (sessionsState.connectionState) {
        ConnectionState.Connected -> AnyaStatusTone.Success
        ConnectionState.Connecting, ConnectionState.Reconnecting -> AnyaStatusTone.Info
        ConnectionState.Error -> AnyaStatusTone.Danger
        ConnectionState.Disconnected -> AnyaStatusTone.Neutral
    }
    val statusLabel = when (sessionsState.connectionState) {
        ConnectionState.Connected -> {
            val pending = sessionsState.pendingApprovals.size
            if (pending > 0) {
                stringResource(R.string.status_connected_pending, pending)
            } else {
                stringResource(R.string.status_connected)
            }
        }
        ConnectionState.Connecting, ConnectionState.Reconnecting ->
            stringResource(R.string.status_connecting)
        ConnectionState.Error -> stringResource(R.string.status_error)
        ConnectionState.Disconnected -> stringResource(R.string.status_disconnected)
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .drop(1)
            .filter { pagerState.currentPageOffsetFraction == 0f }
            .collect {
                if (suppressNextSettleHaptic) {
                    suppressNextSettleHaptic = false
                } else {
                    haptics.linearTick()
                }
            }
    }

    fun selectPage(page: Int) {
        suppressNextSettleHaptic = true
        scope.launch {
            pagerState.animateScrollToPage(page.coerceIn(0, MainPage.Count - 1))
        }
    }

    fun selectTab(navId: String) {
        selectPage(MainPage.fromBottomNavId(navId, currentPage))
    }

    fun dismissConnectionPanel(after: (() -> Unit)? = null) {
        scope.launch {
            sheetState.hide()
            showConnectionPanel = false
            after?.invoke()
        }
    }

    AnyaScreen(
        topBar = {
            AnyaTopBar(
                title = "Anya",
                showBrand = true,
                subtitle = topSubtitle,
                actions = {
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
                        modifier = Modifier.layoutPadding(end = AnyaSpace.Sm),
                    ) {
                        AnyaTopBarIconChip(
                            icon = Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.nav_search_chats),
                            onClick = {
                                haptics.buttonPress()
                                showSearchPanel = true
                            },
                        )
                        AnyaConnectionChip(
                            label = statusLabel,
                            tone = tone,
                            onClick = {
                                haptics.buttonPress()
                                showConnectionPanel = true
                            },
                        )
                    }
                },
            )
        },
        bottomBar = {
            AnyaBottomNavBar(
                items = listOf(
                    AnyaBottomNavItem(
                        id = "conversations",
                        label = stringResource(R.string.nav_conversations),
                        icon = AnyaIcons.ChatCircleOutline,
                        selectedIcon = AnyaIcons.ChatCircle,
                    ),
                    AnyaBottomNavItem(
                        id = "approvals",
                        label = stringResource(R.string.nav_inbox),
                        icon = Icons.Outlined.Inbox,
                        selectedIcon = Icons.Rounded.Inbox,
                        badge = sessionsState.pendingApprovals.size,
                    ),
                    AnyaBottomNavItem(
                        id = "settings",
                        label = stringResource(R.string.nav_settings),
                        icon = Icons.Rounded.Settings,
                        selectedIcon = Icons.Rounded.Settings,
                        badge = if (settingsState.availableUpdateVersion != null) 1 else 0,
                    ),
                ),
                selectedId = selectedNavId,
                onSelect = ::selectTab,
            )
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Segment stays pinned; only the pager content below slides.
            if (showSegmentHeader) {
                SessionsSegmentHeader(
                    selectedIndex = segmentProgress.roundToInt().coerceIn(0, 1),
                    selectedProgress = segmentProgress,
                    onSegmentSelect = ::selectPage,
                    modifier = Modifier.graphicsLayer { alpha = segmentHeaderAlpha },
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                beyondViewportPageCount = 1,
                key = { page ->
                    when (page) {
                        MainPage.Ask -> "ask"
                        MainPage.Workspace -> "workspace"
                        MainPage.Approvals -> "approvals"
                        else -> "settings"
                    }
                },
            ) { page ->
                when (page) {
                    MainPage.Ask -> {
                        SessionsAskContent(
                            state = sessionsState,
                            onOpenSession = { id ->
                                haptics.linearTick()
                                onOpenSession(id, null)
                            },
                            onRefresh = {
                                haptics.buttonPress()
                                sessionsViewModel.refresh()
                            },
                            onDeleteSession = sessionsViewModel::deleteSession,
                        )
                    }
                    MainPage.Workspace -> {
                        SessionsWorkspaceContent(
                            state = sessionsState,
                            onToggleWorkspace = sessionsViewModel::toggleWorkspace,
                            onOpenSession = { id ->
                                haptics.linearTick()
                                onOpenSession(id, null)
                            },
                            onRefresh = {
                                haptics.buttonPress()
                                sessionsViewModel.refresh()
                            },
                            onDeleteSession = sessionsViewModel::deleteSession,
                            onNewSessionInWorkspace = { workspaceId ->
                                haptics.buttonPress()
                                onNewSession(workspaceId)
                            },
                        )
                    }
                    MainPage.Approvals -> {
                        ApprovalTabContent(
                            state = approvalState,
                            onOpenSession = { id ->
                                haptics.linearTick()
                                onOpenSession(id, null)
                            },
                        )
                    }
                    else -> {
                        SettingsTabContent(
                            state = settingsState,
                            onOpenConnection = {
                                haptics.linearTick()
                                onOpenConnectionSettings()
                            },
                            onOpenGeneral = {
                                haptics.linearTick()
                                onOpenGeneralSettings()
                            },
                            onOpenAbout = {
                                haptics.linearTick()
                                onOpenAboutSettings()
                            },
                        )
                    }
                }
            }
        }

        // Circular new-chat FAB: 随问 only — workspaces create sessions from the row +.
        if (absolutePage < 1f) {
            AnyaFloatingActionButton(
                onClick = { onNewSession(null) },
                icon = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.nav_new_chat),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .layoutPadding(end = AnyaSpace.Screen, bottom = AnyaSpace.Xl)
                    .graphicsLayer { alpha = (1f - absolutePage).coerceIn(0f, 1f) },
            )
        }
        }
    }

    if (showSearchPanel) {
        ModalBottomSheet(
            onDismissRequest = {
                showSearchPanel = false
                sessionsViewModel.clearSearch()
            },
            sheetState = searchSheetState,
            containerColor = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
            dragHandle = { AnyaSheetDragHandle() },
        ) {
            SessionSearchPanel(
                state = searchState,
                onQueryChange = sessionsViewModel::onSearchQueryChange,
                onOpenSession = { sessionId, messageId ->
                    haptics.linearTick()
                    // Navigate immediately; do not wait for sheet hide (can stall).
                    showSearchPanel = false
                    sessionsViewModel.clearSearch()
                    onOpenSession(sessionId, messageId)
                },
            )
        }
    }

    if (showConnectionPanel) {
        ModalBottomSheet(
            onDismissRequest = { showConnectionPanel = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
            dragHandle = { AnyaSheetDragHandle() },
        ) {
            ConnectionPanel(
                connectionState = sessionsState.connectionState,
                credential = settingsState.credential,
                tone = tone,
                statusLabel = statusLabel,
                pendingCount = sessionsState.pendingApprovals.size,
                onConnect = settingsViewModel::connect,
                onDisconnect = settingsViewModel::disconnect,
                onUnpair = {
                    dismissConnectionPanel {
                        settingsViewModel.unpair()
                        onRePair()
                    }
                },
                onGoPair = {
                    dismissConnectionPanel(onRePair)
                },
                onOpenInbox = {
                    dismissConnectionPanel {
                        selectPage(MainPage.Approvals)
                    }
                },
                onDismiss = { dismissConnectionPanel() },
            )
        }
    }
}

@Composable
private fun AnyaSheetDragHandle() {
    Box(
        modifier = Modifier
            .padding(top = 10.dp, bottom = 6.dp)
            .size(width = 36.dp, height = 4.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
    )
}

@Composable
private fun ConnectionPanel(
    connectionState: ConnectionState,
    credential: DeviceCredential?,
    tone: AnyaStatusTone,
    statusLabel: String,
    pendingCount: Int,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onUnpair: () -> Unit,
    onGoPair: () -> Unit,
    onOpenInbox: () -> Unit,
    onDismiss: () -> Unit,
) {
    val canConnect = credential != null &&
        connectionState != ConnectionState.Connected &&
        connectionState != ConnectionState.Connecting &&
        connectionState != ConnectionState.Reconnecting
    val canDisconnect = connectionState == ConnectionState.Connected ||
        connectionState == ConnectionState.Connecting ||
        connectionState == ConnectionState.Reconnecting

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AnyaSpace.Screen)
            .padding(bottom = AnyaSpace.Xxl),
        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Md),
    ) {
        Text(
            text = stringResource(R.string.connection_panel_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.connection_panel_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AnyaSurfaceCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = stringResource(R.string.connection_panel_status), style = MaterialTheme.typography.bodyMedium)
                AnyaConnectionChip(label = statusLabel, tone = tone)
            }
            if (credential == null) {
                Text(
                    text = stringResource(R.string.connection_panel_not_paired),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                AnyaMetaRow(label = stringResource(R.string.connection_panel_host), value = credential.host)
                AnyaMetaRow(label = stringResource(R.string.connection_panel_port), value = credential.port.toString())
                AnyaMetaRow(label = stringResource(R.string.connection_panel_device), value = credential.deviceId.take(8) + "…")
            }
        }
        if (pendingCount > 0) {
            AnyaSecondaryButton(
                text = stringResource(R.string.connection_panel_view_inbox, pendingCount),
                onClick = onOpenInbox,
            )
        }
        when {
            canConnect -> AnyaPrimaryButton(text = stringResource(R.string.connection_panel_reconnect), onClick = onConnect)
            canDisconnect -> AnyaPrimaryButton(text = stringResource(R.string.connection_panel_disconnect), onClick = onDisconnect)
            credential == null -> AnyaPrimaryButton(text = stringResource(R.string.connection_panel_go_pair), onClick = onGoPair)
        }
        if (credential != null) {
            AnyaSecondaryButton(text = stringResource(R.string.connection_panel_unpair), onClick = onUnpair)
        }
        AnyaSecondaryButton(text = stringResource(R.string.connection_panel_close), onClick = onDismiss)
        Spacer(modifier = Modifier.height(AnyaSpace.Sm))
    }
}
