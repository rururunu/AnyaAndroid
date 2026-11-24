package ai.anya.companion.core.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ai.anya.companion.core.common.di.ApplicationScope
import ai.anya.companion.core.model.inbox.InboxResultKind
import ai.anya.companion.core.model.inbox.InboxResultRecord
import ai.anya.companion.core.model.session.ChatSharedFile
import ai.anya.companion.core.model.session.SharedFileStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

private val Context.inboxResultDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anya_inbox_results",
)

@Singleton
public class InboxResultStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val dataStore = context.inboxResultDataStore
    private val mutex = Mutex()
    private val _records = MutableStateFlow<List<InboxResultRecord>>(emptyList())
    public val records: StateFlow<List<InboxResultRecord>> = _records.asStateFlow()

    init {
        appScope.launch {
            mutex.withLock {
                _records.value = decode(dataStore.data.first()).sortedByDescending { it.createdAtEpochMs }
            }
        }
    }

    public suspend fun deliveryCursor(deviceId: String): Long =
        dataStore.data.first()[longPreferencesKey("delivery_cursor_$deviceId")] ?: 0L

    public suspend fun saveDeliveryPage(deviceId: String, next: Long, records: List<InboxResultRecord>) {
        val key = longPreferencesKey("delivery_cursor_$deviceId")
        mutex.withLock {
            val saved = dataStore.edit { prefs ->
                if (next <= (prefs[key] ?: 0L)) return@edit
                val merged = decode(prefs).associateBy { it.deviceId to it.id }.toMutableMap()
                for (record in records) {
                    val id = record.deviceId to record.id
                    merged[id] = merged[id]?.let { merge(it, record) } ?: record
                }
                prefs[KEY] = json.encodeToString(SERIALIZER, merged.values.toList())
                prefs[key] = next
            }
            _records.value = decode(saved).sortedByDescending { it.createdAtEpochMs }
        }
    }

    public fun upsert(record: InboxResultRecord) {
        if (record.id.isBlank() || record.sessionId.isBlank()) return
        mutate { list ->
            val idx = list.indexOfFirst { it.id == record.id && it.deviceId == record.deviceId }
            if (idx < 0) {
                list + record
            } else {
                val old = list[idx]
                list.toMutableList().also { next ->
                    next[idx] = merge(old, record)
                }
            }
        }
    }

    public fun patchFile(offerId: String, file: ChatSharedFile) {
        if (offerId.isBlank()) return
        mutate { list ->
            var found = false
            val next = list.map { record ->
                if (record.id != offerId || record.kind != InboxResultKind.File) {
                    record
                } else {
                    found = true
                    record.copy(
                        name = file.name.ifBlank { record.name },
                        path = file.path.ifBlank { record.path },
                        mime = file.mime.ifBlank { record.mime },
                        size = if (file.size > 0L) file.size else record.size,
                        fileStatus = file.status,
                        localPath = file.localPath ?: record.localPath,
                    )
                }
            }
            if (found) next else list
        }
    }

    public fun markUrlViewed(offerId: String) {
        if (offerId.isBlank()) return
        mutate { list ->
            list.map { record ->
                if (record.id == offerId && record.kind == InboxResultKind.Url) {
                    record.copy(urlViewed = true)
                } else {
                    record
                }
            }
        }
    }

    public fun delete(offerId: String) {
        if (offerId.isBlank()) return
        mutate { list -> list.filterNot { it.id == offerId } }
    }

    public fun clear() {
        mutate { emptyList() }
    }

    public fun removeSessions(sessionIds: Set<String>) {
        if (sessionIds.isEmpty()) return
        mutate { list -> list.filterNot { it.sessionId in sessionIds } }
    }

    private fun merge(old: InboxResultRecord, incoming: InboxResultRecord): InboxResultRecord {
        val keepReady = old.fileStatus == SharedFileStatus.Ready || old.localPath != null
        return incoming.copy(
            createdAtEpochMs = old.createdAtEpochMs.takeIf { it > 0L }
                ?: incoming.createdAtEpochMs,
            sessionTitle = incoming.sessionTitle ?: old.sessionTitle,
            workspaceName = incoming.workspaceName ?: old.workspaceName,
            fileStatus = if (keepReady) old.fileStatus else incoming.fileStatus,
            localPath = old.localPath ?: incoming.localPath,
            urlViewed = old.urlViewed || incoming.urlViewed,
            name = incoming.name.ifBlank { old.name },
            path = incoming.path.ifBlank { old.path },
            mime = incoming.mime.ifBlank { old.mime },
            size = if (incoming.size > 0L) incoming.size else old.size,
            publicUrl = incoming.publicUrl.ifBlank { old.publicUrl },
            originUrl = incoming.originUrl.ifBlank { old.originUrl },
            workspaceId = incoming.workspaceId ?: old.workspaceId,
            syncedDelivery = old.syncedDelivery || incoming.syncedDelivery,
        )
    }

    private fun mutate(transform: (List<InboxResultRecord>) -> List<InboxResultRecord>) {
        appScope.launch {
            try {
                mutex.withLock {
                    val saved = dataStore.edit { prefs ->
                        val current = decode(prefs)
                        val next = transform(current)
                        if (next != current) prefs[KEY] = json.encodeToString(SERIALIZER, next)
                    }
                    _records.value = decode(saved).sortedByDescending { it.createdAtEpochMs }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.w(error, "Failed to persist inbox results")
            }
        }
    }

    private fun decode(prefs: Preferences): List<InboxResultRecord> =
        prefs[KEY]?.let { json.decodeFromString(SERIALIZER, it) }.orEmpty()

    private companion object {
        val KEY = stringPreferencesKey("inbox_results_v1")
        val SERIALIZER = ListSerializer(InboxResultRecord.serializer())
    }
}
