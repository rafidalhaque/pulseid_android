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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceHistoryScreen(authApi: AuthApi, isAdmin: Boolean) {
    val scope = rememberCoroutineScope()
    var allRecords by remember { mutableStateOf<List<AttendanceRecord>>(emptyList()) }
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
            
            if (historyResponse.success) {
                allRecords = historyResponse.records.sortedBy { it.date }
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
                        AttendanceHistoryItem(record, isoFormat, timeFormat, displayFormat, dateFormat)
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
    isoFormat: SimpleDateFormat,
    timeFormat: SimpleDateFormat,
    displayFormat: SimpleDateFormat,
    dateFormat: SimpleDateFormat
) {
    val parseTime = { timeStr: String? ->
        if (timeStr == null) null else {
            val formats = listOf(
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss.SSSSSS",
                "yyyy-MM-dd HH:mm:ss.SSS"
            )
            var parsedDate: Date? = null
            for (format in formats) {
                try {
                    val sdf = SimpleDateFormat(format, Locale.getDefault()).apply {
                        timeZone = isoFormat.timeZone
                    }
                    parsedDate = sdf.parse(timeStr)
                    if (parsedDate != null) break
                } catch (e: Exception) {
                }
            }
            parsedDate
        }
    }

    val formatMinutes = { totalMinutes: Int? ->
        if (totalMinutes == null) "" else {
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            String.format("%02d:%02d", hours, minutes)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Date Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val date = try { dateFormat.parse(record.date) } catch (e: Exception) { null }
                Text(
                    text = date?.let { displayFormat.format(it) } ?: record.date,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                val entry = parseTime(record.entryTime)
                val exit = parseTime(record.exitTime)
                val durationText = if (record.exitTime == null) {
                    "Still In Office"
                } else if (entry != null && exit != null) {
                    val diff = exit.time - entry.time
                    val hours = diff / (1000 * 60 * 60)
                    val minutes = (diff / (1000 * 60)) % 60
                    "${hours}h ${minutes}m"
                } else "N/A"

                Text(
                    text = durationText,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (record.exitTime == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (record.employeeName != null) {
                Text(
                    text = record.employeeName!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Entry Row
            val entryDate = parseTime(record.entryTime)
            val entryTimeStr = entryDate?.let { timeFormat.format(it) } ?: record.entryTime ?: "--:--"
            Text(text = "Entry: $entryTimeStr", style = MaterialTheme.typography.bodyLarge)
            if (record.lateEntry != null) {
                Text(
                    text = "Late ${formatMinutes(record.lateEntry)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Red,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Exit Row
            if (record.exitTime == null) {
                Text(
                    text = "Still In Office",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                val exitDate = parseTime(record.exitTime)
                val exitTimeStr = exitDate?.let { timeFormat.format(it) } ?: record.exitTime ?: "--:--"
                Text(text = "Exit: $exitTimeStr", style = MaterialTheme.typography.bodyLarge)
                if (record.earlyExit != null) {
                    Text(
                        text = "Early ${formatMinutes(record.earlyExit)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFA500),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Overtime Row
            if (record.overtime != null) {
                Text(
                    text = "Overtime ${formatMinutes(record.overtime)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}