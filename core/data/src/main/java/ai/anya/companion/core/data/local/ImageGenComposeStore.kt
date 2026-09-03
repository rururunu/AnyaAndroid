package ai.anya.companion.core.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ai.anya.companion.core.model.image.ImageGenCompose
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.imageGenDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anya_image_gen_compose",
)

/** Persists the image-mode toolbar (ratio / resolution / style / count) like the desktop composer. */
@Singleton
public class ImageGenComposeStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    private val dataStore = context.imageGenDataStore

    public val flow: Flow<ImageGenCompose> = dataStore.data.map { prefs ->
        prefs[KEY]?.let { raw ->
            runCatching { json.decodeFromString(ImageGenCompose.serializer(), raw) }.getOrNull()
        } ?: ImageGenCompose()
    }

    public suspend fun save(compose: ImageGenCompose) {
        dataStore.edit { prefs -> prefs[KEY] = json.encodeToString(ImageGenCompose.serializer(), compose) }
    }

    private companion object {
        val KEY = stringPreferencesKey("compose_v1")
    }
}
