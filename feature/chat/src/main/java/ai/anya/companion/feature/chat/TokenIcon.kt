package ai.anya.companion.feature.chat

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun TokenIcon(
    iconUrl: String?,
    fallback: ImageVector,
    fallbackLetter: String? = null,
    size: Dp = 14.dp,
) {
    val context = LocalContext.current
    var bitmap by remember(iconUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(iconUrl) {
        bitmap = iconUrl?.let { loadCachedOrRemoteIcon(context.filesDir, it) }
    }
    when {
        bitmap != null -> {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(3.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        !fallbackLetter.isNullOrBlank() -> {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = fallbackLetter.take(1).uppercase(),
                    fontSize = (size.value * 0.65f).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        else -> {
            Icon(
                imageVector = fallback,
                contentDescription = null,
                modifier = Modifier.size(size),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

private suspend fun loadCachedOrRemoteIcon(
    filesDir: File,
    url: String,
): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        when {
            url.startsWith("file://", ignoreCase = true) -> {
                val path = url.removePrefix("file://")
                BitmapFactory.decodeFile(path)?.asImageBitmap()
            }
            url.startsWith("/") -> {
                BitmapFactory.decodeFile(url)?.asImageBitmap()
            }
            else -> {
                val cacheDir = File(filesDir, "icon_cache").also { it.mkdirs() }
                val key = sha1(url)
                val cached = File(cacheDir, "$key.png")
                if (cached.exists() && cached.length() > 0L) {
                    return@runCatching BitmapFactory.decodeFile(cached.absolutePath)?.asImageBitmap()
                }
                val decoded = when {
                    url.startsWith("data:", ignoreCase = true) -> {
                        val comma = url.indexOf(',')
                        if (comma <= 0) return@runCatching null
                        val bytes = Base64.decode(url.substring(comma + 1), Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    url.startsWith("http://", ignoreCase = true) ||
                        url.startsWith("https://", ignoreCase = true) -> {
                        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 8_000
                            readTimeout = 8_000
                            instanceFollowRedirects = true
                        }
                        connection.inputStream.use { BitmapFactory.decodeStream(it) }
                    }
                    else -> null
                } ?: return@runCatching null
                runCatching {
                    cached.outputStream().use { out ->
                        decoded.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
                decoded.asImageBitmap()
            }
        }
    }.getOrNull()
}

private fun sha1(value: String): String {
    val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}
