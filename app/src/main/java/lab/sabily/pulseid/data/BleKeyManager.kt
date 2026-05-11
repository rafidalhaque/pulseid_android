package lab.sabily.pulseid.data

import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class BleKeyManager(
    private val api: AuthApi,
    private val securityManager: SecurityManager
) {
    private var masterSecret: String? = null

    suspend fun getDailyUuid(): String? {
        val employeeUuid = securityManager.getEmployeeUuid() ?: return null
        
        // Fetch secret if not in memory
        if (masterSecret == null) {
            try {
                val response = api.getEmployeeKey()
                masterSecret = response.master_secret
            } catch (e: Exception) {
                return null
            }
        }

        val secret = masterSecret ?: return null
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val input = employeeUuid + date
        
        return generateHmacUuid(secret, input)
    }

    private fun generateHmacUuid(key: String, data: String): String {
        val algorithm = "HmacSHA256"
        val mac = Mac.getInstance(algorithm)
        val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), algorithm)
        mac.init(secretKey)

        val hash = mac.doFinal(data.toByteArray(Charsets.UTF_8))

        // Extract first 16 bytes (128 bits)
        val buffer = ByteBuffer.wrap(hash, 0, 16)
        val mostSigBits = buffer.long
        val leastSigBits = buffer.long

        return UUID(mostSigBits, leastSigBits).toString()
    }
    
    fun clearSecret() {
        masterSecret = null
    }
}