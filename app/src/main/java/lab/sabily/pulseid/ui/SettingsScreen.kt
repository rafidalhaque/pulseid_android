package lab.sabily.pulseid.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import lab.sabily.pulseid.data.SecurityManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    securityManager: SecurityManager,
    appUpdateViewModel: AppUpdateViewModel,
    onNavigateToAppLockSetup: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(text = "App Lock", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            AppLockSettings(securityManager, onNavigateToAppLockSetup)

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text(text = "App Updates", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            AppUpdateSettings(appUpdateViewModel)
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            val intervalOptions = listOf(
                "Immediately" to 0L,
                "15 seconds" to 15000L,
                "30 seconds" to 30000L,
                "1 minute" to 60000L,
                "5 minutes" to 300000L
            )

            var expanded by remember { mutableStateOf(false) }
            val currentInterval = securityManager.getAppLockInterval()
            val selectedOption = intervalOptions.find { it.second == currentInterval }?.first ?: "30 seconds"

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Auto Lock Interval")
                Box {
                    TextButton(onClick = { expanded = true }) {
                        Text(selectedOption)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        intervalOptions.forEach { (label, value) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    securityManager.saveAppLockInterval(value)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
