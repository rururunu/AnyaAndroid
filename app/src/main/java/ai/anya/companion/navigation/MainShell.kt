package ai.anya.companion.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.padding as layoutPadding
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.anya.companion.core.designsystem.component.AnyaBottomNavBar
import ai.anya.companion.core.designsystem.component.AnyaBottomNavItem
import ai.anya.companion.core.designsystem.component.AnyaConnectionChip
import ai.anya.companion.core.designsystem.component.AnyaMetaRow
import ai.anya.companion.core.designsystem.component.AnyaPrimaryButton
import ai.anya.companion.core.designsystem.component.AnyaScreen
import ai.anya.companion.core.designsystem.component.AnyaSecondaryButton
import ai.anya.companion.core.designsystem.component.AnyaStatusTone
import ai.anya.companion.core.designsystem.component.AnyaSurfaceCard
import ai.anya.companion.core.designsystem.component.AnyaTopBar
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

    fun subtitle(page: Int): String = when (page) {
        Approvals -> "收件"
        Settings -> "设置"
        else -> "对话"
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
    onRePair: () -> Unit,
    sessionsViewModel: SessionsViewModel = hiltViewModel(),
    approvalViewModel: ApprovalViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val pagerState = rememberPagerState(
        initialPage = MainPage.Ask,
        pageCount = { MainPage.Count },
    )
    var showConnectionPanel by rememberSaveable { mutableStateOf(false) }
    var showSearchPanel by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val searchSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val sessionsState by sessionsViewModel.state.collectAsStateWithLifecycle()
    val searchState by sessionsViewModel.searchState.collectAsStateWithLifecycle()
    val approvalState by approvalViewModel.state.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()

    val currentPage = pagerState.currentPage
    val absolutePage = currentPage + pagerState.currentPageOffsetFraction
    val selectedNavId = MainPage.bottomNavId(
        absolutePage.roundToInt().coerceIn(0, MainPage.Count - 1),
    )
    val topSubtitle = MainPage.subtitle(currentPage)
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
            if (pending > 0) "已连接 · $pending" else "已连接"
        }
        ConnectionState.Connecting, ConnectionState.Reconnecting -> "连接中"
        ConnectionState.Error -> "连接失败"
        ConnectionState.Disconnected -> "未连接"
    }

    fun selectPage(page: Int) {
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
                    IconButton(onClick = { showSearchPanel = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "搜索对话",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    AnyaConnectionChip(
                        label = statusLabel,
                        tone = tone,
                        modifier = Modifier.layoutPadding(end = AnyaSpace.Sm),
                        onClick = { showConnectionPanel = true },
                    )
                },
            )
        },
        bottomBar = {
            AnyaBottomNavBar(
                items = listOf(
                    AnyaBottomNavItem(
                        id = "conversations",
                        label = "对话",
                        icon = AnyaIcons.ChatCircleOutline,
                        selectedIcon = AnyaIcons.ChatCircle,
                    ),
                    AnyaBottomNavItem(
                        id = "approvals",
                        label = "收件",
                        icon = Icons.Outlined.Inbox,
                        selectedIcon = Icons.Rounded.Inbox,
                        badge = sessionsState.pendingApprovals.size,
                    ),
                    AnyaBottomNavItem(
                        id = "settings",
                        label = "设置",
                        icon = Icons.Rounded.Settings,
                        selectedIcon = Icons.Rounded.Settings,
                    ),
                ),
                selectedId = selectedNavId,
                onSelect = ::selectTab,
            )
        },
    ) {
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
                            onOpenSession = { id -> onOpenSession(id, null) },
                            onRefresh = sessionsViewModel::refresh,
                        )
                    }
                    MainPage.Workspace -> {
                        SessionsWorkspaceContent(
                            state = sessionsState,
                            onToggleWorkspace = sessionsViewModel::toggleWorkspace,
                            onOpenSession = { id -> onOpenSession(id, null) },
                            onRefresh = sessionsViewModel::refresh,
                        )
                    }
                    MainPage.Approvals -> {
                        ApprovalTabContent(
                            state = approvalState,
                            onOpenSession = { id -> onOpenSession(id, null) },
                        )
                    }
                    else -> {
                        SettingsTabContent(
                            state = settingsState,
                            onDisconnect = settingsViewModel::disconnect,
                            onConnect = settingsViewModel::connect,
                            onUnpair = {
                                settingsViewModel.unpair()
                                onRePair()
                            },
                        )
                    }
                }
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
        ) {
            SessionSearchPanel(
                state = searchState,
                onQueryChange = sessionsViewModel::onSearchQueryChange,
                onOpenSession = { sessionId, messageId ->
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
            text = "连接",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        AnyaSurfaceCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "状态", style = MaterialTheme.typography.bodyMedium)
                AnyaConnectionChip(label = statusLabel, tone = tone)
            }
            if (credential == null) {
                Text(
                    text = "尚未配对桌面端，请先扫码或输入配对码。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                AnyaMetaRow(label = "主机", value = credential.host)
                AnyaMetaRow(label = "端口", value = credential.port.toString())
                AnyaMetaRow(label = "设备", value = credential.deviceId.take(8) + "…")
            }
        }
        if (pendingCount > 0) {
            AnyaSecondaryButton(
                text = "查看收件（$pendingCount）",
                onClick = onOpenInbox,
            )
        }
        when {
            canConnect -> AnyaPrimaryButton(text = "重新连接", onClick = onConnect)
            canDisconnect -> AnyaPrimaryButton(text = "断开连接", onClick = onDisconnect)
            credential == null -> AnyaPrimaryButton(text = "去配对", onClick = onGoPair)
        }
        if (credential != null) {
            AnyaSecondaryButton(text = "解除配对", onClick = onUnpair)
        }
        AnyaSecondaryButton(text = "关闭", onClick = onDismiss)
        Spacer(modifier = Modifier.height(AnyaSpace.Sm))
    }
}
