package lab.sabily.pulseid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import lab.sabily.pulseid.data.AttendanceRecord
import lab.sabily.pulseid.data.AuthApi
import lab.sabily.pulseid.data.WorkPolicy
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceHistoryScreen(authApi: AuthApi, isAdmin: Boolean) {
    val scope = rememberCoroutineScope()
    var allRecords by remember { mutableStateOf<List<AttendanceRecord>>(emptyList()) }
    var workPolicies by remember { mutableStateOf<List<WorkPolicy>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var fromDate by remember { mutableStateOf<Long?>(null) }
    var toDate by remember { mutableStateOf<Long?>(null) }
    var showAllRecords by remember { mutableStateOf(false) }

    var showFromDatePicker by remember { mutableStateOf(false) }
    var showToDatePicker by remember { mutableStateOf(false) }

    val dhakaZone = remember { TimeZone.getTimeZone("Asia/Dhaka") }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = dhakaZone } }
    val displayFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).apply { timeZone = dhakaZone } }
    val isoFormat = remember { 
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply { timeZone = dhakaZone }
    }
    val timeFormat = remember { 
        SimpleDateFormat("hh:mm a", Locale.getDefault()).apply { timeZone = dhakaZone }
    }

    val refreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }

    val fetchHistory = suspend {
        isRefreshing = true
        try {
            val historyResponse = authApi.getAttendanceHistory(all = if (showAllRecords) true else null)
            val policyResponse = authApi.getWorkPolicies()
            
            if (historyResponse.success && policyResponse.success) {
                allRecords = historyResponse.records.sortedByDescending { it.date }
                workPolicies = policyResponse.policy
            } else {
                error = "Failed to fetch data"
            }
        } catch (e: Exception) {
            error = e.message ?: "An unknown error occurred"
        } finally {
            isRefreshing = false
            isLoading = false
        }
    }

    LaunchedEffect(showAllRecords) {
        fetchHistory()
    }

    val filteredRecords = remember(allRecords, fromDate, toDate) {
        allRecords.filter { record ->
            val recordDate = try { dateFormat.parse(record.date)?.time ?: 0L } catch(e:Exception) { 0L }
            val afterFrom = fromDate?.let { recordDate >= it } ?: true
            val beforeTo = toDate?.let { recordDate <= it } ?: true
            afterFrom && beforeTo
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                fetchHistory()
            }
        },
        state = refreshState,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Attendance History",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (isAdmin) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("All", style = MaterialTheme.typography.labelMedium)
                        Switch(
                            checked = showAllRecords,
                            onCheckedChange = { showAllRecords = it },
                            modifier = Modifier.scale(0.7f)
                        )
                    }
                }
            }

            // Date Range Filters
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterButton(
                    label = "From",
                    date = fromDate,
                    format = displayFormat,
                    onClick = { showFromDatePicker = true },
                    onClear = { fromDate = null },
                    modifier = Modifier.weight(1f)
                )
                FilterButton(
                    label = "To",
                    date = toDate,
                    format = displayFormat,
                    onClick = { showToDatePicker = true },
                    onClear = { toDate = null },
                    modifier = Modifier.weight(1f)
                )
            }

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
                    items(filteredRecords) { record ->
                        val matchedPolicy = workPolicies.find { 
                            record.date >= it.startDate && record.date <= it.endDate 
                        }
                        AttendanceHistoryItem(record, matchedPolicy, isoFormat, timeFormat, displayFormat, dateFormat)
                    }
                }
            }
        }
    }

    // Date Pickers
    if (showFromDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showFromDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    fromDate = datePickerState.selectedDateMillis
                    showFromDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showFromDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showToDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showToDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    toDate = datePickerState.selectedDateMillis
                    showToDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showToDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun FilterButton(
    label: String,
    date: Long?,
    format: SimpleDateFormat,
    onClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(text = label, style = MaterialTheme.typography.labelSmall)
            Text(
                text = date?.let { format.format(Date(it)) } ?: "Select Date",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
        }
    }
}

@Composable
fun AttendanceHistoryItem(
    record: AttendanceRecord,
    policy: WorkPolicy?,
    isoFormat: SimpleDateFormat,
    timeFormat: SimpleDateFormat,
    displayFormat: SimpleDateFormat,
    dateFormat: SimpleDateFormat
) {
    val parseTime = { timeStr: String? ->
        if (timeStr == null) null else {
            try {
                isoFormat.parse(timeStr)
            } catch (e: Exception) {
                try {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
                        timeZone = isoFormat.timeZone
                    }.parse(timeStr)
                } catch (e2: Exception) {
                    try {
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS", Locale.getDefault()).apply {
                            timeZone = isoFormat.timeZone
                        }.parse(timeStr)
                    } catch (e3: Exception) {
                        null
                    }
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val date = try { dateFormat.parse(record.date) } catch (e: Exception) { null }
                Text(
                    text = date?.let { displayFormat.format(it) } ?: record.date,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Duration
                val durationText = if (record.exitTime == null) {
                    "Still In Office"
                } else if (record.entryTime == null) {
                    "N/A"
                } else {
                    val entry = parseTime(record.entryTime)
                    val exit = parseTime(record.exitTime)
                    if (entry != null && exit != null) {
                        val diff = exit.time - entry.time
                        val hours = diff / (1000 * 60 * 60)
                        val minutes = (diff / (1000 * 60)) % 60
                        "${hours}h ${minutes}m"
                    } else "N/A"
                }
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (record.exitTime == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (record.employeeName != null) {
                Text(
                    text = record.employeeName!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column {
                    Text(text = "Entry", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    val entryDate = parseTime(record.entryTime)
                    val entryTimeStr = entryDate?.let { timeFormat.format(it) } ?: record.entryTime ?: "--:--"
                    Text(text = entryTimeStr, style = MaterialTheme.typography.bodyLarge)
                }

                Column {
                    Text(text = "Exit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    val exitDate = parseTime(record.exitTime)
                    val exitTimeStr = exitDate?.let { timeFormat.format(it) } ?: record.exitTime ?: "--:--"
                    Text(text = exitTimeStr, style = MaterialTheme.typography.bodyLarge)
                }
            }

            // Status Badges
            if (policy != null && record.entryTime != null) {
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val entryDate = parseTime(record.entryTime)
                    if (entryDate != null) {
                        val calendar = Calendar.getInstance().apply { time = entryDate }
                        val policyStart = policy.officeStart.split(":")
                        val startHour = policyStart[0].toInt()
                        val startMin = policyStart[1].toInt()

                        val entryCal = Calendar.getInstance().apply { 
                            time = entryDate
                        }
                        val scheduledStartCal = Calendar.getInstance().apply {
                            time = entryDate
                            set(Calendar.HOUR_OF_DAY, startHour)
                            set(Calendar.MINUTE, startMin)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }

                        val lateThreshold = scheduledStartCal.timeInMillis + (policy.lateMinutes * 60 * 1000)
                        if (entryCal.timeInMillis > lateThreshold) {
                            val lateDiff = entryCal.timeInMillis - scheduledStartCal.timeInMillis
                            val lateMins = lateDiff / (60 * 1000)
                            StatusBadge(text = "Late ${lateMins}m", color = Color.Red)
                        }
                    }

                    if (record.exitTime != null) {
                        val exitDate = parseTime(record.exitTime)
                        if (exitDate != null) {
                            val policyEnd = policy.officeEnd.split(":")
                            val endHour = policyEnd[0].toInt()
                            val endMin = policyEnd[1].toInt()

                            val exitCal = Calendar.getInstance().apply { time = exitDate }
                            val scheduledEndCal = Calendar.getInstance().apply {
                                time = exitDate
                                set(Calendar.HOUR_OF_DAY, endHour)
                                set(Calendar.MINUTE, endMin)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }

                            val earlyThreshold = scheduledEndCal.timeInMillis - (policy.earlyMinutes * 60 * 1000)
                            if (exitCal.timeInMillis < earlyThreshold) {
                                val earlyDiff = scheduledEndCal.timeInMillis - exitCal.timeInMillis
                                val earlyMins = earlyDiff / (60 * 1000)
                                StatusBadge(text = "Early ${earlyMins}m", color = Color(0xFFFFA500)) // Orange
                            }

                            if (exitCal.timeInMillis > scheduledEndCal.timeInMillis) {
                                val otDiff = exitCal.timeInMillis - scheduledEndCal.timeInMillis
                                val otMins = otDiff / (60 * 1000)
                                if (otMins > 0) {
                                    StatusBadge(text = "OT ${otMins}m", color = Color(0xFF4CAF50)) // Green
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.extraSmall,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}