package ai.anya.companion.core.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
import kotlinx.coroutines.flow.map
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
            val loaded = dataStore.data.map { prefs ->
                prefs[KEY]?.let { raw ->
                    runCatching {
                        json.decodeFromString(
                            ListSerializer(InboxResultRecord.serializer()),
                            raw,
                        )
                    }.onFailure { error ->
                        Timber.w(error, "Failed to decode inbox results")
                    }.getOrNull()
                }.orEmpty()
            }.first()
            mutex.withLock {
                val existing = _records.value
                if (existing.isEmpty()) {
                    _records.value = loaded.sortedByDescending { it.createdAtEpochMs }
                } else {
                    val ids = existing.map { it.id }.toSet()
                    val extras = loaded.filter { it.id !in ids }
                    if (extras.isNotEmpty()) {
                        _records.value = (existing + extras).sortedByDescending { it.createdAtEpochMs }
                    }
                }
            }
        }
    }

    public fun upsert(record: InboxResultRecord) {
        if (record.id.isBlank() || record.sessionId.isBlank()) return
        mutate { list ->
            val idx = list.indexOfFirst { it.id == record.id }
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
        )
    }

    private fun mutate(transform: (List<InboxResultRecord>) -> List<InboxResultRecord>) {
        appScope.launch {
            mutex.withLock {
                val next = transform(_records.value).sortedByDescending { it.createdAtEpochMs }
                if (next == _records.value) return@withLock
                _records.value = next
                runCatching {
                    dataStore.edit { prefs ->
                        prefs[KEY] = json.encodeToString(
                            ListSerializer(InboxResultRecord.serializer()),
                            next,
                        )
                    }
                }.onFailure { error ->
                    Timber.w(error, "Failed to persist inbox results")
                }
            }
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("inbox_results_v1")
    }
}
