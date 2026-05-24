package lab.sabily.pulseid.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lab.sabily.pulseid.data.AuthApi
import lab.sabily.pulseid.data.Esp32PingResult

data class Esp32ManagementState(
    val pingResult: Esp32PingResult? = null,
    val isPingLoading: Boolean = false,
    val isRebootLoading: Boolean = false,
    val lastSeenPingResult: Esp32PingResult? = null,
    val isDeviceOnline: Boolean = true,
    val snackbarMessage: String? = null
)

class Esp32ManagementViewModel(private val authApi: AuthApi) : ViewModel() {
    private val _state = MutableStateFlow(Esp32ManagementState())
    val state: StateFlow<Esp32ManagementState> = _state.asStateFlow()

    private var pollingJob: Job? = null

    fun startPolling() {
        if (pollingJob != null) return
        pollingJob = viewModelScope.launch {
            while (true) {
                ping()
                delay(10000) // Poll every 10 seconds
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun ping() {
        viewModelScope.launch {
            _state.update { it.copy(isPingLoading = true) }
            try {
                val result = authApi.esp32Ping()
                if (result.success) {
                    _state.update {
                        it.copy(
                            pingResult = result,
                            lastSeenPingResult = result,
                            isDeviceOnline = true,
                            isPingLoading = false
                        )
                    }
                } else {
                    handlePingFailure()
                }
            } catch (e: Exception) {
                handlePingFailure()
            }
        }
    }

    private fun handlePingFailure() {
        _state.update {
            it.copy(
                pingResult = null,
                isDeviceOnline = false,
                isPingLoading = false
            )
        }
    }

    fun reboot() {
        viewModelScope.launch {
            _state.update { it.copy(isRebootLoading = true) }
            try {
                val res = authApi.esp32Command(lab.sabily.pulseid.data.Esp32CommandRequest("reboot"))
                if (res.success) {
                    _state.update { it.copy(snackbarMessage = "Reboot command sent", isRebootLoading = false) }
                } else {
                    _state.update { it.copy(snackbarMessage = "Failed to send reboot command", isRebootLoading = false) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = "Error: ${e.message}", isRebootLoading = false) }
            }
        }
    }

    fun onSnackbarShown() {
        _state.update { it.copy(snackbarMessage = null) }
    }

    class Factory(private val authApi: AuthApi) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return Esp32ManagementViewModel(authApi) as T
        }
    }
}
