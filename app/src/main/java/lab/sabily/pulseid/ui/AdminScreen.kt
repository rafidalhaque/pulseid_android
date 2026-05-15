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
import lab.sabily.pulseid.ui.WorkPolicyListScreen
import lab.sabily.pulseid.ui.AddWorkPolicyScreen
import lab.sabily.pulseid.ui.EditWorkPolicyScreen
import lab.sabily.pulseid.ui.HolidaysScreen
import lab.sabily.pulseid.ui.LeaveManagementScreen

@Composable
fun AdminScreen(authApi: AuthApi) {
    var currentSubScreen by remember { mutableStateOf<AdminSubScreen?>(null) }
    var selectedEmployeeId by remember { mutableStateOf<String?>(null) }
    var employeeToEdit by remember { mutableStateOf<lab.sabily.pulseid.data.Employee?>(null) }
    var policyToEdit by remember { mutableStateOf<lab.sabily.pulseid.data.WorkPolicy?>(null) }

    BackHandler(enabled = currentSubScreen != null) {
        currentSubScreen = when (currentSubScreen) {
            AdminSubScreen.IMPORT -> null
            AdminSubScreen.LOGS -> null
            AdminSubScreen.ALL_EMPLOYEES -> null
            AdminSubScreen.HOLIDAYS -> null
            AdminSubScreen.WORK_POLICY -> null
            AdminSubScreen.LEAVE_REQUESTS -> null
            AdminSubScreen.ATTENDANCE_SHEET -> null
            AdminSubScreen.ADD_HOLIDAY -> AdminSubScreen.HOLIDAYS
            AdminSubScreen.ADD_WORK_POLICY -> AdminSubScreen.WORK_POLICY
            AdminSubScreen.EDIT_WORK_POLICY -> AdminSubScreen.WORK_POLICY
            AdminSubScreen.EMPLOYEE_DETAIL -> AdminSubScreen.ALL_EMPLOYEES
            AdminSubScreen.ADD_EMPLOYEE -> AdminSubScreen.ALL_EMPLOYEES
            AdminSubScreen.EDIT_EMPLOYEE -> AdminSubScreen.EMPLOYEE_DETAIL
            null -> null
        }
    }

    when (currentSubScreen) {
        AdminSubScreen.ATTENDANCE_SHEET -> {
            AttendanceSheetScreen(
                authApi = authApi,
                onBack = { currentSubScreen = null }
            )
        }
        AdminSubScreen.LEAVE_REQUESTS -> {
            LeaveManagementScreen(
                authApi = authApi,
                onBack = { currentSubScreen = null }
            )
        }
        AdminSubScreen.WORK_POLICY -> {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                IconButton(
                    onClick = { currentSubScreen = null },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                WorkPolicyListScreen(
                    authApi = authApi,
                    onAddClick = { currentSubScreen = AdminSubScreen.ADD_WORK_POLICY },
                    onEditClick = { policy ->
                        policyToEdit = policy
                        currentSubScreen = AdminSubScreen.EDIT_WORK_POLICY
                    }
                )
            }
        }
        AdminSubScreen.ADD_WORK_POLICY -> {
            AddWorkPolicyScreen(
                authApi = authApi,
                onSuccess = { currentSubScreen = AdminSubScreen.WORK_POLICY },
                onBack = { currentSubScreen = AdminSubScreen.WORK_POLICY }
            )
        }
        AdminSubScreen.EDIT_WORK_POLICY -> {
            policyToEdit?.let { policy ->
                EditWorkPolicyScreen(
                    authApi = authApi,
                    policy = policy,
                    onSuccess = { currentSubScreen = AdminSubScreen.WORK_POLICY },
                    onBack = { currentSubScreen = AdminSubScreen.WORK_POLICY }
                )
            }
        }
        AdminSubScreen.HOLIDAYS -> {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                IconButton(
                    onClick = { currentSubScreen = null },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                HolidaysScreen(
                    authApi = authApi,
                    onAddClick = { currentSubScreen = AdminSubScreen.ADD_HOLIDAY }
                )
            }
        }
        AdminSubScreen.ADD_HOLIDAY -> {
            AddHolidayScreen(
                authApi = authApi,
                onSuccess = { currentSubScreen = AdminSubScreen.HOLIDAYS },
                onBack = { currentSubScreen = AdminSubScreen.HOLIDAYS }
            )
        }
        AdminSubScreen.IMPORT -> {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
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
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
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
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
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
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
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
                    .statusBarsPadding()
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
                    onClick = { currentSubScreen = AdminSubScreen.LEAVE_REQUESTS },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Leave Requests")
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
                    onClick = { currentSubScreen = AdminSubScreen.HOLIDAYS },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Holidays")
                }

                Button(
                    onClick = { currentSubScreen = AdminSubScreen.WORK_POLICY },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Work Policy")
                }

                Button(
                    onClick = { currentSubScreen = AdminSubScreen.ATTENDANCE_SHEET },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Attendance Sheet")
                }
            }
        }
    }
}

enum class AdminSubScreen {
    IMPORT, LOGS, ALL_EMPLOYEES, EMPLOYEE_DETAIL, ADD_EMPLOYEE, EDIT_EMPLOYEE, ADD_HOLIDAY, HOLIDAYS, WORK_POLICY, ADD_WORK_POLICY, EDIT_WORK_POLICY, LEAVE_REQUESTS, ATTENDANCE_SHEET
}
