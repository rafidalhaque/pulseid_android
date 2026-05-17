package lab.sabily.pulseid.ui

import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import lab.sabily.pulseid.data.SecurityManager

@Composable
fun AppLockScreen(
    securityManager: SecurityManager,
    appLockViewModel: AppLockViewModel
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }

    val biometricPrompt = remember(activity) {
        BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                appLockViewModel.unlock()
            }
        })
    }

    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("App Lock")
            .setSubtitle("Authenticate to unlock PulseID")
            .setNegativeButtonText("Use PIN")
            .build()
    }

    LaunchedEffect(Unit) {
        if (securityManager.isBiometricEnabled()) {
            biometricPrompt.authenticate(promptInfo)
        }
    }

    BackHandler {
        // Intercept back button to prevent bypassing lock
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "PulseID Locked",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            PinIndicator(pin.length)

            if (error != null) {
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            NumericKeypad(
                onNumberClick = { num ->
                    if (pin.length < 6) pin += num
                    if (pin.length in 4..6) {
                        if (securityManager.verifyPin(pin)) {
                            appLockViewModel.unlock()
                        } else if (pin.length == 6) {
                            error = "Incorrect PIN"
                            pin = ""
                        }
                    }
                    if (pin.length < 4) error = null
                },
                onBackspaceClick = {
                    if (pin.isNotEmpty()) pin = pin.dropLast(1)
                    error = null
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (securityManager.isBiometricEnabled()) {
                IconButton(
                    onClick = { biometricPrompt.authenticate(promptInfo) },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        Icons.Default.Fingerprint,
                        contentDescription = "Biometric",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // Manual "Unlock" button if they entered 4-5 digits and it's correct but didn't auto-unlock
            if (pin.length in 4..6) {
                Button(
                    onClick = {
                        if (securityManager.verifyPin(pin)) {
                            appLockViewModel.unlock()
                        } else {
                            error = "Incorrect PIN"
                            pin = ""
                        }
                    },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Unlock")
                }
            }
        }
    }
}