package ai.anya.companion.core.data.local

import android.content.Context
import ai.anya.companion.core.common.di.IoDispatcher
import ai.anya.companion.core.model.session.ChatMessage
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

private val Context.sessionHistoryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anya_session_history",
)

@Serializable
private data class SessionHistoryIndexEntry(
    val lastUsedAt: Long,
    val sizeBytes: Long,
)

/**
 * On-disk cache of `session.history` payloads, so a long session can paint immediately.
 *
 * Desktop history arrives as a single full payload, so opening a big session used to show nothing
 * until the whole round trip plus decode finished. One JSON file per session keeps the read cheap,
 * and a small preference index records recency + size for LRU eviction. Bounded by
 * [MAX_SESSIONS] / [MAX_SESSION_BYTES] / [MAX_TOTAL_BYTES]; anything over budget is not cached.
 */
@Singleton
public class SessionHistoryStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val dataStore = context.sessionHistoryDataStore
    private val historyDir: File
        get() = File(context.filesDir, DIR_NAME).also { it.mkdirs() }

    /** Cached history for [sessionId], or null when nothing usable is stored. */
    public suspend fun load(sessionId: String): List<ChatMessage>? = withContext(ioDispatcher) {
        val file = fileFor(sessionId)
        if (!file.exists()) return@withContext null
        val light = runCatching {
            json.decodeFromString<List<ChatMessage>>(file.readText())
        }.onFailure { error ->
            Timber.w(error, "chat history cache read failed for %s", sessionId)
            file.delete()
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return@withContext null
        // Turns the user already expanded are cached in full; put that detail back on top.
        val detail = readDetail(sessionId) ?: return@withContext light
        light.map { message -> detail[message.id] ?: message }
    }

    /** Every cached session, keyed by session id — used by local search fallbacks. */
    public suspend fun loadAll(): Map<String, List<ChatMessage>> =
        index().keys.mapNotNull { sessionId -> load(sessionId)?.let { sessionId to it } }.toMap()

    /**
     * Writes [messages] as the cache for [sessionId]. Empty or oversized payloads drop the cached
     * copy instead of storing it, and the index is always kept in sync.
     */
    public suspend fun save(sessionId: String, messages: List<ChatMessage>) {
        val body = withContext(ioDispatcher) {
            runCatching { json.encodeToString(messages) }.getOrNull()
        }
        val sizeBytes = body?.toByteArray()?.size?.toLong() ?: 0L
        if (body == null || messages.isEmpty() || sizeBytes > MAX_SESSION_BYTES) {
            Timber.d("chat history not cached for %s (%d bytes)", sessionId, sizeBytes)
            remove(sessionId)
            return
        }
        withContext(ioDispatcher) {
            runCatching { fileFor(sessionId).writeText(body) }
                .onFailure { error -> Timber.w(error, "chat history cache write failed for %s", sessionId) }
        }
        persistIndex(
            index() + (sessionId to SessionHistoryIndexEntry(System.currentTimeMillis(), sizeBytes)),
        )
        evictIfNeeded()
    }

    /**
     * Writes the full projection of [messages] — a turn the user expanded — beside the light
     * snapshot, so a later light refresh cannot drop detail the user already waited for.
     */
    public suspend fun saveDetail(sessionId: String, messages: List<ChatMessage>) {
        if (messages.isEmpty()) return
        withContext(ioDispatcher) {
            val merged = readDetail(sessionId).orEmpty() + messages.associateBy { it.id }
            val body = runCatching { json.encodeToString(merged.values.toList()) }.getOrNull()
            val sizeBytes = body?.toByteArray()?.size?.toLong() ?: 0L
            if (body == null || sizeBytes > MAX_DETAIL_BYTES) {
                Timber.d("chat detail not cached for %s (%d bytes)", sessionId, sizeBytes)
                return@withContext
            }
            runCatching { detailFileFor(sessionId).writeText(body) }
                .onFailure { error -> Timber.w(error, "chat detail cache write failed for %s", sessionId) }
        }
    }

    /** Detail overlay for [sessionId], keyed by message id. */
    public suspend fun loadDetail(sessionId: String): Map<String, ChatMessage>? =
        withContext(ioDispatcher) { readDetail(sessionId) }

    /** Refreshes recency without rewriting the payload, so cache hits survive LRU. */
    public suspend fun touch(sessionId: String) {
        val entries = index()
        val entry = entries[sessionId] ?: return
        persistIndex(entries + (sessionId to entry.copy(lastUsedAt = System.currentTimeMillis())))
    }

    public suspend fun remove(sessionId: String) {
        withContext(ioDispatcher) {
            fileFor(sessionId).delete()
            detailFileFor(sessionId).delete()
        }
        persistIndex(index() - sessionId)
    }

    public suspend fun clear() {
        withContext(ioDispatcher) { historyDir.listFiles()?.forEach { it.delete() } }
        persistIndex(emptyMap())
    }

    private suspend fun index(): Map<String, SessionHistoryIndexEntry> =
        dataStore.data.first()[KEY_INDEX]?.let { raw ->
            runCatching { json.decodeFromString<Map<String, SessionHistoryIndexEntry>>(raw) }.getOrNull()
        } ?: emptyMap()

    private suspend fun persistIndex(entries: Map<String, SessionHistoryIndexEntry>) {
        dataStore.edit { prefs -> prefs[KEY_INDEX] = json.encodeToString(entries) }
    }

    /** Drops least-recently-used sessions until both the count and total-size budgets hold. */
    private suspend fun evictIfNeeded() {
        val entries = index()
        val kept = LinkedHashMap<String, SessionHistoryIndexEntry>()
        var total = 0L
        entries.entries.sortedByDescending { it.value.lastUsedAt }.forEach { (sessionId, entry) ->
            if (kept.size >= MAX_SESSIONS || total + entry.sizeBytes > MAX_TOTAL_BYTES) {
                withContext(ioDispatcher) {
                    fileFor(sessionId).delete()
                    detailFileFor(sessionId).delete()
                }
            } else {
                kept[sessionId] = entry
                total += entry.sizeBytes
            }
        }
        if (kept.size != entries.size) persistIndex(kept)
    }

    private fun fileFor(sessionId: String): File = File(historyDir, "${safeName(sessionId)}.json")

    /** Detail overlay: same name plus `.detail`, holding only turns the user actually expanded. */
    private fun detailFileFor(sessionId: String): File =
        File(historyDir, "${safeName(sessionId)}.detail.json")

    private fun readDetail(sessionId: String): Map<String, ChatMessage>? {
        val file = detailFileFor(sessionId)
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString<List<ChatMessage>>(file.readText()).associateBy { it.id }
        }.onFailure { error ->
            Timber.w(error, "chat detail cache read failed for %s", sessionId)
            file.delete()
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** Session ids come from the desktop; strip anything that could escape the cache directory. */
    private fun safeName(sessionId: String): String {
        val cleaned = sessionId
            .filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
            .take(MAX_NAME_CHARS)
        return if (cleaned.isEmpty() || cleaned.all { it == '.' }) "session" else cleaned
    }

    private companion object {
        const val DIR_NAME = "chat_history"
        const val MAX_SESSIONS = 20
        const val MAX_NAME_CHARS = 96
        const val MAX_SESSION_BYTES = 4L * 1024L * 1024L
        const val MAX_TOTAL_BYTES = 32L * 1024L * 1024L
        /** Expanded-turn detail is much heavier than the light snapshot, hence its own budget. */
        const val MAX_DETAIL_BYTES = 8L * 1024L * 1024L

        val KEY_INDEX = stringPreferencesKey("history_index_v1")
    }
}
