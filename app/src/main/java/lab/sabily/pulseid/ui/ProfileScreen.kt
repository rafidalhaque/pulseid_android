package lab.sabily.pulseid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import lab.sabily.pulseid.data.AuthApi
import lab.sabily.pulseid.data.ProfileResponse
import lab.sabily.pulseid.data.SecurityManager

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.launch

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    securityManager: SecurityManager,
    authApi: AuthApi,
    onLogout: () -> Unit,
    onNavigateToAppLockSetup: () -> Unit,
    appUpdateViewModel: AppUpdateViewModel
) {
    var profile by remember { mutableStateOf<ProfileResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val refreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    val fetchProfile = suspend {
        isRefreshing = true
        try {
            profile = authApi.getProfile()
            error = null
        } catch (e: Exception) {
            error = e.message ?: "An unknown error occurred"
        } finally {
            isRefreshing = false
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        fetchProfile()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    fetchProfile()
                }
            },
            state = refreshState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                if (isLoading) {
                    CircularProgressIndicator()
                } else if (error != null) {
                    Text(text = error!!, color = MaterialTheme.colorScheme.error)
                } else if (profile != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Top,
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(text = "Profile", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 24.dp))
                        
                        Column(modifier = Modifier.padding(bottom = 16.dp)) {
                            Text(text = "ID", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = profile!!.employee.id, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(profile!!.employee.id))
                                    scope.launch {
                                        snackbarHostState.showSnackbar("ID copied to clipboard")
                                    }
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy ID", modifier = Modifier.size(20.dp))
                                }
                            }
                        }

                        ProfileItem(label = "Name", value = profile!!.employee.name)
                        ProfileItem(label = "Email", value = profile!!.employee.email)
                        ProfileItem(label = "Designation", value = profile!!.employee.designation)
                        ProfileItem(label = "Join Date", value = profile!!.employee.joinDate ?: "N/A")
                        ProfileItem(label = "ID Number", value = profile!!.employee.idNumber)

                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                        Text(text = "App Lock", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        
                        AppLockSettings(securityManager, onNavigateToAppLockSetup)

                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                        Text(text = "App Updates", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        
                        AppUpdateSettings(appUpdateViewModel)

                        Spacer(modifier = Modifier.weight(1f))

                        Button(
                            onClick = onLogout,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                        ) {
                            Text("Logout")
                        }

                        val context = androidx.compose.ui.platform.LocalContext.current
                        val versionText = remember(context) {
                            try {
                                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                                "Version ${packageInfo.versionName} (${packageInfo.versionCode})"
                            } catch (e: Exception) {
                                "Version Unknown"
                            }
                        }

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = versionText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppUpdateSettings(viewModel: AppUpdateViewModel) {
    val state by viewModel.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val currentVersion = remember(context) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                "Version ${packageInfo.versionName} (${packageInfo.longVersionCode})"
            } else {
                @Suppress("DEPRECATION")
                "Version ${packageInfo.versionName} (${packageInfo.versionCode})"
            }
        } catch (e: Exception) {
            "Version Unknown"
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(text = currentVersion, style = MaterialTheme.typography.bodyMedium)
        
        Spacer(modifier = Modifier.height(8.dp))

        when (state) {
            is AppUpdateState.Idle -> {
                Button(onClick = { viewModel.checkForUpdates() }) {
                    Text("Check for Updates")
                }
            }
            is AppUpdateState.Checking -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Checking for updates...")
                }
            }
            is AppUpdateState.UpdateAvailable -> {
                val info = (state as AppUpdateState.UpdateAvailable).versionInfo
                Column {
                    Text("Update available: ${info.versionName}", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("Changelog: ${info.changelog}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.startDownload(info) }) {
                        Text("Download & Install")
                    }
                }
            }
            is AppUpdateState.UpToDate -> {
                Text("You're on the latest version", color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { viewModel.checkForUpdates() }) {
                    Text("Check Again")
                }
            }
            is AppUpdateState.Downloading -> {
                val progress = (state as AppUpdateState.Downloading).progress
                Column {
                    Text("Downloading... ${(progress * 100).toInt()}%")
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            is AppUpdateState.Error -> {
                val message = (state as AppUpdateState.Error).message
                Column {
                    Text("Error: $message", color = MaterialTheme.colorScheme.error)
                    Button(onClick = { viewModel.checkForUpdates() }) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileItem(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun AppLockSettings(securityManager: SecurityManager, onNavigateToAppLockSetup: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isAppLockEnabled by remember(securityManager.isAppLockEnabled()) { mutableStateOf(securityManager.isAppLockEnabled()) }
    var isBiometricEnabled by remember(securityManager.isBiometricEnabled()) { mutableStateOf(securityManager.isBiometricEnabled()) }
    
    val biometricManager = remember { BiometricManager.from(context) }
    val canAuthenticate = remember {
        biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Enable App Lock")
            Switch(
                checked = isAppLockEnabled,
                onCheckedChange = { enabled ->
                    if (enabled && securityManager.getAppLockPin() == null) {
                        onNavigateToAppLockSetup()
                    } else if (!enabled) {
                        securityManager.clearAppLock()
                        isAppLockEnabled = false
                        isBiometricEnabled = false
                    } else {
                        securityManager.setAppLockEnabled(enabled)
                        isAppLockEnabled = enabled
                    }
                }
            )
        }

        if (isAppLockEnabled && securityManager.getAppLockPin() != null) {
            TextButton(onClick = onNavigateToAppLockSetup) {
                Text("Change PIN")
            }

            if (canAuthenticate) {
                val activity = context as FragmentActivity
                val executor = remember { ContextCompat.getMainExecutor(context) }
                val biometricPrompt = remember {
                    BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            securityManager.setBiometricEnabled(true)
                            isBiometricEnabled = true
                        }
                    })
                }

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Enable Biometric")
                    .setSubtitle("Confirm your identity to enable biometric unlock")
                    .setNegativeButtonText("Cancel")
                    .build()

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Biometric Unlock")
                    Switch(
                        checked = isBiometricEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                biometricPrompt.authenticate(promptInfo)
                            } else {
                                securityManager.setBiometricEnabled(false)
                                isBiometricEnabled = false
                            }
                        }
                    )
                }
            } else {
                Text(
                    text = "Biometric unlock is not available on this device due to restrictions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}