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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import lab.sabily.pulseid.ui.theme.PulseIDTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PulseIDTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BleAdvertisingScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun BleAdvertisingScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bleAdvertiser = remember { BleAdvertiser(context) }
    val status by bleAdvertiser.status.collectAsState()

    val bluetoothEnableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            bleAdvertiser.startAdvertising()
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
            bleAdvertiser.startAdvertising()
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "BLE Advertising Status:", style = MaterialTheme.typography.titleMedium)
        Text(text = status, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 24.dp))

        Button(
            onClick = {
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
                        bleAdvertiser.startAdvertising()
                    }
                } else {
                    launcher.launch(requiredPermissions)
                }
            },
            modifier = Modifier.fillMaxWidth(0.7f).padding(bottom = 8.dp)
        ) {
            Text("Start Advertising")
        }

        OutlinedButton(
            onClick = { bleAdvertiser.stopAdvertising() },
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Text("Stop Advertising")
        }
    }
}