package ai.anya.companion.feature.chat

import android.widget.Toast
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Difference
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.UploadFile
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import ai.anya.companion.core.designsystem.component.AnyaLoadingIndicator
import ai.anya.companion.core.designsystem.component.AnyaSegmentedControl
import ai.anya.companion.core.designsystem.component.AnyaSheetCard
import ai.anya.companion.core.designsystem.component.AnyaSheetHairline
import ai.anya.companion.core.designsystem.component.AnyaSheetHeader
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import ai.anya.companion.core.model.workspace.McpServerSummary
import ai.anya.companion.core.model.workspace.SkillSummary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.rounded.Undo
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ai.anya.companion.core.designsystem.haptic.rememberAnyaHaptics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.anya.companion.core.designsystem.component.MascotFace
import ai.anya.companion.core.designsystem.component.MascotState
import ai.anya.companion.core.designsystem.component.MascotTool
import ai.anya.companion.core.designsystem.component.AnyaInlineLoadingMark
import ai.anya.companion.core.designsystem.component.AnyaGlassIconChip
import ai.anya.companion.core.designsystem.component.anyaGlassSurface
import ai.anya.companion.core.designsystem.icon.AnyaIcons
import ai.anya.companion.core.designsystem.theme.AnyaColors
import ai.anya.companion.core.designsystem.theme.AnyaSpace
import ai.anya.companion.core.designsystem.theme.LocalDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import ai.anya.companion.core.domain.repository.ConnectionState
import ai.anya.companion.core.model.session.ChatMessage
import ai.anya.companion.core.model.session.ChatMode
import ai.anya.companion.core.model.session.ChatModelInfo
import ai.anya.companion.core.model.session.ChatRole
import ai.anya.companion.core.model.session.ChatSharedFile
import ai.anya.companion.core.model.session.ContextUsageSnapshot
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

private sealed class ChatDisplayItem {
    abstract val key: String

    data class User(val message: ChatMessage) : ChatDisplayItem() {
        override val key: String get() = message.id
    }

    data class Assistant(
        val message: ChatMessage,
        val injects: List<ChatMessage>,
        val turnUser: ChatMessage?,
    ) : ChatDisplayItem() {
        override val key: String get() = message.id
    }
}

/**
 * Fold soft-inject user messages into the preceding assistant turn (desktop MessageList).
 *
 * Single forward pass: the owning user message is carried along instead of rescanning backwards
 * for every assistant, which keeps this O(n) on long histories.
 */
private fun buildChatDisplayItems(messages: List<ChatMessage>): List<ChatDisplayItem> {
    val items = mutableListOf<ChatDisplayItem>()
    var turnUser: ChatMessage? = null
    messages.forEach { message ->
        when (message.role) {
            ChatRole.User -> {
                if (isSoftInjectContent(message.content)) {
                    val last = items.lastOrNull()
                    if (last is ChatDisplayItem.Assistant) {
                        items[items.lastIndex] = last.copy(injects = last.injects + message)
                    } else {
                        items += ChatDisplayItem.User(message)
                    }
                } else {
                    turnUser = message
                    items += ChatDisplayItem.User(message)
                }
            }
            else -> {
                items += ChatDisplayItem.Assistant(
                    message = message,
                    injects = emptyList(),
                    turnUser = turnUser,
                )
            }
        }
    }
    return items
}

/** Tracks which completed-turn "已完成" folds are expanded. */
internal class CompletedFoldController {
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
    onOpenSettings: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val attachCatalog by viewModel.attachCatalog.collectAsStateWithLifecycle()
    val download by viewModel.download.collectAsStateWithLifecycle()
    val localUploads by viewModel.localUploads.collectAsStateWithLifecycle()
    val contextUsage by viewModel.contextUsage.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.refreshAttachCatalog()
    }
    LaunchedEffect(
        state.sessionId,
        state.messages.size,
        state.draft,
        state.compose.chatModel,
        state.connectionState,
    ) {
        viewModel.refreshContextUsage()
    }
    ChatScreen(
        state = state,
        attachCatalog = attachCatalog,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        onDraftChange = viewModel::onDraftChange,
        onSend = viewModel::send,
        onStop = viewModel::stop,
        onChatModeSelect = viewModel::setChatMode,
        onApprovalModeSelect = viewModel::setApprovalMode,
        onModelSelect = viewModel::setModel,
        onThinkingSelect = viewModel::applyThinkingOption,
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
        onEnsureImagePreview = viewModel::ensureImagePreview,
        onExportSharedFile = viewModel::exportSharedFile,
        onFetchSharedFile = viewModel::fetchSharedFile,
        onMarkSharedUrlViewed = viewModel::markInboxUrlViewed,
        onDismissDownload = viewModel::dismissDownloadNotice,
        contextUsage = contextUsage,
        onEditFromImage = viewModel::startImageEdit,
        onClearImageEditRefs = viewModel::clearImageEditRefs,
        onRemoveImageEditRef = viewModel::removeImageEditRef,
        onRewindToUserMessage = viewModel::rewindToUserMessage,
        onUpdateImageGenCompose = viewModel::updateImageGenCompose,
        onSetImageGenModel = viewModel::setImageGenModel,
        onRefreshImageGenOptions = viewModel::refreshImageGenOptions,
        onPickAlbumRefs = viewModel::uploadAlbumAsRefs,
        onGuideStaged = viewModel::guideStaged,
        onRemoveStaged = viewModel::removeStaged,
        onLoadWorkingTreeDiff = viewModel::loadWorkingTreeDiff,
        onLoadEnvironmentContext = viewModel::loadEnvironmentContext,
        onLoadOlderHistory = viewModel::loadOlderMessages,
        onNeedMessageDetail = viewModel::ensureMessageDetail,
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
    onThinkingSelect: (String) -> Unit,
    onAttachInsert: (String) -> Unit,
    onAttachOpen: () -> Unit,
    onApprovePlan: (String) -> Unit,
    onAnswerAsk: (Map<Int, List<String>>, Boolean) -> Unit,
    onAnswerToolApproval: (ai.anya.companion.core.model.approval.ApprovalDecision) -> Unit,
    onRetryConnection: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    download: FileDownloadUiState = FileDownloadUiState(),
    localUploads: List<LocalUploadItem> = emptyList(),
    onPickLocalFiles: (List<android.net.Uri>) -> Unit = {},
    onDownloadFile: (String) -> Unit = {},
    onEnsureImagePreview: (String) -> Unit = {},
    onExportSharedFile: (String) -> Unit = {},
    onFetchSharedFile: (String) -> Unit = {},
    onMarkSharedUrlViewed: (String) -> Unit = {},
    onDismissDownload: () -> Unit = {},
    contextUsage: ContextUsageSnapshot = ContextUsageSnapshot(),
    onEditFromImage: (desktopPath: String, previewDataUrl: String?, regionPlacement: String?) -> Unit = { _, _, _ -> },
    onClearImageEditRefs: () -> Unit = {},
    onRemoveImageEditRef: (ImageEditRef) -> Unit = {},
    onRewindToUserMessage: (String) -> Unit = {},
    onUpdateImageGenCompose: (
        ratio: String?,
        resolution: String?,
        styleId: String?,
        count: Int?,
    ) -> Unit = { _, _, _, _ -> },
    onSetImageGenModel: (provider: String, model: String) -> Unit = { _, _ -> },
    onRefreshImageGenOptions: () -> Unit = {},
    onPickAlbumRefs: (List<android.net.Uri>) -> Unit = {},
    onGuideStaged: (Int) -> Unit = {},
    onRemoveStaged: (Int) -> Unit = {},
    onLoadWorkingTreeDiff: suspend () -> Result<String> = { Result.success("") },
    onLoadEnvironmentContext: suspend () -> Result<ai.anya.companion.core.model.workspace.EnvironmentContext> = {
        Result.failure(Exception("unavailable"))
    },
    onLoadOlderHistory: () -> Unit = {},
    /** Fetches a turn's execution process after the user expands it (light history omits it). */
    onNeedMessageDetail: (String) -> Unit = {},
) {
    MessageDetailRequest.handler = onNeedMessageDetail
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

    // Pull the previous page once the user returns to the very top. Prepending is safe for the
    // viewport: the list keys items by message id, so Compose keeps the anchored message in place.
    var sawNonTop by remember { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) return@LaunchedEffect
        val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        if (!atTop) {
            sawNonTop = true
        } else if (sawNonTop && state.hasMoreHistory) {
            onLoadOlderHistory()
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
    var showDiffReview by remember { mutableStateOf(false) }
    var showEnvironmentContext by remember { mutableStateOf(false) }
    val slashMatches = remember(state.draft) { matchingSlashCommands(state.draft) }
    fun runSlash(command: SlashCommand) {
        onDraftChange("")
        when (command.action) {
            SlashAction.OpenHistory -> sheet = ChatSheet.UserTurns
            SlashAction.OpenModel -> sheet = ChatSheet.Model
            SlashAction.OpenThinking -> sheet = ChatSheet.Model
            SlashAction.OpenSettings -> onOpenSettings()
            SlashAction.OpenAttach -> {
                onAttachOpen()
                sheet = ChatSheet.Attach
            }
            SlashAction.ClearInput -> onDraftChange("")
            SlashAction.ShowContext -> showEnvironmentContext = true
            SlashAction.Exit -> onBack()
        }
    }
    val albumRefPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) onPickAlbumRefs(uris)
    }
    /** User message whose checkpoint the user asked to roll back to (confirm first). */
    var rewindTarget by remember { mutableStateOf<String?>(null) }
    /** Sub-agent parent activity + the pool its children live in. */
    var subagentInspect by remember { mutableStateOf<Pair<ToolActivity, List<ToolActivity>>?>(null) }

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
    val canvas = MaterialTheme.colorScheme.surface
    // One liquid-glass layer for the whole screen: the header chips and the floating
    // composer refract the message list scrolling underneath them.
    val canvasBackground by rememberUpdatedState(canvas)
    val backdrop = rememberLayerBackdrop {
        drawRect(canvasBackground)
        drawContent()
    }
    var selectionEpoch by remember { mutableIntStateOf(0) }

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
                    .layerBackdrop(backdrop)
                    .padding(top = headerContentDp, bottom = composerFootprintDp),
                contentAlignment = Alignment.Center,
            ) {
                AnyaLoadingIndicator(
                    size = 56.dp,
                    label = null,
                )
            }
        } else if (state.messages.isEmpty()) {
            EmptyConversationHero(
                chatMode = state.compose.chatMode,
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .padding(top = headerContentDp, bottom = composerFootprintDp),
            )
        } else {
            val messagesSnapshot = state.messages
            val imagePreviews = state.imagePreviews
            // Derived per recomposition but compared by value: a streaming delta rewrites message
            // text without touching sharedFiles, so the heavy actions below survive the delta.
            val sharedFileRefs = messagesSnapshot.flatMap { it.sharedFiles }
            val generatedImageActions = remember(
                sharedFileRefs,
                imagePreviews,
                onEnsureImagePreview,
                onEditFromImage,
            ) {
                GeneratedImageActions(
                    fetch = onEnsureImagePreview,
                    resolveLocal = { path ->
                        findLocalSharedImage(
                            imagePreviews.asSequence() + sharedFileRefs.asSequence(),
                            path,
                        )
                    },
                    preview = { previewSharedFile = it },
                    editFromImage = { desktopPath ->
                        onEditFromImage(desktopPath, null, null)
                    },
                )
            }
            val turnActions = remember(
                state.checkpointsByUserMessage,
                state.cacheUsages,
                state.rewinding,
                onRewindToUserMessage,
            ) {
                TurnActions(
                    checkpointsByUserMessage = state.checkpointsByUserMessage,
                    cacheUsages = state.cacheUsages,
                    rewinding = state.rewinding,
                    onRewind = { rewindTarget = it },
                    onInspectSubagent = { parent, pool -> subagentInspect = parent to pool },
                )
            }
            val displayItems = remember(messagesSnapshot) { buildChatDisplayItems(messagesSnapshot) }
            key(selectionEpoch) {
                SelectionContainer(modifier = Modifier.fillMaxSize()) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalGeneratedImageActions provides generatedImageActions,
                        LocalTurnActions provides turnActions,
                        LocalShowThinking provides state.showThinking,
                    ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(backdrop),
                        contentPadding = PaddingValues(
                            start = AnyaSpace.Screen,
                            end = AnyaSpace.Screen,
                            top = headerContentDp + AnyaSpace.Lg,
                            bottom = composerFootprintDp + AnyaSpace.Sm + bottomInset,
                        ),
                        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Lg),
                    ) {
                        if (state.isLoadingOlder) {
                            item(key = "older-history-loading") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = AnyaSpace.Sm),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AnyaLoadingIndicator(size = 28.dp, label = null)
                                }
                            }
                        }
                        items(displayItems, key = { it.key }) { item ->
                            when (item) {
                                is ChatDisplayItem.User -> MessageBubble(
                                    message = item.message,
                                    skills = attachCatalog.skills,
                                    mcpServers = attachCatalog.mcpServers,
                                    onApprovePlan = onApprovePlan,
                                    turnUserMessage = null,
                                    approvedPlanIds = state.planApprovedMessageIds,
                                    foldController = foldController,
                                    onOpenDiff = { request -> diffRequest = request },
                                    onExportSharedFile = onExportSharedFile,
                                    onFetchSharedFile = onFetchSharedFile,
                                    onPreviewSharedImage = { previewSharedFile = it },
                                    onOpenSharedUrl = {
                                        previewSharedUrl = it
                                        onMarkSharedUrlViewed(it.offerId)
                                    },
                                )
                                is ChatDisplayItem.Assistant -> {
                                    MessageBubble(
                                        message = item.message,
                                        skills = attachCatalog.skills,
                                        mcpServers = attachCatalog.mcpServers,
                                        onApprovePlan = onApprovePlan,
                                        turnUserMessage = item.turnUser,
                                        approvedPlanIds = state.planApprovedMessageIds,
                                        foldController = foldController,
                                        onOpenDiff = { request -> diffRequest = request },
                                        onExportSharedFile = onExportSharedFile,
                                        onFetchSharedFile = onFetchSharedFile,
                                        onPreviewSharedImage = { previewSharedFile = it },
                                        onOpenSharedUrl = {
                                            previewSharedUrl = it
                                            onMarkSharedUrlViewed(it.offerId)
                                        },
                                    )
                                    if (item.injects.isNotEmpty()) {
                                        SoftInjectList(injects = item.injects)
                                    }
                                }
                            }
                        }
                    }
                    }
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
                        selectionEpoch++
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
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.changedToDown() }) {
                                selectionEpoch++
                            }
                        }
                    }
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

                if (slashMatches.isNotEmpty()) {
                    SlashSuggestionsPanel(
                        commands = slashMatches,
                        onSelect = { runSlash(it) },
                        modifier = Modifier.padding(horizontal = AnyaSpace.Screen),
                    )
                }

                MobileComposer(
                    draft = state.draft,
                    busy = state.busy,
                    backdrop = backdrop,
                    chatMode = state.compose.chatMode,
                    modelLabel = if (state.compose.chatMode == ChatMode.Image) {
                        state.imageGenOptions.selectedLabel
                    } else {
                        state.compose.modelDisplayName
                    },
                    modelProvider = if (state.compose.chatMode == ChatMode.Image) {
                        state.imageGenOptions.provider
                    } else {
                        state.compose.chatModelProvider
                    },
                    modelId = if (state.compose.chatMode == ChatMode.Image) {
                        state.imageGenOptions.model
                    } else {
                        state.compose.chatModel
                    },
                    contextUsage = contextUsage,
                    skills = attachCatalog.skills,
                    mcpServers = attachCatalog.mcpServers,
                    pendingAsk = state.pendingAsk,
                    imageGenCompose = state.imageGenCompose,
                    imageGenOptions = state.imageGenOptions,
                    onDraftChange = onDraftChange,
                    onSend = {
                        val slash = resolveSlashCommand(state.draft)
                        if (slash != null) {
                            runSlash(slash)
                        } else {
                            onSend()
                        }
                    },
                    onStop = onStop,
                    onAttachClick = {
                        onAttachOpen()
                        sheet = ChatSheet.Attach
                    },
                    onModelClick = {
                        if (state.compose.chatMode == ChatMode.Image) {
                            onRefreshImageGenOptions()
                            sheet = ChatSheet.ImageModel
                        } else {
                            sheet = ChatSheet.Model
                        }
                    },
                    onContextClick = { sheet = ChatSheet.Context },
                    onAnswerAsk = onAnswerAsk,
                    onAnswerToolApproval = onAnswerToolApproval,
                    imageEditRefs = state.imageEditRefs,
                    onRemoveImageEditRef = onRemoveImageEditRef,
                    onImageRatioClick = { sheet = ChatSheet.ImageRatio },
                    onImageResolutionClick = { sheet = ChatSheet.ImageResolution },
                    onImageStyleClick = { sheet = ChatSheet.ImageStyle },
                    onImageCountClick = { sheet = ChatSheet.ImageCount },
                    onPickAlbumRefs = {
                        albumRefPicker.launch(arrayOf("image/*"))
                    },
                    stagedMessages = state.stagedMessages,
                    onGuideStaged = onGuideStaged,
                    onRemoveStaged = onRemoveStaged,
                )
            }
            Spacer(
                modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars),
            )
        }

        ChatTopBar(
            chatMode = state.compose.chatMode,
            approvalMode = state.compose.toolApprovalMode,
            backdrop = backdrop,
            canvas = canvas,
            onHeaderHeight = { headerContentPx = it },
            onBack = onBack,
            onModeClick = { sheet = ChatSheet.ChatMode },
            onApprovalClick = { sheet = ChatSheet.ApprovalMode },
            onHistoryClick = { sheet = ChatSheet.UserTurns },
            onReviewClick = { showDiffReview = true },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.changedToDown() }) {
                                selectionEpoch++
                            }
                        }
                    }
                }
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
            currentId = state.compose.chatModel,
            currentProvider = state.compose.chatModelProvider,
            reasoningEffort = state.compose.reasoningEffort,
            onSelect = { onModelSelect(it) },
            onThinkingSelect = onThinkingSelect,
            onDismiss = { sheet = ChatSheet.None },
        )
        ChatSheet.Context -> ContextUsageSheet(
            usage = contextUsage,
            rounds = sessionRoundCount(state.messages),
            steps = sessionStepCount(state.messages),
            cacheHitPercent = sessionCacheHitPercent(state.cacheUsages.values),
            onDismiss = { sheet = ChatSheet.None },
        )
        ChatSheet.Attach -> AttachSheet(
            catalog = attachCatalog,
            localUploads = localUploads,
            imageMode = state.compose.chatMode == ChatMode.Image,
            onRefresh = onAttachOpen,
            onInsert = {
                onAttachInsert(it)
                sheet = ChatSheet.None
            },
            onPickLocalFiles = { uris ->
                onPickLocalFiles(uris)
                sheet = ChatSheet.None
            },
            onPickAlbumRefs = { uris ->
                onPickAlbumRefs(uris)
                sheet = ChatSheet.None
            },
            onDismiss = { sheet = ChatSheet.None },
            onDownloadFile = { path ->
                onDownloadFile(path)
                // Close so the progress banner above the composer is visible.
                sheet = ChatSheet.None
            },
        )
        ChatSheet.ImageRatio -> ImageGenOptionSheet(
            title = "画面比例",
            options = ai.anya.companion.core.model.image.ImageGenRatio.entries.map {
                it.id to it.label
            },
            currentId = state.imageGenCompose.ratio,
            onSelect = {
                onUpdateImageGenCompose(it, null, null, null)
                sheet = ChatSheet.None
            },
            onDismiss = { sheet = ChatSheet.None },
        )
        ChatSheet.ImageResolution -> ImageGenOptionSheet(
            title = "清晰度",
            options = ai.anya.companion.core.model.image.ImageGenResolution.entries.map {
                it.id to it.label
            },
            currentId = state.imageGenCompose.resolution,
            onSelect = {
                onUpdateImageGenCompose(null, it, null, null)
                sheet = ChatSheet.None
            },
            onDismiss = { sheet = ChatSheet.None },
        )
        ChatSheet.ImageStyle -> ImageGenStyleSheet(
            compose = state.imageGenCompose,
            options = state.imageGenOptions,
            onSelect = {
                onUpdateImageGenCompose(null, null, it, null)
                sheet = ChatSheet.None
            },
            onDismiss = { sheet = ChatSheet.None },
        )
        ChatSheet.ImageCount -> ImageGenOptionSheet(
            title = "生成数量",
            options = ai.anya.companion.core.model.image.IMAGE_GEN_COUNTS.map {
                it.toString() to "${it} 张"
            },
            currentId = state.imageGenCompose.count.toString(),
            onSelect = {
                onUpdateImageGenCompose(null, null, null, it.toIntOrNull())
                sheet = ChatSheet.None
            },
            onDismiss = { sheet = ChatSheet.None },
        )
        ChatSheet.ImageModel -> ImageGenModelSheet(
            options = state.imageGenOptions,
            onSelect = { provider, model ->
                onSetImageGenModel(provider, model)
                sheet = ChatSheet.None
            },
            onDismiss = { sheet = ChatSheet.None },
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
    if (showDiffReview) {
        DiffReviewSheet(
            messages = state.messages,
            workspaceId = null,
            loadUncommitted = { onLoadWorkingTreeDiff() },
            onOpenDiff = { request ->
                showDiffReview = false
                diffRequest = request
            },
            onDismiss = { showDiffReview = false },
        )
    }
    if (showEnvironmentContext) {
        EnvironmentContextSheet(
            load = onLoadEnvironmentContext,
            onDismiss = { showEnvironmentContext = false },
        )
    }
    subagentInspect?.let { (parent, pool) ->
        SubagentSheet(
            parent = parent,
            pool = pool,
            onOpenDiff = { request -> diffRequest = request },
            onDismiss = { subagentInspect = null },
        )
    }
    rewindTarget?.let { userMessageId ->
        val checkpoint = state.checkpointsByUserMessage[userMessageId]
        AlertDialog(
            onDismissRequest = { rewindTarget = null },
            title = { Text("回退到这一轮之前？") },
            text = {
                Text(
                    if (checkpoint != null && checkpoint.fileCount > 0) {
                        "将恢复 ${checkpoint.fileCount} 个文件到该轮之前的状态，并删除之后的对话。此操作不可撤销。"
                    } else {
                        "将删除这一轮及之后的对话。此操作不可撤销。"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        rewindTarget = null
                        onRewindToUserMessage(userMessageId)
                    },
                ) { Text("回退") }
            },
            dismissButton = { TextButton(onClick = { rewindTarget = null }) { Text("取消") } },
        )
    }
    previewSharedFile?.let { file ->
        if (file.mime.startsWith("image/")) {
            SharedImageFullscreenDialog(
                file = file,
                onDismiss = { previewSharedFile = null },
                onExport = { onExportSharedFile(file.offerId) },
                onEditFromImage = { request ->
                    onEditFromImage(request.desktopPath, request.previewDataUrl, request.regionPlacement)
                },
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

private enum class ChatSheet {
    None,
    ChatMode,
    ApprovalMode,
    Model,
    Context,
    Attach,
    UserTurns,
    ImageRatio,
    ImageResolution,
    ImageStyle,
    ImageCount,
    ImageModel,
}

// region Top bar

/**
 * Gap between the status-bar inset and the chat top bar's chips. The bar used to be
 * pulled *into* the inset so it hugged the system clock; that read as cramped, so the
 * chips now sit a small distance below the status bar instead.
 */
private val TopBarStatusGap = 8.dp

/**
 * Composer glass, at the strength the library's own sample uses for a pane:
 * `vibrancy` + 12dp blur + a 24/32dp lens with chromatic aberration. The shared chip
 * tokens - 4dp blur, 8/12dp lens, no aberration - are sized for 32dp controls and read
 * as a flat tint across a panel this large.
 */
private val ComposerGlassBlur = 12.dp
private val ComposerGlassRefractionHeight = 24.dp
private val ComposerGlassRefractionAmount = 32.dp

/**
 * Veil drawn over the refracted content. The library's sample uses `Color.White` at 20%,
 * which assumes a dark, busy backdrop; the light theme would vanish into a near-white
 * canvas under that, so light veils with black instead.
 *
 * The veil is needed at all because `liquidGlassSurfaceColor()` composes
 * `colorScheme.surface`, and the composer's backdrop base *is* that surface - the shared
 * tint therefore lands within 1/255 of the canvas (`#202020` over `#1F1F1F`) and shows
 * nothing.
 */
private const val ComposerGlassDarkAlpha = 0.20f
private const val ComposerGlassLightAlpha = 0.08f

/** Composer glass tint: a white lift in dark, a dark lift in light. */
@Composable
private fun composerGlassTint(): Color = if (LocalDarkTheme.current) {
    Color.White.copy(alpha = ComposerGlassDarkAlpha)
} else {
    Color.Black.copy(alpha = ComposerGlassLightAlpha)
}

@Composable
private fun ChatTopBar(
    chatMode: ChatMode,
    approvalMode: ToolApprovalMode,
    backdrop: Backdrop,
    canvas: Color,
    onHeaderHeight: (Int) -> Unit,
    onBack: () -> Unit,
    onModeClick: () -> Unit,
    onApprovalClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onReviewClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val haptic = rememberAnyaHaptics()
    val barHeight = 40.dp
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(
        modifier = modifier
            .fillMaxWidth()
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
                // Sits below the status-bar inset with a small gap, so the chips are
                // not flush against the top of the screen.
                .padding(
                    start = AnyaSpace.Md,
                    end = AnyaSpace.Md,
                    top = statusBarTop + TopBarStatusGap,
                    bottom = AnyaSpace.Xs,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
        ) {
            AnyaGlassIconChip(
                backdrop = backdrop,
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                height = barHeight,
                onClick = {
                    haptic.buttonPress()
                    onBack()
                },
            )
            ChatModeChip(backdrop = backdrop, chatMode = chatMode, onClick = onModeClick)
            Spacer(modifier = Modifier.weight(1f))
            ApprovalModeChip(backdrop = backdrop, approvalMode = approvalMode, onClick = onApprovalClick)
            AnyaGlassIconChip(
                backdrop = backdrop,
                icon = Icons.Rounded.Difference,
                contentDescription = "审查更改",
                height = barHeight,
                onClick = {
                    haptic.buttonPress()
                    onReviewClick()
                },
            )
            AnyaGlassIconChip(
                backdrop = backdrop,
                icon = Icons.Rounded.History,
                contentDescription = "发出的消息",
                height = barHeight,
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
private fun ChatModeChip(backdrop: Backdrop, chatMode: ChatMode, onClick: () -> Unit) {
    val haptic = rememberAnyaHaptics()
    AnyaGlassIconChip(
        backdrop = backdrop,
        icon = chatMode.icon(),
        contentDescription = chatMode.chineseLabel(),
        height = 40.dp,
        onClick = {
            haptic.linearTick()
            onClick()
        },
    )
}

@Composable
private fun ApprovalModeChip(backdrop: Backdrop, approvalMode: ToolApprovalMode, onClick: () -> Unit) {
    val haptic = rememberAnyaHaptics()
    AnyaGlassIconChip(
        backdrop = backdrop,
        icon = approvalMode.icon(),
        contentDescription = approvalMode.chineseLabel(),
        height = 40.dp,
        onClick = {
            haptic.linearTick()
            onClick()
        },
    )
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

private fun ChatMode.chineseLabel(): String = when (this) {
    ChatMode.Ask -> "询问"
    ChatMode.Agent -> "Agent"
    ChatMode.Plan -> "计划"
    ChatMode.Image -> "生图"
}

private fun ChatMode.icon(): ImageVector = when (this) {
    ChatMode.Ask -> AnyaIcons.ChatCircleOutline
    ChatMode.Agent -> AnyaIcons.StarFour
    ChatMode.Plan -> Icons.AutoMirrored.Rounded.Assignment
    ChatMode.Image -> Icons.Rounded.Brush
}

/** What the hero mascot holds, per chat mode: ask -> phone, agent -> wrench, plan -> clipboard, image -> brush. */
private fun ChatMode.mascotTool(): MascotTool = when (this) {
    ChatMode.Ask -> MascotTool.Phone
    ChatMode.Agent -> MascotTool.Wrench
    ChatMode.Plan -> MascotTool.Clipboard
    ChatMode.Image -> MascotTool.Brush
}

/** Empty conversation: the mascot greets, carrying the current mode's prop. Tap it to make it beam. */
@Composable
private fun EmptyConversationHero(
    chatMode: ChatMode,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = AnyaSpace.Xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MascotFace(
            modifier = Modifier.size(132.dp),
            state = MascotState.Idle,
            tool = chatMode.mascotTool(),
            interactive = true,
            contentDescription = "Anya",
        )
        Spacer(modifier = Modifier.height(AnyaSpace.Lg))
        Text(
            text = "开始对话",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(AnyaSpace.Xs))
        Text(
            text = when (chatMode) {
                ChatMode.Image -> "生图模式：每条消息都会出图"
                else -> "消息会同步到桌面端工作台"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
    }
}

private fun ToolApprovalMode.chineseLabel(): String = when (this) {
    ToolApprovalMode.Ask -> "请求"
    ToolApprovalMode.Auto -> "自动"
    ToolApprovalMode.AlwaysAllow -> "完全批准"
}

private fun ToolApprovalMode.icon(): ImageVector = when (this) {
    ToolApprovalMode.Ask -> AnyaIcons.ShieldQuestion
    ToolApprovalMode.Auto -> AnyaIcons.Shield
    ToolApprovalMode.AlwaysAllow -> AnyaIcons.ShieldOff
}

// endregion

// region Bottom sheets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnyaBottomSheet(
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
            AnyaSheetHeader(
                title = "发出的消息",
                subtitle = "点击一条可跳转到对话中的位置",
                modifier = Modifier.padding(bottom = AnyaSpace.Md),
            )
            if (turns.isEmpty()) {
                Text(
                    text = "还没有发出的消息",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = AnyaSpace.Lg),
                )
            } else {
                AnyaSheetCard {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp),
                    ) {
                        itemsIndexed(turns, key = { _, message -> message.id }) { index, message ->
                            val timeLabel = formatUserTurnTime(message.createdAtEpochMs)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptic.linearTick()
                                        onJump(message.id)
                                    }
                                    .padding(horizontal = AnyaSpace.Lg, vertical = AnyaSpace.Sm),
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
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = if (timeLabel.isNotEmpty()) 2.dp else 0.dp),
                                )
                            }
                            if (index < turns.lastIndex) {
                                AnyaSheetHairline(insetStart = AnyaSpace.Lg)
                            }
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
            AnyaSheetHeader(
                title = "对话模式",
                subtitle = "选择本轮对话的工作方式",
                modifier = Modifier.padding(bottom = AnyaSpace.Md),
            )
            val modes = listOf(ChatMode.Ask, ChatMode.Agent, ChatMode.Plan, ChatMode.Image)
            AnyaSheetCard {
                modes.forEachIndexed { index, mode ->
                    SheetOptionRow(
                        label = mode.chineseLabel(),
                        selected = mode == current,
                        leading = { ModeGlyph(icon = mode.icon()) },
                        showDivider = index < modes.lastIndex,
                        onClick = { onSelect(mode) },
                    )
                }
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
            AnyaSheetHeader(
                title = "工具审批模式",
                subtitle = "控制 Agent 使用工具时的确认策略",
                modifier = Modifier.padding(bottom = AnyaSpace.Md),
            )
            val modes = listOf(ToolApprovalMode.Ask, ToolApprovalMode.Auto, ToolApprovalMode.AlwaysAllow)
            AnyaSheetCard {
                modes.forEachIndexed { index, mode ->
                    SheetOptionRow(
                        label = mode.chineseLabel(),
                        selected = mode == current,
                        leading = { ModeGlyph(icon = mode.icon()) },
                        showDivider = index < modes.lastIndex,
                        onClick = { onSelect(mode) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageGenOptionSheet(
    title: String,
    options: List<Pair<String, String>>,
    currentId: String,
    onSelect: (String) -> Unit,
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
            AnyaSheetHeader(
                title = title,
                modifier = Modifier.padding(bottom = AnyaSpace.Md),
            )
            AnyaSheetCard {
                options.forEachIndexed { index, (id, label) ->
                    SheetOptionRow(
                        label = label,
                        selected = id == currentId,
                        showDivider = index < options.lastIndex,
                        onClick = { onSelect(id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageGenStyleSheet(
    compose: ai.anya.companion.core.model.image.ImageGenCompose,
    options: ai.anya.companion.core.model.image.ImageGenOptions,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val builtins = ai.anya.companion.core.model.image.IMAGE_GEN_STYLE_PRESETS
    AnyaBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AnyaSpace.Lg)
                .navigationBarsPadding()
                .padding(bottom = AnyaSpace.Md),
        ) {
            AnyaSheetHeader(
                title = "风格",
                modifier = Modifier.padding(bottom = AnyaSpace.Md),
            )
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
            ) {
                AnyaSheetCard {
                    builtins.forEachIndexed { index, preset ->
                        SheetOptionRow(
                            label = preset.label,
                            selected = compose.styleId == preset.id,
                            showDivider = index < builtins.lastIndex,
                            onClick = { onSelect(preset.id) },
                        )
                    }
                }
                if (options.styles.isNotEmpty()) {
                    Text(
                        text = "桌面自定义",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = AnyaSpace.Xs),
                    )
                    AnyaSheetCard {
                        options.styles.forEachIndexed { index, style ->
                            SheetOptionRow(
                                label = style.name.ifBlank { style.id },
                                selected = compose.styleId == style.id,
                                showDivider = index < options.styles.lastIndex,
                                onClick = { onSelect(style.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageGenModelSheet(
    options: ai.anya.companion.core.model.image.ImageGenOptions,
    onSelect: (provider: String, model: String) -> Unit,
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
            AnyaSheetHeader(
                title = "生图模型",
                subtitle = "切换后写入桌面端设置，两端同步",
                modifier = Modifier.padding(bottom = AnyaSpace.Md),
            )
            if (options.choices.isEmpty()) {
                Text(
                    text = "暂无可用生图模型（需桌面端 ≥ 0.2.18）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                AnyaSheetCard {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        options.choices.forEachIndexed { index, choice ->
                            SheetOptionRow(
                                label = choice.label,
                                selected = choice.provider == options.provider && choice.model == options.model,
                                showDivider = index < options.choices.lastIndex,
                                onClick = { onSelect(choice.provider, choice.model) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelSheet(
    models: List<ChatModelInfo>,
    currentId: String,
    currentProvider: String,
    reasoningEffort: String,
    onSelect: (ChatModelInfo) -> Unit,
    onThinkingSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = rememberAnyaHaptics()
    val groups = remember(models) { groupModelsByProvider(models) }
    val hierarchical = groups.size > 1
    var activeProvider by remember { mutableStateOf<String?>(null) }
    val showingGroups = hierarchical && activeProvider == null
    val visibleModels = remember(groups, activeProvider, models) {
        when {
            activeProvider != null -> groups.find { it.provider == activeProvider }?.models.orEmpty()
            groups.size == 1 -> groups.first().models
            else -> models
        }
    }
    val activeGroupLabel = groups.find { it.provider == activeProvider }?.label.orEmpty()
    val currentModel = findModelEntry(models, currentId, currentProvider)
    val currentGroupLabel = groups.find { it.provider == currentProvider }?.label.orEmpty()
    val currentSelectionLabel = listOf(
        currentGroupLabel,
        currentModel?.let { getModelDisplayLabel(it) }
            ?: getModelDisplayLabel(currentId, currentProvider, null),
    ).filter { it.isNotBlank() }
        .distinct()
        .joinToString(" · ")
    val thinkingControl = resolveReasoningControl(currentModel, currentId, currentProvider)
    val thinkingOptions = thinkingOptionsFor(thinkingControl, currentModel)
    val thinkingBranding = resolveModelBranding(
        modelId = currentId,
        displayName = currentModel?.displayName ?: currentModel?.label.orEmpty(),
    )
    val thinkingAccent = if (thinkingBranding.monochrome) {
        MaterialTheme.colorScheme.onBackground
    } else {
        thinkingBranding.accent
    }
    AnyaBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AnyaSpace.Lg)
                .navigationBarsPadding()
                .padding(bottom = AnyaSpace.Md),
        ) {
            AnyaSheetHeader(
                title = if (showingGroups) "选择提供商" else "选择模型",
                subtitle = if (showingGroups) {
                    "先选择分组，再选择该分组下的模型"
                } else {
                    "切换后将用于后续消息"
                },
                modifier = Modifier.padding(bottom = AnyaSpace.Md),
            )
            if (models.isEmpty()) {
                Text(
                    text = "暂无可用模型",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = AnyaSpace.Lg),
                )
            } else {
                AnyaSheetCard {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    ) {
                        if (showingGroups) {
                            groups.forEachIndexed { index, group ->
                                val branding = resolveGroupBranding(group)
                                val hasCurrent = group.models.any { model ->
                                    isModelSelected(model, currentId, currentProvider)
                                }
                                SheetOptionRow(
                                    label = group.label,
                                    subtitle = "${group.models.size} 个模型",
                                    selected = hasCurrent,
                                    leading = if (branding.hasIcon) {
                                        { VendorBadge(branding = branding, size = 28.dp) }
                                    } else {
                                        null
                                    },
                                    trailing = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (hasCurrent) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurface,
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Rounded.ChevronRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    showDivider = index < groups.lastIndex,
                                    onClick = {
                                        haptic.tick()
                                        activeProvider = group.provider
                                    },
                                )
                            }
                        } else {
                            if (hierarchical && activeProvider != null) {
                                SheetOptionRow(
                                    label = activeGroupLabel.ifBlank { "返回分组" },
                                    selected = false,
                                    leading = {
                                        Icon(
                                            imageVector = Icons.Rounded.ChevronLeft,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    showDivider = visibleModels.isNotEmpty(),
                                    onClick = {
                                        haptic.tick()
                                        activeProvider = null
                                    },
                                )
                            }
                            visibleModels.forEachIndexed { index, model ->
                                val branding = resolveModelBranding(model)
                                val selected = isModelSelected(model, currentId, currentProvider)
                                SheetOptionRow(
                                    label = getModelDisplayLabel(model),
                                    subtitle = getModelDisplaySubtitle(model),
                                    selected = selected,
                                    leading = if (branding.hasIcon) {
                                        { VendorBadge(branding = branding, size = 28.dp) }
                                    } else {
                                        null
                                    },
                                    showDivider = index < visibleModels.lastIndex,
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
            if (currentSelectionLabel.isNotBlank() || thinkingOptions.size > 1) {
                Spacer(modifier = Modifier.height(AnyaSpace.Md))
                AnyaSheetHairline()
                Spacer(modifier = Modifier.height(AnyaSpace.Md))
                if (currentSelectionLabel.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Md),
                    ) {
                        Text(
                            text = "当前选择",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        Text(
                            text = currentSelectionLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (thinkingOptions.size > 1) {
                    if (currentSelectionLabel.isNotBlank()) {
                        Spacer(modifier = Modifier.height(AnyaSpace.Md))
                    }
                    ThinkingEffortPanel(
                        options = thinkingOptions,
                        selectedId = selectedThinkingId(
                            thinkingControl,
                            currentModel,
                            currentId,
                            reasoningEffort,
                        ),
                        accent = thinkingAccent,
                        onSelect = onThinkingSelect,
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachSheet(
    catalog: AttachCatalogUiState,
    localUploads: List<LocalUploadItem>,
    imageMode: Boolean = false,
    onRefresh: () -> Unit,
    onInsert: (String) -> Unit,
    onPickLocalFiles: (List<android.net.Uri>) -> Unit,
    onPickAlbumRefs: (List<android.net.Uri>) -> Unit = {},
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
    val albumPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) onPickAlbumRefs(uris)
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
            Box(modifier = Modifier.fillMaxWidth()) {
                AnyaSheetHeader(
                    title = "添加到消息",
                    modifier = Modifier.align(Alignment.Center),
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
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
                        showAlbumRef = imageMode,
                        onPick = { picker.launch(arrayOf("*/*")) },
                        onPickAlbum = { albumPicker.launch(arrayOf("image/*")) },
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
    showAlbumRef: Boolean = false,
    onPick: () -> Unit,
    onPickAlbum: () -> Unit = {},
) {
    val haptic = rememberAnyaHaptics()
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Md),
    ) {
        if (showAlbumRef) {
            Button(
                onClick = {
                    haptic.buttonPress()
                    onPickAlbum()
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
                    imageVector = Icons.Rounded.Image,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "相册作参考图",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
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
                containerColor = if (showAlbumRef) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                contentColor = if (showAlbumRef) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onPrimary
                },
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
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showDivider: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = AnyaSpace.Lg, vertical = AnyaSpace.Sm),
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
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (trailing != null) {
                trailing()
            } else if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (showDivider) {
            AnyaSheetHairline(insetStart = AnyaSpace.Lg)
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
    chatMode: ChatMode,
    modelLabel: String,
    modelProvider: String,
    modelId: String,
    contextUsage: ContextUsageSnapshot,
    skills: List<SkillSummary>,
    mcpServers: List<McpServerSummary>,
    /** Layer the composer refracts; the message list is recorded into it. */
    backdrop: Backdrop,
    pendingAsk: ai.anya.companion.core.model.approval.PendingApproval?,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onAttachClick: () -> Unit,
    onModelClick: () -> Unit,
    onContextClick: () -> Unit,
    onAnswerAsk: (Map<Int, List<String>>, Boolean) -> Unit,
    onAnswerToolApproval: (ai.anya.companion.core.model.approval.ApprovalDecision) -> Unit,
    imageEditRefs: List<ImageEditRef> = emptyList(),
    onRemoveImageEditRef: (ImageEditRef) -> Unit = {},
    imageGenCompose: ai.anya.companion.core.model.image.ImageGenCompose =
        ai.anya.companion.core.model.image.ImageGenCompose(),
    imageGenOptions: ai.anya.companion.core.model.image.ImageGenOptions =
        ai.anya.companion.core.model.image.ImageGenOptions(),
    onImageRatioClick: () -> Unit = {},
    onImageResolutionClick: () -> Unit = {},
    onImageStyleClick: () -> Unit = {},
    onImageCountClick: () -> Unit = {},
    onPickAlbumRefs: () -> Unit = {},
    stagedMessages: List<String> = emptyList(),
    onGuideStaged: (Int) -> Unit = {},
    onRemoveStaged: (Int) -> Unit = {},
) {
    val canSendNormal = draft.isNotBlank() && !busy
    val canStage = draft.isNotBlank() && busy && pendingAsk == null
    val canSend = canSendNormal || canStage
    val showStop = busy && draft.isBlank() && pendingAsk == null
    val dark = LocalDarkTheme.current
    val toolBg = if (dark) AnyaColors.SurfaceRaisedDark else AnyaColors.SurfaceLight
    val glassTint = composerGlassTint()
    val shape = RoundedCornerShape(20.dp)
    val ink = MaterialTheme.colorScheme.onBackground
    val chips = remember(draft) { composerChips(draft) }
    val body = remember(draft) { composerBody(draft) }
    val draftFocus = remember { FocusRequester() }
    val controlSize = 40.dp
    val edge = 8.dp
    val modelBranding = remember(modelProvider, modelId, modelLabel) {
        resolveModelBranding(modelId, modelLabel)
    }
    val imageMode = chatMode == ChatMode.Image
    val styleLabel = remember(imageGenCompose.styleId, imageGenOptions.styles) {
        ai.anya.companion.core.model.image.IMAGE_GEN_STYLE_PRESETS
            .firstOrNull { it.id == imageGenCompose.styleId }?.label
            ?: imageGenOptions.styles.firstOrNull { it.id == imageGenCompose.styleId }?.name
            ?: "风格"
    }
    val fieldPlaceholder = when {
        busy -> "暂存，本轮结束后发送…"
        imageMode -> "描述你想生成的画面…"
        else -> "随时提问…"
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
            // Pane-sized glass (see ComposerGlassBlur): the chip defaults read as a
            // flat tint at this size.
            .anyaGlassSurface(
                backdrop = backdrop,
                shape = shape,
                surfaceTint = glassTint,
                blurRadius = ComposerGlassBlur,
                refractionHeight = ComposerGlassRefractionHeight,
                refractionAmount = ComposerGlassRefractionAmount,
                chromaticAberration = true,
            )
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (stagedMessages.isNotEmpty() && pendingAsk == null) {
            StagedMessagesPanel(
                messages = stagedMessages,
                onGuide = onGuideStaged,
                onRemove = onRemoveStaged,
            )
        }
        if (imageEditRefs.isNotEmpty() && pendingAsk == null) {
            ImageEditRefsStrip(refs = imageEditRefs, onRemove = onRemoveImageEditRef)
        }
        if (imageMode && pendingAsk == null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ImageGenChip(
                    label = imageGenCompose.ratioEnum.label,
                    onClick = onImageRatioClick,
                    enabled = true,
                )
                ImageGenChip(
                    label = imageGenCompose.resolutionEnum.label,
                    onClick = onImageResolutionClick,
                    enabled = true,
                )
                ImageGenChip(
                    label = styleLabel,
                    onClick = onImageStyleClick,
                    enabled = true,
                )
                ImageGenChip(
                    label = "${imageGenCompose.count} 张",
                    onClick = onImageCountClick,
                    enabled = true,
                )
                ImageGenChip(
                    label = "相册参考",
                    onClick = onPickAlbumRefs,
                    enabled = true,
                )
            }
        }
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
        } else {
            val fieldScroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp, max = 160.dp)
                    .verticalScroll(fieldScroll)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { draftFocus.requestFocus() },
                    ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (chips.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        chips.forEach { chip ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                    .clickable(enabled = true) {
                                        onDraftChange(
                                            rebuildComposerDraft(
                                                chips.filterNot { it == chip },
                                                body,
                                            ),
                                        )
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                InlineMentionToken(
                                    segment = chip,
                                    skills = skills,
                                    mcpServers = mcpServers,
                                )
                            }
                        }
                    }
                }
                BasicTextField(
                    value = body,
                    onValueChange = { next ->
                        onDraftChange(rebuildComposerDraft(chips, next))
                    },
                    enabled = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 32.dp)
                        .focusRequester(draftFocus),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = ink,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { if (canSend) onSend() },
                    ),
                    decorationBox = { inner ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (body.isEmpty()) {
                                Text(
                                    text = fieldPlaceholder,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (dark) {
                                        AnyaColors.FieldPlaceholderDark
                                    } else {
                                        AnyaColors.FieldPlaceholderLight
                                    },
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
                backdrop = backdrop,
                enabled = pendingAsk == null,
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
                enabled = pendingAsk == null && !busy,
                backdrop = backdrop,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (!imageMode) {
                MeterRingButton(
                    ratio = contextUsage.usageRatio,
                    color = contextUsageColor(contextUsage.usageRatio),
                    onClick = onContextClick,
                    contentDescription = "上下文占用",
                    enabled = pendingAsk == null,
                    size = controlSize,
                )
            }
            val haptic = rememberAnyaHaptics()
            ComposerCircleButton(
                background = when {
                    canSend || showStop -> ink
                    else -> toolBg
                },
                enabled = pendingAsk == null && (canSend || showStop),
                emitHaptic = false,
                onClick = {
                    haptic.confirm()
                    when {
                        canSend -> onSend()
                        showStop -> onStop()
                    }
                },
                size = controlSize,
            ) {
                Icon(
                    imageVector = if (showStop) Icons.Rounded.Stop else AnyaIcons.StarFour,
                    contentDescription = when {
                        showStop -> "停止"
                        canStage -> "暂存"
                        else -> "发送"
                    },
                    tint = when {
                        canSend || showStop -> MaterialTheme.colorScheme.background
                        else -> ink
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun StagedMessagesPanel(
    messages: List<String>,
    onGuide: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    val haptic = rememberAnyaHaptics()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "暂存 · 本轮结束后自动发送",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        messages.forEachIndexed { index, message ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        haptic.buttonPress()
                        onGuide(index)
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text("引导", style = MaterialTheme.typography.labelMedium)
                }
                IconButton(
                    onClick = {
                        haptic.tick()
                        onRemove(index)
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "移除暂存",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageGenChip(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val haptic = rememberAnyaHaptics()
    val dark = LocalDarkTheme.current
    val bg = if (dark) AnyaColors.SurfaceRaisedDark else AnyaColors.SurfaceLight
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable(enabled = enabled) {
                haptic.linearTick()
                onClick()
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
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
    placeholder: String? = null,
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
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(
            onSend = { if (canSend) onSend() },
        ),
        maxLines = 5,
        decorationBox = { inner ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (value.isEmpty()) {
                    Text(
                        text = if (busy) "回答中…" else (placeholder ?: "随时提问…"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (LocalDarkTheme.current) {
                            AnyaColors.FieldPlaceholderDark
                        } else {
                            AnyaColors.FieldPlaceholderLight
                        },
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
    val dark = LocalDarkTheme.current
    val blue = if (dark) AnyaColors.AccentBlueDark else AnyaColors.AccentBlueLight
    val teal = if (dark) AnyaColors.AccentTealDark else AnyaColors.AccentTealLight
    val orange = if (dark) AnyaColors.AccentOrangeDark else AnyaColors.AccentOrangeLight
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
    /** Non-null paints the pill as liquid glass instead of a flat surface fill. */
    backdrop: Backdrop? = null,
) {
    val haptic = rememberAnyaHaptics()
    val shape = RoundedCornerShape(999.dp)
    val fill = if (backdrop != null) {
        Modifier.anyaGlassSurface(
            backdrop = backdrop,
            shape = shape,
            surfaceTint = composerGlassTint(),
            blurRadius = ComposerGlassBlur,
            refractionHeight = ComposerGlassRefractionHeight,
            refractionAmount = ComposerGlassRefractionAmount,
            chromaticAberration = true,
        )
    } else {
        Modifier.background(MaterialTheme.colorScheme.surface)
    }
    Row(
        modifier = Modifier
            .height(height)
            .defaultMinSize(minHeight = height)
            .clip(shape)
            .then(fill)
            .semantics { contentDescription = label }
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
    ) {
        if (branding.hasIcon) {
            VendorBadge(branding = branding, size = 18.dp)
        } else {
            Icon(
                imageVector = Icons.Rounded.SmartToy,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun ComposerCircleButton(
    background: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    emitHaptic: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 36.dp,
    /** Non-null paints the button as liquid glass instead of [background]. */
    backdrop: Backdrop? = null,
    content: @Composable () -> Unit,
) {
    val haptic = rememberAnyaHaptics()
    val fill = if (backdrop != null) {
        Modifier.anyaGlassSurface(
            backdrop = backdrop,
            shape = CircleShape,
            surfaceTint = composerGlassTint(),
            blurRadius = ComposerGlassBlur,
            refractionHeight = ComposerGlassRefractionHeight,
            refractionAmount = ComposerGlassRefractionAmount,
            chromaticAberration = true,
        )
    } else {
        Modifier.background(background)
    }
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
            .then(fill)
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
    /** The user message that started this assistant turn (for duration / token stats). */
    turnUserMessage: ChatMessage? = null,
    approvedPlanIds: Set<String>,
    foldController: CompletedFoldController,
    onOpenDiff: (DiffViewRequest) -> Unit = {},
    onExportSharedFile: (String) -> Unit = {},
    onFetchSharedFile: (String) -> Unit = {},
    onPreviewSharedImage: (ai.anya.companion.core.model.session.ChatSharedFile) -> Unit = {},
    onOpenSharedUrl: (ai.anya.companion.core.model.session.ChatSharedUrl) -> Unit = {},
) {
    if (message.sharedFiles.isNotEmpty() || message.sharedUrls.isNotEmpty()) {
        DisableSelection {
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
                turnUserMessage = turnUserMessage,
                onApprovePlan = onApprovePlan,
                approvedPlanIds = approvedPlanIds,
                foldController = foldController,
                onOpenDiff = onOpenDiff,
                onNeedDetail = { MessageDetailRequest.handler(it) },
            )
        }
    }
}

@Composable
private fun MessageCopyButton(text: String) {
    if (text.isBlank()) return
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val haptic = rememberAnyaHaptics()
    DisableSelection {
        IconButton(
            onClick = {
                haptic.buttonPress()
                clipboard.setText(AnnotatedString(text))
                Toast.makeText(context, context.getString(R.string.chat_copied), Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = stringResource(R.string.chat_copy),
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoftInjectList(injects: List<ChatMessage>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AnyaSpace.Xs),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        injects.forEach { inject ->
            SoftInjectCard(content = inject.content)
        }
    }
}

@Composable
private fun UserBubble(
    message: ChatMessage,
    skills: List<SkillSummary>,
    mcpServers: List<McpServerSummary>,
) {
    // Reference images (data URLs / desktop paths) render as thumbnails, not as raw markdown.
    val displayContent = remember(message.content) { stripSoftInjectMarker(message.content) }
    val extras = remember(displayContent) { parseUserExtras(displayContent) }
    val envFence = remember(extras.body) { extractEnvironmentFence(extras.body) }
    val bodyWithoutEnv = remember(extras.body, envFence) {
        if (envFence == null) extras.body
        else extras.body.replace(
            Regex("""```(?:environment|env-context)\s*\n[\s\S]*?```""", RegexOption.IGNORE_CASE),
            "",
        ).trim()
    }
    val userContent = remember(bodyWithoutEnv) { parseUserContent(bodyWithoutEnv) }
    val parts = remember(userContent.text) { parseInlineParts(userContent.text) }
    val hasTokens = parts.any { it !is ComposerSegment.Text }
    val softInject = isSoftInjectContent(message.content)
    Column(
        modifier = Modifier.fillMaxWidth(0.86f),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Xs),
    ) {
        if (softInject) {
            SoftInjectCard(content = message.content)
            return@Column
        }
        extras.imageAnalyses.forEach { analysis ->
            ImageAnalysisCard(block = analysis)
        }
        extras.selection?.takeIf { it.isNotBlank() }?.let { selection ->
            SelectionCard(selection = selection, lineCount = extras.selectionLines)
        }
        envFence?.let { EnvironmentFenceCard(body = it) }
        if (userContent.images.isNotEmpty()) {
            DisableSelection { UserMessageImages(images = userContent.images) }
        }
        if (userContent.text.isNotBlank()) Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 5.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            if (!hasTokens) {
                Text(
                    text = userContent.text,
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MessageCopyButton(text = userContent.text)
            RewindButton(userMessageId = message.id)
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
}

/** Desktop "回退到此处": only when the desktop recorded a checkpoint for this user turn. */
@Composable
private fun RewindButton(userMessageId: String, modifier: Modifier = Modifier) {
    val actions = LocalTurnActions.current
    if (userMessageId !in actions.checkpointsByUserMessage) return
    val haptic = rememberAnyaHaptics()
    DisableSelection {
        IconButton(
            onClick = {
                haptic.buttonPress()
                actions.onRewind(userMessageId)
            },
            enabled = !actions.rewinding,
            modifier = modifier.size(32.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Undo,
                contentDescription = "回退到此处",
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * Bridge for the on-demand detail fetch.
 *
 * The assistant subtree sits several composables below [ChatScreen], so the screen installs its
 * handler here instead of threading the callback through every layer between.
 */
internal object MessageDetailRequest {
    var handler: (String) -> Unit = {}
}

@Composable
private fun AssistantContent(
    message: ChatMessage,
    turnUserMessage: ChatMessage?,
    onApprovePlan: (String) -> Unit,
    approvedPlanIds: Set<String>,
    foldController: CompletedFoldController,
    onOpenDiff: (DiffViewRequest) -> Unit = {},
    /** Fetches a turn's execution process after the user expands it. */
    onNeedDetail: (String) -> Unit = {},
) {
    // No heavy bubble chrome for assistant turns — plain text on the white canvas.
    Column(
        modifier = Modifier.fillMaxWidth(0.96f),
        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
    ) {
        val live = message.status == MessageStatus.Streaming || message.status == MessageStatus.Pending
        val hasWork = message.content.isNotBlank() ||
            !message.reasoning.isNullOrBlank() ||
            message.toolActivities.isNotEmpty()
        val turnStartedAt = turnUserMessage?.createdAtEpochMs?.takeIf { it > 0 }
        if (hasWork) {
            // Same structure as the desktop: chronological while live; once done, thinking +
            // tools fold under "已完成" and images + the reply stay open.
            DisableSelection {
                AgentWorkDetails(
                    message = message,
                    foldExpanded = foldController.isExpanded(message.id),
                    onFoldExpandedChange = { foldController.setExpanded(message.id, it) },
                    onOpenDiff = onOpenDiff,
                    detailOmitted = message.detailOmitted,
                    onNeedDetail = { onNeedDetail(message.id) },
                )
            }
        }
        val statusLabel = activityLabel(message, hasDuration = turnStartedAt != null)
        if (statusLabel != null) {
            val drawing = message.toolActivities.lastOrNull { it.status == "running" }
                ?.let { isGeneratedImageActivity(it) } == true
            DisableSelection { AssistantActivityIndicator(label = statusLabel, drawing = drawing) }
        } else if (!hasWork && live && turnStartedAt == null) {
            Text(
                text = "…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DisableSelection {
            TurnFooter(
                message = message,
                turnStartedAtEpochMs = turnStartedAt,
                turnUserMessage = turnUserMessage,
                copyButton = { MessageCopyButton(text = message.content) },
            )
        }
        if (message.planTasks.isNotEmpty()) {
            DisableSelection {
                PlanCard(
                    tasks = message.planTasks,
                    approved = message.id in approvedPlanIds ||
                        message.planTasks.any { it.status != "pending" },
                    onApprove = { onApprovePlan(message.id) },
                )
            }
        }
        if (message.codeChanges.isNotEmpty()) {
            DisableSelection {
                CodeChangesCard(
                    changes = message.codeChanges,
                    onOpenChange = { change ->
                        val diff = findDiffForPath(message.toolActivities, change.path)
                        if (diff != null) {
                            onOpenDiff(DiffViewRequest(path = change.path, unifiedDiff = diff))
                        }
                    },
                )
                // Desktop CodeChangesSummary "撤销": restore the workspace to before this turn.
                val turnActions = LocalTurnActions.current
                val userId = turnUserMessage?.id
                if (!live && userId != null && userId in turnActions.checkpointsByUserMessage) {
                    TextButton(
                        onClick = { turnActions.onRewind(userId) },
                        enabled = !turnActions.rewinding,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Undo,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "撤销本轮更改", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
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

private val FILE_OP_KINDS = setOf("create", "edit", "delete", "move")

@Composable
internal fun ToolActivityList(
    activities: List<ToolActivity>,
    pool: List<ToolActivity> = activities,
    onOpenDiff: (DiffViewRequest) -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
    ) {
        activities.forEach { activity ->
            val tasks = remember(activity) { tasksFromActivity(activity) }
            val hunks = remember(activity) { collectActivityHunks(activity) }
            when {
                // A running generate_image is a plain row ("图片 · 正在生图…"); the card appears once done.
                isGeneratedImageActivity(activity) && activity.status != "running" ->
                    GeneratedImageToolCard(activity = activity)
                activity.kind == "shell" -> ShellTerminalCard(activity = activity)
                tasks.isNotEmpty() -> EmbeddedTaskList(tasks = tasks)
                hunks.isNotEmpty() || activity.kind in FILE_OP_KINDS ->
                    FileDiffCard(activity = activity, hunks = hunks, onOpenDiff = onOpenDiff)
                else -> ToolActivityRow(activity = activity, pool = pool, onOpenDiff = onOpenDiff)
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
                // Dark terminal body with ANSI colours, like the desktop ShellTerminalCard.
                val terminalFg = TerminalForeground
                val rendered = remember(body) {
                    if (hasAnsi(body)) parseAnsi(body).toAnnotatedString(terminalFg) else AnnotatedString(body)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(TerminalBackground),
                ) {
                    Text(
                        text = if (body.isBlank() && running) AnnotatedString("正在运行…") else rendered,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp,
                        ),
                        color = if (body.isBlank() && running) terminalFg.copy(alpha = 0.6f) else terminalFg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
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

/**
 * Thinking (or, with [title], folded mid-turn narration). [showSummary] = false drops the
 * disclosure chrome and shows the text directly — used inside the completed "已完成" fold.
 */
@Composable
internal fun ReasoningSection(
    reasoning: String,
    streaming: Boolean,
    showSummary: Boolean = true,
    title: String? = null,
    visible: Boolean = true,
) {
    // Bail before BOTH branches: the `showSummary = false` path renders the reasoning text directly,
    // so a check placed only in the disclosure branch would leak it straight into the completed fold.
    if (!visible) return
    if (!showSummary) {
        Text(
            text = reasoning,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                lineHeight = 21.sp,
                letterSpacing = 0.13.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
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
                    text = title ?: if (streaming) "思考中…" else "思考过程",
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
