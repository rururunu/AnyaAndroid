package ai.anya.companion.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import ai.anya.companion.R
import androidx.compose.foundation.layout.padding as layoutPadding
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.anya.companion.core.designsystem.component.AnyaBottomNavItem
import ai.anya.companion.core.designsystem.component.AnyaBrandRow
import ai.anya.companion.core.designsystem.component.AnyaConnectionChip
import ai.anya.companion.core.designsystem.component.AnyaGlassFloatingActionButton
import ai.anya.companion.core.designsystem.component.AnyaGlassBottomNavBar
import ai.anya.companion.core.designsystem.component.AnyaGlassBottomSheet
import ai.anya.companion.core.designsystem.component.AnyaGlassConnectionChip
import ai.anya.companion.core.designsystem.component.AnyaGlassIconChip
import ai.anya.companion.core.designsystem.component.AnyaMetaRow
import ai.anya.companion.core.designsystem.component.AnyaSheetActionRow
import ai.anya.companion.core.designsystem.component.AnyaSheetCard
import ai.anya.companion.core.designsystem.component.AnyaSheetHeader
import ai.anya.companion.core.designsystem.component.AnyaSheetInfoRow
import ai.anya.companion.core.designsystem.component.AnyaStatusTone
import ai.anya.companion.core.designsystem.component.AnyaSurfaceCard
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import ai.anya.companion.core.designsystem.haptic.rememberAnyaHaptics
import ai.anya.companion.core.designsystem.icon.AnyaIcons
import ai.anya.companion.core.designsystem.theme.AnyaColors
import ai.anya.companion.core.designsystem.theme.AnyaSpace
import ai.anya.companion.core.domain.repository.ConnectionState
import ai.anya.companion.core.model.protocol.DeviceCredential
import ai.anya.companion.feature.approval.ApprovalTabContent
import ai.anya.companion.feature.approval.ApprovalViewModel
import ai.anya.companion.feature.sessions.SessionsAskContent
import ai.anya.companion.feature.sessions.SessionsSegmentHeader
import ai.anya.companion.feature.sessions.SessionsViewModel
import ai.anya.companion.feature.sessions.SessionsWorkspaceContent
import ai.anya.companion.feature.settings.HostSwitcherSheet
import ai.anya.companion.feature.settings.SettingsTabContent
import ai.anya.companion.feature.settings.SettingsViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlin.math.roundToInt

/** Height of the segment control row (44) with its vertical padding (16 top + 12 bottom). */
private val SegmentRowHeight = AnyaSpace.Lg + 44.dp + AnyaSpace.Md

/**
 * At-rest top inset for the Ask / Workspace lists, so the first card sits *below* the
 * segment capsule instead of under it. The lists themselves are bounded from just under
 * the brand row rather than from `segmentTop` — that is what lets cards scroll up behind
 * the capsule and give the glass something to refract instead of flat page background.
 */
private val SegmentContentInset = SegmentRowHeight

/** Height of the floating brand row (status-bar padding is applied separately). */
private val TopChromeHeight = 48.dp

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

    /** Bottom-nav item order, matching the `items` list handed to the nav bar. */
    val BottomNavIds = listOf("conversations", "approvals", "settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainRoute(
    onOpenSession: (sessionId: String, messageId: String?) -> Unit,
    onNewSession: (workspaceId: String?) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenConnectionSettings: () -> Unit,
    onOpenGeneralSettings: () -> Unit,
    onOpenAboutSettings: () -> Unit,
    onOpenArchivedSessions: () -> Unit,
    onAddDevice: () -> Unit,
    onUnpairLast: () -> Unit,
    sessionsViewModel: SessionsViewModel = hiltViewModel(),
    approvalViewModel: ApprovalViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val haptics = rememberAnyaHaptics()
    val pagerState = rememberPagerState(
        initialPage = MainPage.Ask,
        pageCount = { MainPage.Count },
    )
    val inboxPagerState = rememberPagerState(pageCount = { 2 })
    val defaultPagerNestedScroll = PagerDefaults.pageNestedScrollConnection(
        state = pagerState,
        orientation = Orientation.Horizontal,
    )
    val pagerNestedScroll = remember(pagerState, inboxPagerState, defaultPagerNestedScroll) {
        inboxAwarePagerNestedScroll(pagerState, inboxPagerState, defaultPagerNestedScroll)
    }
    var showConnectionPanel by rememberSaveable { mutableStateOf(false) }
    var showHostSwitcher by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var suppressNextSettleHaptic by rememberSaveable { mutableStateOf(false) }

    val sessionsState by sessionsViewModel.state.collectAsStateWithLifecycle()
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
    // Pill position in bottom-nav *item* units: Ask(0) and Workspace(1) share the
    // first item, so the pill only travels once the pager leaves Workspace.
    val navProgress = (absolutePage - MainPage.Workspace)
        .coerceIn(0f, (MainPage.BottomNavIds.size - 1).toFloat())
    val segmentProgress = absolutePage.coerceIn(0f, 1f)
    val showSegmentHeader = absolutePage < 2f
    val segmentHeaderAlpha = when {
        absolutePage <= 1f -> 1f
        absolutePage >= 2f -> 0f
        else -> (2f - absolutePage).coerceIn(0f, 1f)
    }

    val inboxBadge = sessionsState.pendingApprovals.size
    val tone = when (sessionsState.connectionState) {
        ConnectionState.Connected -> AnyaStatusTone.Success
        ConnectionState.Connecting, ConnectionState.Reconnecting -> AnyaStatusTone.Info
        ConnectionState.Error -> AnyaStatusTone.Danger
        ConnectionState.Disconnected -> AnyaStatusTone.Neutral
    }
    val statusLabel = when (sessionsState.connectionState) {
        ConnectionState.Connected -> {
            val pending = inboxBadge
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

    // Liquid glass backdrop: the scrolling content below is rendered into this
    // layer so the floating top chips / bottom bar can refract it.
    //
    // The draw lambda runs in DrawScope, not in a @Composable scope, so the background
    // colour is read here and kept current via rememberUpdatedState instead of reading
    // MaterialTheme inside the lambda (which is a compile error).
    val backdropBackground by rememberUpdatedState(MaterialTheme.colorScheme.background)
    val glassBackdrop = rememberLayerBackdrop {
        drawRect(backdropBackground)
        drawContent()
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val segmentTop = statusBarTop + TopChromeHeight + SegmentRowHeight

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .drop(1)
            .filter { pagerState.currentPageOffsetFraction == 0f }
            .collect {
                if (suppressNextSettleHaptic) {
                    suppressNextSettleHaptic = false
                } else {
                    haptics.selection()
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

    fun selectTabStep(step: Int) {
        val currentIndex = MainPage.BottomNavIds.indexOf(selectedNavId).coerceAtLeast(0)
        val targetIndex = (currentIndex + step).coerceIn(0, MainPage.BottomNavIds.lastIndex)
        if (targetIndex == currentIndex) return
        haptics.selection()
        selectTab(MainPage.BottomNavIds[targetIndex])
    }

    // The panel is an in-composition overlay now (not a ModalBottomSheet), so dismissal is
    // a plain state flip and the overlay animates itself out.
    fun dismissConnectionPanel(after: (() -> Unit)? = null) {
        showConnectionPanel = false
        after?.invoke()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Content layer: rendered into the glass backdrop so the floating
        // chrome (top chips / bottom bar) can refract it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(glassBackdrop),
        ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
            beyondViewportPageCount = 1,
            pageNestedScrollConnection = pagerNestedScroll,
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
                            modifier = Modifier.padding(top = statusBarTop + TopChromeHeight),
                            contentPaddingTop = SegmentContentInset,
                            onOpenSession = { id ->
                                haptics.linearTick()
                                onOpenSession(id, null)
                            },
                            onRefresh = {
                                haptics.buttonPress()
                                sessionsViewModel.refresh()
                            },
                            onArchiveSession = sessionsViewModel::archiveSession,
                            onDeleteSession = sessionsViewModel::deleteSession,
                            onRenameSession = sessionsViewModel::renameSession,
                            onDismissActionError = sessionsViewModel::clearActionError,
                        )
                    }
                    MainPage.Workspace -> {
                        SessionsWorkspaceContent(
                            state = sessionsState,
                            modifier = Modifier.padding(top = statusBarTop + TopChromeHeight),
                            contentPaddingTop = SegmentContentInset,
                            onToggleWorkspace = sessionsViewModel::toggleWorkspace,
                            onOpenSession = { id ->
                                haptics.linearTick()
                                onOpenSession(id, null)
                            },
                            onRefresh = {
                                haptics.buttonPress()
                                sessionsViewModel.refresh()
                            },
                            onArchiveSession = sessionsViewModel::archiveSession,
                            onDeleteSession = sessionsViewModel::deleteSession,
                            onNewSessionInWorkspace = { workspaceId ->
                                haptics.buttonPress()
                                onNewSession(workspaceId)
                            },
                            onArchiveWorkspace = sessionsViewModel::archiveWorkspace,
                            onRenameSession = sessionsViewModel::renameSession,
                            onPinWorkspace = sessionsViewModel::pinWorkspace,
                            onRenameWorkspace = sessionsViewModel::renameWorkspace,
                            onDismissActionError = sessionsViewModel::clearActionError,
                        )
                    }
                    MainPage.Approvals -> {
                        Box(modifier = Modifier.padding(top = statusBarTop + TopChromeHeight)) {
                        ApprovalTabContent(
                            state = approvalState,
                            pagerState = inboxPagerState,
                            onOpenSession = { id ->
                                haptics.linearTick()
                                onOpenSession(id, null)
                            },
                            onDeleteResult = approvalViewModel::deleteResult,
                        )
                        }
                    }
                    else -> {
                        Box(modifier = Modifier.padding(top = statusBarTop + TopChromeHeight)) {
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
                            onOpenArchived = {
                                haptics.linearTick()
                                onOpenArchivedSessions()
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
        }

        // Floating top chrome: brand row + liquid-glass action chips.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                // Solid down to the segment capsule's top edge. The list is bounded at
                // statusBarTop + TopChromeHeight, but the capsule sits AnyaSpace.Lg
                // lower, so scrolled rows used to show through that 16dp strip and read
                // as a seam between the brand row and the capsule. The bottom padding
                // must keep matching SessionsSegmentHeader's top padding.
                // `surface`, not `background`: the whole app is wrapped in a Material3
                // `Surface` (see MainActivity), so every unpainted area below is
                // colorScheme.surface (#FFFFFF). Painting this band with `background`
                // shaded it #F8F8F8 and the seam returned as a colour step.
                .background(MaterialTheme.colorScheme.surface)
                .padding(bottom = AnyaSpace.Lg)
                .statusBarsPadding()
                .layoutPadding(horizontal = AnyaSpace.Screen)
                .padding(top = AnyaSpace.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnyaBrandRow(
                title = settingsState.credential?.resolvedDisplayName() ?: "Anya",
                subtitle = topSubtitle,
                onBrandLongClick = {
                    haptics.longPress()
                    showHostSwitcher = true
                },
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
            ) {
                AnyaGlassIconChip(
                    backdrop = glassBackdrop,
                    icon = Icons.Rounded.Search,
                    contentDescription = stringResource(R.string.nav_search_chats),
                    onClick = {
                        haptics.buttonPress()
                        onOpenSearch()
                    },
                )
                AnyaGlassConnectionChip(
                    backdrop = glassBackdrop,
                    label = statusLabel,
                    tone = tone,
                    onClick = {
                        haptics.buttonPress()
                        showConnectionPanel = true
                    },
                )
            }
        }

        if (showSegmentHeader) {
            SessionsSegmentHeader(
                selectedIndex = segmentProgress.roundToInt().coerceIn(0, 1),
                selectedProgress = segmentProgress,
                backdrop = glassBackdrop,
                onSegmentSelect = ::selectPage,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .layoutPadding(top = statusBarTop + TopChromeHeight)
                    .graphicsLayer { alpha = segmentHeaderAlpha },
            )
        }

        // Floating liquid-glass bottom navigation.
        AnyaGlassBottomNavBar(
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
                    badge = inboxBadge,
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
            backdrop = glassBackdrop,
            pillProgress = navProgress,
            onSwipeStep = ::selectTabStep,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = AnyaSpace.Md),
        )

        // Circular new-chat FAB: 随问 only — workspaces create sessions from the row +.
        if (absolutePage < 1f) {
            AnyaGlassFloatingActionButton(
                backdrop = glassBackdrop,
                onClick = { onNewSession(null) },
                icon = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.nav_new_chat),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .layoutPadding(end = AnyaSpace.Screen, bottom = 96.dp)
                    .graphicsLayer { alpha = (1f - absolutePage).coerceIn(0f, 1f) },
            )
        }

        // Connection panel as a same-window glass overlay. It has to live inside this Box
        // - a sibling of the recorded content, exactly like the nav bar and FAB above.
        // A ModalBottomSheet runs in its own window, where `drawBackdrop` cannot sample
        // this page's layer at all, so the panel used to render as a flat tint.
        BackHandler(enabled = showConnectionPanel) { dismissConnectionPanel() }
        AnyaGlassBottomSheet(
            visible = showConnectionPanel,
            onDismissRequest = { dismissConnectionPanel() },
            backdrop = glassBackdrop,
        ) {
            ConnectionPanel(
                connectionState = sessionsState.connectionState,
                credential = settingsState.credential,
                tone = tone,
                statusLabel = statusLabel,
                pendingCount = inboxBadge,
                onConnect = settingsViewModel::connect,
                onDisconnect = settingsViewModel::disconnect,
                onUnpair = {
                    dismissConnectionPanel {
                        val last = settingsState.pairedDevices.size <= 1
                        settingsViewModel.unpair()
                        if (last) onUnpairLast()
                    }
                },
                onGoPair = {
                    dismissConnectionPanel(onAddDevice)
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

    if (showHostSwitcher) {
        HostSwitcherSheet(
            devices = settingsState.pairedDevices,
            activeDeviceId = settingsState.credential?.rosterKey(),
            connectionState = sessionsState.connectionState,
            onSelect = { deviceId ->
                settingsViewModel.switchDevice(deviceId)
            },
            onAdd = {
                showHostSwitcher = false
                onAddDevice()
            },
            onDismiss = { showHostSwitcher = false },
        )
    }
}

/**
 * Connection sheet content, structured the way Apple's HIG lays out an action sheet:
 * a centered title block, an opaque grouped list for the connection details, one grouped
 * card of text actions (destructive ones in red), and a separate emphasized Close card
 * below. The sheet itself is already glass, so every control inside sits on a solid
 * surface — layering glass rows over glass is what made the old buttons read muddy.
 */
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

    val accent = MaterialTheme.colorScheme.primary
    val inboxLabel = stringResource(R.string.connection_panel_view_inbox, pendingCount)
    val reconnectLabel = stringResource(R.string.connection_panel_reconnect)
    val disconnectLabel = stringResource(R.string.connection_panel_disconnect)
    val goPairLabel = stringResource(R.string.connection_panel_go_pair)
    val unpairLabel = stringResource(R.string.connection_panel_unpair)

    // iOS action order: neutral actions first, destructive ones last.
    val actions = buildList {
        if (pendingCount > 0) add(Triple(inboxLabel, accent, onOpenInbox))
        when {
            canConnect -> add(Triple(reconnectLabel, accent, onConnect))
            canDisconnect -> add(Triple(disconnectLabel, AnyaColors.Danger, onDisconnect))
            credential == null -> add(Triple(goPairLabel, accent, onGoPair))
        }
        if (credential != null) add(Triple(unpairLabel, AnyaColors.Danger, onUnpair))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AnyaSpace.Lg)
            .padding(bottom = AnyaSpace.Xl),
        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnyaSheetHeader(
            title = stringResource(R.string.connection_panel_title),
            subtitle = stringResource(R.string.connection_panel_subtitle),
        )
        Spacer(modifier = Modifier.height(AnyaSpace.Xs))
        AnyaSheetCard {
            AnyaSheetInfoRow(
                label = stringResource(R.string.connection_panel_status),
                showDivider = credential != null,
            ) {
                AnyaConnectionChip(label = statusLabel, tone = tone)
            }
            if (credential != null) {
                AnyaSheetInfoRow(
                    label = stringResource(R.string.connection_panel_host),
                    value = credential.resolvedDisplayName(),
                )
                AnyaSheetInfoRow(
                    label = stringResource(R.string.connection_panel_address),
                    value = credential.host,
                )
                AnyaSheetInfoRow(
                    label = stringResource(R.string.connection_panel_port),
                    value = credential.port.toString(),
                    showDivider = false,
                )
            }
        }
        if (credential == null) {
            Text(
                text = stringResource(R.string.connection_panel_not_paired),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnyaSheetCard {
            actions.forEachIndexed { index, (label, color, action) ->
                AnyaSheetActionRow(
                    label = label,
                    color = color,
                    onClick = action,
                    showDivider = index < actions.lastIndex,
                )
            }
        }
        // Close lives on its own card, separated from the action group — the
        // action-sheet cancel pattern.
        AnyaSheetCard {
            AnyaSheetActionRow(
                label = stringResource(R.string.connection_panel_close),
                color = MaterialTheme.colorScheme.onSurface,
                onClick = onDismiss,
                showDivider = false,
                emphasized = true,
            )
        }
    }
}

/**
 * Let the inbox inner pager take horizontal drags until it hits an edge, then the
 * main pager continues (workspace ← inbox → settings).
 */
private fun inboxAwarePagerNestedScroll(
    pagerState: PagerState,
    inboxPagerState: PagerState,
    default: NestedScrollConnection,
): NestedScrollConnection = object : NestedScrollConnection {
    private fun inboxConsumes(deltaX: Float): Boolean {
        if (pagerState.currentPage != MainPage.Approvals) return false
        val atStart = inboxPagerState.currentPage == 0 &&
            inboxPagerState.currentPageOffsetFraction <= 0.001f
        val atEnd = inboxPagerState.currentPage >= inboxPagerState.pageCount - 1 &&
            inboxPagerState.currentPageOffsetFraction >= -0.001f
        return when {
            deltaX > 0f -> !atStart
            deltaX < 0f -> !atEnd
            else -> false
        }
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        return if (inboxConsumes(available.x)) Offset.Zero else default.onPreScroll(available, source)
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        return if (inboxConsumes(available.x)) Offset.Zero else default.onPostScroll(consumed, available, source)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        return if (inboxConsumes(available.x)) Velocity.Zero else default.onPreFling(available)
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        return if (inboxConsumes(available.x)) {
            Velocity.Zero
        } else {
            default.onPostFling(consumed, available)
        }
    }
}
