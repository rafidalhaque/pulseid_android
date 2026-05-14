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
import lab.sabily.pulseid.data.AddLeaveRequest
import lab.sabily.pulseid.data.AuthApi
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestLeaveScreen(
    authApi: AuthApi,
    onSuccess: () -> Unit,
    onBack: () -> Unit
) {
    var leaveCause by remember { mutableStateOf("") }
    var leaveType by remember { mutableStateOf("Sick") }
    var expanded by remember { mutableStateOf(false) }
    
    val leaveTypes = listOf("Sick", "Casual", "Annual", "Other")
    
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isSubmitting by remember { mutableStateOf(false) }

    val selectedDate = datePickerState.selectedDateMillis?.let {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(it))
    } ?: "Select Date"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Request Leave") },
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
            OutlinedCard(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Date", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = selectedDate,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selectedDate == "Select Date") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = leaveType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Leave Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        leaveTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    leaveType = type
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = leaveCause,
                onValueChange = { leaveCause = it },
                label = { Text("Leave Cause") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (leaveCause.isBlank() || datePickerState.selectedDateMillis == null) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Please fill all fields")
                        }
                        return@Button
                    }

                    scope.launch {
                        isSubmitting = true
                        try {
                            val request = AddLeaveRequest(
                                date = selectedDate,
                                leaveType = leaveType,
                                leaveCause = leaveCause
                            )
                            val response = authApi.addLeave(request)
                            if (response.success) {
                                // Launch snackbar in a separate coroutine so it doesn't block onSuccess
                                launch {
                                    snackbarHostState.showSnackbar("Leave requested successfully")
                                }
                                onSuccess()
                            } else {
                                snackbarHostState.showSnackbar(response.message ?: "Failed to request leave")
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
                    Text("Submit Request")
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) {
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
}
