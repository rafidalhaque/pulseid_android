package lab.sabily.pulseid.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import lab.sabily.pulseid.R
import lab.sabily.pulseid.data.LoginRequest
import lab.sabily.pulseid.data.NetworkModule
import lab.sabily.pulseid.data.SecurityManager
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    securityManager: SecurityManager,
    networkModule: NetworkModule,
    onLoginSuccess: () -> Unit
) {
    var uuid by remember { mutableStateOf(securityManager.getEmployeeUuid() ?: "") }
    val isFromKeystore = remember(uuid) { securityManager.getEmployeeUuid() != null && uuid == securityManager.getEmployeeUuid() }
    
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.mipmap.pulseid_foreground),
            contentDescription = "PulseID Logo",
            modifier = Modifier.size(120.dp)
        )
        Text("Login with Your Credentials", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = uuid,
            onValueChange = { if (!isFromKeystore) uuid = it },
            label = { Text("Employee UUID") },
            readOnly = isFromKeystore,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                val description = if (passwordVisible) "Hide password" else "Show password"

                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = description)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (errorMessage != null) {
            Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = {
                isLoading = true
                errorMessage = null
                scope.launch {
                    try {
                        val response = networkModule.api.login(LoginRequest(uuid, password))
                        if (response.success) {
                            securityManager.saveToken(response.token)
                            securityManager.saveEmployeeUuid(uuid)
                            onLoginSuccess()
                        } else {
                            errorMessage = "Login failed"
                        }
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "An error occurred"
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading && uuid.isNotEmpty() && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("Login")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = {
                securityManager.clearAllData()
                uuid = ""
                password = ""
                errorMessage = null
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Clear Data", color = MaterialTheme.colorScheme.error)
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        val context = androidx.compose.ui.platform.LocalContext.current
        val versionText = remember(context) {
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                "Version ${packageInfo.versionName} (${packageInfo.versionCode})"
            } catch (e: Exception) {
                "Version Unknown"
            }
        }
        
        Text(
            text = versionText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}