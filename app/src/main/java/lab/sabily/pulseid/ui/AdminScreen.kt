package lab.sabily.pulseid.ui

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

    when (currentSubScreen) {
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
        AdminSubScreen.ALL_MEMBERS -> {
            Column(modifier = Modifier.fillMaxSize()) {
                IconButton(
                    onClick = { currentSubScreen = null },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                AllMembersScreen(
                    authApi = authApi,
                    onMemberClick = { id ->
                        selectedEmployeeId = id
                        currentSubScreen = AdminSubScreen.MEMBER_DETAIL
                    }
                )
            }
        }
        AdminSubScreen.MEMBER_DETAIL -> {
            Column(modifier = Modifier.fillMaxSize()) {
                IconButton(
                    onClick = { currentSubScreen = AdminSubScreen.ALL_MEMBERS },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                selectedEmployeeId?.let {
                    MemberDetailScreen(authApi = authApi, employeeId = it)
                }
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
                    onClick = { currentSubScreen = AdminSubScreen.ALL_MEMBERS },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("All Members")
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
            }
        }
    }
}

enum class AdminSubScreen {
    IMPORT, LOGS, ALL_MEMBERS, MEMBER_DETAIL
}
