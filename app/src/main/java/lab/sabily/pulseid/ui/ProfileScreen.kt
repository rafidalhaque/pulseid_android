package lab.sabily.pulseid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lab.sabily.pulseid.data.ProfileResponse
import lab.sabily.pulseid.data.SecurityManager
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

private const val BASE_URL = "https://pulseid.teamsabily.online"

@Composable
fun ProfileScreen(securityManager: SecurityManager) {
    var profile by remember { mutableStateOf<ProfileResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL("$BASE_URL/api/profile")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer ${securityManager.getToken()}")
                connection.setRequestProperty("Accept", "application/json")

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    profile = Json.decodeFromString<ProfileResponse>(responseText)
                } else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    securityManager.clearToken()
                } else {
                    error = "Failed to fetch profile: $responseCode"
                }
            } catch (e: Exception) {
                error = e.message ?: "An unknown error occurred"
            } finally {
                isLoading = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        if (isLoading) {
            CircularProgressIndicator()
        } else if (error != null) {
            Text(text = error!!, color = MaterialTheme.colorScheme.error)
        } else if (profile != null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                Text(text = "Profile", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 24.dp))
                
                ProfileItem(label = "Name", value = profile!!.name)
                ProfileItem(label = "Email", value = profile!!.email)
                ProfileItem(label = "Designation", value = profile!!.designation)
                ProfileItem(label = "Join Date", value = profile!!.join_date ?: "N/A")
                ProfileItem(label = "ID Number", value = profile!!.id_no)
            }
        }
    }
}

@Composable
fun ProfileItem(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}