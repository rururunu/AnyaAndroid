package ai.anya.companion.core.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ai.anya.companion.core.model.workspace.McpServerSummary
import ai.anya.companion.core.model.workspace.SkillSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.attachCatalogDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anya_attach_catalog",
)

@Serializable
public data class CachedAttachCatalog(
    public val skills: List<SkillSummary> = emptyList(),
    public val mcpServers: List<McpServerSummary> = emptyList(),
)

@Singleton
public class AttachCatalogStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    private val dataStore = context.attachCatalogDataStore
    private val iconDir: File
        get() = File(context.filesDir, "icon_cache").also { it.mkdirs() }

    public val cachedFlow: Flow<CachedAttachCatalog> = dataStore.data.map { prefs ->
        prefs[KEY_CATALOG]?.let { raw ->
            runCatching { json.decodeFromString(CachedAttachCatalog.serializer(), raw) }.getOrNull()
        } ?: CachedAttachCatalog()
    }

    public suspend fun load(): CachedAttachCatalog = cachedFlow.first()

    public suspend fun save(skills: List<SkillSummary>, mcpServers: List<McpServerSummary>) {
        val remappedSkills = skills.map { skill ->
            skill.copy(iconUrl = cacheIcon(skill.iconUrl) ?: skill.iconUrl)
        }
        val remappedMcp = mcpServers.map { server ->
            server.copy(iconUrl = cacheIcon(server.iconUrl) ?: server.iconUrl)
        }
        dataStore.edit { prefs ->
            prefs[KEY_CATALOG] = json.encodeToString(
                CachedAttachCatalog.serializer(),
                CachedAttachCatalog(skills = remappedSkills, mcpServers = remappedMcp),
            )
        }
    }

    /**
     * Downloads remote / data-URI icons into app-private storage and returns a `file://` URI.
     * Already-local paths are returned as-is.
     */
    public suspend fun cacheIcon(iconUrl: String?): String? = withContext(Dispatchers.IO) {
        val url = iconUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return@withContext null
        if (url.startsWith("file://", ignoreCase = true) || url.startsWith("/")) {
            return@withContext url
        }
        val key = sha1(url)
        val target = File(iconDir, "$key.png")
        if (target.exists() && target.length() > 0L) {
            return@withContext "file://${target.absolutePath}"
        }
        val bitmap = decodeIconBitmap(url) ?: return@withContext null
        runCatching {
            FileOutputStream(target).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            "file://${target.absolutePath}"
        }.getOrNull()
    }

    private fun decodeIconBitmap(url: String): Bitmap? = runCatching {
        when {
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
        }
    }.getOrNull()

    private fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val KEY_CATALOG = stringPreferencesKey("attach_catalog_v1")
    }
}
