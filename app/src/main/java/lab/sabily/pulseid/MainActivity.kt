package lab.sabily.pulseid

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import lab.sabily.pulseid.data.*
import lab.sabily.pulseid.ui.*
import lab.sabily.pulseid.ui.theme.PulseIDTheme
import lab.sabily.pulseid.ui.onboarding.WelcomeScreen
import lab.sabily.pulseid.ui.onboarding.ServerSetupScreen
import lab.sabily.pulseid.ui.onboarding.OnboardingLoginScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModelProvider
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.firstOrNull
import lab.sabily.pulseid.worker.NtfyForegroundService

class MainActivity : FragmentActivity() {
    private lateinit var appLockViewModel: AppLockViewModel
    private lateinit var appUpdateViewModel: AppUpdateViewModel
    private var deepLinkRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val securityManager = SecurityManager(this)
        val settingsManager = SettingsManager(this)
        val networkModule = NetworkModule(securityManager)
        val bleKeyManager = BleKeyManager(networkModule.api, securityManager, settingsManager)

        handleDeepLink(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        appLockViewModel = ViewModelProvider(this, AppLockViewModel.Factory(securityManager))[AppLockViewModel::class.java]
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLockViewModel)

        appUpdateViewModel = ViewModelProvider(this, AppUpdateViewModel.Factory(application, networkModule.updateApi))[AppUpdateViewModel::class.java]

        if (securityManager.getToken() != null) {
            lifecycleScope.launch {
                val topic = settingsManager.ntfyTopic.firstOrNull()
                val token = settingsManager.ntfyToken.firstOrNull()
                if (topic != null && token != null) {
                    android.util.Log.d("MainActivity", "Starting NtfyForegroundService on app start for topic: $topic")
                    NtfyForegroundService.start(this@MainActivity)
                } else {
                    android.util.Log.w("MainActivity", "Ntfy credentials missing on app start")
                }
            }
        }

        setContent {
            PulseIDTheme {
                val officeTz by settingsManager.officeTz.collectAsState(initial = SettingsManager.DEFAULT_TZ)
                CompositionLocalProvider(LocalOfficeTz provides officeTz) {
                    val navController = rememberNavController()
                    val route by deepLinkRoute

                    val startDestination = remember {
                        if (securityManager.getToken() != null && securityManager.getBaseUrl() != null) {
                            "main"
                        } else if (securityManager.getBaseUrl() != null) {
                            "onboarding/login"
                        } else {
                            "onboarding/welcome"
                        }
                    }

                    NavHost(navController = navController, startDestination = startDestination) {
                        composable("onboarding/welcome") {
                            WelcomeScreen(onGetStarted = { navController.navigate("onboarding/server") })
                        }
                        composable("onboarding/server") {
                            ServerSetupScreen(
                                securityManager = securityManager,
                                networkModule = networkModule,
                                onServerConnected = { navController.navigate("onboarding/login") }
                            )
                        }
                        composable("onboarding/login") {
                            OnboardingLoginScreen(
                                securityManager = securityManager,
                                settingsManager = settingsManager,
                                networkModule = networkModule,
                                onLoginSuccess = {
                                    navController.navigate("main") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("main") {
                            MainAppContent(
                                securityManager = securityManager,
                                settingsManager = settingsManager,
                                networkModule = networkModule,
                                bleKeyManager = bleKeyManager,
                                appLockViewModel = appLockViewModel,
                                appUpdateViewModel = appUpdateViewModel,
                                deepLinkRoute = route,
                                onDeepLinkHandled = { deepLinkRoute.value = null },
                                onLogout = {
                                    NtfyForegroundService.stop(this@MainActivity)
                                    val destination = if (securityManager.getBaseUrl() != null) "onboarding/login" else "onboarding/welcome"
                                    navController.navigate(destination) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        intent?.data?.let { uri ->
            if (uri.scheme == "pulseid") {
                deepLinkRoute.value = uri.toString()
            }
        }
    }
}

@Composable
fun MainAppContent(
    securityManager: SecurityManager,
    settingsManager: SettingsManager,
    networkModule: NetworkModule,
    bleKeyManager: BleKeyManager,
    appLockViewModel: AppLockViewModel,
    appUpdateViewModel: AppUpdateViewModel,
    deepLinkRoute: String?,
    onDeepLinkHandled: () -> Unit,
    onLogout: () -> Unit
) {
    val isAuthenticated = securityManager.getToken() != null
    if (!isAuthenticated) return

    val scope = rememberCoroutineScope()
    val updateState by appUpdateViewModel.state.collectAsState()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    var isAdmin by remember { mutableStateOf(false) }
    var isSettingUpPin by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(deepLinkRoute, isAdmin) {
        if (deepLinkRoute != null) {
            when (deepLinkRoute) {
                "pulseid://leaves/pending" -> {
                    if (isAdmin) {
                        navController.navigate(Screen.AdminScreenWrapper.createRoute(AdminSubScreen.LEAVE_REQUESTS.name)) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                        onDeepLinkHandled()
                    }
                }
                "pulseid://leaves/my" -> {
                    navController.navigate(Screen.Requests.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                    onDeepLinkHandled()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        appUpdateViewModel.checkForUpdates(silent = true)
    }

    LaunchedEffect(updateState) {
        if (updateState is AppUpdateState.UpdateAvailable) {
            showUpdateDialog = true
        }
    }

    if (showUpdateDialog && updateState is AppUpdateState.UpdateAvailable) {
        val info = (updateState as AppUpdateState.UpdateAvailable).versionInfo
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("Update Available") },
            text = {
                Column {
                    Text("A new version of PulseID is available: ${info.versionName}")
                    Spacer(modifier = Modifier.height(8.dp))
                }
            },
            confirmButton = {
                Button(onClick = {
                    appUpdateViewModel.startDownload(info)
                    showUpdateDialog = false
                }) {
                    Text("Update Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("Later")
                }
            }
        )
    }

    BackHandler(enabled = (isAuthenticated && currentDestination?.route != Screen.Home.route) || isSettingUpPin) {
        if (isSettingUpPin) {
            isSettingUpPin = false
        } else {
            navController.popBackStack()
        }
    }

    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) {
            try {
                val response = networkModule.api.getProfile()
                isAdmin = response.employee.isAdmin
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to fetch profile for admin check", e)
            }
        } else {
            isAdmin = false
            isSettingUpPin = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (isAuthenticated) {
                    NavigationBar {
                        Screen.bottomNavItems.forEach { screen ->
                            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true ||
                                (screen == Screen.Profile && (
                                    currentDestination?.route == Screen.AdminPanel.route ||
                                    currentDestination?.route == Screen.Settings.route ||
                                    currentDestination?.route?.startsWith("admin_screen/") == true
                                ))
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    val icon = if (selected) screen.icon else screen.outlinedIcon
                                    if (icon != null) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = screen.label
                                        )
                                    }
                                },
                                label = { screen.label?.let { Text(it, style = MaterialTheme.typography.labelLarge) } }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                if (isSettingUpPin) {
                    AppLockSetupScreen(
                        securityManager = securityManager,
                        onBack = { isSettingUpPin = false }
                    )
                } else {
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route
                    ) {
                        composable(Screen.Home.route) {
                            BleAdvertisingScreen(
                                bleKeyManager = bleKeyManager,
                                authApi = networkModule.api,
                                isAdmin = isAdmin,
                                appLockViewModel = appLockViewModel,
                                securityManager = securityManager
                            )
                        }
                        composable(Screen.Activity.route) {
                            AttendanceHistoryScreen(
                                authApi = networkModule.api,
                                isAdmin = isAdmin
                            )
                        }
                        composable(Screen.Requests.route) {
                            LeaveScreen(authApi = networkModule.api)
                        }
                        composable(Screen.Profile.route) {
                            ProfileScreen(
                                authApi = networkModule.api,
                                isAdmin = isAdmin,
                                onLogout = onLogout,
                                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                                onNavigateToNotifications = { /* navController.navigate(Screen.Notifications.route) */ },
                                onNavigateToAdminPanel = { navController.navigate(Screen.AdminPanel.route) }
                            )
                        }
                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                securityManager = securityManager,
                                appUpdateViewModel = appUpdateViewModel,
                                onNavigateToAppLockSetup = { isSettingUpPin = true },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.AdminPanel.route) {
                            AdminPanelScreen(
                                onNavigateToAdminSubScreen = { subScreen ->
                                    navController.navigate(Screen.AdminScreenWrapper.createRoute(subScreen.name))
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.AdminScreenWrapper.route) { backStackEntry ->
                            val subScreenName = backStackEntry.arguments?.getString("initialSubScreen")
                            val initialSubScreen = subScreenName?.let { AdminSubScreen.valueOf(it) }
                            AdminScreen(
                                authApi = networkModule.api,
                                securityManager = securityManager,
                                initialSubScreen = initialSubScreen,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleAdvertisingScreen(
    bleKeyManager: BleKeyManager,
    authApi: AuthApi,
    isAdmin: Boolean,
    appLockViewModel: AppLockViewModel,
    securityManager: SecurityManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bleAdvertiser = remember { BleAdvertiser(context) }
    val status by bleAdvertiser.status.collectAsState()
    var currentUuid by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var pollingJob by remember { mutableStateOf<Job?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    var presentEmployees by remember { mutableStateOf<List<AttendanceRecord>>(emptyList()) }
    var leftEmployees by remember { mutableStateOf<List<AttendanceRecord>>(emptyList()) }
    var hiddenEmployeeIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoadingData by remember { mutableStateOf(false) }

    val refreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }
    val officeTz = LocalOfficeTz.current

    val fetchAttendanceData = suspend {
        isRefreshing = true
        try {
            coroutineScope {
                val presentDeferred = async { authApi.getPresentEmployees() }
                val leftDeferred = async { authApi.getLeftEmployees() }
                
                try {
                    presentEmployees = presentDeferred.await().filter { it.employeeId !in hiddenEmployeeIds }
                    leftEmployees = leftDeferred.await().filter { it.employeeId !in hiddenEmployeeIds }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Failed to fetch attendance data", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to fetch attendance data", e)
        } finally {
            isRefreshing = false
            isLoadingData = false
        }
    }

    LaunchedEffect(Unit) {
        isLoadingData = true
        // Fetch hidden employee IDs once (position == "0")
        try {
            val response = authApi.getAllMembers(all = true)
            if (response.success) {
                hiddenEmployeeIds = response.employee
                    .filter { it.position == "0" }
                    .map { it.id }
                    .toSet()
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to fetch members for filtering", e)
        }

        while (true) {
            fetchAttendanceData()
            delay(5000)
        }
    }

    LaunchedEffect(status) {
        if (status == "Started") {
            if (pollingJob == null || pollingJob?.isActive == false) {
                pollingJob = launch {
                    val startTime = System.currentTimeMillis()
                    val timeZone = TimeZone.getTimeZone(officeTz)
                    while (System.currentTimeMillis() - startTime < 30000) {
                        try {
                            val present = authApi.getPresentEmployees()
                            val now = Calendar.getInstance(timeZone).time
                            val matched = present.any { record ->
                                val entryDate = DateUtils.parseFlexibleTime(record.entryTime, officeTz)
                                entryDate != null && (now.time - entryDate.time) in 0..60000
                            }
                            if (matched) {
                                bleAdvertiser.stopAdvertising()
                                snackbarHostState.showSnackbar("Access Granted ✓")
                                break
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Polling error", e)
                        }
                        delay(3000)
                    }
                    pollingJob = null
                }
            }
        } else {
            pollingJob?.cancel()
            pollingJob = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("PulseID") },
                actions = {
                    if (securityManager.isAppLockEnabled()) {
                        val isLocked by appLockViewModel.isLocked.collectAsState()
                        IconButton(onClick = { appLockViewModel.lock() }) {
                            Icon(
                                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = if (isLocked) "Lock" else "Unlock"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    fetchAttendanceData()
                }
            },
            state = refreshState,
            modifier = modifier.fillMaxSize().padding(padding)
        ) {
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

            val isAdvertising = status == "Started" || status == "Starting..."

            val statusColor = when {
                status == "Started" -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                status == "Starting..." -> androidx.compose.ui.graphics.Color(0xFFFFA000)
                status.startsWith("Error") -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            val buttonColors = if (isAdvertising) {
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            } else {
                ButtonDefaults.buttonColors()
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Status:", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyLarge,
                    color = statusColor,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

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
                    colors = buttonColors,
                    modifier = Modifier.fillMaxWidth(0.7f).padding(bottom = 32.dp)
                ) {
                    Text(if (isAdvertising) "Stop" else "Start")
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isAdmin) {
                    CurrentlyInOfficeCard(
                        employees = presentEmployees,
                        isLoading = isLoadingData
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    LeftTodayCard(
                        employees = leftEmployees,
                        isLoading = isLoadingData
                    )
                }
            }
        }
    }
}

@Composable
fun CurrentlyInOfficeCard(
    employees: List<AttendanceRecord>,
    isLoading: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val officeTz = LocalOfficeTz.current
    val timeZone = remember(officeTz) { TimeZone.getTimeZone(officeTz) }
    val timeFormat = remember(timeZone) { 
        SimpleDateFormat("hh:mm a", Locale.getDefault()).apply { this.timeZone = timeZone }
    }
    val isoFormat = remember(timeZone) { 
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply { this.timeZone = timeZone }
    }

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
                        val formattedTime = record.entryTime?.let { entryTime ->
                            try {
                                val date = isoFormat.parse(entryTime)
                                date?.let { "since ${timeFormat.format(it)}" } ?: entryTime
                            } catch (e: Exception) {
                                entryTime
                            }
                        } ?: "--:--"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = record.employeeName ?: "Unknown",
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
@Composable
fun LeftTodayCard(
    employees: List<AttendanceRecord>,
    isLoading: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val officeTz = LocalOfficeTz.current
    val timeZone = remember(officeTz) { TimeZone.getTimeZone(officeTz) }
    val timeFormat = remember(timeZone) { 
        SimpleDateFormat("hh:mm a", Locale.getDefault()).apply { this.timeZone = timeZone }
    }
    val isoFormat = remember(timeZone) { 
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply { this.timeZone = timeZone }
    }

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
                        text = "Left Today",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isLoading) "Loading..." else "${employees.size} employees left",
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
                        val formattedTime = record.exitTime?.let { exitTime ->
                            try {
                                val date = isoFormat.parse(exitTime)
                                date?.let { "left at ${timeFormat.format(it)}" } ?: exitTime
                            } catch (e: Exception) {
                                exitTime
                            }
                        } ?: "--:--"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = record.employeeName ?: "Unknown",
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
                    text = "No employees left yet today",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}
