package ai.anya.companion.feature.chat

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ai.anya.companion.core.designsystem.R as DesignR
import ai.anya.companion.core.model.session.ChatModelInfo

internal data class ModelProviderBranding(
    val brand: ModelVendor,
    @DrawableRes val iconRes: Int?,
    /** True for single-color marks that should follow text color (e.g. ChatGPT). */
    val monochrome: Boolean = false,
) {
    val hasIcon: Boolean get() = iconRes != null
}

internal enum class ModelVendor {
    OpenAi,
    Anthropic,
    Gemini,
    DeepSeek,
    Qwen,
    Glm,
    Mimo,
    Meta,
    Mistral,
    Xai,
    Cohere,
    Doubao,
    Microsoft,
    Unknown,
}

/**
 * Match order: provider → model id → ownedBy → display label keywords.
 * Only vendors with a drawable under designsystem show an icon; otherwise none.
 */
internal fun resolveModelProviderBranding(
    provider: String,
    modelId: String = "",
    ownedBy: String = "",
    label: String = "",
): ModelProviderBranding {
    val key = normalizeProviderKey(provider, modelId, ownedBy, label)
    return when {
        key.contains("openai") || key.contains("gpt") || key.contains("chatgpt") ||
            key.contains("o1") || key.contains("o3") || key.contains("o4") ->
            branding(ModelVendor.OpenAi, DesignR.drawable.ic_model_chatgpt, monochrome = true)

        key.contains("gemini") || key.contains("google") ->
            branding(ModelVendor.Gemini, DesignR.drawable.ic_model_gemini)

        key.contains("deepseek") ->
            branding(ModelVendor.DeepSeek, DesignR.drawable.ic_model_deepseek)

        key.contains("glm") || key.contains("zhipu") || key.contains("chatglm") ->
            branding(ModelVendor.Glm, DesignR.drawable.ic_model_glm)

        key.contains("anthropic") || key.contains("claude") ->
            branding(ModelVendor.Anthropic, null)

        key.contains("qwen") || key.contains("alibaba") || key.contains("dashscope") ->
            branding(ModelVendor.Qwen, null)

        key.contains("mimo") || key.contains("xiaomi") ->
            branding(ModelVendor.Mimo, null)

        key.contains("meta") || key.contains("llama") ->
            branding(ModelVendor.Meta, null)

        key.contains("mistral") || key.contains("mixtral") || key.contains("codestral") ->
            branding(ModelVendor.Mistral, null)

        key.contains("xai") || key.contains("grok") ->
            branding(ModelVendor.Xai, null)

        key.contains("cohere") || key.contains("command") ->
            branding(ModelVendor.Cohere, null)

        key.contains("doubao") || key.contains("bytedance") || key.contains("seed") ->
            branding(ModelVendor.Doubao, null)

        key.contains("microsoft") || key.contains("azure") || key.contains("phi") ->
            branding(ModelVendor.Microsoft, null)

        else -> branding(ModelVendor.Unknown, null)
    }
}

internal fun resolveModelProviderBranding(model: ChatModelInfo): ModelProviderBranding =
    resolveModelProviderBranding(
        provider = model.provider,
        modelId = model.id,
        ownedBy = model.ownedBy,
        label = model.label,
    )

/**
 * When an icon is shown, hide the vendor/product name and keep only the model variant (型号).
 * Without an icon, keep the full label.
 */
internal fun modelUiLabel(
    branding: ModelProviderBranding,
    fullLabel: String,
    modelId: String = "",
): String {
    val raw = fullLabel.trim().ifBlank {
        modelId.substringAfterLast('/').substringAfterLast(':').ifBlank { modelId }
    }
    if (!branding.hasIcon) return raw.ifBlank { "模型" }
    return stripVendorName(branding.brand, raw).ifBlank { raw }
}

@Composable
internal fun VendorBadge(
    branding: ModelProviderBranding,
    size: Dp = 28.dp,
) {
    val res = branding.iconRes ?: return
    val colorFilter = if (branding.monochrome) {
        ColorFilter.tint(MaterialTheme.colorScheme.onBackground)
    } else {
        null
    }
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = Modifier.size(size),
        contentScale = ContentScale.Fit,
        colorFilter = colorFilter,
    )
}

private fun branding(
    brand: ModelVendor,
    @DrawableRes iconRes: Int?,
    monochrome: Boolean = false,
): ModelProviderBranding = ModelProviderBranding(
    brand = brand,
    iconRes = iconRes,
    monochrome = monochrome,
)

private fun normalizeProviderKey(
    provider: String,
    modelId: String,
    ownedBy: String,
    label: String,
): String {
    return listOf(provider, modelId, ownedBy, label)
        .joinToString("|") { it.lowercase() }
        .replace(Regex("[^a-z0-9|\\u4e00-\\u9fff]"), "")
}

private fun stripVendorName(brand: ModelVendor, label: String): String {
    val patterns = when (brand) {
        ModelVendor.OpenAi -> listOf(
            Regex("""(?i)^(openai|chatgpt)\b[\s\-_/]*"""),
            Regex("""(?i)^gpt[\s\-_/]*"""),
        )
        ModelVendor.Gemini -> listOf(
            Regex("""(?i)^(google\s+)?gemini\b[\s\-_/]*"""),
            Regex("""(?i)^google\b[\s\-_/]*"""),
        )
        ModelVendor.DeepSeek -> listOf(
            Regex("""(?i)^deepseek\b[\s\-_/]*"""),
        )
        ModelVendor.Glm -> listOf(
            Regex("""(?i)^(chat)?glm\b[\s\-_/]*"""),
            Regex("""(?i)^zhipu\b[\s\-_/]*"""),
        )
        else -> emptyList()
    }
    var result = label.trim()
    for (pattern in patterns) {
        result = pattern.replace(result, "").trim()
    }
    return result.trimStart('-', '_', '/', ' ')
}
