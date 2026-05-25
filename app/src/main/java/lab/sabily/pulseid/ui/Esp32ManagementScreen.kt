package lab.sabily.pulseid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import lab.sabily.pulseid.data.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Esp32ManagementScreen(
    authApi: AuthApi,
    onBack: () -> Unit,
    onNavigateToNvsGenerator: () -> Unit,
    viewModel: Esp32ManagementViewModel = viewModel(factory = Esp32ManagementViewModel.Factory(authApi))
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showRebootDialog by remember { mutableStateOf(false) }

    val dhakaZone = remember { TimeZone.getTimeZone("Asia/Dhaka") }
    val displayFormat = remember { 
        SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale.getDefault()).apply { timeZone = dhakaZone } 
    }

    LaunchedEffect(Unit) {
        viewModel.startPolling()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopPolling()
        }
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onSnackbarShown()
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
                },
                actions = {
                    IconButton(onClick = { viewModel.ping() }, enabled = !state.isPingLoading) {
                        if (state.isPingLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
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
                            
                            val statusText = if (state.isDeviceOnline) "Online" else "Offline"
                            val statusColor = if (state.isDeviceOnline) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                            
                            Surface(
                                color = statusColor.copy(alpha = 0.1f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = statusText,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = statusColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (!state.isDeviceOnline) {
                            Text(
                                "Device Down",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                            
                            val lastSeen = state.lastSeenPingResult?.ntp_time?.let { ntpTime ->
                                DateUtils.parseFlexibleDate(ntpTime)?.let { date ->
                                    displayFormat.format(date)
                                }
                            }
                            
                            Text(
                                text = if (lastSeen != null) "Last seen: $lastSeen" else "Never connected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (state.pingResult != null) {
                            val pingRes = state.pingResult!!
                            StatusField("Uptime", pingRes.uptime_ms?.let { DateUtils.formatUptime(it) } ?: "N/A")
                            StatusField("NTP Time", pingRes.ntp_time ?: "N/A")
                            StatusField("Free Heap", "${pingRes.free_heap ?: "N/A"} bytes")
                            StatusField("WiFi IP", pingRes.wifi_ip ?: "N/A")
                            
                            val rssi = pingRes.wifi_rssi
                            val rssiLabel = when {
                                rssi == null -> "N/A"
                                rssi >= -60 -> "Strong"
                                rssi >= -75 -> "Good"
                                else -> "Weak"
                            }
                            StatusField("WiFi RSSI", "$rssi dBm ($rssiLabel)")
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Last updated: Just now (auto-polls every 30s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text("Fetching device status...", style = MaterialTheme.typography.bodyMedium)
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
                            enabled = !state.isRebootLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.isRebootLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onError)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Rebooting...")
                            } else {
                                Text("Reboot ESP32")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = onNavigateToNvsGenerator,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Generate NVS")
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
                        viewModel.reboot()
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
