package lab.sabily.pulseid.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {
    companion object {
        private val OFFICE_TZ = stringPreferencesKey("office_tz")
        private val NTFY_TOPIC = stringPreferencesKey("ntfy_topic")
        private val NTFY_TOKEN = stringPreferencesKey("ntfy_token")
        private val JWT_TOKEN = stringPreferencesKey("jwt_token")
        const val DEFAULT_TZ = "Asia/Dhaka"
    }

    val officeTz: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[OFFICE_TZ] ?: DEFAULT_TZ
        }

    val ntfyTopic: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[NTFY_TOPIC] }

    val ntfyToken: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[NTFY_TOKEN] }

    val jwtToken: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[JWT_TOKEN] }

    suspend fun saveOfficeTz(tz: String) {
        context.dataStore.edit { preferences ->
            preferences[OFFICE_TZ] = tz
        }
    }

    suspend fun saveNtfyCredentials(topic: String?, token: String?) {
        context.dataStore.edit { preferences ->
            if (topic != null) preferences[NTFY_TOPIC] = topic
            else preferences.remove(NTFY_TOPIC)
            
            if (token != null) preferences[NTFY_TOKEN] = token
            else preferences.remove(NTFY_TOKEN)
        }
    }

    suspend fun saveJwtToken(token: String?) {
        context.dataStore.edit { preferences ->
            if (token != null) preferences[JWT_TOKEN] = token
            else preferences.remove(JWT_TOKEN)
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.remove(NTFY_TOPIC)
            preferences.remove(NTFY_TOKEN)
            preferences.remove(JWT_TOKEN)
        }
    }
}
