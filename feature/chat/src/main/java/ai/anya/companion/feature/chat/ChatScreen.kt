package ai.anya.companion.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.zIndex
import ai.anya.companion.core.model.workspace.McpServerSummary
import ai.anya.companion.core.model.workspace.SkillSummary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import ai.anya.companion.core.designsystem.component.AnyaLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import ai.anya.companion.core.designsystem.haptic.rememberAnyaHaptics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.anya.companion.core.designsystem.component.AnyaChatContent
import ai.anya.companion.core.designsystem.component.AnyaEmptyState
import ai.anya.companion.core.designsystem.component.AnyaMessageNavRail
import ai.anya.companion.core.designsystem.component.AnyaScreen
import ai.anya.companion.core.designsystem.component.AnyaTopBarIconChip
import ai.anya.companion.core.designsystem.icon.AnyaIcons
import ai.anya.companion.core.designsystem.theme.AnyaSpace
import ai.anya.companion.core.domain.repository.ConnectionState
import ai.anya.companion.core.model.session.ChatMessage
import ai.anya.companion.core.model.session.ChatMode
import ai.anya.companion.core.model.session.ChatModelInfo
import ai.anya.companion.core.model.session.ChatRole
import ai.anya.companion.core.model.session.CodeChangeEntry
import ai.anya.companion.core.model.session.MessageStatus
import ai.anya.companion.core.model.session.PlanTaskItem
import ai.anya.companion.core.model.session.ToolActivity
import ai.anya.companion.core.model.session.ToolApprovalMode
import androidx.compose.ui.text.font.FontFamily
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Distance that's guaranteed to exceed any realistic remaining scroll
 * range. Used with [androidx.compose.foundation.lazy.LazyListState.animateScrollBy],
 * which — unlike a naive manual offset calculation — is *contractually*
 * clamped by the framework to however much content is actually left to
 * scroll. Requesting "a huge amount" therefore always lands exactly on the
 * true maximum scroll position in a single animation, with no need to
 * reason about `afterContentPadding`/`viewportEndOffset` ourselves (that
 * math is exactly what was causing the settle to sometimes stop short,
 * needing repeated taps to fully reach bottom).
 */
private const val BOTTOM_OVERSHOOT_PX = 100_000f

/**
 * Scroll to the exact bottom of the list in one continuous animation.
 *
 * `animateScrollToItem`/`scrollToItem` only guarantee the target item's
 * *top* is revealed, not that its bottom clears our floating composer. So
 * we snap the target on-screen first (nothing visible jumps, since it
 * wasn't visible yet anyway), then let a single, deliberately oversized
 * `animateScrollBy` clamp itself against the real max scroll offset — this
 * is guaranteed correct regardless of content padding/composer height.
 */
private suspend fun androidx.compose.foundation.lazy.LazyListState.animateSettleToBottom(lastIndex: Int) {
    if (lastIndex < 0) return
    val visible = layoutInfo.visibleItemsInfo.lastOrNull()
    if (visible == null || visible.index != lastIndex) {
        scrollToItem(lastIndex)
    }
    animateScrollBy(BOTTOM_OVERSHOOT_PX)
}

/**
 * Persistent warning shown above the composer whenever the gateway link
 * isn't [ConnectionState.Connected]. Sending, approving, or answering while
 * offline used to just fail with a raw exception string — this makes the
 * blocked state visible up front and offers a one-tap retry, instead of
 * silently interrupting the user's action once they've already committed to it.
 */
@Composable
private fun ConnectionLostBanner(
    connectionState: ConnectionState,
    onRetry: () -> Unit,
) {
    val haptic = rememberAnyaHaptics()
    val (label, showRetry) = when (connectionState) {
        ConnectionState.Connecting -> "正在连接…" to false
        ConnectionState.Reconnecting -> "连接已断开，正在自动重连…" to false
        ConnectionState.Error -> "连接失败，发送和操作暂时无法送达" to true
        ConnectionState.Disconnected -> "连接已断开，发送和操作暂时无法送达" to true
        ConnectionState.Connected -> return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f))
            .padding(horizontal = AnyaSpace.Screen, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        if (showRetry) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.16f))
                    .clickable {
                        haptic.buttonPress()
                        onRetry()
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "重连",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

/** Vertical extent (root coordinates) of one expanded "已完成" fold, plus its meta label. */
private data class FoldBounds(val top: Float, val bottom: Float, val meta: String?)

/**
 * Tracks every currently-expanded "已完成" fold's on-screen bounds so
 * [ChatScreen] can decide which one (if any) has scrolled its own header out
 * of view while the user is still scrolled through its body — that's the one
 * that gets a floating duplicate header pinned to the top, matching desktop's
 * `position: sticky` fold summary.
 */
private class CompletedFoldController {
    val expanded = mutableStateMapOf<String, Boolean>()
    val bounds = mutableStateMapOf<String, FoldBounds>()
    var listTop by mutableFloatStateOf(0f)

    fun isExpanded(messageId: String): Boolean = expanded[messageId] == true

    fun setExpanded(messageId: String, value: Boolean) {
        expanded[messageId] = value
        if (!value) bounds.remove(messageId)
    }

    fun setBounds(messageId: String, value: FoldBounds?) {
        if (value == null) bounds.remove(messageId) else bounds[messageId] = value
    }
}

private fun userMessagePreview(content: String): String {
    val compact = content.replace(Regex("\\s+"), " ").trim()
    if (compact.isEmpty()) return "用户消息"
    return if (compact.length > 72) compact.take(72) + "…" else compact
}

@Composable
public fun ChatRoute(
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val attachCatalog by viewModel.attachCatalog.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.refreshAttachCatalog()
    }
    ChatScreen(
        state = state,
        attachCatalog = attachCatalog,
        onBack = onBack,
        onDraftChange = viewModel::onDraftChange,
        onSend = viewModel::send,
        onStop = viewModel::stop,
        onChatModeSelect = viewModel::setChatMode,
        onApprovalModeSelect = viewModel::setApprovalMode,
        onModelSelect = viewModel::setModel,
        onAttachInsert = viewModel::attachInsert,
        onAttachOpen = viewModel::refreshAttachCatalog,
        onApprovePlan = viewModel::approvePlan,
        onAnswerAsk = viewModel::answerAsk,
        onAnswerToolApproval = viewModel::answerToolApproval,
        onRetryConnection = viewModel::retryConnection,
    )
}

@Composable
public fun ChatScreen(
    state: ChatUiState,
    attachCatalog: AttachCatalogUiState,
    onBack: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onChatModeSelect: (ChatMode) -> Unit,
    onApprovalModeSelect: (ToolApprovalMode) -> Unit,
    onModelSelect: (ChatModelInfo) -> Unit,
    onAttachInsert: (String) -> Unit,
    onAttachOpen: () -> Unit,
    onApprovePlan: (String) -> Unit,
    onAnswerAsk: (Map<Int, List<String>>, Boolean) -> Unit,
    onAnswerToolApproval: (ai.anya.companion.core.model.approval.ApprovalDecision) -> Unit,
    onRetryConnection: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = rememberAnyaHaptics()
    val imeBottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    var stickToBottom by remember { mutableStateOf(true) }
    var scrubRailIndex by remember { mutableStateOf<Int?>(null) }
    var didFocusMessage by remember(state.focusMessageId) { mutableStateOf(false) }

    val userMessageIndexes = remember(state.messages) {
        state.messages.mapIndexedNotNull { index, message ->
            if (message.role == ChatRole.User) index else null
        }
    }
    val userPreviews = remember(state.messages, userMessageIndexes) {
        userMessageIndexes.map { msgIndex ->
            userMessagePreview(state.messages[msgIndex].content)
        }
    }

    LaunchedEffect(state.messages, state.focusMessageId) {
        val targetId = state.focusMessageId ?: return@LaunchedEffect
        if (didFocusMessage) return@LaunchedEffect
        val index = state.messages.indexOfFirst { it.id == targetId }
        if (index < 0) return@LaunchedEffect
        stickToBottom = false
        listState.animateScrollToItem(index)
        didFocusMessage = true
    }

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content) {
        if (state.focusMessageId != null && !didFocusMessage) return@LaunchedEffect
        if (stickToBottom && state.messages.isNotEmpty()) {
            listState.animateSettleToBottom(state.messages.lastIndex)
        }
    }

    LaunchedEffect(imeBottomPadding, stickToBottom, state.messages.size) {
        if (imeBottomPadding > 0.dp && stickToBottom && state.messages.isNotEmpty()) {
            listState.animateSettleToBottom(state.messages.lastIndex)
        }
    }

    val activeUserRailIndex by remember {
        derivedStateOf {
            if (userMessageIndexes.isEmpty()) return@derivedStateOf 0
            val scrub = scrubRailIndex
            if (scrub != null) return@derivedStateOf scrub.coerceIn(0, userMessageIndexes.lastIndex)

            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            if (visible.isEmpty()) {
                return@derivedStateOf userMessageIndexes.lastIndex.coerceAtLeast(0)
            }

            // Match desktop: last user message whose top has crossed the viewport top band.
            val thresholdPx = with(density) { 48.dp.toPx() }
            val firstVisible = visible.first().index
            var active = 0
            userMessageIndexes.forEachIndexed { railIdx, msgIdx ->
                if (msgIdx < firstVisible) {
                    active = railIdx
                    return@forEachIndexed
                }
                val item = visible.find { it.index == msgIdx } ?: return@forEachIndexed
                if (item.offset <= thresholdPx) {
                    active = railIdx
                }
            }
            active
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && scrubRailIndex == null) {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return@LaunchedEffect
            stickToBottom = lastVisible.index >= state.messages.lastIndex - 1 &&
                lastVisible.offset + lastVisible.size <= info.viewportEndOffset + with(density) { 48.dp.roundToPx() }
        }
    }

    fun jumpToUserRail(railIndex: Int, animate: Boolean) {
        val messageIndex = userMessageIndexes.getOrNull(railIndex) ?: return
        stickToBottom = false
        scrubRailIndex = null
        scope.launch {
            if (animate) {
                listState.animateScrollToItem(messageIndex)
            } else {
                listState.scrollToItem(messageIndex)
            }
        }
    }

    fun scrubToUserRail(railIndex: Int) {
        val messageIndex = userMessageIndexes.getOrNull(railIndex) ?: return
        stickToBottom = false
        scrubRailIndex = railIndex
        scope.launch {
            listState.scrollToItem(messageIndex)
        }
    }

    fun scrollToBottom() {
        scrubRailIndex = null
        stickToBottom = true
        if (state.messages.isEmpty()) return
        scope.launch {
            listState.animateSettleToBottom(state.messages.lastIndex)
        }
    }

    // Only offer "scroll to bottom" once we're more than a full screen away —
    // a tiny nudge upward (even inside one long, tool-heavy message) shouldn't
    // pop the button in.
    //
    // IMPORTANT: this must only close over `listState` (a stable, remembered
    // object), not `state` — `remember { derivedStateOf { ... } }` with no
    // keys runs its lambda-building block just once, on the first
    // composition. If the lambda captured the `state` parameter directly, it
    // would keep referencing that first (often still-loading/empty) snapshot
    // forever, and the button would never show again.
    val showScrollToBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.totalItemsCount == 0) return@derivedStateOf false
            val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            val lastIndex = info.totalItemsCount - 1
            if (lastVisible.index < lastIndex) {
                // At least one more message below the current view.
                true
            } else {
                // viewportEndOffset doesn't subtract afterContentPadding (the
                // composer's reserved clearance) — the real visible edge is
                // viewportEndOffset - afterContentPadding.
                val trueBottom = info.viewportEndOffset - info.afterContentPadding
                val viewportHeight = trueBottom - info.viewportStartOffset
                val remaining = (lastVisible.offset + lastVisible.size) - trueBottom
                remaining > viewportHeight
            }
        }
    }

    var sheet by remember { mutableStateOf(ChatSheet.None) }

    // The composer floats *over* the message list rather than reserving its
    // own row (its height varies a lot: plain field vs. multi-line ask/tool
    // approval panels), so we measure its real footprint and feed that back
    // into the list as bottom clearance — otherwise the last bit of content
    // ends up hidden underneath it.
    var composerFootprintPx by remember { mutableStateOf(0) }
    val composerFootprintDp = with(density) { composerFootprintPx.toDp() }

    val foldController = remember { CompletedFoldController() }
    val stickyFold by remember {
        derivedStateOf {
            foldController.bounds.entries.firstOrNull { (id, b) ->
                foldController.isExpanded(id) && b.top < foldController.listTop && b.bottom > foldController.listTop
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnyaScreen(
            topBar = {
                ChatTopBar(
                    chatMode = state.compose.chatMode,
                    approvalMode = state.compose.toolApprovalMode,
                    onBack = onBack,
                    onModeClick = { sheet = ChatSheet.ChatMode },
                    onApprovalClick = { sheet = ChatSheet.ApprovalMode },
                )
            },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // White chat canvas so the grey floating composer can lift off it.
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                if (state.messages.isEmpty() && state.historyLoading) {
                    // History is still loading from the desktop — show a spinner
                    // instead of the empty state, otherwise it reads as "no
                    // messages yet" even for sessions that do have history.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = composerFootprintDp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnyaLoadingIndicator(
                            size = 56.dp,
                            label = null,
                        )
                    }
                } else if (state.messages.isEmpty()) {
                    AnyaEmptyState(
                        icon = AnyaIcons.ChatCircleOutline,
                        title = "开始对话",
                        subtitle = "消息会同步到桌面端工作台",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = composerFootprintDp),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coordinates ->
                                foldController.listTop = coordinates.positionInRoot().y
                            },
                        contentPadding = PaddingValues(
                            start = AnyaSpace.Screen,
                            end = AnyaSpace.Screen + 12.dp,
                            top = AnyaSpace.Lg,
                            // Reserve composer height + keyboard inset so content scrolls with IME.
                            bottom = composerFootprintDp + AnyaSpace.Sm + imeBottomPadding,
                        ),
                        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Lg),
                    ) {
                        items(state.messages, key = ChatMessage::id) { message ->
                            MessageBubble(
                                message = message,
                                skills = attachCatalog.skills,
                                mcpServers = attachCatalog.mcpServers,
                                onApprovePlan = onApprovePlan,
                                approvedPlanIds = state.planApprovedMessageIds,
                                foldController = foldController,
                            )
                        }
                    }
                    // Floating duplicate of whichever "已完成" fold header has
                    // scrolled off the top while its body is still on screen —
                    // keeps the collapse control reachable without scrolling back up.
                    val sticky = stickyFold
                    if (sticky != null) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth()
                                .zIndex(3f),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 2.dp,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { foldController.setExpanded(sticky.key, false) }
                                    .padding(horizontal = AnyaSpace.Screen, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                                Text(
                                    text = "已完成",
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                val meta = sticky.value.meta
                                if (meta != null) {
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        text = "（$meta）",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    )
                                }
                            }
                        }
                    }
                    if (userMessageIndexes.isNotEmpty()) {
                        AnyaMessageNavRail(
                            pageCount = userMessageIndexes.size,
                            selectedIndex = activeUserRailIndex,
                            previews = userPreviews,
                            onScrub = ::scrubToUserRail,
                            onJump = { jumpToUserRail(it, animate = true) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = AnyaSpace.Sm, bottom = AnyaSpace.Sm)
                                .zIndex(1f),
                        )
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showScrollToBottom,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(
                                end = 10.dp,
                                bottom = composerFootprintDp + imeBottomPadding + 10.dp,
                            )
                            .zIndex(2f),
                        enter = fadeIn() + scaleIn(initialScale = 0.86f),
                        exit = fadeOut() + scaleOut(targetScale = 0.86f),
                    ) {
                        Surface(
                            onClick = {
                                haptics.buttonPress()
                                scrollToBottom()
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                            shadowElevation = 4.dp,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "滚动到底部",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                }

                // Floating layer: error + composer, pinned to the bottom, on top of
                // whatever the message area is showing. Measuring its own height
                // here (rather than trusting a Column weight to exclude it) keeps
                // the reserved clearance correct even as it grows for ask/approval
                // panels or multi-line drafts.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .imePadding(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coordinates ->
                                composerFootprintPx = coordinates.size.height
                            },
                    ) {
                        if (state.connectionState != ConnectionState.Connected) {
                            ConnectionLostBanner(
                                connectionState = state.connectionState,
                                onRetry = onRetryConnection,
                            )
                        }
                        if (state.error != null) {
                            Text(
                                text = state.error.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .padding(horizontal = AnyaSpace.Screen)
                                    .padding(bottom = AnyaSpace.Xs),
                            )
                        }

                        MobileComposer(
                            draft = state.draft,
                            busy = state.busy,
                            modelLabel = state.compose.modelDisplayName,
                            modelProvider = state.compose.chatModelProvider,
                            modelId = state.compose.chatModel,
                            skills = attachCatalog.skills,
                            mcpServers = attachCatalog.mcpServers,
                            pendingAsk = state.pendingAsk,
                            onDraftChange = onDraftChange,
                            onSend = onSend,
                            onStop = onStop,
                            onAttachClick = {
                                onAttachOpen()
                                sheet = ChatSheet.Attach
                            },
                            onModelClick = { sheet = ChatSheet.Model },
                            onAnswerAsk = onAnswerAsk,
                            onAnswerToolApproval = onAnswerToolApproval,
                        )
                    }
                }
            }
        }
    }

    when (sheet) {
        ChatSheet.ChatMode -> ChatModeSheet(
            current = state.compose.chatMode,
            onSelect = {
                onChatModeSelect(it)
                sheet = ChatSheet.None
            },
            onDismiss = { sheet = ChatSheet.None },
        )
        ChatSheet.ApprovalMode -> ApprovalModeSheet(
            current = state.compose.toolApprovalMode,
            onSelect = {
                onApprovalModeSelect(it)
                sheet = ChatSheet.None
            },
            onDismiss = { sheet = ChatSheet.None },
        )
        ChatSheet.Model -> ModelSheet(
            models = state.models,
            current = state.compose.chatModel,
            onSelect = {
                onModelSelect(it)
                sheet = ChatSheet.None
            },
            onDismiss = { sheet = ChatSheet.None },
        )
        ChatSheet.Attach -> AttachSheet(
            catalog = attachCatalog,
            onRefresh = onAttachOpen,
            onInsert = {
                onAttachInsert(it)
                sheet = ChatSheet.None
            },
            onDismiss = { sheet = ChatSheet.None },
        )
        ChatSheet.None -> Unit
    }
}

private enum class ChatSheet { None, ChatMode, ApprovalMode, Model, Attach }

// region Top bar

@Composable
private fun ChatTopBar(
    chatMode: ChatMode,
    approvalMode: ToolApprovalMode,
    onBack: () -> Unit,
    onModeClick: () -> Unit,
    onApprovalClick: () -> Unit,
) {
    val haptic = rememberAnyaHaptics()
    Surface(color = MaterialTheme.colorScheme.background) {
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
                onClick = {
                    haptic.buttonPress()
                    onBack()
                },
            )
            ChatModeChip(chatMode = chatMode, onClick = onModeClick)
            Spacer(modifier = Modifier.weight(1f))
            ApprovalModeChip(approvalMode = approvalMode, onClick = onApprovalClick)
        }
    }
}

@Composable
private fun ChatModeChip(chatMode: ChatMode, onClick: () -> Unit) {
    PillChip(onClick = onClick) {
        Icon(
            imageVector = chatMode.icon(),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = chatMode.chineseLabel(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ApprovalModeChip(approvalMode: ToolApprovalMode, onClick: () -> Unit) {
    PillChip(onClick = onClick) {
        Icon(
            imageVector = approvalMode.icon(),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = approvalMode.chineseLabel(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModeGlyph(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PillChip(
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val haptic = rememberAnyaHaptics()
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .clickable {
                haptic.linearTick()
                onClick()
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

private fun ChatMode.chineseLabel(): String = when (this) {
    ChatMode.Ask -> "询问"
    ChatMode.Agent -> "Agent"
    ChatMode.Plan -> "计划"
}

private fun ChatMode.icon(): ImageVector = when (this) {
    ChatMode.Ask -> AnyaIcons.ChatCircleOutline
    ChatMode.Agent -> AnyaIcons.StarFour
    ChatMode.Plan -> Icons.AutoMirrored.Rounded.Assignment
}

private fun ToolApprovalMode.chineseLabel(): String = when (this) {
    ToolApprovalMode.Ask -> "询问"
    ToolApprovalMode.Auto -> "自动"
    ToolApprovalMode.AlwaysAllow -> "一律允许"
}

private fun ToolApprovalMode.icon(): ImageVector = when (this) {
    ToolApprovalMode.Ask -> AnyaIcons.ChatCircleOutline
    ToolApprovalMode.Auto -> Icons.Rounded.Shield
    ToolApprovalMode.AlwaysAllow -> Icons.Rounded.LockOpen
}

// endregion

// region Bottom sheets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnyaBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
            )
        },
    ) {
        content()
    }
}

@Composable
private fun ChatModeSheet(
    current: ChatMode,
    onSelect: (ChatMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AnyaBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AnyaSpace.Lg)
                .navigationBarsPadding()
                .padding(bottom = AnyaSpace.Md),
        ) {
            Text(
                text = "对话模式",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "选择本轮对话的工作方式",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = AnyaSpace.Md),
            )
            listOf(ChatMode.Ask, ChatMode.Agent, ChatMode.Plan).forEach { mode ->
                SheetOptionRow(
                    label = mode.chineseLabel(),
                    selected = mode == current,
                    leading = { ModeGlyph(icon = mode.icon()) },
                    onClick = { onSelect(mode) },
                )
            }
        }
    }
}

@Composable
private fun ApprovalModeSheet(
    current: ToolApprovalMode,
    onSelect: (ToolApprovalMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AnyaBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AnyaSpace.Lg)
                .navigationBarsPadding()
                .padding(bottom = AnyaSpace.Md),
        ) {
            Text(
                text = "工具审批模式",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "控制 Agent 使用工具时的确认策略",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = AnyaSpace.Md),
            )
            listOf(ToolApprovalMode.Ask, ToolApprovalMode.Auto, ToolApprovalMode.AlwaysAllow).forEach { mode ->
                SheetOptionRow(
                    label = mode.chineseLabel(),
                    selected = mode == current,
                    leading = { ModeGlyph(icon = mode.icon()) },
                    onClick = { onSelect(mode) },
                )
            }
        }
    }
}

@Composable
private fun ModelSheet(
    models: List<ChatModelInfo>,
    current: String,
    onSelect: (ChatModelInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = rememberAnyaHaptics()
    AnyaBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AnyaSpace.Lg)
                .navigationBarsPadding()
                .padding(bottom = AnyaSpace.Md),
        ) {
            Text(
                text = "选择模型",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "切换后将用于后续消息",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = AnyaSpace.Md),
            )
            if (models.isEmpty()) {
                Text(
                    text = "暂无可用模型",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = AnyaSpace.Lg),
                )
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                ) {
                    models.forEach { model ->
                        val branding = resolveModelProviderBranding(model)
                        SheetOptionRow(
                            label = modelUiLabel(branding, model.label, model.id),
                            selected = model.id == current,
                            leading = if (branding.hasIcon) {
                                { VendorBadge(branding = branding, size = 28.dp) }
                            } else {
                                null
                            },
                            onClick = {
                                haptic.tick()
                                onSelect(model)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachSheet(
    catalog: AttachCatalogUiState,
    onRefresh: () -> Unit,
    onInsert: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = rememberAnyaHaptics()
    var tab by rememberSaveable { mutableStateOf(AttachTab.Files) }
    var expandedDirs by rememberSaveable { mutableStateOf(setOf<String>()) }

    AnyaBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp, max = 640.dp)
                .padding(horizontal = AnyaSpace.Lg)
                .navigationBarsPadding()
                .padding(bottom = AnyaSpace.Md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "添加到消息",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (catalog.loading) {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            },
                        )
                        .then(
                            if (!catalog.loading) {
                                Modifier.clickable {
                                    haptic.buttonPress()
                                    onRefresh()
                                }
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = if (catalog.loading) "同步中…" else "刷新",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (catalog.loading) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(AnyaSpace.Sm))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AttachTab.entries.forEach { item ->
                    val selected = tab == item
                    Text(
                        text = when (item) {
                            AttachTab.Files -> "文件"
                            AttachTab.Skills -> "Skills"
                            AttachTab.Mcp -> "MCP"
                        },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.surface
                                else Color.Transparent,
                            )
                            .clickable {
                                if (tab != item) {
                                    haptic.linearTick()
                                }
                                tab = item
                            }
                            .padding(vertical = 10.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            Spacer(modifier = Modifier.height(AnyaSpace.Md))

            when {
                catalog.loading &&
                    catalog.fileTree.isEmpty() &&
                    catalog.skills.isEmpty() &&
                    catalog.mcpServers.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnyaLoadingIndicator(
                            size = 56.dp,
                            label = null,
                        )
                    }
                }
                tab == AttachTab.Files -> {
                    val error = catalog.filesError
                    val tree = catalog.fileTree
                    when {
                        !error.isNullOrBlank() && tree.isEmpty() -> {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        tree.isEmpty() -> {
                            Text(
                                text = catalog.files?.name?.let { "工作区「$it」暂无可展示文件" }
                                    ?: "未选择工作区，或当前工作区没有文件",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                items(tree, key = { it.path }) { node ->
                                    AttachFileTreeNode(
                                        node = node,
                                        depth = 0,
                                        expanded = expandedDirs,
                                        onToggle = { path ->
                                            expandedDirs = if (path in expandedDirs) {
                                                expandedDirs - path
                                            } else {
                                                expandedDirs + path
                                            }
                                        },
                                        onSelect = { path, isDir ->
                                            onInsert(if (isDir) "@$path/ " else "@$path ")
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                tab == AttachTab.Skills -> {
                    when {
                        catalog.skillsError != null && catalog.skills.isEmpty() -> {
                            Text(
                                text = catalog.skillsError,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        catalog.skills.isEmpty() -> {
                            Text(
                                text = "暂无可用 Skill。可在桌面端设置中启用或安装。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            ) {
                                items(catalog.skills, key = { it.id }) { skill ->
                                    AttachOptionRow(
                                        icon = Icons.Rounded.AutoAwesome,
                                        iconUrl = skill.iconUrl,
                                        label = skill.title.ifBlank { skill.name },
                                        subtitle = skill.description.takeIf { it.isNotBlank() },
                                        onClick = { onInsert("#skill:${skill.name} ") },
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    when {
                        catalog.mcpError != null && catalog.mcpServers.isEmpty() -> {
                            Text(
                                text = catalog.mcpError,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        catalog.mcpServers.isEmpty() -> {
                            Text(
                                text = "暂无已启用的 MCP。请到桌面端设置 → MCP 安装后打开开关。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            ) {
                                items(catalog.mcpServers, key = { it.id }) { server ->
                                    AttachOptionRow(
                                        icon = Icons.Rounded.Extension,
                                        iconUrl = server.iconUrl,
                                        label = server.title.ifBlank { server.id },
                                        subtitle = server.description.takeIf { it.isNotBlank() }
                                            ?: server.qualifiedName,
                                        onClick = { onInsert("#mcp:${server.id} ") },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class AttachTab { Files, Skills, Mcp }

@Composable
private fun AttachFileTreeNode(
    node: ai.anya.companion.core.model.workspace.FileNode,
    depth: Int,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    onSelect: (path: String, isDir: Boolean) -> Unit,
) {
    val isDir = node.isDirectory || node.children.isNotEmpty()
    val isExpanded = node.path in expanded
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AnyaSpace.ControlRadius))
            .clickable {
                if (isDir) onToggle(node.path) else onSelect(node.path, false)
            }
            .padding(
                start = (AnyaSpace.Sm.value + depth * 14f).dp,
                end = AnyaSpace.Sm,
                top = 10.dp,
                bottom = 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
    ) {
        Icon(
            imageVector = when {
                !isDir -> Icons.Rounded.Description
                isExpanded -> Icons.Rounded.ExpandMore
                else -> Icons.Rounded.ChevronRight
            },
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = node.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isDir) {
            Text(
                text = "引用",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onSelect(node.path, true) },
            )
        }
    }
    if (isDir && isExpanded) {
        node.children.forEach { child ->
            AttachFileTreeNode(
                node = child,
                depth = depth + 1,
                expanded = expanded,
                onToggle = onToggle,
                onSelect = onSelect,
            )
        }
    }
}

@Composable
private fun AttachOptionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    iconUrl: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AnyaSpace.ControlRadius))
            .clickable(onClick = onClick)
            .padding(vertical = AnyaSpace.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Md),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            TokenIcon(
                iconUrl = iconUrl,
                fallback = icon,
                fallbackLetter = label,
                size = 20.dp,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SheetOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AnyaSpace.ControlRadius))
            .background(
                if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = AnyaSpace.Sm, vertical = AnyaSpace.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Md),
    ) {
        if (leading != null) {
            leading()
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

// endregion

// region Composer

/**
 * Floating composer matching the reference card:
 * white canvas → soft grey rounded sheet → text on top → + / model pill / ★ below.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MobileComposer(
    draft: String,
    busy: Boolean,
    modelLabel: String,
    modelProvider: String,
    modelId: String,
    skills: List<SkillSummary>,
    mcpServers: List<McpServerSummary>,
    pendingAsk: ai.anya.companion.core.model.approval.PendingApproval?,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onAttachClick: () -> Unit,
    onModelClick: () -> Unit,
    onAnswerAsk: (Map<Int, List<String>>, Boolean) -> Unit,
    onAnswerToolApproval: (ai.anya.companion.core.model.approval.ApprovalDecision) -> Unit,
) {
    val canSend = draft.isNotBlank() && !busy
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val barBg = if (dark) Color(0xFF2A2A2C) else Color(0xFFF2F2F2)
    val toolBg = if (dark) Color(0xFF3A3A3C) else Color.White
    val shape = RoundedCornerShape(28.dp)
    val ink = MaterialTheme.colorScheme.onBackground
    val parsed = remember(draft) { parseComposerText(draft) }
    val controlSize = 36.dp
    val edge = 8.dp
    val modelBranding = remember(modelProvider, modelId, modelLabel) {
        resolveModelProviderBranding(
            provider = modelProvider,
            modelId = modelId,
            label = modelLabel,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = edge, end = edge, bottom = edge, top = 4.dp)
            .shadow(
                elevation = 6.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.05f),
                spotColor = Color.Black.copy(alpha = 0.08f),
            )
            .clip(shape)
            .background(barBg)
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (pendingAsk != null && pendingAsk.kind == ai.anya.companion.core.model.approval.ApprovalKind.Tool) {
            ToolApprovalPanel(
                ask = pendingAsk,
                onDecide = onAnswerToolApproval,
            )
        } else if (pendingAsk != null) {
            AskUserPanel(
                ask = pendingAsk,
                onAnswer = onAnswerAsk,
            )
        } else if (parsed.segments.isEmpty()) {
            ComposerPlainField(
                value = draft,
                busy = busy,
                canSend = canSend,
                ink = ink,
                onValueChange = onDraftChange,
                onSend = onSend,
            )
        } else {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp, max = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                parsed.segments.forEach { segment ->
                    when (segment) {
                        is ComposerSegment.Text -> {
                            Text(
                                text = segment.text.trim(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (busy) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    ink
                                },
                            )
                        }
                        else -> {
                            InlineMentionToken(
                                segment = segment,
                                skills = skills,
                                mcpServers = mcpServers,
                            )
                        }
                    }
                }
                BasicTextField(
                    value = parsed.liveMessage,
                    onValueChange = { live ->
                        onDraftChange(serializeComposerSegments(parsed.segments, live))
                    },
                    enabled = !busy,
                    modifier = Modifier
                        .widthIn(min = 48.dp)
                        .onPreviewKeyEvent { event ->
                            if (
                                event.type == KeyEventType.KeyDown &&
                                event.key == Key.Backspace &&
                                parsed.liveMessage.isEmpty() &&
                                parsed.segments.isNotEmpty()
                            ) {
                                onDraftChange(
                                    serializeComposerSegments(parsed.segments.dropLast(1), ""),
                                )
                                true
                            } else {
                                false
                            }
                        },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = if (busy) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            ink
                        },
                    ),
                    cursorBrush = SolidColor(Color(0xFF3B82F6)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { if (canSend) onSend() },
                    ),
                    maxLines = 5,
                    decorationBox = { inner ->
                        Box {
                            if (parsed.liveMessage.isEmpty() && parsed.segments.isEmpty()) {
                                Text(
                                    text = if (busy) "回答中…" else "随时提问…",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color(0xFF9A9A9A),
                                )
                            }
                            inner()
                        }
                    },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ComposerCircleButton(
                background = toolBg,
                enabled = pendingAsk == null && !busy,
                onClick = onAttachClick,
                size = controlSize,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "添加",
                    tint = ink,
                    modifier = Modifier.size(20.dp),
                )
            }
            ModelPill(
                label = modelLabel,
                branding = modelBranding,
                onClick = onModelClick,
                height = controlSize,
                enabled = pendingAsk == null,
            )
            Spacer(modifier = Modifier.weight(1f))
            val haptic = rememberAnyaHaptics()
            ComposerCircleButton(
                background = when {
                    busy && pendingAsk == null -> ink
                    canSend -> ink
                    else -> toolBg
                },
                enabled = pendingAsk == null && (busy || canSend),
                emitHaptic = false,
                onClick = {
                    haptic.confirm()
                    if (busy) onStop() else onSend()
                },
                size = controlSize,
            ) {
                Icon(
                    imageVector = if (busy && pendingAsk == null) {
                        Icons.Rounded.Stop
                    } else {
                        AnyaIcons.StarFour
                    },
                    contentDescription = if (busy && pendingAsk == null) "停止" else "发送",
                    tint = when {
                        (busy && pendingAsk == null) || canSend -> MaterialTheme.colorScheme.background
                        else -> ink
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ToolApprovalPanel(
    ask: ai.anya.companion.core.model.approval.PendingApproval,
    onDecide: (ai.anya.companion.core.model.approval.ApprovalDecision) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "工具审批",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = ask.title.ifBlank { ask.toolName.orEmpty() },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        ask.toolName?.takeIf { it.isNotBlank() && it != ask.title }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val options = listOf(
            Triple("允许一次", "仅允许本次工具操作", ai.anya.companion.core.model.approval.ApprovalDecision.AllowOnce),
            Triple("本会话允许", "本会话内记住该工具", ai.anya.companion.core.model.approval.ApprovalDecision.AllowSession),
            Triple("拒绝", "取消本次工具操作", ai.anya.companion.core.model.approval.ApprovalDecision.Deny),
        )
        val haptic = rememberAnyaHaptics()
        options.forEach { (label, desc, decision) ->
            Surface(
                onClick = {
                    if (decision == ai.anya.companion.core.model.approval.ApprovalDecision.Deny) {
                        haptic.reject()
                    } else {
                        haptic.confirm()
                    }
                    onDecide(decision)
                },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AskUserPanel(
    ask: ai.anya.companion.core.model.approval.PendingApproval,
    onAnswer: (Map<Int, List<String>>, Boolean) -> Unit,
) {
    val questions = ask.questions
    var questionIndex by remember(ask.requestId) { mutableStateOf(0) }
    var answers by remember(ask.requestId) { mutableStateOf(mapOf<Int, List<String>>()) }
    var multiSelected by remember(ask.requestId) { mutableStateOf(setOf<String>()) }

    if (questions.isEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm)) {
            Text(
                text = ask.title.ifBlank { "需要回答" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            ask.previewSummary?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "桌面端已发起询问，但未收到选项内容。请在桌面端完成选择，或重新发送。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val question = questions[questionIndex.coerceIn(0, questions.lastIndex)]
    val isLast = questionIndex >= questions.lastIndex
    val haptic = rememberAnyaHaptics()

    Column(verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = question.header.ifBlank { "确认意图" },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (questions.size > 1) {
                Text(
                    text = "${questionIndex + 1}/${questions.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = question.question,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        question.options.forEach { option ->
            val selected = option.label in multiSelected
            Surface(
                onClick = {
                    haptic.tick()
                    if (question.multiSelect) {
                        multiSelected = if (selected) {
                            multiSelected - option.label
                        } else {
                            multiSelected + option.label
                        }
                    } else {
                        val next = answers + (questionIndex to listOf(option.label))
                        if (isLast) {
                            onAnswer(next, false)
                        } else {
                            answers = next
                            multiSelected = emptySet()
                            questionIndex += 1
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    option.description?.takeIf { it.isNotBlank() }?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        // Match desktop: always offer "不选择，由我补充".
        Surface(
            onClick = {
                val filled = answers.toMutableMap()
                for (index in questionIndex until questions.size) {
                    filled[index] = emptyList()
                }
                onAnswer(filled, true)
            },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(
                    text = "不选择，由我补充",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "不选择上述选项，在下方输入框补充说明",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (question.multiSelect) {
            val canConfirm = multiSelected.isNotEmpty()
            Surface(
                onClick = {
                    if (!canConfirm) return@Surface
                    haptic.confirm()
                    val next = answers + (questionIndex to multiSelected.toList())
                    if (isLast) {
                        onAnswer(next, false)
                    } else {
                        answers = next
                        multiSelected = emptySet()
                        questionIndex += 1
                    }
                },
                enabled = canConfirm,
                shape = RoundedCornerShape(12.dp),
                color = if (canConfirm) {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = if (canConfirm) 0.14f else 0.06f,
                    ),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (canConfirm) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isLast) "提交所选" else "确认并下一题",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = if (canConfirm) {
                                MaterialTheme.colorScheme.onBackground
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            },
                        )
                        Text(
                            text = if (canConfirm) {
                                "已选 ${multiSelected.size} 项"
                            } else {
                                "请先勾选至少一个选项"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (canConfirm) 1f else 0.7f,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerPlainField(
    value: String,
    busy: Boolean,
    canSend: Boolean,
    ink: Color,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = !busy,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp, max = 140.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = if (busy) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                ink
            },
            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
        ),
        cursorBrush = SolidColor(Color(0xFF3B82F6)),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(
            onSend = { if (canSend) onSend() },
        ),
        maxLines = 5,
        decorationBox = { inner ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (value.isEmpty()) {
                    Text(
                        text = if (busy) "回答中…" else "随时提问…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF9A9A9A),
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun InlineMentionToken(
    segment: ComposerSegment,
    skills: List<SkillSummary>,
    mcpServers: List<McpServerSummary>,
) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val blue = if (dark) Color(0xFF60A5FA) else Color(0xFF2563EB)
    val teal = if (dark) Color(0xFF2DD4BF) else Color(0xFF0F766E)
    val orange = if (dark) Color(0xFFFB923C) else Color(0xFFC2410C)
    val (label, iconUrl, color, fallback, letter) = when (segment) {
        is ComposerSegment.Mention -> TokenVisual(
            label = "@${mentionDisplayLabel(segment.path, segment.isDir)}",
            iconUrl = null,
            color = if (segment.isDir) blue else teal,
            fallback = if (segment.isDir) Icons.Rounded.Folder else Icons.Rounded.Description,
            letter = mentionBasename(segment.path),
        )
        is ComposerSegment.Skill -> TokenVisual(
            label = skillMentionLabel(segment.id, skills),
            iconUrl = skillMentionIconUrl(segment.id, skills),
            color = orange,
            fallback = Icons.Rounded.AutoAwesome,
            letter = skillMentionLabel(segment.id, skills),
        )
        is ComposerSegment.Mcp -> TokenVisual(
            label = mcpMentionLabel(segment.id, mcpServers),
            iconUrl = mcpMentionIconUrl(segment.id, mcpServers),
            color = blue,
            fallback = Icons.Rounded.SmartToy,
            letter = mcpMentionLabel(segment.id, mcpServers),
        )
        is ComposerSegment.Text -> return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        TokenIcon(
            iconUrl = iconUrl,
            fallback = fallback,
            fallbackLetter = letter,
            size = 14.dp,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class TokenVisual(
    val label: String,
    val iconUrl: String?,
    val color: Color,
    val fallback: ImageVector,
    val letter: String,
)

@Composable
private fun ModelPill(
    label: String,
    branding: ModelProviderBranding,
    onClick: () -> Unit,
    height: androidx.compose.ui.unit.Dp = 36.dp,
    enabled: Boolean = true,
) {
    val haptic = rememberAnyaHaptics()
    val displayLabel = modelUiLabel(branding, label)
    Row(
        modifier = Modifier
            .height(height)
            .defaultMinSize(minHeight = height)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (enabled) {
                    Modifier.clickable {
                        haptic.buttonPress()
                        onClick()
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (branding.hasIcon) {
            VendorBadge(branding = branding, size = 18.dp)
        }
        Text(
            text = displayLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ComposerCircleButton(
    background: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    emitHaptic: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 36.dp,
    content: @Composable () -> Unit,
) {
    val haptic = rememberAnyaHaptics()
    Box(
        modifier = Modifier
            .size(size)
            .shadow(
                elevation = 1.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.03f),
                spotColor = Color.Black.copy(alpha = 0.06f),
            )
            .clip(CircleShape)
            .background(background)
            .then(
                if (enabled) {
                    Modifier.clickable {
                        if (emitHaptic) {
                            haptic.buttonPress()
                        }
                        onClick()
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// endregion

// region Message rendering

@Composable
private fun MessageBubble(
    message: ChatMessage,
    skills: List<SkillSummary>,
    mcpServers: List<McpServerSummary>,
    onApprovePlan: (String) -> Unit,
    approvedPlanIds: Set<String>,
    foldController: CompletedFoldController,
) {
    val isUser = message.role == ChatRole.User
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        if (isUser) {
            UserBubble(message = message, skills = skills, mcpServers = mcpServers)
        } else {
            AssistantContent(
                message = message,
                onApprovePlan = onApprovePlan,
                approvedPlanIds = approvedPlanIds,
                foldController = foldController,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserBubble(
    message: ChatMessage,
    skills: List<SkillSummary>,
    mcpServers: List<McpServerSummary>,
) {
    val parts = remember(message.content) { parseInlineParts(message.content) }
    val hasTokens = parts.any { it !is ComposerSegment.Text }
    Column(
        modifier = Modifier.fillMaxWidth(0.86f),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Xs),
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 5.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            if (!hasTokens) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = AnyaSpace.Lg, vertical = AnyaSpace.Md),
                )
            } else {
                FlowRow(
                    modifier = Modifier.padding(horizontal = AnyaSpace.Lg, vertical = AnyaSpace.Md),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    parts.forEach { part ->
                        when (part) {
                            is ComposerSegment.Text -> {
                                if (part.text.isNotBlank()) {
                                    Text(
                                        text = part.text,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                            }
                            else -> InlineMentionToken(
                                segment = part,
                                skills = skills,
                                mcpServers = mcpServers,
                            )
                        }
                    }
                }
            }
        }
        if (message.createdAtEpochMs > 0L) {
            Text(
                text = formatMessageTime(message.createdAtEpochMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier.padding(end = AnyaSpace.Xs),
            )
        }
    }
}

@Composable
private fun AssistantContent(
    message: ChatMessage,
    onApprovePlan: (String) -> Unit,
    approvedPlanIds: Set<String>,
    foldController: CompletedFoldController,
) {
    // No heavy bubble chrome for assistant turns — plain text on the white canvas.
    Column(
        modifier = Modifier.fillMaxWidth(0.96f),
        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
    ) {
        val reasoning = message.reasoning
        val hasReasoning = !reasoning.isNullOrBlank()
        val hasActivities = message.toolActivities.isNotEmpty()
        // Match desktop: a turn is "live" while pending or actively streaming.
        // Only once it's done do reasoning + tool activity fold into "已完成".
        val live = message.status == MessageStatus.Streaming || message.status == MessageStatus.Pending
        if (live) {
            if (hasReasoning) {
                ReasoningSection(reasoning = reasoning!!, streaming = message.status == MessageStatus.Streaming)
            }
            if (hasActivities) {
                ToolActivityList(activities = message.toolActivities)
            }
        } else if (hasReasoning || hasActivities) {
            CompletedWorkFold(
                messageId = message.id,
                reasoning = reasoning,
                activities = message.toolActivities,
                controller = foldController,
            )
        }
        if (message.content.isNotBlank() || message.status == MessageStatus.Streaming) {
            val body = message.content.ifBlank {
                if (message.status == MessageStatus.Streaming && message.toolActivities.isEmpty()) "…" else ""
            }
            if (body.isNotBlank()) {
                AnyaChatContent(
                    content = body,
                    textStyle = MaterialTheme.typography.bodyLarge,
                )
            }
        } else if (reasoning.isNullOrBlank() && message.toolActivities.isEmpty()) {
            Text(
                text = "…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (message.planTasks.isNotEmpty()) {
            PlanCard(
                tasks = message.planTasks,
                approved = message.id in approvedPlanIds ||
                    message.planTasks.any { it.status != "pending" },
                onApprove = { onApprovePlan(message.id) },
            )
        }
        if (message.codeChanges.isNotEmpty()) {
            CodeChangesCard(changes = message.codeChanges)
        }
        if (message.status == MessageStatus.Error) {
            Text(
                text = "生成出错",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Once a turn is done, fold reasoning + tool activity into a single collapsed
 * "已完成" row — matching desktop's completed-turn fold. Only the final reply
 * stays outside of it.
 */
@Composable
private fun CompletedWorkFold(
    messageId: String,
    reasoning: String?,
    activities: List<ToolActivity>,
    controller: CompletedFoldController,
) {
    val expanded = controller.isExpanded(messageId)
    val meta = remember(reasoning, activities.size) {
        val chars = reasoning?.trim()?.length ?: 0
        when {
            chars > 0 -> "${java.text.NumberFormat.getIntegerInstance().format(chars)} 字"
            activities.isNotEmpty() -> "${activities.size} 个操作"
            else -> null
        }
    }
    // Only expanded folds need their bounds tracked (for the sticky-header
    // overlay); drop the entry the moment this leaves composition (list item
    // recycled) or collapses, so stale bounds never get treated as "active".
    DisposableEffect(messageId) {
        onDispose { controller.setBounds(messageId, null) }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                if (expanded) {
                    val top = coordinates.positionInRoot().y
                    controller.setBounds(
                        messageId,
                        FoldBounds(top = top, bottom = top + coordinates.size.height, meta = meta),
                    )
                } else {
                    controller.setBounds(messageId, null)
                }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { controller.setExpanded(messageId, !expanded) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Text(
                text = "已完成",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!expanded && meta != null) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "（$meta）",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
            ) {
                if (!reasoning.isNullOrBlank()) {
                    Text(
                        text = reasoning,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            lineHeight = 21.sp,
                            letterSpacing = 0.13.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (activities.isNotEmpty()) {
                    ToolActivityList(activities = activities)
                }
            }
        }
    }
}

@Composable
private fun ToolActivityList(activities: List<ToolActivity>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
    ) {
        activities.forEach { activity ->
            when (activity.kind) {
                "shell" -> ShellTerminalCard(activity = activity)
                "create", "edit", "delete", "move" -> FileOpCard(activity = activity)
                else -> GenericToolCard(activity = activity)
            }
        }
    }
}

@Composable
private fun ShellTerminalCard(activity: ToolActivity) {
    val running = activity.status == "running"
    var expanded by rememberSaveable(activity.id) { mutableStateOf(running) }
    LaunchedEffect(activity.status) {
        if (running) expanded = true
    }
    val title = remember(activity) { shellActivityTitle(activity) }
    val body = remember(activity) { shellActivityBody(activity) }
    val statusLabel = when {
        running -> "执行中…"
        activity.status == "error" || !activity.success -> "失败"
        else -> null
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = ">_",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (statusLabel != null) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (activity.status == "error" || !activity.success) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            if (expanded) {
                Text(
                    text = body.ifBlank { if (running) "正在运行…" else "" },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp,
                    ),
                    color = if (body.isBlank() && running) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}

@Composable
private fun FileOpCard(activity: ToolActivity) {
    val path = activity.preview?.path
        ?: activity.arguments?.get("path")?.jsonPrimitive?.contentOrNull
        ?: activity.title
    val running = activity.status == "running"
    val kindLabel = when (activity.kind) {
        "create" -> "新建"
        "delete" -> "删除"
        "move" -> "移动"
        else -> "编辑"
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Description,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = kindLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = when {
                    running -> "执行中…"
                    activity.status == "error" || !activity.success -> "失败"
                    else -> "完成"
                },
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    activity.status == "error" || !activity.success -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun GenericToolCard(activity: ToolActivity) {
    val running = activity.status == "running"
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = activity.title.ifBlank { activity.toolName },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = when {
                    running -> "执行中…"
                    activity.status == "error" || !activity.success -> "失败"
                    else -> "完成"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun shellActivityTitle(activity: ToolActivity): String {
    val description = activity.arguments?.get("description")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    if (description.isNotEmpty()) return description
    return activity.title
        .replace(Regex("^执行命令[：:]\\s*"), "")
        .replace(Regex("^运行命令[：:]\\s*"), "")
        .replace(Regex("^Run(?:ning)?(?:\\s+command)?[：:]\\s*", RegexOption.IGNORE_CASE), "")
        .trim()
        .ifBlank { activity.toolName }
}

private fun shellActivityBody(activity: ToolActivity): String {
    val command = activity.arguments?.get("command")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    val output = activity.result?.trim().orEmpty().ifBlank {
        extractShellOutput(activity.detail)
    }
    return when {
        output.isNotBlank() && command.isNotBlank() &&
            !output.contains(command.take(minOf(40, command.length))) -> {
            "$ $command\n\n$output"
        }
        output.isNotBlank() -> output
        command.isNotBlank() -> "$ $command"
        else -> ""
    }
}

private fun extractShellOutput(detail: String?): String {
    if (detail.isNullOrBlank()) return ""
    Regex("\\*\\*输出[：:]\\*\\*\\s*```[^\\n]*\\n([\\s\\S]*?)```")
        .find(detail)
        ?.groupValues
        ?.getOrNull(1)
        ?.trimEnd()
        ?.let { return it }
    Regex("```(?:powershell|bash|shell|ps1)?\\n([\\s\\S]*?)```")
        .find(detail)
        ?.groupValues
        ?.getOrNull(1)
        ?.trimEnd()
        ?.let { return it }
    return detail
}

@Composable
private fun ReasoningSection(reasoning: String, streaming: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    var userPinned by remember { mutableStateOf(false) }
    LaunchedEffect(streaming) {
        if (streaming) {
            userPinned = false
            expanded = true
        } else if (!userPinned) {
            expanded = false
        }
    }
    val displayText = remember(reasoning, streaming) {
        if (!streaming) return@remember reasoning
        val maxChars = 12_000
        val maxLines = 240
        var text = reasoning
        var truncated = false
        if (text.length > maxChars) {
            text = text.takeLast(maxChars)
            truncated = true
        }
        val lines = text.lines()
        if (lines.size > maxLines) {
            text = lines.takeLast(maxLines).joinToString("\n")
            truncated = true
        }
        if (truncated) "...\n$text" else text
    }
    val collapsedHint = remember(reasoning.length) {
        val formatted = java.text.NumberFormat.getIntegerInstance().format(reasoning.length)
        "（$formatted 字）"
    }
    val scrollState = rememberScrollState()
    LaunchedEffect(displayText, streaming, expanded) {
        if (streaming && expanded) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!streaming) {
                            expanded = !expanded
                            userPinned = expanded
                        }
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Text(
                    text = if (streaming) "思考中…" else "思考过程",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!streaming && !expanded) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = collapsedHint,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
            }
            if (expanded && displayText.isNotBlank()) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 21.sp,
                        letterSpacing = 0.13.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(scrollState)
                        .padding(top = 2.dp, bottom = 4.dp),
                )
            }
    }
}

@Composable
private fun PlanCard(
    tasks: List<PlanTaskItem>,
    approved: Boolean,
    onApprove: () -> Unit,
) {
    val haptic = rememberAnyaHaptics()
    Surface(
        shape = RoundedCornerShape(AnyaSpace.CardRadius),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AnyaSpace.Lg),
            verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
        ) {
            Text(
                text = "计划",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            tasks.forEach { task ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = (task.level * 12).dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
                ) {
                    Text(
                        text = when (task.status) {
                            "completed" -> "✓"
                            "in_progress" -> "›"
                            "cancelled" -> "×"
                            else -> "·"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (task.status == "completed") {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        },
                    )
                    Text(
                        text = task.content,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (task.status == "in_progress") FontWeight.SemiBold else FontWeight.Normal,
                        textDecoration = if (task.status == "completed" || task.status == "cancelled") {
                            TextDecoration.LineThrough
                        } else {
                            TextDecoration.None
                        },
                        color = if (task.status == "completed" || task.status == "cancelled") {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        },
                    )
                }
            }
            Button(
                onClick = {
                    haptic.confirm()
                    onApprove()
                },
                enabled = !approved,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(top = AnyaSpace.Xs),
                shape = RoundedCornerShape(AnyaSpace.ControlRadius),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Text(
                    text = if (approved) "已批准并执行" else "批准并执行",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun CodeChangesCard(changes: List<CodeChangeEntry>) {
    var expanded by rememberSaveable(changes.hashCode()) { mutableStateOf(false) }
    val totalAdded = changes.sumOf { it.added }
    val totalRemoved = changes.sumOf { it.removed }
    val visible = if (expanded) changes else changes.take(3)
    val remaining = changes.size - visible.size

    Surface(
        shape = RoundedCornerShape(AnyaSpace.CardRadius),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AnyaSpace.Lg),
            verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "已编辑 ${changes.size} 个文件",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (totalAdded > 0) {
                    Text(
                        text = "+$totalAdded",
                        style = MaterialTheme.typography.labelMedium,
                        color = ai.anya.companion.core.designsystem.theme.AnyaColors.Success,
                    )
                }
                if (totalRemoved > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "-$totalRemoved",
                        style = MaterialTheme.typography.labelMedium,
                        color = ai.anya.companion.core.designsystem.theme.AnyaColors.Danger,
                    )
                }
            }
            visible.forEach { change ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = change.path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    if (change.added > 0) {
                        Text(
                            text = "+${change.added}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ai.anya.companion.core.designsystem.theme.AnyaColors.Success,
                        )
                    }
                    if (change.removed > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "-${change.removed}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ai.anya.companion.core.designsystem.theme.AnyaColors.Danger,
                        )
                    }
                }
            }
            if (remaining > 0) {
                Text(
                    text = "展开另外 $remaining 个",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { expanded = true },
                )
            }
        }
    }
}

// endregion

private fun formatMessageTime(epochMs: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
