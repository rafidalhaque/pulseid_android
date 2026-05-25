package lab.sabily.pulseid.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import org.junit.Test
import org.junit.Assert.*

class VersionInfoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `VersionInfo deserialization should succeed without changelog`() {
        val jsonString = """
            {
                "version_code": 91,
                "version_name": "1.18.12",
                "download_url": "https://example.com/app.apk"
            }
        """.trimIndent()

        val versionInfo = json.decodeFromString<VersionInfo>(jsonString)

        assertEquals(91, versionInfo.versionCode)
        assertEquals("1.18.12", versionInfo.versionName)
        assertEquals("https://example.com/app.apk", versionInfo.downloadUrl)
    }
}
