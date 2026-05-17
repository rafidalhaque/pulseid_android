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

    fun clearEmployeeUuid() {
        prefs.edit().remove(KEY_EMPLOYEE_UUID).apply()
    }

    fun clearAllData() {
        android.util.Log.d("SecurityManager", "Clearing all data")
        prefs.edit().clear().apply()
        _isAuthenticated.value = false
    }

    // App Lock
    fun saveAppLockPin(pin: String) {
        val hashedPin = hashPin(pin)
        prefs.edit().putString(KEY_APP_LOCK_PIN, hashedPin).apply()
    }

    fun getAppLockPin(): String? = prefs.getString(KEY_APP_LOCK_PIN, null)

    fun isAppLockEnabled(): Boolean = prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)

    fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()
    }

    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_APP_LOCK_BIOMETRIC_ENABLED, false)

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_LOCK_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun verifyPin(pin: String): Boolean {
        val savedPin = getAppLockPin()
        return savedPin != null && savedPin == hashPin(pin)
    }

    private fun hashPin(pin: String): String {
        val bytes = pin.toByteArray()
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    companion object {
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_EMPLOYEE_UUID = "employee_uuid"
        private const val KEY_APP_LOCK_PIN = "app_lock_pin"
        private const val KEY_APP_LOCK_BIOMETRIC_ENABLED = "app_lock_biometric_enabled"
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
    }
}