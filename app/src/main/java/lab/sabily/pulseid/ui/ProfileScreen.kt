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
    onNavigateToAppLockSetup: () -> Unit
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
                        modifier = Modifier.fillMaxSize(),
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
fun ProfileItem(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun AppLockSettings(securityManager: SecurityManager, onNavigateToAppLockSetup: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isAppLockEnabled by remember { mutableStateOf(securityManager.isAppLockEnabled()) }
    var isBiometricEnabled by remember { mutableStateOf(securityManager.isBiometricEnabled()) }
    
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
                    } else {
                        securityManager.setAppLockEnabled(enabled)
                        isAppLockEnabled = enabled
                    }
                }
            )
        }

        if (securityManager.getAppLockPin() != null) {
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
            }
        }
    }
}