package lab.sabily.pulseid.ui

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lab.sabily.pulseid.data.AuthApi
import lab.sabily.pulseid.data.NvsGeneratorRequest
import okhttp3.ResponseBody
import java.io.OutputStream

data class NvsGeneratorState(
    val baseUrl: String = lab.sabily.pulseid.data.NetworkModule.BASE_URL,
    val wifiSsid: String = "",
    val wifiPassword: String = "",
    val otaPassword: String = "",
    val isLoading: Boolean = false,
    val snackbarMessage: String? = null
)

class NvsGeneratorViewModel(private val authApi: AuthApi) : ViewModel() {
    private val _state = MutableStateFlow(NvsGeneratorState())
    val state: StateFlow<NvsGeneratorState> = _state.asStateFlow()

    fun onWifiSsidChange(value: String) = _state.update { it.copy(wifiSsid = value) }
    fun onWifiPasswordChange(value: String) = _state.update { it.copy(wifiPassword = value) }
    fun onOtaPasswordChange(value: String) = _state.update { it.copy(otaPassword = value) }

    fun generateNvs(context: Context) {
        val currentState = _state.value
        if (currentState.wifiSsid.isBlank() || currentState.wifiPassword.isBlank() || currentState.otaPassword.isBlank()) {
            _state.update { it.copy(snackbarMessage = "Please fill all fields") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val response = authApi.generateNvs(
                    NvsGeneratorRequest(
                        base_url = currentState.baseUrl,
                        wifi_ssid = currentState.wifiSsid,
                        wifi_password = currentState.wifiPassword,
                        ota_password = currentState.otaPassword
                    )
                )
                
                val fileName = "nvs_${System.currentTimeMillis()}.bin"
                val savedUri = saveFileToDownloads(context, response, fileName)
                
                if (savedUri != null) {
                    _state.update { it.copy(snackbarMessage = "Success: Saved as $fileName", isLoading = false) }
                } else {
                    _state.update { it.copy(snackbarMessage = "Failed to save file", isLoading = false) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = "Error: ${e.message}", isLoading = false) }
            }
        }
    }

    private fun saveFileToDownloads(context: Context, body: ResponseBody, fileName: String): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        return uri?.let {
            try {
                val outputStream: OutputStream? = resolver.openOutputStream(it)
                outputStream?.use { os ->
                    body.byteStream().use { inputStream ->
                        inputStream.copyTo(os)
                    }
                }
                it
            } catch (e: Exception) {
                null
            }
        }
    }

    fun onSnackbarShown() = _state.update { it.copy(snackbarMessage = null) }

    class Factory(private val authApi: AuthApi) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NvsGeneratorViewModel(authApi) as T
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NvsGeneratorScreen(
    authApi: AuthApi,
    onBack: () -> Unit,
    viewModel: NvsGeneratorViewModel = viewModel(factory = NvsGeneratorViewModel.Factory(authApi))
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var wifiPasswordVisible by remember { mutableStateOf(false) }
    var otaPasswordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onSnackbarShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("NVS Generator") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = { },
                label = { Text("Base URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = false
            )

            OutlinedTextField(
                value = state.wifiSsid,
                onValueChange = { viewModel.onWifiSsidChange(it) },
                label = { Text("WiFi SSID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = state.wifiPassword,
                onValueChange = { viewModel.onWifiPasswordChange(it) },
                label = { Text("WiFi Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (wifiPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { wifiPasswordVisible = !wifiPasswordVisible }) {
                        Icon(
                            imageVector = if (wifiPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle visibility"
                        )
                    }
                }
            )

            OutlinedTextField(
                value = state.otaPassword,
                onValueChange = { viewModel.onOtaPasswordChange(it) },
                label = { Text("OTA Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (otaPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { otaPasswordVisible = !otaPasswordVisible }) {
                        Icon(
                            imageVector = if (otaPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle visibility"
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { viewModel.generateNvs(context) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generating...")
                } else {
                    Text("Generate NVS")
                }
            }
        }
    }
}
