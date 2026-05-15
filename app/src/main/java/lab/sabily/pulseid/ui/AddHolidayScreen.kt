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
                    if (holidayName.isBlank() || datePickerState.selectedDateMillis == null) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Please fill all fields")
                        }
                        return@Button
                    }

                    scope.launch {
                        isSubmitting = true
                        try {
                            val request = AddHolidayRequest(
                                date = selectedDate,
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
