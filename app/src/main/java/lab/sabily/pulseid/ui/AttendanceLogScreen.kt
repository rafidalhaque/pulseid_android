package lab.sabily.pulseid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import lab.sabily.pulseid.data.AttendanceLogRecord
import lab.sabily.pulseid.data.AuthApi
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceLogScreen(authApi: AuthApi) {
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf<List<AttendanceLogRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val refreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }

    val dhakaZone = remember { TimeZone.getTimeZone("Asia/Dhaka") }
    val isoFormat = remember { 
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply { timeZone = dhakaZone }
    }
    val displayFormat = remember { 
        SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).apply { timeZone = dhakaZone }
    }

    val fetchLogs = suspend {
        isRefreshing = true
        try {
            val response = authApi.getAttendanceLogs()
            if (response.success) {
                logs = response.log.sortedByDescending { it.recordedTimestamp }
                error = null
            } else {
                error = "Failed to fetch logs"
            }
        } catch (e: Exception) {
            error = e.message ?: "An unknown error occurred"
        } finally {
            isRefreshing = false
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        fetchLogs()
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                fetchLogs()
            }
        },
        state = refreshState,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = "Attendance Logs",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (error != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = error!!, color = MaterialTheme.colorScheme.error)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(logs) { log ->
                        AttendanceLogItem(log, isoFormat, displayFormat)
                    }
                }
            }
        }
    }
}

@Composable
fun AttendanceLogItem(
    log: AttendanceLogRecord,
    isoFormat: SimpleDateFormat,
    displayFormat: SimpleDateFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = log.employeeName ?: "Unknown Employee",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Badge(
                    containerColor = if (log.eventType == "IN") 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = log.eventType,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val formattedTime = log.recordedTimestamp?.let { timestamp ->
                try {
                    val date = isoFormat.parse(timestamp)
                    date?.let { displayFormat.format(it) } ?: timestamp
                } catch (e: Exception) {
                    timestamp
                }
            } ?: "N/A"

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "RSSI: ${log.recordedRssi.toInt()} dBm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = "UUID: ${log.bleUuid}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
