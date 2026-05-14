package lab.sabily.pulseid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
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
    var employees by remember { mutableStateOf<List<lab.sabily.pulseid.data.Employee>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    val refreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedStatus by remember { mutableStateOf("All") }
    var selectedDate by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredRecords = remember(leaveRecords, selectedStatus, selectedDate, searchQuery, employees) {
        leaveRecords.filter { record ->
            val employeeName = record.employeeName 
                ?: employees.find { it.id == record.employeeId }?.name 
                ?: "Unknown Employee"
                
            val statusMatch = selectedStatus == "All" || record.isApproved == selectedStatus
            val dateMatch = selectedDate == null || record.date.contains(selectedDate!!, ignoreCase = true)
            val nameMatch = searchQuery.isEmpty() || employeeName.contains(searchQuery, ignoreCase = true)
            
            statusMatch && dateMatch && nameMatch
        }
    }

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
            
            // Fetch employees for name mapping fallback
            val empResponse = authApi.getAllMembers()
            if (empResponse.success) {
                employees = empResponse.employee
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
        // Auto-refresh every 10 seconds
        while (true) {
            kotlinx.coroutines.delay(10000)
            fetchLeaveRecords()
        }
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
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search Employee") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statuses = listOf("All", "Approved", "Not Approved", "Rejected")
                    statuses.forEach { status ->
                        FilterChip(
                            selected = selectedStatus == status,
                            onClick = { selectedStatus = status },
                            label = { Text(status, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedDate == null) "All Dates" else "Date: $selectedDate",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row {
                        if (selectedDate != null) {
                            TextButton(onClick = { selectedDate = null }) {
                                Text("Clear Date")
                            }
                        }
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Pick Date")
                        }
                    }
                }

                if (isLoading && !isRefreshing && leaveRecords.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (error != null && leaveRecords.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = error!!, color = MaterialTheme.colorScheme.error)
                    }
                } else if (filteredRecords.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = if (leaveRecords.isEmpty()) "No leave requests found" else "No records match filters")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(filteredRecords) { record ->
                            val employeeName = record.employeeName 
                                ?: employees.find { it.id == record.employeeId }?.name 
                                ?: "Unknown Employee"
                                
                            LeaveManagementCard(
                                record = record.copy(employeeName = employeeName),
                                onApprove = { showConfirmDialog = record to "Approved" },
                                onReject = { showConfirmDialog = record to "Rejected" }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val sdf = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())
                        selectedDate = sdf.format(Date(it))
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showConfirmDialog != null) {
        val (record, action) = showConfirmDialog!!
        val employeeName = record.employeeName 
            ?: employees.find { it.id == record.employeeId }?.name 
            ?: "Unknown Employee"

        AlertDialog(
            onDismissRequest = { showConfirmDialog = null },
            title = { Text("$action Leave Request") },
            text = { Text("Are you sure you want to $action the leave request for $employeeName on ${record.date}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val currentAction = action
                        val currentRecord = record
                        showConfirmDialog = null // Dismiss immediately
                        scope.launch {
                            try {
                                val response = authApi.manageLeave(ManageLeaveRequest(currentRecord.id, currentAction))
                                if (response.success) {
                                    launch {
                                        snackbarHostState.showSnackbar("Request $currentAction")
                                    }
                                    fetchLeaveRecords()
                                } else {
                                    snackbarHostState.showSnackbar(response.message ?: "Action failed")
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar(e.message ?: "An error occurred")
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
