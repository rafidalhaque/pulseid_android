package lab.sabily.pulseid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import lab.sabily.pulseid.data.AuthApi
import lab.sabily.pulseid.data.Holiday
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HolidaysScreen(
    authApi: AuthApi,
    onAddClick: () -> Unit
) {
    var holidays by remember { mutableStateOf<List<Holiday>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val fetchHolidays = {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val response = authApi.getHolidays()
                if (response.success) {
                    holidays = response.holidays
                } else {
                    errorMessage = "Failed to fetch holidays"
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "An unknown error occurred"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchHolidays()
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = "Add Holiday")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "Holidays",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Box(modifier = Modifier.weight(1f)) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (errorMessage != null) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = { fetchHolidays() }) {
                            Text("Retry")
                        }
                    }
                } else if (holidays.isEmpty()) {
                    Text(
                        text = "No holidays found",
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(holidays) { holiday ->
                            HolidayItem(holiday)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HolidayItem(holiday: Holiday) {
    val inputFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val outputFormat = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }
    val yearFormat = remember { SimpleDateFormat("yyyy", Locale.getDefault()) }

    val formattedDate = remember(holiday.date, holiday.endDate) {
        try {
            val startDate = inputFormat.parse(holiday.date)
            if (holiday.endDate != null) {
                val endDate = inputFormat.parse(holiday.endDate)
                if (startDate != null && endDate != null) {
                    val startStr = outputFormat.format(startDate)
                    val endStr = outputFormat.format(endDate)
                    val yearStr = yearFormat.format(endDate)
                    "$startStr - $endStr $yearStr"
                } else {
                    holiday.date
                }
            } else {
                if (startDate != null) {
                    val dateStr = outputFormat.format(startDate)
                    val yearStr = yearFormat.format(startDate)
                    "$dateStr $yearStr"
                } else {
                    holiday.date
                }
            }
        } catch (e: Exception) {
            holiday.date
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = holiday.holidayName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (holiday.isNational) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "National",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}
