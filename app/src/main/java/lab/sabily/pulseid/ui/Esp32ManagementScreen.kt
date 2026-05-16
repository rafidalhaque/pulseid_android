package lab.sabily.pulseid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lab.sabily.pulseid.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Esp32ManagementScreen(authApi: AuthApi, onBack: () -> Unit) {
    // State
    var pingResult by remember { mutableStateOf<Esp32PingResult?>(null) }
    var isPingLoading by remember { mutableStateOf(false) }
    var isRebootLoading by remember { mutableStateOf(false) }
    var showRebootDialog by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Automatic polling
    LaunchedEffect(Unit) {
        while (true) {
            isPingLoading = true
            try {
                val pingRes = authApi.esp32Ping()
                if (pingRes.success) {
                    pingResult = pingRes
                }
            } catch (e: Exception) {
                // Silently ignore polling errors to avoid spamming the user
            } finally {
                isPingLoading = false
            }
            delay(10000)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("ESP32 Management") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Device Status Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Device Status",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (isPingLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (pingResult == null) {
                            Text("Fetching device status...", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            StatusField("Uptime", pingResult?.uptime_ms?.let { DateUtils.formatUptime(it) } ?: "N/A")
                            StatusField("Free Heap", "${pingResult?.free_heap ?: "N/A"} bytes")
                            StatusField("WiFi IP", pingResult?.wifi_ip ?: "N/A")
                            
                            val rssi = pingResult?.wifi_rssi
                            val rssiLabel = when {
                                rssi == null -> "N/A"
                                rssi >= -60 -> "Strong"
                                rssi >= -75 -> "Good"
                                else -> "Weak"
                            }
                            StatusField("WiFi RSSI", "$rssi dBm ($rssiLabel)")
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Last updated: Just now (auto-polls every 10s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Device Control Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Device Control",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = { showRebootDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            enabled = !isRebootLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isRebootLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onError)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Rebooting...")
                            } else {
                                Text("Reboot ESP32")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRebootDialog) {
        AlertDialog(
            onDismissRequest = { showRebootDialog = false },
            title = { Text("Reboot Device") },
            text = { Text("Are you sure you want to reboot the ESP32? It will be offline for a short moment.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRebootDialog = false
                        scope.launch {
                            isRebootLoading = true
                            try {
                                val res = authApi.esp32Command(Esp32CommandRequest("reboot"))
                                if (res.success) {
                                    scope.launch { snackbarHostState.showSnackbar("Reboot command sent") }
                                } else {
                                    scope.launch { snackbarHostState.showSnackbar("Failed to send reboot command") }
                                }
                            } catch (e: Exception) {
                                scope.launch { snackbarHostState.showSnackbar("Error: ${e.message}") }
                            } finally {
                                isRebootLoading = false
                            }
                        }
                    }
                ) {
                    Text("Reboot", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRebootDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun StatusField(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
