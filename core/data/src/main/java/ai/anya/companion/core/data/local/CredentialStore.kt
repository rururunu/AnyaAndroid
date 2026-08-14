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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.credentialDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anya_companion_credentials",
)

@Serializable
public data class PairedDeviceRoster(
    public val devices: List<DeviceCredential> = emptyList(),
    public val activeDeviceId: String? = null,
) {
    public fun active(): DeviceCredential? {
        val id = activeDeviceId
        return devices.firstOrNull { it.deviceId == id } ?: devices.firstOrNull()
    }
}

@Singleton
public class CredentialStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) {
    private val dataStore = context.credentialDataStore

    public val rosterFlow: Flow<PairedDeviceRoster> = dataStore.data.map { prefs ->
        decodeRoster(prefs)
    }

    public val credentialFlow: Flow<DeviceCredential?> = rosterFlow.map { it.active() }

    public suspend fun saveRoster(roster: PairedDeviceRoster) {
        val normalized = roster.copy(
            devices = roster.devices.distinctBy { it.deviceId },
            activeDeviceId = roster.active()?.deviceId,
        )
        dataStore.edit { prefs ->
            prefs[KEY_ROSTER] = json.encodeToString(
                PairedDeviceRoster.serializer(),
                normalized,
            )
            prefs.remove(KEY_CREDENTIAL)
        }
    }

    public suspend fun upsert(credential: DeviceCredential, makeActive: Boolean = true) {
        dataStore.edit { prefs ->
            val current = decodeRoster(prefs)
            val devices = current.devices.filterNot { it.deviceId == credential.deviceId } + credential
            val activeId = if (makeActive) credential.deviceId else {
                current.activeDeviceId?.takeIf { id -> devices.any { it.deviceId == id } }
                    ?: credential.deviceId
            }
            prefs[KEY_ROSTER] = json.encodeToString(
                PairedDeviceRoster.serializer(),
                PairedDeviceRoster(devices = devices, activeDeviceId = activeId),
            )
            prefs.remove(KEY_CREDENTIAL)
        }
    }

    public suspend fun setActive(deviceId: String) {
        dataStore.edit { prefs ->
            val current = decodeRoster(prefs)
            if (current.devices.none { it.deviceId == deviceId }) return@edit
            prefs[KEY_ROSTER] = json.encodeToString(
                PairedDeviceRoster.serializer(),
                current.copy(activeDeviceId = deviceId),
            )
            prefs.remove(KEY_CREDENTIAL)
        }
    }

    public suspend fun remove(deviceId: String) {
        dataStore.edit { prefs ->
            val current = decodeRoster(prefs)
            val devices = current.devices.filterNot { it.deviceId == deviceId }
            val activeId = when {
                devices.isEmpty() -> null
                current.activeDeviceId == deviceId -> devices.maxByOrNull { it.pairedAtEpochMs }?.deviceId
                else -> current.activeDeviceId?.takeIf { id -> devices.any { it.deviceId == id } }
                    ?: devices.first().deviceId
            }
            prefs[KEY_ROSTER] = json.encodeToString(
                PairedDeviceRoster.serializer(),
                PairedDeviceRoster(devices = devices, activeDeviceId = activeId),
            )
            prefs.remove(KEY_CREDENTIAL)
        }
    }

    /** Writes a single credential as the only paired device. Prefer [upsert]. */
    public suspend fun save(credential: DeviceCredential) {
        upsert(credential, makeActive = true)
    }

    public suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_CREDENTIAL)
            prefs.remove(KEY_ROSTER)
        }
    }

    private fun decodeRoster(prefs: Preferences): PairedDeviceRoster {
        prefs[KEY_ROSTER]?.let { raw ->
            val roster = runCatching {
                json.decodeFromString(PairedDeviceRoster.serializer(), raw)
            }.getOrNull()
            if (roster != null) return roster
        }
        val legacy = prefs[KEY_CREDENTIAL]?.let { raw ->
            runCatching { json.decodeFromString(DeviceCredential.serializer(), raw) }.getOrNull()
        }
        return if (legacy != null) {
            PairedDeviceRoster(devices = listOf(legacy), activeDeviceId = legacy.deviceId)
        } else {
            PairedDeviceRoster()
        }
    }

    private companion object {
        val KEY_CREDENTIAL = stringPreferencesKey("device_credential")
        val KEY_ROSTER = stringPreferencesKey("device_roster")
    }
}
