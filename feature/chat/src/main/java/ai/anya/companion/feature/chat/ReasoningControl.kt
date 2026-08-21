package ai.anya.companion.feature.chat

import ai.anya.companion.core.model.session.ChatModelInfo

internal enum class ReasoningEffort(val wire: String, val labelZh: String) {
    Disabled("disabled", "关闭"),
    None("none", "无"),
    Minimal("minimal", "最小"),
    Low("low", "低"),
    Medium("medium", "中"),
    High("high", "高"),
    Xhigh("xhigh", "极高"),
    Max("max", "最高"),
    ;

    companion object {
        fun fromWire(value: String?): ReasoningEffort? {
            val key = value?.trim()?.lowercase().orEmpty()
            if (key.isEmpty()) return null
            return entries.find { it.wire == key }
        }
    }
}

internal data class ReasoningProfile(
    val levels: List<ReasoningEffort>,
    val defaultLevel: ReasoningEffort,
)

internal sealed class ReasoningControl {
    data object None : ReasoningControl()
    data object Variants : ReasoningControl()
    data class Effort(val profile: ReasoningProfile) : ReasoningControl()
}

internal data class ThinkingOption(
    val id: String,
    val label: String,
)

private val EFFORT_RANK = listOf(
    ReasoningEffort.Disabled,
    ReasoningEffort.None,
    ReasoningEffort.Minimal,
    ReasoningEffort.Low,
    ReasoningEffort.Medium,
    ReasoningEffort.High,
    ReasoningEffort.Xhigh,
    ReasoningEffort.Max,
)

private val PROFILES = mapOf(
    "deepseek" to ReasoningProfile(
        listOf(ReasoningEffort.Disabled, ReasoningEffort.Low, ReasoningEffort.High, ReasoningEffort.Max),
        ReasoningEffort.High,
    ),
    "openai" to ReasoningProfile(
        listOf(
            ReasoningEffort.None,
            ReasoningEffort.Minimal,
            ReasoningEffort.Low,
            ReasoningEffort.Medium,
            ReasoningEffort.High,
            ReasoningEffort.Xhigh,
            ReasoningEffort.Max,
        ),
        ReasoningEffort.Medium,
    ),
    "openai-o" to ReasoningProfile(
        listOf(ReasoningEffort.Low, ReasoningEffort.Medium, ReasoningEffort.High),
        ReasoningEffort.Medium,
    ),
    "grok" to ReasoningProfile(
        listOf(ReasoningEffort.Low, ReasoningEffort.Medium, ReasoningEffort.High, ReasoningEffort.Xhigh),
        ReasoningEffort.High,
    ),
    "grok45" to ReasoningProfile(
        listOf(ReasoningEffort.Low, ReasoningEffort.Medium, ReasoningEffort.High),
        ReasoningEffort.High,
    ),
    "claude" to ReasoningProfile(
        listOf(
            ReasoningEffort.Low,
            ReasoningEffort.Medium,
            ReasoningEffort.High,
            ReasoningEffort.Xhigh,
            ReasoningEffort.Max,
        ),
        ReasoningEffort.High,
    ),
    "kimi-k3" to ReasoningProfile(
        listOf(ReasoningEffort.Low, ReasoningEffort.High, ReasoningEffort.Max),
        ReasoningEffort.Max,
    ),
    "kimi-k2" to ReasoningProfile(
        listOf(ReasoningEffort.Disabled, ReasoningEffort.High),
        ReasoningEffort.High,
    ),
    "qwen38" to ReasoningProfile(
        listOf(ReasoningEffort.Disabled, ReasoningEffort.Low, ReasoningEffort.Medium, ReasoningEffort.Xhigh),
        ReasoningEffort.Xhigh,
    ),
    "qwen" to ReasoningProfile(
        listOf(ReasoningEffort.Disabled, ReasoningEffort.High),
        ReasoningEffort.High,
    ),
    "glm52" to ReasoningProfile(
        listOf(
            ReasoningEffort.None,
            ReasoningEffort.Minimal,
            ReasoningEffort.Low,
            ReasoningEffort.Medium,
            ReasoningEffort.High,
            ReasoningEffort.Xhigh,
            ReasoningEffort.Max,
        ),
        ReasoningEffort.Max,
    ),
    "glm51" to ReasoningProfile(
        listOf(
            ReasoningEffort.None,
            ReasoningEffort.Minimal,
            ReasoningEffort.Low,
            ReasoningEffort.Medium,
            ReasoningEffort.High,
            ReasoningEffort.Xhigh,
        ),
        ReasoningEffort.Xhigh,
    ),
    "glm" to ReasoningProfile(
        listOf(ReasoningEffort.Disabled, ReasoningEffort.High),
        ReasoningEffort.High,
    ),
    "minimax" to ReasoningProfile(
        listOf(ReasoningEffort.Disabled, ReasoningEffort.High),
        ReasoningEffort.High,
    ),
    "generic" to ReasoningProfile(
        listOf(
            ReasoningEffort.Disabled,
            ReasoningEffort.Low,
            ReasoningEffort.Medium,
            ReasoningEffort.High,
            ReasoningEffort.Max,
        ),
        ReasoningEffort.High,
    ),
)

internal fun resolveReasoningControl(
    model: ChatModelInfo?,
    fallbackId: String = "",
    fallbackProvider: String = "",
): ReasoningControl {
    if (model != null && model.thinkingVariants.size > 1) return ReasoningControl.Variants
    if (model?.reasoning?.supported == false) return ReasoningControl.None
    val id = model?.id?.takeIf { it.isNotBlank() } ?: fallbackId
    val provider = model?.provider?.takeIf { it.isNotBlank() } ?: fallbackProvider
    val family = resolveReasoningFamily(id, provider)
    if (family != null) {
        return ReasoningControl.Effort(PROFILES.getValue(family))
    }
    if (model?.reasoning?.supported == true) {
        return ReasoningControl.Effort(PROFILES.getValue("generic"))
    }
    return ReasoningControl.None
}

internal fun clampReasoningEffort(requested: ReasoningEffort, profile: ReasoningProfile): ReasoningEffort {
    if (requested in profile.levels) return requested
    if (requested == ReasoningEffort.Disabled && ReasoningEffort.None in profile.levels) {
        return ReasoningEffort.None
    }
    if (requested == ReasoningEffort.None && ReasoningEffort.Disabled in profile.levels) {
        return ReasoningEffort.Disabled
    }
    val off = listOf(ReasoningEffort.Disabled, ReasoningEffort.None)
    if (requested in off && profile.levels.none { it in off }) {
        return profile.defaultLevel
    }
    val target = EFFORT_RANK.indexOf(requested)
    var best = profile.defaultLevel
    var bestDist = Int.MAX_VALUE
    for (level in profile.levels) {
        val dist = kotlin.math.abs(EFFORT_RANK.indexOf(level) - target)
        if (dist < bestDist || (dist == bestDist && EFFORT_RANK.indexOf(level) > EFFORT_RANK.indexOf(best))) {
            bestDist = dist
            best = level
        }
    }
    return best
}

internal fun thinkingOptionsFor(
    control: ReasoningControl,
    model: ChatModelInfo?,
): List<ThinkingOption> = when (control) {
    ReasoningControl.None -> emptyList()
    ReasoningControl.Variants -> (model?.thinkingVariants ?: emptyList()).map { variant ->
        ThinkingOption(id = variant.id, label = localizeThinkingTier(variant.label))
    }
    is ReasoningControl.Effort -> control.profile.levels.map { effort ->
        ThinkingOption(id = effort.wire, label = effort.labelZh)
    }
}

internal fun selectedThinkingId(
    control: ReasoningControl,
    model: ChatModelInfo?,
    currentModelId: String,
    reasoningEffort: String,
): String = when (control) {
    ReasoningControl.None -> ""
    ReasoningControl.Variants -> {
        val variants = model?.thinkingVariants.orEmpty()
        variants.find { it.id == currentModelId }?.id
            ?: variants.find { it.id == model?.id }?.id
            ?: variants.firstOrNull()?.id
            ?: currentModelId
    }
    is ReasoningControl.Effort -> {
        val requested = ReasoningEffort.fromWire(reasoningEffort) ?: control.profile.defaultLevel
        clampReasoningEffort(requested, control.profile).wire
    }
}

internal fun localizeThinkingTier(label: String): String = when (label.trim().lowercase()) {
    "low" -> "低"
    "high" -> "高"
    "agent" -> "Agent"
    "default" -> "默认"
    "off", "disabled", "none" -> "关闭"
    "max" -> "最高"
    else -> label.ifBlank { "思考" }
}

internal fun findModelEntry(
    models: List<ChatModelInfo>,
    modelId: String,
    provider: String,
): ChatModelInfo? {
    val id = modelId.trim()
    if (id.isEmpty()) return null
    val providerId = provider.trim()
    return models.find { model ->
        (providerId.isEmpty() || model.provider == providerId) &&
            (model.id == id || model.thinkingVariants.any { it.id == id })
    } ?: models.find { model ->
        model.id == id || model.thinkingVariants.any { it.id == id }
    }
}

internal fun isModelSelected(
    model: ChatModelInfo,
    currentId: String,
    currentProvider: String,
): Boolean {
    val id = currentId.trim()
    if (id.isEmpty()) return false
    if (currentProvider.isNotBlank() && model.provider != currentProvider.trim()) return false
    if (model.id == id) return true
    return model.thinkingVariants.any { it.id == id }
}

private fun resolveReasoningFamily(modelId: String, providerId: String): String? {
    val model = modelId.trim().lowercase()
    val provider = providerId.trim().lowercase()
    if (model.isEmpty() && provider.isEmpty()) return null
    if (model.startsWith("deepseek") || model.contains("deepseek") || provider == "deepseek") {
        return "deepseek"
    }
    if (isGrok(model, provider)) {
        return if (Regex("grok[-_.]?4[.-]?5").containsMatchIn(model)) "grok45" else "grok"
    }
    if (Regex("""(^|[-_./])kimi[-_.]?k3\b""").containsMatchIn(model) || model.contains("kimi-k3")) {
        return "kimi-k3"
    }
    if (Regex("""(^|[-_./])(kimi|moonshot)\b""").containsMatchIn(model) ||
        provider == "kimi" ||
        provider == "moonshot"
    ) {
        return "kimi-k2"
    }
    if (Regex("qwen3[.-]?8").containsMatchIn(model)) return "qwen38"
    if (Regex("""(^|[-_./])(qwen|qwq|qvq)\b""").containsMatchIn(model) || provider == "qwen") {
        return "qwen"
    }
    if (Regex("glm[-_.]?5[.-]?[23]").containsMatchIn(model) ||
        Regex("glm[-_.]?5[.-]?[4-9]").containsMatchIn(model)
    ) {
        return "glm52"
    }
    if (Regex("glm[-_.]?5[.-]?1").containsMatchIn(model)) return "glm51"
    if (Regex("""(^|[-_./])(glm|chatglm)\b""").containsMatchIn(model) ||
        provider == "zhipu" ||
        provider == "glm"
    ) {
        return "glm"
    }
    if (Regex("""(^|[-_./])(claude|anthropic)\b""").containsMatchIn(model) || provider == "anthropic") {
        return "claude"
    }
    if (Regex("""(^|[-_./])(minimax|mimo)\b""").containsMatchIn(model) || provider == "minimax") {
        return "minimax"
    }
    if (Regex("""\bo[1-4](?:-mini|-pro)?\b""").containsMatchIn(model)) return "openai-o"
    if (Regex("""(^|[-_./])gpt-5\b""").containsMatchIn(model)) return "openai"
    return null
}

private fun isGrok(model: String, provider: String): Boolean =
    Regex("""(^|[-_./])grok\b""").containsMatchIn(model) ||
        provider == "xai" ||
        provider == "grok" ||
        provider.contains("x.ai")
