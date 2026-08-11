package ai.anya.companion.core.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ai.anya.companion.core.model.protocol.DeviceCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.credentialDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anya_companion_credentials",
)

@Singleton
public class CredentialStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) {
    private val dataStore = context.credentialDataStore

    public val credentialFlow: Flow<DeviceCredential?> = dataStore.data.map { prefs ->
        prefs[KEY_CREDENTIAL]?.let { raw ->
            runCatching { json.decodeFromString(DeviceCredential.serializer(), raw) }.getOrNull()
        }
    }

    public suspend fun save(credential: DeviceCredential) {
        dataStore.edit { prefs ->
            prefs[KEY_CREDENTIAL] = json.encodeToString(DeviceCredential.serializer(), credential)
        }
    }

    public suspend fun clear() {
        dataStore.edit { it.remove(KEY_CREDENTIAL) }
    }

    private companion object {
        val KEY_CREDENTIAL = stringPreferencesKey("device_credential")
    }
}
