package lab.sabily.pulseid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import lab.sabily.pulseid.data.AuthApi
import lab.sabily.pulseid.data.LeaveRecord
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaveHistoryScreen(
    authApi: AuthApi,
    onRequestLeave: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var leaveRecords by remember { mutableStateOf<List<LeaveRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    val refreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }

    val fetchLeaveRecords = suspend {
        isRefreshing = true
        try {
            val response = authApi.getLeaveRecords()
            if (response.success) {
                // Sorting by date descending. Dates are in "12 May, 2026" format according to sample, 
                // but usually API returns ISO. Sample says "12 May, 2026".
                // Let's try to parse it.
                val sdf = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())
                leaveRecords = response.body.sortedByDescending { 
                    try { sdf.parse(it.date) } catch (e: Exception) { Date(0) }
                }
            } else {
                error = "Failed to fetch leave records"
            }
        } catch (e: Exception) {
            error = e.message ?: "An unknown error occurred"
        } finally {
            isRefreshing = false
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        fetchLeaveRecords()
        // Auto-refresh every 15 seconds
        while (true) {
            kotlinx.coroutines.delay(15000)
            fetchLeaveRecords()
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onRequestLeave) {
                Icon(Icons.Default.Add, contentDescription = "Request Leave")
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    fetchLeaveRecords()
                }
            },
            state = refreshState,
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    text = "Leave History",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (isLoading && !isRefreshing) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (error != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = error!!, color = MaterialTheme.colorScheme.error)
                    }
                } else if (leaveRecords.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "No leave records found")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(leaveRecords) { record ->
                            LeaveRecordCard(record)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LeaveRecordCard(record: LeaveRecord) {
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
                    text = record.date,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                LeaveStatusBadge(record.isApproved)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Type: ${record.leaveType}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Text(
                text = "Cause: ${record.leaveCause}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (record.employeeName != null) {
                Text(
                    text = "Employee: ${record.employeeName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun LeaveStatusBadge(status: String) {
    val color = when (status) {
        "Approved" -> Color(0xFF4CAF50) // Green
        "Rejected" -> Color(0xFFF44336) // Red
        "Not Approved" -> Color(0xFFFF9800) // Orange/Yellow
        else -> Color.Gray
    }
    
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.extraSmall,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}
