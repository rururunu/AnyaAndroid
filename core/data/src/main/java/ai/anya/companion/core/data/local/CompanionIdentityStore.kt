package ai.anya.companion.core.data.local

import android.content.Context
import android.provider.Settings
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.companionIdentityDataStore by preferencesDataStore(
    name = "anya_companion_identity",
)

/**
 * Install-scoped phone identity sent to desktop as `hello.deviceId`.
 * Survives re-pair; prefers [Settings.Secure.ANDROID_ID], else a persisted UUID.
 */
@Singleton
public class CompanionIdentityStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.companionIdentityDataStore
    private val mutex = Mutex()
    private var cached: String? = null

    public suspend fun getOrCreate(): String = mutex.withLock {
        cached?.let { return it }
        val prefs = dataStore.data.first()
        prefs[KEY_INSTANCE_ID]?.trim()?.takeIf { it.isNotEmpty() }?.let {
            cached = it
            return it
        }
        val created = androidIdOrNull() ?: "uuid:${UUID.randomUUID()}"
        dataStore.edit { it[KEY_INSTANCE_ID] = created }
        cached = created
        created
    }

    private fun androidIdOrNull(): String? {
        val raw = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()?.trim().orEmpty()
        // Known broken emulator sentinel from older Android builds.
        if (raw.isEmpty() || raw == "9774d56d682e549c") return null
        return "aid:$raw"
    }

    private companion object {
        val KEY_INSTANCE_ID = stringPreferencesKey("instance_id")
    }
}
