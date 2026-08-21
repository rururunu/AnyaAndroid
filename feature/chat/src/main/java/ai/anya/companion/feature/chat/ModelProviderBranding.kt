package ai.anya.companion.feature.chat

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import ai.anya.companion.core.designsystem.R as DesignR
import ai.anya.companion.core.model.session.ChatModelInfo

internal data class ModelProviderBranding(
    val brand: ModelVendor,
    @DrawableRes val iconRes: Int?,
    /** True for single-color marks that should follow text color (e.g. ChatGPT). */
    val monochrome: Boolean = false,
    val accent: Color,
    val faviconUrl: String? = null,
) {
    val hasIcon: Boolean get() = iconRes != null || !faviconUrl.isNullOrBlank()
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
    Kimi,
    MiniMax,
    Unknown,
}

/**
 * Desktop `getProviderIcon`: exact provider / preset key, then keyword fallback.
 * Used for **group** rows (DeepSeek / Gemini brand; custom groups prefer favicon).
 */
internal fun resolveProviderBranding(
    provider: String,
    presetId: String = "",
    faviconUrl: String = "",
    name: String = "",
): ModelProviderBranding {
    val exact = provider.trim().ifBlank { presetId.trim() }
    brandedProviderKey(exact)?.let { brand ->
        return branding(brand).copy(faviconUrl = faviconUrl.takeIf { it.isNotBlank() })
    }
    brandedProviderKey(presetId.trim())?.let { brand ->
        return branding(brand).copy(faviconUrl = faviconUrl.takeIf { it.isNotBlank() })
    }
    matchBrandByKeyword(provider, presetId, name)?.let { brand ->
        return branding(brand).copy(faviconUrl = faviconUrl.takeIf { it.isNotBlank() })
    }
    return branding(ModelVendor.Unknown).copy(faviconUrl = faviconUrl.takeIf { it.isNotBlank() })
}

/**
 * Desktop `getModelIcon`: match id / display name only.
 * A Claude model on an OpenAI-compatible proxy must show Claude, not the proxy.
 */
internal fun resolveModelBranding(
    modelId: String,
    displayName: String = "",
): ModelProviderBranding {
    val brand = matchBrandByKeyword(modelId, displayName) ?: ModelVendor.Unknown
    return branding(brand)
}

internal fun resolveModelBranding(model: ChatModelInfo): ModelProviderBranding =
    resolveModelBranding(model.id, model.displayName ?: model.label)

internal fun resolveGroupBranding(group: ModelProviderGroup): ModelProviderBranding {
    val sample = group.models.firstOrNull()
    val preset = sample?.providerPresetId.orEmpty().ifBlank { group.presetId }
    val favicon = sample?.providerFaviconUrl.orEmpty().ifBlank { group.faviconUrl }
    val name = group.label.ifBlank { sample?.providerName.orEmpty() }
    val builtIn = group.provider == "deepseek" || group.provider == "gemini"
    val branding = resolveProviderBranding(
        provider = group.provider,
        presetId = preset,
        faviconUrl = if (builtIn) "" else favicon,
        name = name,
    )
    return if (builtIn) {
        branding.copy(faviconUrl = null)
    } else if (favicon.isNotBlank()) {
        branding.copy(iconRes = null, faviconUrl = favicon)
    } else {
        branding
    }
}

internal fun resolveModelProviderBranding(
    provider: String,
    modelId: String = "",
    ownedBy: String = "",
    label: String = "",
): ModelProviderBranding {
    resolveModelBranding(modelId, label).takeIf { it.iconRes != null }?.let { return it }
    return resolveProviderBranding(provider, name = ownedBy.ifBlank { label })
}

internal fun resolveModelProviderBranding(model: ChatModelInfo): ModelProviderBranding =
    resolveModelBranding(model)

/** Primary label: same as desktop `getModelDisplayLabel`. */
internal fun getModelDisplayLabel(model: ChatModelInfo): String {
    val fromApi = model.displayName?.trim().orEmpty()
    if (fromApi.isNotEmpty()) return fromApi
    return formatModelDisplayName(model.id, model.provider)
}

internal fun getModelDisplayLabel(
    modelId: String,
    provider: String,
    displayName: String?,
): String {
    val fromApi = displayName?.trim().orEmpty()
    if (fromApi.isNotEmpty()) return fromApi
    return formatModelDisplayName(modelId, provider)
}

/** Optional muted subtitle: same as desktop `getModelDisplaySubtitle`. */
internal fun getModelDisplaySubtitle(model: ChatModelInfo): String? {
    if (model.provider == "deepseek") return null
    if (model.provider == "gemini") {
        val label = getModelDisplayLabel(model)
        val shortId = formatModelDisplayName(model.id, model.provider)
        if (shortId != label && shortId != model.id) return shortId
        return null
    }
    return model.ownedBy.trim().takeIf { it.isNotEmpty() }
}

internal fun formatModelDisplayName(modelId: String, provider: String): String {
    val id = modelId.trim()
    if (id.isEmpty()) return id
    if (provider == "deepseek" && Regex("^deepseek[-_]", RegexOption.IGNORE_CASE).containsMatchIn(id)) {
        return id.replace(Regex("^deepseek[-_]", RegexOption.IGNORE_CASE), "")
    }
    if (provider == "gemini") return formatGeminiDisplayName(id)
    return id
}

private fun formatGeminiDisplayName(modelId: String): String {
    val rest = modelId.trim().replace(Regex("^gemini[-_]", RegexOption.IGNORE_CASE), "")
    if (rest.isEmpty()) return ""
    val lower = rest.lowercase()
    var tier = ""
    var body = rest
    when {
        lower.endsWith("-agent") -> {
            tier = "Agent"
            body = rest.dropLast("-agent".length)
        }
        lower.endsWith("-high") -> {
            tier = "High"
            body = rest.dropLast("-high".length)
        }
        lower.endsWith("-low") -> {
            tier = "Low"
            body = rest.dropLast("-low".length)
        }
    }
    body = body.trimEnd('-')
    val parts = body.split('-').filter { it.isNotBlank() }
    val base = when {
        parts.size >= 2 -> "Gemini ${parts[0]} ${parts.drop(1).joinToString(" ") { it.replaceFirstChar(Char::uppercase) }}"
        parts.size == 1 -> "Gemini ${parts[0].replaceFirstChar(Char::uppercase)}"
        else -> "Gemini $body"
    }
    return if (tier.isNotEmpty()) "$base ($tier)" else base
}

@Composable
internal fun VendorBadge(
    branding: ModelProviderBranding,
    size: Dp = 28.dp,
) {
    val favicon = branding.faviconUrl
    if (!favicon.isNullOrBlank() && branding.iconRes == null) {
        AsyncImage(
            model = favicon,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
            contentScale = ContentScale.Crop,
        )
        return
    }
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

private fun vendorAccent(brand: ModelVendor): Color = when (brand) {
    ModelVendor.OpenAi -> Color(0xFF10A37F)
    ModelVendor.Anthropic -> Color(0xFFD97757)
    ModelVendor.Gemini -> Color(0xFF4285F4)
    ModelVendor.DeepSeek -> Color(0xFF4D6BFE)
    ModelVendor.Qwen -> Color(0xFF615CED)
    ModelVendor.Glm -> Color(0xFF0C38F2)
    ModelVendor.Mimo -> Color(0xFFFF6900)
    ModelVendor.Meta -> Color(0xFF0668E1)
    ModelVendor.Mistral -> Color(0xFFFF7000)
    ModelVendor.Xai -> Color(0xFF1D9BF0)
    ModelVendor.Cohere -> Color(0xFFD18EE2)
    ModelVendor.Doubao -> Color(0xFF1664FF)
    ModelVendor.Microsoft -> Color(0xFF00A4EF)
    ModelVendor.Kimi -> Color(0xFF111111)
    ModelVendor.MiniMax -> Color(0xFF111111)
    ModelVendor.Unknown -> Color(0xFF3B82F6)
}

private fun branding(
    brand: ModelVendor,
    monochrome: Boolean = brand == ModelVendor.OpenAi ||
        brand == ModelVendor.Xai ||
        brand == ModelVendor.Kimi ||
        brand == ModelVendor.MiniMax,
): ModelProviderBranding = ModelProviderBranding(
    brand = brand,
    iconRes = brandIconRes(brand),
    monochrome = monochrome,
    accent = vendorAccent(brand),
)

private fun brandIconRes(brand: ModelVendor): Int? = when (brand) {
    ModelVendor.OpenAi -> DesignR.drawable.ic_model_chatgpt
    ModelVendor.Anthropic -> DesignR.drawable.ic_model_claude
    ModelVendor.Gemini -> DesignR.drawable.ic_model_gemini
    ModelVendor.DeepSeek -> DesignR.drawable.ic_model_deepseek
    ModelVendor.Qwen -> DesignR.drawable.ic_model_qwen
    ModelVendor.Glm -> DesignR.drawable.ic_model_glm
    ModelVendor.Mimo -> DesignR.drawable.ic_model_mimo
    ModelVendor.Meta -> DesignR.drawable.ic_model_meta
    ModelVendor.Mistral -> DesignR.drawable.ic_model_mistral
    ModelVendor.Xai -> DesignR.drawable.ic_model_grok
    ModelVendor.Cohere -> DesignR.drawable.ic_model_cohere
    ModelVendor.Doubao -> DesignR.drawable.ic_model_volcengine
    ModelVendor.Microsoft -> DesignR.drawable.ic_model_microsoft
    ModelVendor.Kimi -> DesignR.drawable.ic_model_kimi
    ModelVendor.MiniMax -> DesignR.drawable.ic_model_minimax
    ModelVendor.Unknown -> null
}

/** Desktop `providerIcons` exact keys. */
private fun brandedProviderKey(value: String): ModelVendor? = when (value.trim().lowercase()) {
    "deepseek" -> ModelVendor.DeepSeek
    "gemini" -> ModelVendor.Gemini
    "mimo" -> ModelVendor.Mimo
    "zhipu", "glm" -> ModelVendor.Glm
    "volcengine" -> ModelVendor.Doubao
    "minimax" -> ModelVendor.MiniMax
    "kimi" -> ModelVendor.Kimi
    "openai" -> ModelVendor.OpenAi
    "claude", "anthropic" -> ModelVendor.Anthropic
    "grok", "xai" -> ModelVendor.Xai
    "qwen" -> ModelVendor.Qwen
    else -> null
}

/**
 * Desktop `providerIconKeywords` order: DeepSeek, Gemini, GLM, Kimi, MiMo,
 * Volcengine, MiniMax, Qwen, Claude, Grok, OpenAI.
 */
private fun matchBrandByKeyword(vararg texts: String): ModelVendor? {
    for (text in texts) {
        val value = text.trim()
        if (value.isEmpty()) continue
        val lower = value.lowercase()
        val brand = when {
            lower.contains("deepseek") -> ModelVendor.DeepSeek
            Regex("gemini|antigravity", RegexOption.IGNORE_CASE).containsMatchIn(value) ->
                ModelVendor.Gemini
            Regex("glm|zhipu|智谱", RegexOption.IGNORE_CASE).containsMatchIn(value) ->
                ModelVendor.Glm
            Regex("kimi|moonshot", RegexOption.IGNORE_CASE).containsMatchIn(value) ->
                ModelVendor.Kimi
            Regex("mimo|小米", RegexOption.IGNORE_CASE).containsMatchIn(value) ->
                ModelVendor.Mimo
            Regex("volcengine|doubao|豆包|火山", RegexOption.IGNORE_CASE).containsMatchIn(value) ->
                ModelVendor.Doubao
            lower.contains("minimax") -> ModelVendor.MiniMax
            Regex("qwen|qwq|qvq|tongyi|通义千问|千问", RegexOption.IGNORE_CASE).containsMatchIn(value) ->
                ModelVendor.Qwen
            Regex("claude|anthropic", RegexOption.IGNORE_CASE).containsMatchIn(value) ->
                ModelVendor.Anthropic
            Regex("grok|x-?ai\\b", RegexOption.IGNORE_CASE).containsMatchIn(value) ->
                ModelVendor.Xai
            Regex("gpt|o[1-4](?:-mini|-pro)?\\b|openai|chatgpt", RegexOption.IGNORE_CASE)
                .containsMatchIn(value) -> ModelVendor.OpenAi
            lower.contains("llama") || lower.contains("meta") -> ModelVendor.Meta
            Regex("mistral|mixtral|codestral", RegexOption.IGNORE_CASE).containsMatchIn(value) ->
                ModelVendor.Mistral
            lower.contains("cohere") -> ModelVendor.Cohere
            lower.contains("microsoft") || lower.contains("azure") -> ModelVendor.Microsoft
            else -> null
        }
        if (brand != null) return brand
    }
    return null
}

internal data class ModelProviderGroup(
    val provider: String,
    val label: String,
    val presetId: String = "",
    val faviconUrl: String = "",
    val models: List<ChatModelInfo>,
)

private val PROVIDER_SORT_ORDER = listOf("deepseek", "gemini")

internal fun providerDisplayName(
    provider: String,
    ownedBy: String = "",
    presetId: String = "",
    providerName: String = "",
): String {
    val key = provider.trim()
    if (key.equals("deepseek", ignoreCase = true)) return "DeepSeek"
    if (key.equals("gemini", ignoreCase = true)) return "Gemini"
    if (providerName.isNotBlank()) return providerName.trim()
    val preset = presetId.trim().ifBlank { key }.lowercase()
    return when (preset) {
        "mimo" -> "小米 MiMo"
        "zhipu", "glm" -> "智谱 GLM"
        "volcengine" -> "火山方舟"
        "minimax" -> "MiniMax"
        "kimi", "moonshot" -> "Kimi"
        "openai" -> "OpenAI"
        "claude", "anthropic" -> "Claude"
        "grok", "xai" -> "Grok"
        "qwen" -> "通义千问"
        else -> ownedBy.trim().ifBlank {
            if (key.isEmpty()) "其他"
            else key.split(Regex("[-_\\s]+"))
                .filter { it.isNotBlank() }
                .joinToString(" ") { part ->
                    part.replaceFirstChar { ch -> ch.uppercase() }
                }
        }
    }
}

/** Group models by provider; known vendors first, then A–Z. Matches desktop. */
internal fun groupModelsByProvider(models: List<ChatModelInfo>): List<ModelProviderGroup> {
    val map = linkedMapOf<String, MutableList<ChatModelInfo>>()
    for (model in models) {
        val key = model.provider.trim().ifBlank { "other" }
        map.getOrPut(key) { mutableListOf() }.add(model)
    }
    return map.entries
        .sortedWith { a, b ->
            val ai = PROVIDER_SORT_ORDER.indexOf(a.key)
            val bi = PROVIDER_SORT_ORDER.indexOf(b.key)
            when {
                ai != -1 || bi != -1 -> {
                    when {
                        ai == -1 -> 1
                        bi == -1 -> -1
                        else -> ai.compareTo(bi)
                    }
                }
                else -> {
                    val labelA = groupLabel(a.key, a.value)
                    val labelB = groupLabel(b.key, b.value)
                    labelA.compareTo(labelB, ignoreCase = true)
                }
            }
        }
        .map { (provider, grouped) ->
            val sample = grouped.firstOrNull()
            ModelProviderGroup(
                provider = provider,
                label = groupLabel(if (provider == "other") "" else provider, grouped),
                presetId = sample?.providerPresetId.orEmpty(),
                faviconUrl = sample?.providerFaviconUrl.orEmpty(),
                models = grouped,
            )
        }
}

private fun groupLabel(provider: String, models: List<ChatModelInfo>): String {
    val sample = models.firstOrNull()
    return providerDisplayName(
        provider = provider,
        ownedBy = sample?.ownedBy.orEmpty(),
        presetId = sample?.providerPresetId.orEmpty(),
        providerName = sample?.providerName.orEmpty(),
    )
}
