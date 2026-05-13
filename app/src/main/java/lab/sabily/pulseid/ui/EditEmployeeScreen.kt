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
import lab.sabily.pulseid.data.EditEmployeeRequest
import lab.sabily.pulseid.data.AuthApi
import lab.sabily.pulseid.data.Employee
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEmployeeScreen(
    authApi: AuthApi,
    employee: Employee,
    onSuccess: () -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf(employee.name) }
    var email by remember { mutableStateOf(employee.email) }
    var idNumber by remember { mutableStateOf(employee.idNumber) }
    
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val initialDate = try {
        employee.joinDate?.let { dateFormatter.parse(it)?.time } ?: System.currentTimeMillis()
    } catch (e: Exception) {
        System.currentTimeMillis()
    }
    
    var joinDate by remember { mutableLongStateOf(initialDate) }
    var designation by remember { mutableStateOf(employee.designation) }
    var password by remember { mutableStateOf("") }
    var position by remember { mutableStateOf(employee.position) }
    var isAdmin by remember { mutableStateOf(employee.isAdmin) }
    
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Employee") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = idNumber,
                onValueChange = { idNumber = it },
                label = { Text("ID Number") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = dateFormatter.format(Date(joinDate)),
                onValueChange = { },
                label = { Text("Join Date") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    TextButton(onClick = { showDatePicker = true }) {
                        Text("Pick")
                    }
                }
            )
            
            OutlinedTextField(
                value = designation,
                onValueChange = { designation = it },
                label = { Text("Designation") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password (Leave blank to keep current)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = position,
                onValueChange = { position = it },
                label = { Text("Position (Integer)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Is Admin")
                Switch(checked = isAdmin, onCheckedChange = { isAdmin = it })
            }
            
            if (errorMessage != null) {
                Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
            }
            
            Button(
                onClick = {
                    val posInt = position.toIntOrNull()
                    if (posInt == null) {
                        errorMessage = "Position must be an integer"
                        return@Button
                    }
                    
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        try {
                            val request = EditEmployeeRequest(
                                uuid = employee.id,
                                name = if (name != employee.name) name else null,
                                email = if (email != employee.email) email else null,
                                idNumber = if (idNumber != employee.idNumber) idNumber else null,
                                joinDate = if (dateFormatter.format(Date(joinDate)) != employee.joinDate) dateFormatter.format(Date(joinDate)) else null,
                                designation = if (designation != employee.designation) designation else null,
                                password = if (password.isNotBlank()) password else null,
                                position = if (posInt.toString() != employee.position) posInt else null,
                                isAdmin = if (isAdmin != employee.isAdmin) isAdmin else null
                            )
                            val response = authApi.editEmployee(request)
                            if (response.success) {
                                onSuccess()
                            } else {
                                errorMessage = response.message ?: "Failed to edit employee"
                            }
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "An error occurred"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Save Changes")
                }
            }
        }
    }
    
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = joinDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    joinDate = datePickerState.selectedDateMillis ?: joinDate
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
