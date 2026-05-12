package lab.sabily.pulseid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import lab.sabily.pulseid.data.AttendanceRecord
import lab.sabily.pulseid.data.AuthApi
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceHistoryScreen(authApi: AuthApi) {
    var allRecords by remember { mutableStateOf<List<AttendanceRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var fromDate by remember { mutableStateOf<Long?>(null) }
    var toDate by remember { mutableStateOf<Long?>(null) }

    var showFromDatePicker by remember { mutableStateOf(false) }
    var showToDatePicker by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val displayFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val isoFormat = remember { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        try {
            val response = authApi.getAttendanceHistory()
            if (response.success) {
                allRecords = response.records.sortedByDescending { it.date }
            } else {
                error = "Failed to fetch history"
            }
        } catch (e: Exception) {
            error = e.message ?: "An unknown error occurred"
        } finally {
            isLoading = false
        }
    }

    val filteredRecords = remember(allRecords, fromDate, toDate) {
        allRecords.filter { record ->
            val recordDate = dateFormat.parse(record.date)?.time ?: 0L
            val afterFrom = fromDate?.let { recordDate >= it } ?: true
            val beforeTo = toDate?.let { recordDate <= it } ?: true
            afterFrom && beforeTo
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Attendance History",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val date = dateFormat.parse(record.date)
                Text(
                    text = date?.let { displayFormat.format(it) } ?: record.date,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                // Duration
                val durationText = if (record.exit_time == null) {
                    "Still In Office"
                } else {
                    try {
                        val entry = isoFormat.parse(record.entry_time)
                        val exit = isoFormat.parse(record.exit_time)
                        if (entry != null && exit != null) {
                            val diff = exit.time - entry.time
                            val hours = diff / (1000 * 60 * 60)
                            val minutes = (diff / (1000 * 60)) % 60
                            "${hours}h ${minutes}m"
                        } else "N/A"
                    } catch (e: Exception) {
                        "N/A"
                    }
                }
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (record.exit_time == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column {
                    Text(text = "Entry", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    val entryTime = try {
                        val d = isoFormat.parse(record.entry_time)
                        d?.let { timeFormat.format(it) } ?: record.entry_time
                    } catch (e: Exception) {
                        record.entry_time
                    }
                    Text(text = entryTime, style = MaterialTheme.typography.bodyLarge)
                }
                
                Column {
                    Text(text = "Exit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    val exitTime = if (record.exit_time == null) "--:--" else {
                        try {
                            val d = isoFormat.parse(record.exit_time)
                            d?.let { timeFormat.format(it) } ?: record.exit_time
                        } catch (e: Exception) {
                            record.exit_time
                        }
                    }
                    Text(text = exitTime, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}