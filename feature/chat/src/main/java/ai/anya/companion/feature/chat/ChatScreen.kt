package ai.anya.companion.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Stop
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.rounded.UploadFile
import ai.anya.companion.core.designsystem.component.AnyaLoadingIndicator
import ai.anya.companion.core.designsystem.component.AnyaSegmentedControl
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import ai.anya.companion.core.model.workspace.McpServerSummary
import ai.anya.companion.core.model.workspace.SkillSummary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
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
import ai.anya.companion.core.designsystem.component.AnyaInlineLoadingMark
import ai.anya.companion.core.designsystem.component.AnyaTopBarIconChip
import ai.anya.companion.core.designsystem.icon.AnyaIcons
import ai.anya.companion.core.designsystem.theme.AnyaSpace
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import ai.anya.companion.core.domain.repository.ConnectionState
import ai.anya.companion.core.model.session.ChatMessage
import ai.anya.companion.core.model.session.ChatMode
import ai.anya.companion.core.model.session.ChatModelInfo
import ai.anya.companion.core.model.session.ChatRole
import ai.anya.companion.core.model.session.ChatSharedFile
import ai.anya.companion.core.model.session.SharedFileStatus
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
/** Inline banner above the composer showing file download progress / outcome. */
@Composable
private fun DownloadNoticeBanner(
    download: FileDownloadUiState,
    onDismiss: () -> Unit,
) {
    val haptic = rememberAnyaHaptics()
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AnyaSpace.Screen)
            .padding(bottom = AnyaSpace.Xs)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (download.inProgress) {
            AnyaInlineLoadingMark(size = 16.dp)
        } else {
            Icon(
                imageVector = Icons.Rounded.Description,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = download.message.orEmpty(),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!download.inProgress && download.localUri != null) {
            Text(
                text = stringResource(R.string.shared_file_open),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable {
                        haptic.buttonPress()
                        openSharedFile(
                            context,
                            ChatSharedFile(
                                offerId = "",
                                path = "",
                                name = download.fileName.orEmpty(),
                                mime = download.mime ?: "*/*",
                                exportedUri = download.localUri,
                                status = SharedFileStatus.Ready,
                            ),
                        )
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        if (!download.inProgress) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.shared_file_close),
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .clickable {
                        haptic.tick()
                        onDismiss()
                    }
                    .padding(3.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConnectionLostBanner(
    connectionState: ConnectionState,
    onRetry: () -> Unit,
) {
    val haptic = rememberAnyaHaptics()
    val (label, showRetry) = when (connectionState) {
        ConnectionState.Connecting -> stringResource(R.string.chat_connecting) to false
        ConnectionState.Reconnecting -> stringResource(R.string.chat_reconnecting) to false
        ConnectionState.Error -> stringResource(R.string.chat_connection_error) to true
        ConnectionState.Disconnected -> stringResource(R.string.chat_disconnected) to true
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
                    text = stringResource(R.string.chat_reconnect),
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

/** Tracks which completed-turn "已完成" folds are expanded. */
private class CompletedFoldController {
    val expanded = mutableStateMapOf<String, Boolean>()

    fun isExpanded(messageId: String): Boolean = expanded[messageId] == true

    fun setExpanded(messageId: String, value: Boolean) {
        expanded[messageId] = value
    }
}

private fun userMessagePreview(content: String): String {
    val compact = content.replace(Regex("\\s+"), " ").trim()
    if (compact.isEmpty()) return "用户消息"
    return if (compact.length > 72) compact.take(72) + "…" else compact
}

private fun formatUserTurnTime(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    val then = java.util.Calendar.getInstance().apply { timeInMillis = epochMs }
    val now = java.util.Calendar.getInstance()
    val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(then.time)
    val sameDay = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
    val sameYear = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR)
    return when {
        sameDay -> time
        sameYear -> java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.CHINA).format(then.time)
        else -> java.text.SimpleDateFormat("yyyy年M月d日 HH:mm", java.util.Locale.CHINA).format(then.time)
    }
}

@Composable
public fun ChatRoute(
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val attachCatalog by viewModel.attachCatalog.collectAsStateWithLifecycle()
    val download by viewModel.download.collectAsStateWithLifecycle()
    val localUploads by viewModel.localUploads.collectAsStateWithLifecycle()
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
        download = download,
        localUploads = localUploads,
        onPickLocalFiles = viewModel::uploadPickedUris,
        onDownloadFile = viewModel::downloadFile,
        onExportSharedFile = viewModel::exportSharedFile,
        onFetchSharedFile = viewModel::fetchSharedFile,
        onDismissDownload = viewModel::dismissDownloadNotice,
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
    download: FileDownloadUiState = FileDownloadUiState(),
    localUploads: List<LocalUploadItem> = emptyList(),
    onPickLocalFiles: (List<android.net.Uri>) -> Unit = {},
    onDownloadFile: (String) -> Unit = {},
    onExportSharedFile: (String) -> Unit = {},
    onFetchSharedFile: (String) -> Unit = {},
    onDismissDownload: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = rememberAnyaHaptics()
    val bottomInset = WindowInsets.ime.union(WindowInsets.navigationBars)
        .asPaddingValues()
        .calculateBottomPadding()
    var stickToBottom by remember { mutableStateOf(true) }
    var didFocusMessage by remember(state.focusMessageId) { mutableStateOf(false) }
    var previewSharedFile by remember { mutableStateOf<ai.anya.companion.core.model.session.ChatSharedFile?>(null) }
    var previewSharedUrl by remember { mutableStateOf<ai.anya.companion.core.model.session.ChatSharedUrl?>(null) }

    val userTurns = remember(state.messages) {
        state.messages.filter { it.role == ChatRole.User }.asReversed()
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

    LaunchedEffect(bottomInset, stickToBottom, state.messages.size) {
        if (bottomInset > 0.dp && stickToBottom && state.messages.isNotEmpty()) {
            listState.animateSettleToBottom(state.messages.lastIndex)
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return@LaunchedEffect
            stickToBottom = lastVisible.index >= state.messages.lastIndex - 1 &&
                lastVisible.offset + lastVisible.size <= info.viewportEndOffset + with(density) { 48.dp.roundToPx() }
        }
    }

    fun jumpToMessage(messageId: String) {
        val messageIndex = state.messages.indexOfFirst { it.id == messageId }
        if (messageIndex < 0) return
        stickToBottom = false
        scope.launch {
            listState.animateScrollToItem(messageIndex)
        }
    }

    fun scrollToBottom() {
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
    var diffRequest by remember { mutableStateOf<DiffViewRequest?>(null) }

    // The composer floats *over* the message list rather than reserving its
    // own row (its height varies a lot: plain field vs. multi-line ask/tool
    // approval panels), so we measure its real footprint and feed that back
    // into the list as bottom clearance — otherwise the last bit of content
    // ends up hidden underneath it.
    var composerFootprintPx by remember { mutableStateOf(0) }
    val composerFootprintDp = with(density) { composerFootprintPx.toDp() }
    var headerContentPx by remember { mutableStateOf(0) }
    val statusBarDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerContentDp = if (headerContentPx > 0) {
        with(density) { headerContentPx.toDp() }
    } else {
        statusBarDp + 40.dp + AnyaSpace.Xs * 2 + 20.dp
    }
    val hazeState = rememberHazeState()
    val canvas = MaterialTheme.colorScheme.surface

    val foldController = remember { CompletedFoldController() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(canvas),
    ) {
        if (state.messages.isEmpty() && state.historyLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
                    .padding(top = headerContentDp, bottom = composerFootprintDp),
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
                    .hazeSource(hazeState)
                    .padding(top = headerContentDp, bottom = composerFootprintDp),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
                contentPadding = PaddingValues(
                    start = AnyaSpace.Screen,
                    end = AnyaSpace.Screen,
                    top = headerContentDp + AnyaSpace.Lg,
                    bottom = composerFootprintDp + AnyaSpace.Sm + bottomInset,
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
                        onOpenDiff = { request -> diffRequest = request },
                        onExportSharedFile = onExportSharedFile,
                        onFetchSharedFile = onFetchSharedFile,
                        onPreviewSharedImage = { previewSharedFile = it },
                        onOpenSharedUrl = { previewSharedUrl = it },
                    )
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = showScrollToBottom,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 10.dp,
                        bottom = composerFootprintDp + bottomInset + 10.dp,
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

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .hazeEffect(state = hazeState) {
                    backgroundColor = canvas
                    tints = listOf(HazeTint(canvas.copy(alpha = 0.78f)))
                    blurRadius = 24.dp
                    noiseFactor = 0.06f
                    progressive = HazeProgressive.verticalGradient(
                        startIntensity = 0f,
                        endIntensity = 1f,
                    )
                }
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Transparent,
                        0.28f to canvas.copy(alpha = 0.22f),
                        0.62f to canvas.copy(alpha = 0.82f),
                        1.00f to canvas,
                    ),
                )
                .windowInsetsPadding(WindowInsets.ime)
                .zIndex(2f),
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
                if (download.message != null) {
                    DownloadNoticeBanner(
                        download = download,
                        onDismiss = onDismissDownload,
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
            Spacer(
                modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars),
            )
        }

        ChatTopBar(
            chatMode = state.compose.chatMode,
            approvalMode = state.compose.toolApprovalMode,
            hazeState = hazeState,
            canvas = canvas,
            onHeaderHeight = { headerContentPx = it },
            onBack = onBack,
            onModeClick = { sheet = ChatSheet.ChatMode },
            onApprovalClick = { sheet = ChatSheet.ApprovalMode },
            onHistoryClick = { sheet = ChatSheet.UserTurns },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(3f),
        )
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
            localUploads = localUploads,
            onRefresh = onAttachOpen,
            onInsert = {
                onAttachInsert(it)
                sheet = ChatSheet.None
            },
            onPickLocalFiles = { uris ->
                onPickLocalFiles(uris)
                sheet = ChatSheet.None
            },
            onDismiss = { sheet = ChatSheet.None },
            onDownloadFile = { path ->
                onDownloadFile(path)
                // Close so the progress banner above the composer is visible.
                sheet = ChatSheet.None
            },
        )
        ChatSheet.UserTurns -> UserTurnsSheet(
            turns = userTurns,
            onJump = { messageId ->
                sheet = ChatSheet.None
                jumpToMessage(messageId)
            },
            onDismiss = { sheet = ChatSheet.None },
        )
        ChatSheet.None -> Unit
    }

    diffRequest?.let { request ->
        DiffViewerSheet(
            request = request,
            onDismiss = { diffRequest = null },
            onDownload = {
                onDownloadFile(request.path)
                diffRequest = null
            },
        )
    }
    previewSharedFile?.let { file ->
        if (file.mime.startsWith("image/")) {
            SharedImageFullscreenDialog(
                file = file,
                onDismiss = { previewSharedFile = null },
                onExport = { onExportSharedFile(file.offerId) },
            )
        } else {
            SharedDocumentPreviewDialog(
                file = file,
                onDismiss = { previewSharedFile = null },
                onExport = { onExportSharedFile(file.offerId) },
            )
        }
    }
    previewSharedUrl?.let { url ->
        SharedUrlPreviewDialog(
            url = url,
            onDismiss = { previewSharedUrl = null },
        )
    }
}

private enum class ChatSheet { None, ChatMode, ApprovalMode, Model, Attach, UserTurns }

@Composable
private fun chatTopChipColor(): Color =
    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)

// region Top bar

@Composable
private fun ChatTopBar(
    chatMode: ChatMode,
    approvalMode: ToolApprovalMode,
    hazeState: HazeState,
    canvas: Color,
    onHeaderHeight: (Int) -> Unit,
    onBack: () -> Unit,
    onModeClick: () -> Unit,
    onApprovalClick: () -> Unit,
    onHistoryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberAnyaHaptics()
    val barHeight = 40.dp
    val chipColor = chatTopChipColor()
    Column(
        modifier = modifier
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
            .onGloballyPositioned { onHeaderHeight(it.size.height) },
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
            ChatModeChip(chatMode = chatMode, onClick = onModeClick)
            Spacer(modifier = Modifier.weight(1f))
            ApprovalModeChip(approvalMode = approvalMode, onClick = onApprovalClick)
            AnyaTopBarIconChip(
                icon = Icons.Rounded.History,
                contentDescription = "发出的消息",
                height = barHeight,
                containerColor = chipColor,
                onClick = {
                    haptic.buttonPress()
                    onHistoryClick()
                },
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
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
            .height(40.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(chatTopChipColor())
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
private fun UserTurnsSheet(
    turns: List<ChatMessage>,
    onJump: (String) -> Unit,
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
                text = "发出的消息",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "点击一条可跳转到对话中的位置",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = AnyaSpace.Md),
            )
            if (turns.isEmpty()) {
                Text(
                    text = "还没有发出的消息",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = AnyaSpace.Lg),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(turns, key = ChatMessage::id) { message ->
                        val timeLabel = formatUserTurnTime(message.createdAtEpochMs)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    haptic.linearTick()
                                    onJump(message.id)
                                }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                        ) {
                            if (timeLabel.isNotEmpty()) {
                                Text(
                                    text = timeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = userMessagePreview(message.content),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = if (timeLabel.isNotEmpty()) 2.dp else 0.dp),
                            )
                        }
                    }
                }
            }
        }
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
    localUploads: List<LocalUploadItem>,
    onRefresh: () -> Unit,
    onInsert: (String) -> Unit,
    onPickLocalFiles: (List<android.net.Uri>) -> Unit,
    onDismiss: () -> Unit,
    onDownloadFile: ((String) -> Unit)? = null,
) {
    val haptic = rememberAnyaHaptics()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { AttachTab.entries.size })
    var expandedDirs by rememberSaveable { mutableStateOf(setOf<String>()) }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) onPickLocalFiles(uris)
    }

    AnyaBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(640.dp)
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
            AnyaSegmentedControl(
                options = listOf("本机", "文件", "Skills", "MCP"),
                selectedIndex = pagerState.currentPage,
                selectedProgress = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                onSelect = { index ->
                    scope.launch { pagerState.animateScrollToPage(index) }
                },
            )
            Spacer(modifier = Modifier.height(AnyaSpace.Md))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                when (AttachTab.entries[page]) {
                    AttachTab.Upload -> LocalUploadPage(
                        uploads = localUploads,
                        onPick = { picker.launch(arrayOf("*/*")) },
                    )
                    AttachTab.Files -> AttachFilesPage(
                        catalog = catalog,
                        expandedDirs = expandedDirs,
                        onToggle = { path ->
                            expandedDirs = if (path in expandedDirs) {
                                expandedDirs - path
                            } else {
                                expandedDirs + path
                            }
                        },
                        onInsert = onInsert,
                        onDownloadFile = onDownloadFile,
                    )
                    AttachTab.Skills -> AttachSkillsPage(
                        catalog = catalog,
                        onInsert = onInsert,
                    )
                    AttachTab.Mcp -> AttachMcpPage(
                        catalog = catalog,
                        onInsert = onInsert,
                    )
                }
            }
        }
    }
}

private enum class AttachTab { Upload, Files, Skills, Mcp }

@Composable
private fun LocalUploadPage(
    uploads: List<LocalUploadItem>,
    onPick: () -> Unit,
) {
    val haptic = rememberAnyaHaptics()
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Md),
    ) {
        Button(
            onClick = {
                haptic.buttonPress()
                onPick()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            contentPadding = PaddingValues(horizontal = AnyaSpace.Lg),
        ) {
            Icon(
                imageVector = Icons.Rounded.UploadFile,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "从本机选择",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (uploads.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm)) {
                uploads.forEach { item ->
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachFilesPage(
    catalog: AttachCatalogUiState,
    expandedDirs: Set<String>,
    onToggle: (String) -> Unit,
    onInsert: (String) -> Unit,
    onDownloadFile: ((String) -> Unit)?,
) {
    val error = catalog.filesError
    val tree = catalog.fileTree
    when {
        catalog.loading && tree.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AnyaLoadingIndicator(size = 56.dp, label = null)
            }
        }
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
                    ?: "未选择工作区，或当前工作区没有文件。随问请用「本机」上传。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(tree, key = { it.path }) { node ->
                    AttachFileTreeNode(
                        node = node,
                        depth = 0,
                        expanded = expandedDirs,
                        onToggle = onToggle,
                        onSelect = { path, isDir ->
                            onInsert(if (isDir) "@$path/ " else "@$path ")
                        },
                        onDownload = onDownloadFile,
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachSkillsPage(
    catalog: AttachCatalogUiState,
    onInsert: (String) -> Unit,
) {
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
            LazyColumn(modifier = Modifier.fillMaxSize()) {
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

@Composable
private fun AttachMcpPage(
    catalog: AttachCatalogUiState,
    onInsert: (String) -> Unit,
) {
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
            LazyColumn(modifier = Modifier.fillMaxSize()) {
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AttachFileTreeNode(
    node: ai.anya.companion.core.model.workspace.FileNode,
    depth: Int,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    onSelect: (path: String, isDir: Boolean) -> Unit,
    onDownload: ((String) -> Unit)? = null,
) {
    val haptic = rememberAnyaHaptics()
    val isDir = node.isDirectory || node.children.isNotEmpty()
    val isExpanded = node.path in expanded
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AnyaSpace.ControlRadius))
            .combinedClickable(
                onClick = {
                    if (isDir) onToggle(node.path) else onSelect(node.path, false)
                },
                onLongClick = if (!isDir && onDownload != null) {
                    {
                        haptic.buttonPress()
                        onDownload(node.path)
                    }
                } else {
                    null
                },
            )
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
                onDownload = onDownload,
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
    val shape = RoundedCornerShape(20.dp)
    val ink = MaterialTheme.colorScheme.onBackground
    val parsed = remember(draft) { parseComposerText(draft) }
    val controlSize = 40.dp
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
            .padding(start = edge, end = edge, bottom = 14.dp, top = 4.dp)
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
        if (
            pendingAsk != null &&
            (
                pendingAsk.kind == ai.anya.companion.core.model.approval.ApprovalKind.Tool ||
                    pendingAsk.kind == ai.anya.companion.core.model.approval.ApprovalKind.PathPermission
                )
        ) {
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
    val isPath = ask.kind == ai.anya.companion.core.model.approval.ApprovalKind.PathPermission
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
                text = if (isPath) "路径权限" else "工具审批",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = ask.title.ifBlank { ask.toolName.orEmpty() },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        ask.previewSummary?.takeIf { it.isNotBlank() }?.let { preview ->
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ask.toolName?.takeIf { it.isNotBlank() && it != ask.title && !isPath }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val options = if (isPath) {
            listOf(
                Triple("允许一次", "仅允许本次读取/写入该路径", ai.anya.companion.core.model.approval.ApprovalDecision.AllowOnce),
                Triple("始终允许", "本会话记住该路径权限", ai.anya.companion.core.model.approval.ApprovalDecision.AllowSession),
                Triple("拒绝", "取消本次路径访问", ai.anya.companion.core.model.approval.ApprovalDecision.Deny),
            )
        } else {
            listOf(
                Triple("允许一次", "仅允许本次工具操作", ai.anya.companion.core.model.approval.ApprovalDecision.AllowOnce),
                Triple("本会话允许", "本会话内记住该工具", ai.anya.companion.core.model.approval.ApprovalDecision.AllowSession),
                Triple("拒绝", "取消本次工具操作", ai.anya.companion.core.model.approval.ApprovalDecision.Deny),
            )
        }
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
    onOpenDiff: (DiffViewRequest) -> Unit = {},
    onExportSharedFile: (String) -> Unit = {},
    onFetchSharedFile: (String) -> Unit = {},
    onPreviewSharedImage: (ai.anya.companion.core.model.session.ChatSharedFile) -> Unit = {},
    onOpenSharedUrl: (ai.anya.companion.core.model.session.ChatSharedUrl) -> Unit = {},
) {
    if (message.sharedFiles.isNotEmpty() || message.sharedUrls.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm)) {
            if (message.sharedFiles.isNotEmpty()) {
                SharedFilesBlock(
                    files = message.sharedFiles,
                    onExport = onExportSharedFile,
                    onPreviewImage = onPreviewSharedImage,
                    onFetch = onFetchSharedFile,
                )
            }
            if (message.sharedUrls.isNotEmpty()) {
                SharedUrlsBlock(
                    urls = message.sharedUrls,
                    onOpen = onOpenSharedUrl,
                )
            }
        }
        if (message.content.isBlank()) {
            return
        }
    }
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
                onOpenDiff = onOpenDiff,
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
    onOpenDiff: (DiffViewRequest) -> Unit = {},
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
                ToolActivityList(activities = message.toolActivities, onOpenDiff = onOpenDiff)
            }
        } else if (hasReasoning || hasActivities) {
            CompletedWorkFold(
                messageId = message.id,
                reasoning = reasoning,
                activities = message.toolActivities,
                controller = foldController,
                onOpenDiff = onOpenDiff,
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
            CodeChangesCard(
                changes = message.codeChanges,
                onOpenChange = { change ->
                    val diff = findDiffForPath(message.toolActivities, change.path)
                    if (diff != null) {
                        onOpenDiff(DiffViewRequest(path = change.path, unifiedDiff = diff))
                    }
                },
            )
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
    onOpenDiff: (DiffViewRequest) -> Unit = {},
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
    Column(modifier = Modifier.fillMaxWidth()) {
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
                    ToolActivityList(activities = activities, onOpenDiff = onOpenDiff)
                }
            }
        }
    }
}

@Composable
private fun ToolActivityList(
    activities: List<ToolActivity>,
    onOpenDiff: (DiffViewRequest) -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
    ) {
        activities.forEach { activity ->
            when (activity.kind) {
                "shell" -> ShellTerminalCard(activity = activity)
                "create", "edit", "delete", "move" -> FileOpCard(activity = activity, onOpenDiff = onOpenDiff)
                else -> GenericToolCard(activity = activity)
            }
        }
    }
}

/** Latest non-empty diff a message's tool activity produced for [path]. */
internal fun findDiffForPath(activities: List<ToolActivity>, path: String): String? {
    val normalized = path.replace('\\', '/')
    return activities.lastOrNull { activity ->
        val preview = activity.preview ?: return@lastOrNull false
        preview.unifiedDiff.isNotBlank() &&
            (
                preview.path.replace('\\', '/') == normalized ||
                    preview.affectedPaths.any { it.replace('\\', '/') == normalized }
                )
    }?.preview?.unifiedDiff
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
private fun FileOpCard(
    activity: ToolActivity,
    onOpenDiff: (DiffViewRequest) -> Unit = {},
) {
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
    val diff = activity.preview?.unifiedDiff?.takeIf { it.isNotBlank() }
    val haptics = rememberAnyaHaptics()
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
                .then(
                    if (diff != null) {
                        Modifier.clickable {
                            haptics.tick()
                            onOpenDiff(DiffViewRequest(path = path, unifiedDiff = diff))
                        }
                    } else {
                        Modifier
                    },
                )
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
            if (diff != null && !running) {
                Text(
                    text = "查看 diff",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
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
    val live = tasks.any { it.status == "in_progress" }
    var expanded by rememberSaveable(tasks.size, approved) { mutableStateOf(!approved || live) }
    LaunchedEffect(live) {
        if (live) expanded = true
    }
    val done = tasks.count { it.status == "completed" }
    Surface(
        shape = RoundedCornerShape(AnyaSpace.CardRadius),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptic.tick()
                        expanded = !expanded
                    }
                    .padding(horizontal = AnyaSpace.Lg, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "计划",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (!expanded) {
                    Text(
                        text = "$done/${tasks.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = AnyaSpace.Lg, end = AnyaSpace.Lg, bottom = AnyaSpace.Lg),
                    verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
                ) {
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
                                fontWeight = if (task.status == "in_progress") {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Normal
                                },
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
    }
}

@Composable
private fun CodeChangesCard(
    changes: List<CodeChangeEntry>,
    onOpenChange: ((CodeChangeEntry) -> Unit)? = null,
) {
    val haptics = rememberAnyaHaptics()
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (changes.size > 3) {
                            Modifier.clickable {
                                haptics.tick()
                                expanded = !expanded
                            }
                        } else {
                            Modifier
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (onOpenChange != null) {
                                Modifier.clickable {
                                    haptics.tick()
                                    onOpenChange(change)
                                }
                            } else {
                                Modifier
                            },
                        ),
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
            if (changes.size > 3) {
                Text(
                    text = if (expanded) "收起" else "展开另外 $remaining 个",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable {
                        haptics.tick()
                        expanded = !expanded
                    },
                )
            }
        }
    }
}

// endregion

private fun formatMessageTime(epochMs: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
