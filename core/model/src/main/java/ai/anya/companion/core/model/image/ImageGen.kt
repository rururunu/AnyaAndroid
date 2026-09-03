package ai.anya.companion.core.model.image

import ai.anya.companion.core.model.protocol.ImageGenWire
import kotlinx.serialization.Serializable

/* Port of desktop `imageGenMode.ts`: ratios, resolutions, style presets and the payload mapping. */

public enum class ImageGenRatio(public val id: String, public val label: String, public val w: Int, public val h: Int) {
    Auto("auto", "自动", 1, 1),
    UltraWide("21:9", "超宽 21:9", 21, 9),
    WideScreen("16:9", "宽屏 16:9", 16, 9),
    Photo("3:2", "照片 3:2", 3, 2),
    Landscape("4:3", "横向 4:3", 4, 3),
    Square("1:1", "方形 1:1", 1, 1),
    Portrait("3:4", "竖向 3:4", 3, 4),
    Tall("2:3", "竖长 2:3", 2, 3),
    Story("9:16", "竖屏 9:16", 9, 16);

    public companion object {
        public fun fromId(id: String?): ImageGenRatio = entries.firstOrNull { it.id == id } ?: Auto
    }
}

public enum class ImageGenResolution(public val id: String, public val label: String, public val longEdge: Int) {
    Sd("1.5k", "标准 1.5K", 1536),
    Hd("2k", "高清 2K", 2048),
    Uhd("4k", "超清 4K", 3840);

    /** Images API `quality` — distinct from the UI resolution tier. */
    public val apiQuality: String get() = if (this == Sd) "medium" else "high"

    public companion object {
        public fun fromId(id: String?): ImageGenResolution = entries.firstOrNull { it.id == id } ?: Hd
    }
}

public data class ImageGenStylePreset(public val id: String, public val label: String, public val prompt: String)

/** Built-in styles (same prompts as the desktop). Custom templates come from the desktop settings. */
public val IMAGE_GEN_STYLE_PRESETS: List<ImageGenStylePreset> = listOf(
    ImageGenStylePreset("none", "无", ""),
    ImageGenStylePreset(
        "photo", "摄影",
        "Photograph captured with a real camera, not generated CGI. 35mm stills camera, 50mm f/1.8 lens, Kodak Portra 400, optical bokeh, accurate skin pores, fabric weave, and micro-contrast. Available light, slight film grain. Must look like a photograph from a physical camera: no illustration, no painterly edges, no 3D render, no anime.",
    ),
    ImageGenStylePreset(
        "cinematic", "电影感",
        "A single frame from a theatrical feature film. Super 35 / anamorphic 2x, widescreen blocking, motivated practical lights, teal-orange colorist grade, heavy 35mm grain, horizontal anamorphic flares, shallow focus on the subject. Movie production still, not a phone snapshot, not a digital painting, not anime, not product CGI.",
    ),
    ImageGenStylePreset(
        "anime", "动画",
        "Japanese 2D TV anime cel. Crisp ink lineart, hard cel-shaded shadows in 2–3 bands, flat color fills, screen-tone textures, anime face proportions (large eyes, small nose, simplified mouth). 1990s–2000s Kyoto Animation / Gainax look. Strictly 2D: no photoreal skin, no 3D Blender look, no western cartoon, no painterly oil.",
    ),
    ImageGenStylePreset(
        "ghibli", "吉卜力",
        "Hand-painted Studio Ghibli background and character art. Gouache and watercolor skies, lush naive trees, warm pastoral daylight, Ghibli character design (round simple faces, gentle expressions). Storybook atmosphere like My Neighbor Totoro or Kiki's Delivery Service. Not photoreal, not modern digital anime, not dark cyberpunk, not 3D.",
    ),
    ImageGenStylePreset(
        "oil", "油画",
        "Classical oil painting on linen canvas. Thick impasto, visible hog-bristle brushstrokes, layered glazes, Rembrandt/Sargent museum lighting, canvas weave and varnish sheen. Must read as paint on canvas from a meter away. Not a photo, not smooth digital airbrush, not watercolor, not anime.",
    ),
    ImageGenStylePreset(
        "watercolor", "水彩",
        "Transparent watercolor on cold-press paper. Wet-on-wet blooms, granulating pigment, unpainted paper whites, soft bleeding edges, faint pencil underdrawing. Light, airy, unfinished in the corners. Not opaque gouache, not oil impasto, not a photograph, not crisp vector, not anime cel.",
    ),
    ImageGenStylePreset(
        "cyberpunk", "赛博朋克",
        "Nighttime neo-noir cyberpunk city. Saturated magenta and cyan neon signage (CJK glyphs), rain-slick asphalt with neon reflections, dense cables, holograms, smog, wet concrete, high-contrast rim light. Blade Runner / Ghost in the Shell production design. Night only: no pastoral daylight, no Ghibli warmth, no oil-painting look.",
    ),
    ImageGenStylePreset(
        "render3d", "3D 渲染",
        "Path-traced 3D CGI still (Octane or Cycles). Physically based materials, HDRI studio lighting, crisp ray-traced reflections, subsurface skin or product shaders, clean geometry, no film grain. Looks like a 3D software beauty render. Not 2D illustration, not anime lineart, not a camera photo, not painterly concept art.",
    ),
    ImageGenStylePreset(
        "pixel", "像素",
        "Strict pixel art sprite scene. 32–64 px character scale, 16-color NES/SNES palette, chunky pixels, dithering, no anti-aliasing, no smooth gradients. 1990s JRPG / arcade look. If you can see real-world photographic detail, it failed. Not high-resolution, not photoreal, not vector, not anime cel.",
    ),
    ImageGenStylePreset(
        "flat", "扁平插画",
        "Flat vector poster illustration. Hard geometric shapes, 4–6 solid brand colors, no gradients, no photoreal shading, no outlines or a single even hairline, Swiss/Bauhaus graphic design. Poster on a wall, not a 3D scene, not a painting, not a photo, not anime.",
    ),
    ImageGenStylePreset(
        "concept", "概念设计",
        "Film/game production concept art. Painterly digital environment, strong silhouette, atmospheric perspective, single key-light mood, unfinished prop detail, previs for a director. ArtStation film-concept look. Not a finished photograph, not anime, not pixel art, not a clean 3D product shot.",
    ),
)

public val IMAGE_GEN_COUNTS: List<Int> = listOf(1, 2, 3, 4)

private const val IMAGE_GEN_MIN_PX = 256
private const val IMAGE_GEN_MAX_PX = 4096
private const val IMAGE_GEN_SIZE_STEP = 16

public fun clampImagePx(value: Int): Int {
    val clamped = value.coerceIn(IMAGE_GEN_MIN_PX, IMAGE_GEN_MAX_PX)
    val snapped = Math.round(clamped / IMAGE_GEN_SIZE_STEP.toFloat()) * IMAGE_GEN_SIZE_STEP
    return snapped.coerceIn(IMAGE_GEN_MIN_PX, IMAGE_GEN_MAX_PX)
}

/** Long edge follows the resolution tier; the short edge follows the ratio. */
public fun dimensionsFor(ratio: ImageGenRatio, resolution: ImageGenResolution): Pair<Int, Int> {
    val r = if (ratio == ImageGenRatio.Auto) ImageGenRatio.Square else ratio
    val long = resolution.longEdge
    return if (r.w >= r.h) {
        clampImagePx(long) to clampImagePx(Math.round(long * r.h / r.w.toFloat()))
    } else {
        clampImagePx(Math.round(long * r.w / r.h.toFloat())) to clampImagePx(long)
    }
}

/** Phone-side toolbar state, persisted per device. */
@Serializable
public data class ImageGenCompose(
    public val ratio: String = ImageGenRatio.Auto.id,
    public val resolution: String = ImageGenResolution.Hd.id,
    public val styleId: String = "none",
    public val count: Int = 1,
) {
    public val ratioEnum: ImageGenRatio get() = ImageGenRatio.fromId(ratio)
    public val resolutionEnum: ImageGenResolution get() = ImageGenResolution.fromId(resolution)

    /** Desktop `imageGenPayload`: WxH from ratio × tier, quality from tier, style prompt by id. */
    public fun toWire(customStyles: List<ImageStyleTemplateSummary> = emptyList()): ImageGenWire {
        val (width, height) = dimensionsFor(ratioEnum, resolutionEnum)
        val builtin = IMAGE_GEN_STYLE_PRESETS.firstOrNull { it.id == styleId }
        val custom = customStyles.firstOrNull { it.id == styleId }
        return ImageGenWire(
            size = if (ratioEnum == ImageGenRatio.Auto) "auto" else "${width}x$height",
            quality = resolutionEnum.apiQuality,
            n = count.coerceIn(1, 4),
            stylePrompt = builtin?.prompt?.takeIf { it.isNotBlank() } ?: custom?.prompt?.takeIf { it.isNotBlank() },
            styleId = if (custom != null) custom.id else null,
        )
    }
}

@Serializable
public data class ImageModelChoice(
    public val provider: String,
    public val providerName: String = "",
    public val model: String,
) {
    public val label: String get() = if (providerName.isNotBlank()) "$model · $providerName" else model
}

@Serializable
public data class ImageStyleTemplateSummary(
    public val id: String,
    public val name: String,
    public val prompt: String = "",
    public val hasExampleImage: Boolean = false,
)

/** Desktop `imageGen.options` payload. */
@Serializable
public data class ImageGenOptions(
    public val model: String = "",
    public val provider: String = "",
    public val choices: List<ImageModelChoice> = emptyList(),
    public val styles: List<ImageStyleTemplateSummary> = emptyList(),
) {
    public val selectedLabel: String
        get() = choices.firstOrNull { it.provider == provider && it.model == model }?.label
            ?: model.ifBlank { "未配置" }
}
