package lab.sabily.pulseid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import lab.sabily.pulseid.data.AddHolidayRequest
import lab.sabily.pulseid.data.AuthApi
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHolidayScreen(
    authApi: AuthApi,
    onSuccess: () -> Unit,
    onBack: () -> Unit
) {
    var holidayName by remember { mutableStateOf("") }
    var isNational by remember { mutableStateOf(true) }
    var isRange by remember { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    val startDatePickerState = rememberDatePickerState()
    val endDatePickerState = rememberDatePickerState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isSubmitting by remember { mutableStateOf(false) }

    val dateFormatter = remember { 
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    val selectedStartDate = startDatePickerState.selectedDateMillis?.let {
        dateFormatter.format(Date(it))
    } ?: "Select Start Date"

    val selectedEndDate = endDatePickerState.selectedDateMillis?.let {
        dateFormatter.format(Date(it))
    } ?: "Select End Date"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Holiday") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = holidayName,
                onValueChange = { holidayName = it },
                label = { Text("Holiday Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Range Holiday?", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = isRange,
                    onCheckedChange = { isRange = it }
                )
            }

            OutlinedCard(
                onClick = { showStartDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = if (isRange) "Start Date" else "Date", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = selectedStartDate,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selectedStartDate == "Select Start Date") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (isRange) {
                OutlinedCard(
                    onClick = { showEndDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "End Date", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = selectedEndDate,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selectedEndDate == "Select End Date") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Is National Holiday?", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = isNational,
                    onCheckedChange = { isNational = it }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (holidayName.isBlank() || startDatePickerState.selectedDateMillis == null) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Please fill all required fields")
                        }
                        return@Button
                    }

                    if (isRange && endDatePickerState.selectedDateMillis == null) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Please select an end date for range holiday")
                        }
                        return@Button
                    }

                    if (isRange && endDatePickerState.selectedDateMillis!! <= startDatePickerState.selectedDateMillis!!) {
                        scope.launch {
                            snackbarHostState.showSnackbar("End date must be after start date")
                        }
                        return@Button
                    }

                    scope.launch {
                        isSubmitting = true
                        try {
                            val request = AddHolidayRequest(
                                date = selectedStartDate,
                                endDate = if (isRange) selectedEndDate else null,
                                name = holidayName,
                                isNational = isNational
                            )
                            val response = authApi.addHoliday(request)
                            if (response.success) {
                                launch {
                                    snackbarHostState.showSnackbar("Holiday added")
                                }
                                onSuccess()
                            } else {
                                snackbarHostState.showSnackbar(response.message ?: "Failed to add holiday")
                            }
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar(e.message ?: "An unknown error occurred")
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Submit")
                }
            }
        }
    }

    if (showStartDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = startDatePickerState)
        }
    }

    if (showEndDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = endDatePickerState)
        }
    }
}
