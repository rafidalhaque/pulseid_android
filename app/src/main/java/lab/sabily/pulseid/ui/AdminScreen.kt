package lab.sabily.pulseid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import lab.sabily.pulseid.data.AuthApi

@Composable
fun AdminScreen(authApi: AuthApi) {
    var currentSubScreen by remember { mutableStateOf<AdminSubScreen?>(null) }
    var selectedEmployeeId by remember { mutableStateOf<String?>(null) }
    var employeeToEdit by remember { mutableStateOf<lab.sabily.pulseid.data.Employee?>(null) }

    BackHandler(enabled = currentSubScreen != null) {
        currentSubScreen = when (currentSubScreen) {
            AdminSubScreen.IMPORT -> null
            AdminSubScreen.LOGS -> null
            AdminSubScreen.ALL_EMPLOYEES -> null
            AdminSubScreen.ADD_HOLIDAY -> null
            AdminSubScreen.EMPLOYEE_DETAIL -> AdminSubScreen.ALL_EMPLOYEES
            AdminSubScreen.ADD_EMPLOYEE -> AdminSubScreen.ALL_EMPLOYEES
            AdminSubScreen.EDIT_EMPLOYEE -> AdminSubScreen.EMPLOYEE_DETAIL
            null -> null
        }
    }

    when (currentSubScreen) {
        AdminSubScreen.ADD_HOLIDAY -> {
            AddHolidayScreen(
                authApi = authApi,
                onSuccess = { currentSubScreen = null },
                onBack = { currentSubScreen = null }
            )
        }
        AdminSubScreen.IMPORT -> {
            Column(modifier = Modifier.fillMaxSize()) {
                IconButton(
                    onClick = { currentSubScreen = null },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                ImportEmployeeScreen(authApi = authApi)
            }
        }
        AdminSubScreen.LOGS -> {
            Column(modifier = Modifier.fillMaxSize()) {
                IconButton(
                    onClick = { currentSubScreen = null },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                AttendanceLogScreen(authApi = authApi)
            }
        }
        AdminSubScreen.ALL_EMPLOYEES -> {
            Column(modifier = Modifier.fillMaxSize()) {
                IconButton(
                    onClick = { currentSubScreen = null },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                AllEmployeesScreen(
                    authApi = authApi,
                    onEmployeeClick = { id ->
                        selectedEmployeeId = id
                        currentSubScreen = AdminSubScreen.EMPLOYEE_DETAIL
                    },
                    onAddClick = {
                        currentSubScreen = AdminSubScreen.ADD_EMPLOYEE
                    }
                )
            }
        }
        AdminSubScreen.EMPLOYEE_DETAIL -> {
            Column(modifier = Modifier.fillMaxSize()) {
                IconButton(
                    onClick = { currentSubScreen = AdminSubScreen.ALL_EMPLOYEES },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                selectedEmployeeId?.let {
                    MemberDetailScreen(
                        authApi = authApi,
                        employeeId = it,
                        onEditClick = { emp ->
                            employeeToEdit = emp
                            currentSubScreen = AdminSubScreen.EDIT_EMPLOYEE
                        }
                    )
                }
            }
        }
        AdminSubScreen.ADD_EMPLOYEE -> {
            AddEmployeeScreen(
                authApi = authApi,
                onSuccess = {
                    currentSubScreen = AdminSubScreen.ALL_EMPLOYEES
                },
                onBack = { currentSubScreen = AdminSubScreen.ALL_EMPLOYEES }
            )
        }
        AdminSubScreen.EDIT_EMPLOYEE -> {
            employeeToEdit?.let { emp ->
                EditEmployeeScreen(
                    authApi = authApi,
                    employee = emp,
                    onSuccess = {
                        currentSubScreen = AdminSubScreen.ALL_EMPLOYEES
                    },
                    onBack = { currentSubScreen = AdminSubScreen.EMPLOYEE_DETAIL }
                )
            }
        }
        null -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
            ) {
                Button(
                    onClick = { currentSubScreen = AdminSubScreen.ALL_EMPLOYEES },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("All Employees")
                }

                Button(
                    onClick = { currentSubScreen = AdminSubScreen.LOGS },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Attendance Logs")
                }

                Button(
                    onClick = { currentSubScreen = AdminSubScreen.IMPORT },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Import Employee")
                }

                Button(
                    onClick = { currentSubScreen = AdminSubScreen.ADD_HOLIDAY },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Holiday")
                }
            }
        }
    }
}

enum class AdminSubScreen {
    IMPORT, LOGS, ALL_EMPLOYEES, EMPLOYEE_DETAIL, ADD_EMPLOYEE, EDIT_EMPLOYEE, ADD_HOLIDAY
}
