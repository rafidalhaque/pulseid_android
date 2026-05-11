package lab.sabily.pulseid.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SecurityManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_pulseid_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _isAuthenticated = MutableStateFlow(getToken() != null)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    fun saveToken(token: String) {
        android.util.Log.d("SecurityManager", "Saving token: ${token.take(10)}...")
        prefs.edit().putString(KEY_TOKEN, token).apply()
        _isAuthenticated.value = true
    }

    fun getToken(): String? {
        val token = prefs.getString(KEY_TOKEN, null)
        android.util.Log.d("SecurityManager", "Getting token: ${token?.take(10)}...")
        return token
    }

    fun clearToken() {
        android.util.Log.d("SecurityManager", "Clearing token")
        prefs.edit().remove(KEY_TOKEN).apply()
        _isAuthenticated.value = false
    }

    fun saveEmployeeUuid(uuid: String) {
        prefs.edit().putString(KEY_EMPLOYEE_UUID, uuid).apply()
    }

    fun getEmployeeUuid(): String? = prefs.getString(KEY_EMPLOYEE_UUID, null)

    companion object {
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_EMPLOYEE_UUID = "employee_uuid"
    }
}