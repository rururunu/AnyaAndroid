package ai.anya.companion.feature.chat

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.anya.companion.core.model.session.SharedFileStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/* User-message image refs: `![alt](data:image/…)` previews and `![alt](path:…)` desktop references. */

internal data class UserImageRef(val alt: String, val source: String) {
    val isDataUrl: Boolean get() = source.startsWith("data:image/")
    val desktopPath: String get() = source.removePrefix("path:")
    val isEditRegionPreview: Boolean get() = alt == "edit-region"
}

private val USER_IMAGE_RE =
    Regex("!\\[([^\\]]*)]\\((data:image/[^)\\s]+|path:[^)\\s]+|(?:[a-zA-Z]:[\\\\/]|/)[^)\\s]+)\\)")

internal data class UserContentParts(val images: List<UserImageRef>, val text: String)

/** Desktop `parseSelectionAttachment`: pull image refs out of a user message, keep the prose. */
internal fun parseUserContent(content: String): UserContentParts {
    if (!content.contains("![")) return UserContentParts(emptyList(), content)
    val images = USER_IMAGE_RE.findAll(content)
        .map { UserImageRef(alt = it.groupValues[1].trim(), source = it.groupValues[2].trim()) }
        .toList()
    if (images.isEmpty()) return UserContentParts(emptyList(), content)
    val text = USER_IMAGE_RE.replace(content, "")
        .lines()
        .map { it.trimEnd() }
        .filter { it.isNotBlank() }
        .joinToString("\n")
    return UserContentParts(images, text)
}

private suspend fun decodeDataUrl(dataUrl: String): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val comma = dataUrl.indexOf(',')
        if (comma < 0) return@runCatching null
        val bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

/**
 * Thumbnail for one reference: decodes inline data URLs; for desktop paths shows the cached
 * copy when the phone already fetched it (or auto-fetches once), else a placeholder chip.
 */
@Composable
internal fun UserImageThumb(
    ref: UserImageRef,
    size: androidx.compose.ui.unit.Dp = 72.dp,
    onRemove: (() -> Unit)? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val actions = LocalGeneratedImageActions.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = ref.source) {
        value = when {
            ref.isDataUrl -> decodeDataUrl(ref.source)
            ref.source.startsWith("content:") || ref.source.startsWith("file:") ->
                decodeLocalUri(context, ref.source)
            else -> null
        }
    }
    val needsRemote = !ref.isDataUrl &&
        !ref.source.startsWith("content:") &&
        !ref.source.startsWith("file:")
    val local = if (needsRemote) {
        actions.resolveLocal(ref.desktopPath)
    } else {
        null
    }
    val ready = local?.status == SharedFileStatus.Ready && !local.localPath.isNullOrBlank()
    val pending = local?.status == SharedFileStatus.Pending
    androidx.compose.runtime.LaunchedEffect(ref.desktopPath, ready, needsRemote) {
        if (needsRemote && !ready && !pending) {
            actions.fetch(ref.desktopPath)
        }
    }
    Box(modifier = Modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .then(if (ready && local != null) Modifier.clickable { actions.preview(local) } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            when {
                bitmap != null -> Image(
                    bitmap = bitmap!!,
                    contentDescription = ref.alt,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(size),
                )
                ready -> SharedImagePreview(
                    path = local!!.localPath!!,
                    contentDescription = ref.alt,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(size),
                )
                pending || (needsRemote && !ready) -> androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 1.5.dp,
                )
                else -> Icon(
                    imageVector = Icons.Rounded.Image,
                    contentDescription = ref.alt,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (onRemove != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "移除参考图",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

private suspend fun decodeLocalUri(context: android.content.Context, uri: String): ImageBitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { input ->
                BitmapFactory.decodeStream(input)?.asImageBitmap()
            }
        }.getOrNull()
    }

/** Composer strip: the pending "以此图编辑" references (desktop attachedImages chips). */
@Composable
internal fun ImageEditRefsStrip(
    refs: List<ImageEditRef>,
    onRemove: (ImageEditRef) -> Unit,
) {
    if (refs.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        refs.forEach { ref ->
            val previewSource = when {
                ref.previewDataUrl != null -> ref.previewDataUrl
                !ref.localPreviewUri.isNullOrBlank() -> ref.localPreviewUri
                else -> "path:${ref.desktopPath.removePrefix("path:")}"
            }
            val shown = UserImageRef(
                alt = if (ref.previewDataUrl != null) "edit-region" else "image",
                source = previewSource,
            )
            UserImageThumb(ref = shown, size = 64.dp, onRemove = { onRemove(ref) })
            Text(
                text = if (ref.regionPlacement != null) "参考图 · 选区（${ref.regionPlacement}）" else "参考图",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Images inside a user bubble. A region edit carries both the dimmed preview and the
 * original reference; show the preview only (the desktop does the same).
 */
@Composable
internal fun UserMessageImages(images: List<UserImageRef>) {
    val hasPreview = images.any { it.isEditRegionPreview }
    val shown = if (hasPreview) images.filter { it.isEditRegionPreview } else images
    if (shown.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        shown.take(4).forEach { UserImageThumb(ref = it, size = 96.dp) }
    }
}
