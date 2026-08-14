package ai.anya.companion.feature.chat

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import ai.anya.companion.core.designsystem.component.AnyaInlineLoadingMark
import ai.anya.companion.core.designsystem.component.AnyaMarkdown
import ai.anya.companion.core.designsystem.haptic.rememberAnyaHaptics
import ai.anya.companion.core.designsystem.theme.AnyaSpace
import ai.anya.companion.core.model.session.ChatSharedFile
import ai.anya.companion.core.model.session.ChatSharedUrl
import ai.anya.companion.core.model.session.SharedFileStatus
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun SharedFilesBlock(
    files: List<ChatSharedFile>,
    onExport: (String) -> Unit,
    onPreviewImage: (ChatSharedFile) -> Unit,
    onFetch: (String) -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm)) {
        files.forEach { file ->
            SharedFileCard(
                file = file,
                onExport = { onExport(file.offerId) },
                onPreviewImage = { onPreviewImage(file) },
                onFetch = { onFetch(file.offerId) },
            )
        }
    }
}

@Composable
internal fun SharedUrlsBlock(
    urls: List<ChatSharedUrl>,
    onOpen: (ChatSharedUrl) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm)) {
        urls.forEach { url ->
            SharedUrlCard(url = url, onOpen = { onOpen(url) })
        }
    }
}

@Composable
private fun SharedFileCard(
    file: ChatSharedFile,
    onExport: () -> Unit,
    onPreviewImage: () -> Unit,
    onFetch: () -> Unit,
) {
    val haptic = rememberAnyaHaptics()
    val context = LocalContext.current
    val isImage = file.mime.startsWith("image/")
    val ready = file.status == SharedFileStatus.Ready && !file.localPath.isNullOrBlank()
    val exported = !file.exportedUri.isNullOrBlank()

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.widthIn(max = 260.dp),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when {
                file.status == SharedFileStatus.Offered -> {
                    FileMetaRow(
                        file = file,
                        subtitle = if (file.size > 0) formatBytes(file.size) else stringResource(R.string.shared_file_tap_to_fetch),
                        modifier = Modifier.clickable {
                            haptic.tick()
                            onFetch()
                        },
                    )
                    TextButton(
                        onClick = {
                            haptic.buttonPress()
                            onFetch()
                        },
                    ) {
                        Text(text = stringResource(R.string.shared_file_fetch))
                    }
                }
                file.status == SharedFileStatus.Pending && isImage -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp, max = 140.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnyaInlineLoadingMark(size = 22.dp)
                    }
                    Text(
                        text = "${file.name}  ${receiveSubtitle(file)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ReceiveProgressBar(file)
                }
                file.status == SharedFileStatus.Pending -> {
                    FileMetaRow(
                        file = file,
                        subtitle = receiveSubtitle(file),
                    )
                    ReceiveProgressBar(file)
                }
                file.status == SharedFileStatus.Failed -> {
                    FileMetaRow(
                        file = file,
                        subtitle = file.error ?: stringResource(R.string.shared_file_failed),
                    )
                    TextButton(
                        onClick = {
                            haptic.buttonPress()
                            onFetch()
                        },
                    ) {
                        Text(text = stringResource(R.string.shared_file_fetch))
                    }
                }
                isImage && ready -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable {
                                haptic.tick()
                                onPreviewImage()
                            },
                    ) {
                        SharedImagePreview(
                            path = file.localPath!!,
                            contentDescription = file.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 72.dp, max = 140.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Surface(
                            onClick = {
                                haptic.buttonPress()
                                if (exported) {
                                    openSharedFile(context, file)
                                } else {
                                    onExport()
                                }
                            },
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.45f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp),
                        ) {
                            Icon(
                                imageVector = if (exported) {
                                    Icons.Rounded.OpenInNew
                                } else {
                                    Icons.Rounded.Download
                                },
                                contentDescription = if (exported) {
                                    stringResource(R.string.shared_file_open)
                                } else {
                                    stringResource(R.string.shared_file_download_to_device)
                                },
                                tint = Color.White,
                                modifier = Modifier
                                    .padding(7.dp)
                                    .size(16.dp),
                            )
                        }
                    }
                    Text(
                        text = "${file.name}  ${formatBytes(file.size)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                else -> {
                    val previewable = ready && isInternallyPreviewable(file)
                    FileMetaRow(
                        file = file,
                        subtitle = when {
                            ready -> formatBytes(file.size)
                            else -> stringResource(R.string.shared_file_not_ready)
                        },
                        modifier = if (previewable) {
                            Modifier.clickable {
                                haptic.tick()
                                onPreviewImage()
                            }
                        } else {
                            Modifier
                        },
                    )
                    if (ready) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (previewable) {
                                TextButton(
                                    onClick = {
                                        haptic.tick()
                                        onPreviewImage()
                                    },
                                ) {
                                    Text(text = stringResource(R.string.shared_file_preview))
                                }
                            }
                            TextButton(
                                onClick = {
                                    haptic.buttonPress()
                                    if (exported) {
                                        openSharedFile(context, file)
                                    } else {
                                        onExport()
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = if (exported) {
                                        Icons.Rounded.OpenInNew
                                    } else {
                                        Icons.Rounded.Download
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Text(
                                    text = stringResource(
                                        if (exported) {
                                            R.string.shared_file_open
                                        } else {
                                            R.string.shared_file_download
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiveProgressBar(file: ChatSharedFile) {
    val total = file.size
    val received = file.bytesReceived.coerceAtLeast(0L)
    val determinate = total > 0L
    val fraction = if (determinate) {
        (received.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val modifier = Modifier
        .fillMaxWidth()
        .height(4.dp)
        .clip(RoundedCornerShape(999.dp))
    if (determinate) {
        LinearProgressIndicator(progress = { fraction }, modifier = modifier)
    } else {
        LinearProgressIndicator(modifier = modifier)
    }
}

@Composable
private fun receiveSubtitle(file: ChatSharedFile): String {
    val total = file.size
    val received = file.bytesReceived.coerceAtLeast(0L)
    return if (total > 0L) {
        "${formatBytes(received)} / ${formatBytes(total)}"
    } else if (received > 0L) {
        "${formatBytes(received)} · ${stringResource(R.string.shared_file_receiving)}"
    } else {
        stringResource(R.string.shared_file_receiving)
    }
}

@Composable
private fun FileMetaRow(file: ChatSharedFile, subtitle: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Description,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SharedUrlCard(
    url: ChatSharedUrl,
    onOpen: () -> Unit,
) {
    val haptic = rememberAnyaHaptics()
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clickable {
                haptic.tick()
                onOpen()
            },
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = url.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = url.publicUrl,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun SharedImageFullscreenDialog(
    file: ChatSharedFile,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
) {
    val haptic = rememberAnyaHaptics()
    val context = LocalContext.current
    val path = file.localPath ?: return
    val exported = !file.exportedUri.isNullOrBlank()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f)),
        ) {
            SharedImagePreview(
                path = path,
                contentDescription = file.name,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .align(Alignment.Center),
                contentScale = ContentScale.Fit,
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    onClick = {
                        haptic.buttonPress()
                        if (exported) {
                            openSharedFile(context, file)
                        } else {
                            onExport()
                        }
                    },
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.18f),
                ) {
                    Icon(
                        imageVector = if (exported) {
                            Icons.Rounded.OpenInNew
                        } else {
                            Icons.Rounded.Download
                        },
                        contentDescription = if (exported) {
                            stringResource(R.string.shared_file_open)
                        } else {
                            stringResource(R.string.shared_file_download)
                        },
                        tint = Color.White,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(20.dp),
                    )
                }
                Surface(
                    onClick = {
                        haptic.tick()
                        onDismiss()
                    },
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.18f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.shared_file_close),
                        tint = Color.White,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(20.dp),
                    )
                }
            }
            Text(
                text = file.name,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SharedImagePreview(
    path: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val context = LocalContext.current
    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(File(path))
            .crossfade(true)
            .build(),
        imageLoader = imageLoader,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
    )
}

private enum class PreviewKind { Html, Markdown, DocxWord, LegacyWord, None }

private fun previewKindOf(file: ChatSharedFile): PreviewKind {
    val ext = file.name.substringAfterLast('.', "").lowercase()
    return when {
        file.mime == "text/html" || ext == "html" || ext == "htm" -> PreviewKind.Html
        file.mime == "text/markdown" || ext == "md" || ext == "markdown" -> PreviewKind.Markdown
        ext == "docx" -> PreviewKind.DocxWord
        ext == "doc" -> PreviewKind.LegacyWord
        else -> PreviewKind.None
    }
}

internal fun isInternallyPreviewable(file: ChatSharedFile): Boolean =
    previewKindOf(file) != PreviewKind.None

@Composable
internal fun SharedDocumentPreviewDialog(
    file: ChatSharedFile,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
) {
    val haptic = rememberAnyaHaptics()
    val context = LocalContext.current
    val path = file.localPath ?: return
    val exported = !file.exportedUri.isNullOrBlank()
    val kind = remember(file) { previewKindOf(file) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        onClick = {
                            haptic.buttonPress()
                            if (exported) {
                                openSharedFile(context, file)
                            } else {
                                onExport()
                            }
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Icon(
                            imageVector = if (exported) Icons.Rounded.OpenInNew else Icons.Rounded.Download,
                            contentDescription = stringResource(
                                if (exported) R.string.shared_file_open else R.string.shared_file_download,
                            ),
                            modifier = Modifier.padding(8.dp).size(18.dp),
                        )
                    }
                    Surface(
                        onClick = {
                            haptic.tick()
                            onDismiss()
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.shared_file_close),
                            modifier = Modifier.padding(8.dp).size(18.dp),
                        )
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    when (kind) {
                        PreviewKind.Html -> HtmlPreview(path)
                        PreviewKind.Markdown -> MarkdownFilePreview(path)
                        PreviewKind.DocxWord -> DocxWordPreview(path, context)
                        PreviewKind.LegacyWord, PreviewKind.None -> UnsupportedPreview(context)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SharedUrlPreviewDialog(
    url: ChatSharedUrl,
    onDismiss: () -> Unit,
) {
    val haptic = rememberAnyaHaptics()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = url.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        onClick = {
                            haptic.tick()
                            onDismiss()
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.shared_file_close),
                            modifier = Modifier.padding(8.dp).size(18.dp),
                        )
                    }
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            loadUrl(url.publicUrl)
                        }
                    },
                    update = { view ->
                        if (view.url != url.publicUrl) {
                            view.loadUrl(url.publicUrl)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun HtmlPreview(path: String) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                loadUrl(Uri.fromFile(File(path)).toString())
            }
        },
    )
}

@Composable
private fun MarkdownFilePreview(path: String) {
    val text by produceState<String?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching { File(path).readText() }.getOrNull()
        }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val content = text) {
            null -> CircularProgressIndicator()
            else -> SelectionContainer {
                AnyaMarkdown(
                    content = content,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun DocxWordPreview(path: String, context: Context) {
    val text by produceState<String?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { extractDocxText(File(path)) }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val content = text) {
            null -> CircularProgressIndicator()
            else -> if (content.isBlank()) {
                UnsupportedPreview(context)
            } else {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun UnsupportedPreview(context: Context) {
    Text(
        text = stringResource(R.string.shared_file_preview_unsupported),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp),
    )
}

private val DOCX_PARAGRAPH_RE = Regex("<w:p[ />][\\s\\S]*?</w:p>")
private val DOCX_TEXT_RE = Regex("<w:t[^>]*>([\\s\\S]*?)</w:t>")
private val DOCX_BREAK_RE = Regex("<w:br\\s*/?>")

private fun extractDocxText(file: File): String? = runCatching {
    ZipFile(file).use { zip ->
        val entry = zip.getEntry("word/document.xml") ?: return null
        val xml = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).readText()
        DOCX_PARAGRAPH_RE.findAll(xml).joinToString("\n\n") { paragraph ->
            val withBreaks = DOCX_BREAK_RE.replace(paragraph.value, "\n")
            DOCX_TEXT_RE.findAll(withBreaks)
                .joinToString("") { unescapeXml(it.groupValues[1]) }
        }.trim()
    }
}.getOrNull()

private fun unescapeXml(value: String): String = value
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&apos;", "'")
    .replace("&amp;", "&")

internal fun openSharedFile(context: Context, file: ChatSharedFile) {
    val uri = resolveOpenUri(context, file) ?: run {
        Toast.makeText(context, context.getString(R.string.shared_file_open_failed), Toast.LENGTH_SHORT).show()
        return
    }
    val mime = file.mime.ifBlank { guessMimeFromName(file.name) }
    if (launchView(context, uri, mime)) return
    if (mime != "*/*" && launchView(context, uri, "*/*")) return
    Toast.makeText(context, context.getString(R.string.shared_file_open_failed), Toast.LENGTH_SHORT).show()
}

private fun resolveOpenUri(context: Context, file: ChatSharedFile): Uri? {
    file.exportedUri?.takeIf { it.isNotBlank() }?.let { return Uri.parse(it) }
    val path = file.localPath ?: return null
    val cached = File(path)
    if (!cached.isFile) return null
    return runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cached)
    }.getOrNull()
}

private fun launchView(context: Context, uri: Uri, mime: String): Boolean {
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri("", uri)
    }
    val chooser = Intent.createChooser(view, context.getString(R.string.shared_file_open_chooser)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri("", uri)
    }
    return try {
        context.startActivity(chooser)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

private fun guessMimeFromName(name: String): String {
    return when (name.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "pdf" -> "application/pdf"
        "txt", "md", "log" -> "text/plain"
        "json" -> "application/json"
        "zip" -> "application/zip"
        else -> "*/*"
    }
}

private fun formatBytes(size: Long): String {
    if (size <= 0L) return "--"
    val kb = size / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    return String.format("%.1f MB", kb / 1024.0)
}
