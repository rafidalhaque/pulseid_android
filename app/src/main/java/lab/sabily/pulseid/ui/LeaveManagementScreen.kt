package lab.sabily.pulseid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import lab.sabily.pulseid.data.AuthApi
import lab.sabily.pulseid.data.LeaveRecord
import lab.sabily.pulseid.data.ManageLeaveRequest
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaveManagementScreen(
    authApi: AuthApi,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var leaveRecords by remember { mutableStateOf<List<LeaveRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    val refreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    var showConfirmDialog by remember { mutableStateOf<Pair<LeaveRecord, String>?>(null) }

    val fetchLeaveRecords = suspend {
        isRefreshing = true
        try {
            val response = authApi.getLeaveRecords()
            if (response.success) {
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
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Leave Management") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
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
                        Text(text = "No leave requests found")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(leaveRecords) { record ->
                            LeaveManagementCard(
                                record = record,
                                onApprove = { showConfirmDialog = record to "Approved" },
                                onReject = { showConfirmDialog = record to "Rejected" }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showConfirmDialog != null) {
        val (record, action) = showConfirmDialog!!
        AlertDialog(
            onDismissRequest = { showConfirmDialog = null },
            title = { Text("$action Leave Request") },
            text = { Text("Are you sure you want to $action the leave request for ${record.employeeName} on ${record.date}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                val response = authApi.manageLeave(ManageLeaveRequest(record.id, action))
                                if (response.success) {
                                    snackbarHostState.showSnackbar("Request $action")
                                    fetchLeaveRecords()
                                } else {
                                    snackbarHostState.showSnackbar(response.message ?: "Action failed")
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar(e.message ?: "An error occurred")
                            } finally {
                                showConfirmDialog = null
                            }
                        }
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun LeaveManagementCard(
    record: LeaveRecord,
    onApprove: () -> Unit,
    onReject: () -> Unit
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
                Column {
                    Text(
                        text = record.employeeName ?: "Unknown Employee",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = record.date,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
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
            
            if (record.isApproved == "Not Approved") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onReject,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                    ) {
                        Text("Reject")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onApprove,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("Approve")
                    }
                }
            }
        }
    }
}
