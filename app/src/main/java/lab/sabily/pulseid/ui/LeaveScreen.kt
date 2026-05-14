package lab.sabily.pulseid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import lab.sabily.pulseid.data.AuthApi

@Composable
fun LeaveScreen(authApi: AuthApi) {
    var isRequestingLeave by remember { mutableStateOf(false) }

    BackHandler(enabled = isRequestingLeave) {
        isRequestingLeave = false
    }

    if (isRequestingLeave) {
        RequestLeaveScreen(
            authApi = authApi,
            onSuccess = { isRequestingLeave = false },
            onBack = { isRequestingLeave = false }
        )
    } else {
        LeaveHistoryScreen(
            authApi = authApi,
            onRequestLeave = { isRequestingLeave = true }
        )
    }
}
