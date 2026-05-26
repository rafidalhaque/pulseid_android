package lab.sabily.pulseid.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import lab.sabily.pulseid.R
import lab.sabily.pulseid.data.*

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.mipmap.pulseid_foreground),
                contentDescription = "PulseID Logo",
                modifier = Modifier.size(180.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "PulseID",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Smart access. Effortless attendance.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Button(
                onClick = onGetStarted,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text("Get Started")
            }
        }
    }
}

@Composable
fun ServerSetupScreen(
    securityManager: SecurityManager,
    networkModule: NetworkModule,
    onServerConnected: () -> Unit
) {
    var serverUrl by remember { mutableStateOf(securityManager.getBaseUrl() ?: "") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showScanner by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showScanner = true
        } else {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "Camera permission needed to scan QR code",
                    actionLabel = "Settings"
                )
                if (result == SnackbarResult.ActionPerformed) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            }
        }
    }

    if (showScanner) {
        Box(modifier = Modifier.fillMaxSize()) {
            QRScanner(
                onScanSuccess = { result ->
                    serverUrl = result
                    showScanner = false
                }
            )
            IconButton(
                onClick = { showScanner = false },
                modifier = Modifier.padding(16.dp).align(Alignment.TopStart)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close Scanner", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Server Setup",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "Enter your office server URL to connect.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("https://pulseid.example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage != null,
                    supportingText = {
                        if (errorMessage != null) {
                            Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    trailingIcon = {
                        IconButton(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR Code")
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = {
                        var url = serverUrl.trim()
                        if (url.endsWith("/")) url = url.dropLast(1)
                        
                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            errorMessage = "URL must start with http:// or https://"
                            return@Button
                        }
                        
                        isLoading = true
                        errorMessage = null
                        scope.launch {
                            try {
                                securityManager.saveBaseUrl(url)
                                val response = networkModule.api.healthCheck()
                                if (response.success) {
                                    isLoading = false
                                    launch {
                                        snackbarHostState.showSnackbar("Connected to server successfully")
                                    }
                                    onServerConnected()
                                } else {
                                    isLoading = false
                                    errorMessage = "Server health check failed"
                                    securityManager.clearBaseUrl()
                                }
                            } catch (e: Exception) {
                                isLoading = false
                                errorMessage = "Failed to connect: ${e.message}"
                                securityManager.clearBaseUrl()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && serverUrl.isNotBlank()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Connect")
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingLoginScreen(
    securityManager: SecurityManager,
    settingsManager: SettingsManager,
    networkModule: NetworkModule,
    onLoginSuccess: () -> Unit
) {
    var employeeId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Login",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Enter your employee ID and password.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            OutlinedTextField(
                value = employeeId,
                onValueChange = { employeeId = it },
                label = { Text("Employee ID") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, contentDescription = null)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage != null,
                supportingText = {
                    if (errorMessage != null) {
                        Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        try {
                            val response = networkModule.api.onboardingLogin(OnboardingLoginRequest(employee_id = employeeId, password = password))
                            if (response.success) {
                                securityManager.saveJwtToken(response.token)
                                
                                // Fetch profile to get real UUID and admin status
                                try {
                                    val profileResponse = networkModule.api.getProfile()
                                    if (profileResponse.success) {
                                        securityManager.saveEmployeeUuid(profileResponse.employee.id)
                                    }
                                } catch (e: Exception) {
                                    // Fallback to employeeId if profile fetch fails
                                    securityManager.saveEmployeeUuid(employeeId)
                                }
                                
                                // Fire and forget timezone sync
                                launch {
                                    try {
                                        val tzResponse = networkModule.api.getTz()
                                        if (tzResponse.success) {
                                            settingsManager.saveOfficeTz(tzResponse.tz)
                                        }
                                    } catch (e: Exception) {}
                                }
                                
                                onLoginSuccess()
                            } else {
                                errorMessage = "Invalid credentials"
                            }
                        } catch (e: Exception) {
                            errorMessage = "Login failed: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && employeeId.isNotBlank() && password.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Login")
                }
            }
        }
    }
}
