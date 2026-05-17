package lab.sabily.pulseid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lab.sabily.pulseid.data.SecurityManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockSetupScreen(
    securityManager: SecurityManager,
    onBack: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) } // 1: Enter, 2: Confirm
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setup PIN") },
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (step == 1) "Enter a 4-6 digit PIN" else "Confirm your PIN",
                style = MaterialTheme.typography.headlineSmall
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            PinIndicator(if (step == 1) pin.length else confirmPin.length)

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
                    if (step == 1) {
                        if (pin.length < 6) pin += num
                        if (pin.length >= 4 && step == 1 && pin.length == 6) {
                            // Auto proceed if 6 digits? Maybe better to have a check button or just wait for 4-6
                        }
                    } else {
                        if (confirmPin.length < 6) confirmPin += num
                    }
                    error = null
                },
                onBackspaceClick = {
                    if (step == 1) {
                        if (pin.isNotEmpty()) pin = pin.dropLast(1)
                    } else {
                        if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (step == 1) {
                        if (pin.length in 4..6) {
                            step = 2
                        } else {
                            error = "PIN must be 4-6 digits"
                        }
                    } else {
                        if (pin == confirmPin) {
                            securityManager.saveAppLockPin(pin)
                            securityManager.setAppLockEnabled(true)
                            onBack()
                        } else {
                            error = "PINs do not match"
                            confirmPin = ""
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(0.7f),
                enabled = if (step == 1) pin.length in 4..6 else confirmPin.length in 4..6
            ) {
                Text(if (step == 1) "Continue" else "Save PIN")
            }
        }
    }
}