package lab.sabily.pulseid

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import lab.sabily.pulseid.data.BleKeyManager
import lab.sabily.pulseid.data.NetworkModule
import lab.sabily.pulseid.data.SecurityManager
import lab.sabily.pulseid.ui.LoginScreen
import lab.sabily.pulseid.ui.ProfileScreen
import lab.sabily.pulseid.ui.theme.PulseIDTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val securityManager = SecurityManager(this)
        val networkModule = NetworkModule(securityManager)
        val bleKeyManager = BleKeyManager(networkModule.api, securityManager)

        setContent {
            PulseIDTheme {
                val isAuthenticated by securityManager.isAuthenticated.collectAsState()
                var selectedTab by remember { mutableIntStateOf(0) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (isAuthenticated) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                    label = { Text("Home") }
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                                    label = { Text("Profile") }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    if (!isAuthenticated) {
                        LoginScreen(
                            securityManager = securityManager,
                            networkModule = networkModule,
                            onLoginSuccess = { /* Flow will update automatically */ }
                        )
                    } else {
                        Box(modifier = Modifier.padding(innerPadding)) {
                            when (selectedTab) {
                                0 -> BleAdvertisingScreen(
                                    bleKeyManager = bleKeyManager
                                )
                                1 -> ProfileScreen(
                                    securityManager = securityManager,
                                    onLogout = {
                                        securityManager.clearToken()
                                        bleKeyManager.clearSecret()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BleAdvertisingScreen(
    bleKeyManager: BleKeyManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bleAdvertiser = remember { BleAdvertiser(context) }
    val status by bleAdvertiser.status.collectAsState()
    var currentUuid by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val bluetoothEnableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            scope.launch {
                currentUuid = bleKeyManager.getDailyUuid()
                currentUuid?.let { bleAdvertiser.startAdvertising(it) }
            }
        }
    }

    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            scope.launch {
                currentUuid = bleKeyManager.getDailyUuid()
                currentUuid?.let { bleAdvertiser.startAdvertising(it) }
            }
        }
    }

    val isAdvertising = status == "Advertising" || status == "Starting..."

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "BLE Advertising Status:", style = MaterialTheme.typography.titleMedium)
        Text(text = status, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 24.dp))

        Button(
            onClick = {
                if (isAdvertising) {
                    bleAdvertiser.stopAdvertising()
                } else {
                    val hasPermissions = requiredPermissions.all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }
                    if (hasPermissions) {
                        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                        val bluetoothAdapter = bluetoothManager.adapter
                        if (bluetoothAdapter?.isEnabled == false) {
                            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            bluetoothEnableLauncher.launch(enableBtIntent)
                        } else {
                            scope.launch {
                                currentUuid = bleKeyManager.getDailyUuid()
                                if (currentUuid != null) {
                                    bleAdvertiser.startAdvertising(currentUuid!!)
                                }
                            }
                        }
                    } else {
                        launcher.launch(requiredPermissions)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(0.7f).padding(bottom = 32.dp)
        ) {
            Text(if (isAdvertising) "Stop Advertising" else "Start Advertising")
        }
    }
}