package ai.anya.companion.core.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ai.anya.companion.core.model.session.ChatModelInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.modelsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anya_models_catalog",
)

@Serializable
public data class CachedModelsCatalog(
    public val models: List<ChatModelInfo> = emptyList(),
    public val savedAtEpochMs: Long = 0L,
)

@Singleton
public class ModelsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    private val dataStore = context.modelsDataStore

    public val cachedFlow: Flow<CachedModelsCatalog> = dataStore.data.map { prefs ->
        prefs[KEY_MODELS]?.let { raw ->
            runCatching { json.decodeFromString(CachedModelsCatalog.serializer(), raw) }.getOrNull()
        } ?: CachedModelsCatalog()
    }

    public suspend fun load(): CachedModelsCatalog = cachedFlow.first()

    public suspend fun save(models: List<ChatModelInfo>) {
        dataStore.edit { prefs ->
            prefs[KEY_MODELS] = json.encodeToString(
                CachedModelsCatalog.serializer(),
                CachedModelsCatalog(
                    models = models,
                    savedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    private companion object {
        val KEY_MODELS = stringPreferencesKey("models_v1")
    }
}
