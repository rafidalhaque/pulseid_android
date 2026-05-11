package lab.sabily.pulseid.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

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

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
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