package ai.anya.companion.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.anya.companion.core.designsystem.component.AnyaSheetActionRow
import ai.anya.companion.core.designsystem.component.AnyaSheetCard
import ai.anya.companion.core.designsystem.component.AnyaSheetHairline
import ai.anya.companion.core.designsystem.component.AnyaSheetHeader
import ai.anya.companion.core.designsystem.haptic.rememberAnyaHaptics
import ai.anya.companion.core.designsystem.theme.AnyaSpace
import ai.anya.companion.core.model.workspace.EnvironmentContext

public enum class SlashAction {
    OpenHistory,
    OpenModel,
    OpenThinking,
    OpenSettings,
    OpenAttach,
    ClearInput,
    ShowContext,
    Exit,
}

public data class SlashCommand(
    public val command: String,
    public val label: String,
    public val description: String,
    public val action: SlashAction,
)

public val CompanionSlashCommands: List<SlashCommand> = listOf(
    SlashCommand("/history", "history", "查看已发送的消息", SlashAction.OpenHistory),
    SlashCommand("/model", "model", "切换模型", SlashAction.OpenModel),
    SlashCommand("/thinking", "thinking", "调整思考强度", SlashAction.OpenThinking),
    SlashCommand("/settings", "settings", "打开 Companion 设置", SlashAction.OpenSettings),
    SlashCommand("/work", "work", "附加文件 / Skills / MCP", SlashAction.OpenAttach),
    SlashCommand("/context", "context", "查看桌面环境上下文", SlashAction.ShowContext),
    SlashCommand("/clear", "clear", "清空输入框", SlashAction.ClearInput),
    SlashCommand("/exit", "exit", "返回上一页", SlashAction.Exit),
)

/** When the draft is a slash query (`/` or `/mod…`), return matching commands. */
public fun matchingSlashCommands(draft: String): List<SlashCommand> {
    val trimmed = draft.trim()
    if (!trimmed.startsWith("/")) return emptyList()
    if (trimmed.contains(' ') || trimmed.contains('\n')) return emptyList()
    val needle = trimmed.lowercase()
    return CompanionSlashCommands.filter {
        it.command.startsWith(needle) || it.label.startsWith(needle.removePrefix("/"))
    }
}

public fun resolveSlashCommand(draft: String): SlashCommand? {
    val token = draft.trim().lowercase()
    if (!token.startsWith("/") || token.contains(' ') || token.contains('\n')) return null
    return CompanionSlashCommands.firstOrNull { it.command == token }
}

@Composable
public fun SlashSuggestionsPanel(
    commands: List<SlashCommand>,
    onSelect: (SlashCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (commands.isEmpty()) return
    val haptics = rememberAnyaHaptics()
    Surface(
        shape = RoundedCornerShape(AnyaSpace.CardRadius),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = AnyaSpace.Xs)) {
            commands.forEachIndexed { index, command ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptics.tick()
                            onSelect(command)
                        }
                        .heightIn(min = 48.dp)
                        .padding(horizontal = AnyaSpace.Lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AnyaSpace.Md),
                ) {
                    Text(
                        text = command.command,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = command.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (index < commands.lastIndex) {
                    AnyaSheetHairline(insetStart = AnyaSpace.Md)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun EnvironmentContextSheet(
    load: suspend () -> Result<EnvironmentContext>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var context by remember { mutableStateOf<EnvironmentContext?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        error = null
        load().fold(
            onSuccess = { context = it },
            onFailure = { error = it.message ?: "无法获取环境上下文（需桌面 Anya ≥ 0.2.23）" },
        )
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AnyaSpace.Lg)
                .padding(bottom = AnyaSpace.Xl),
            verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnyaSheetHeader(title = "环境上下文")
            Spacer(modifier = Modifier.height(AnyaSpace.Xs))
            when {
                loading -> Text(
                    "正在从桌面读取…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error != null -> Text(
                    error.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                else -> {
                    val ctx = context
                    if (ctx == null) {
                        Text(
                            "暂无上下文",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        AnyaSheetCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(AnyaSpace.Lg),
                                verticalArrangement = Arrangement.spacedBy(AnyaSpace.Md),
                            ) {
                                ContextLine("窗口", ctx.activeWindow)
                                ContextLine("文件", ctx.activeFile)
                                ContextLine("工作区", ctx.workspace?.let { "${it.name} · ${it.root}" })
                                ContextLine("IDE", ctx.ideContext?.ide)
                                ContextLine("语言", ctx.ideContext?.language)
                                ContextLine("选区", ctx.selection ?: ctx.ideContext?.selection)
                                ContextLine("Git", ctx.gitStatus)
                                ContextLine(
                                    "Office",
                                    ctx.officeContext?.let { office ->
                                        listOfNotNull(office.app, office.documentName, office.documentPath)
                                            .joinToString(" · ")
                                            .ifBlank { null }
                                    },
                                )
                                ContextLine("剪贴板", ctx.clipboard?.take(200))
                            }
                        }
                    }
                }
            }
            AnyaSheetCard {
                AnyaSheetActionRow(
                    label = "关闭",
                    emphasized = true,
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun ContextLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
