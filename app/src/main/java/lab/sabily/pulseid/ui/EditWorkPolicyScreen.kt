package lab.sabily.pulseid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import lab.sabily.pulseid.data.EditWorkPolicyRequest
import lab.sabily.pulseid.data.AuthApi
import lab.sabily.pulseid.data.WorkPolicy
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditWorkPolicyScreen(
    authApi: AuthApi,
    policy: WorkPolicy,
    onSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") } }
    
    val initialStartDateMillis = remember(policy.startDate) { dateFormat.parse(policy.startDate)?.time }
    val initialEndDateMillis = remember(policy.endDate) { dateFormat.parse(policy.endDate)?.time }
    
    var startDateMillis by remember { mutableStateOf(initialStartDateMillis) }
    var endDateMillis by remember { mutableStateOf(initialEndDateMillis) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    val initialStartHour = policy.officeStart.split(":")[0].toInt()
    val initialStartMinute = policy.officeStart.split(":")[1].toInt()
    val initialEndHour = policy.officeEnd.split(":")[0].toInt()
    val initialEndMinute = policy.officeEnd.split(":")[1].toInt()

    val startTimePickerState = rememberTimePickerState(initialHour = initialStartHour, initialMinute = initialStartMinute)
    val endTimePickerState = rememberTimePickerState(initialHour = initialEndHour, initialMinute = initialEndMinute)
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    var lateMinutes by remember { mutableStateOf(policy.lateMinutes.toString()) }
    var earlyMinutes by remember { mutableStateOf(policy.earlyMinutes.toString()) }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isSubmitting by remember { mutableStateOf(false) }

    val startDateText = startDateMillis?.let { dateFormat.format(Date(it)) } ?: "Select Start Date"
    val endDateText = endDateMillis?.let { dateFormat.format(Date(it)) } ?: "Select End Date"
    val startTimeText = String.format("%02d:%02d:00", startTimePickerState.hour, startTimePickerState.minute)
    val endTimeText = String.format("%02d:%02d:00", endTimePickerState.hour, endTimePickerState.minute)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Work Policy") },
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
            Text("Validity Period", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedCard(onClick = { showStartDatePicker = true }, modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Start Date", style = MaterialTheme.typography.labelMedium)
                        Text(startDateText, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                OutlinedCard(onClick = { showEndDatePicker = true }, modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("End Date", style = MaterialTheme.typography.labelMedium)
                        Text(endDateText, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Text("Office Hours", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedCard(onClick = { showStartTimePicker = true }, modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Start Time", style = MaterialTheme.typography.labelMedium)
                        Text(startTimeText.substring(0, 5), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                OutlinedCard(onClick = { showEndTimePicker = true }, modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("End Time", style = MaterialTheme.typography.labelMedium)
                        Text(endTimeText.substring(0, 5), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Text("Tolerance (Minutes)", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = lateMinutes,
                    onValueChange = { lateMinutes = it },
                    label = { Text("Late Minutes") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = earlyMinutes,
                    onValueChange = { earlyMinutes = it },
                    label = { Text("Early Minutes") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    val late = lateMinutes.toIntOrNull()
                    val early = earlyMinutes.toIntOrNull()
                    if (late == null || early == null) {
                        scope.launch { snackbarHostState.showSnackbar("Invalid tolerance minutes") }
                        return@Button
                    }

                    scope.launch {
                        isSubmitting = true
                        try {
                            val request = EditWorkPolicyRequest(
                                workPolicyId = policy.id,
                                startDate = if (startDateText != policy.startDate) startDateText else null,
                                endDate = if (endDateText != policy.endDate) endDateText else null,
                                officeStart = if (startTimeText != policy.officeStart) startTimeText else null,
                                officeEnd = if (endTimeText != policy.officeEnd) endTimeText else null,
                                lateMinutes = if (late != policy.lateMinutes) late else null,
                                earlyMinutes = if (early != policy.earlyMinutes) early else null
                            )
                            
                            val response = authApi.editWorkPolicy(request)
                            if (response.success) {
                                launch {
                                    snackbarHostState.showSnackbar("Work policy updated")
                                }
                                onSuccess()
                            } else {
                                snackbarHostState.showSnackbar(response.message ?: "Failed to update policy")
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
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text("Update")
                }
            }
        }
    }

    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startDateMillis)
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startDateMillis = datePickerState.selectedDateMillis
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = endDateMillis)
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endDateMillis = datePickerState.selectedDateMillis
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    if (showStartTimePicker) {
        TimePickerDialog(
            onDismissRequest = { showStartTimePicker = false },
            confirmButton = {
                TextButton(onClick = { showStartTimePicker = false }) { Text("OK") }
            }
        ) { TimePicker(state = startTimePickerState) }
    }

    if (showEndTimePicker) {
        TimePickerDialog(
            onDismissRequest = { showEndTimePicker = false },
            confirmButton = {
                TextButton(onClick = { showEndTimePicker = false }) { Text("OK") }
            }
        ) { TimePicker(state = endTimePickerState) }
    }
}
