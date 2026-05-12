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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import lab.sabily.pulseid.data.AttendanceRecord
import lab.sabily.pulseid.data.AuthApi
import lab.sabily.pulseid.data.BleKeyManager
import lab.sabily.pulseid.data.NetworkModule
import lab.sabily.pulseid.data.SecurityManager
import lab.sabily.pulseid.ui.AttendanceHistoryScreen
import lab.sabily.pulseid.ui.LoginScreen
import lab.sabily.pulseid.ui.ProfileScreen
import lab.sabily.pulseid.ui.theme.PulseIDTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

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
                                    icon = { Icon(Icons.Default.DateRange, contentDescription = "History") },
                                    label = { Text("History") }
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 },
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
                                    bleKeyManager = bleKeyManager,
                                    authApi = networkModule.api
                                )
                                1 -> AttendanceHistoryScreen(
                                    authApi = networkModule.api
                                )
                                2 -> ProfileScreen(
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
    authApi: AuthApi,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bleAdvertiser = remember { BleAdvertiser(context) }
    val status by bleAdvertiser.status.collectAsState()
    var currentUuid by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    var presentEmployees by remember { mutableStateOf<List<AttendanceRecord>>(emptyList()) }
    var isLoadingPresent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoadingPresent = true
        for (i in 1..12) {
            try {
                presentEmployees = authApi.getPresentEmployees()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to fetch present employees", e)
            }
            delay(1000)
        }
        isLoadingPresent = false
    }

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
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter
            if (bluetoothAdapter?.isEnabled == false) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                bluetoothEnableLauncher.launch(enableBtIntent)
            } else {
                scope.launch {
                    currentUuid = bleKeyManager.getDailyUuid()
                    currentUuid?.let { bleAdvertiser.startAdvertising(it) }
                }
            }
        }
    }

    val isAdvertising = status == "Advertising" || status == "Starting..."

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

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

        Spacer(modifier = Modifier.height(16.dp))

        CurrentlyInOfficeCard(
            employees = presentEmployees,
            isLoading = isLoadingPresent
        )
    }
}

@Composable
fun CurrentlyInOfficeCard(
    employees: List<AttendanceRecord>,
    isLoading: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val isoFormat = remember { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Currently In Office",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isLoading) "Loading..." else "${employees.size} employees present",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }

            if (expanded && employees.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(employees) { record ->
                        val formattedTime = try {
                            val date = isoFormat.parse(record.entry_time)
                            date?.let { timeFormat.format(it) } ?: record.entry_time
                        } catch (e: Exception) {
                            record.entry_time
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = record.employee_name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formattedTime,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            } else if (expanded && !isLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No employees currently in office",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}