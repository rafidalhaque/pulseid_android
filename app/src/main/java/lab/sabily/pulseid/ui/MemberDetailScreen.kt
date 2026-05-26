package lab.sabily.pulseid.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import lab.sabily.pulseid.data.AuthApi
import lab.sabily.pulseid.data.Employee
import lab.sabily.pulseid.data.AttendanceRecord
import lab.sabily.pulseid.data.DateUtils
import lab.sabily.pulseid.data.QrUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberDetailScreen(
    authApi: AuthApi,
    employeeId: String,
    onEditClick: (Employee) -> Unit
) {
    var employee by remember { mutableStateOf<Employee?>(null) }
    var history by remember { mutableStateOf<List<AttendanceRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val refreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val officeTz = LocalOfficeTz.current
    val timeZone = remember(officeTz) { TimeZone.getTimeZone(officeTz) }
    val dateFormat = remember(timeZone) { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { this.timeZone = timeZone } }
    val displayFormat = remember(timeZone) { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).apply { this.timeZone = timeZone } }
    val isoFormat = remember(timeZone) { 
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply { this.timeZone = timeZone }
    }
    val timeFormat = remember(timeZone) { 
        SimpleDateFormat("hh:mm a", Locale.getDefault()).apply { this.timeZone = timeZone }
    }

    val fetchDetail = suspend {
        isRefreshing = true
        try {
            val response = authApi.getMemberDetail(employeeId)
            
            if (response.success) {
                employee = response.employee
                history = response.history.sortedByDescending { DateUtils.parseFlexibleDate(it.date, officeTz) ?: Date(0) }
            } else {
                error = "Failed to fetch details"
            }
        } catch (e: Exception) {
            error = e.message ?: "An unknown error occurred"
        } finally {
            isRefreshing = false
            isLoading = false
        }
    }

    LaunchedEffect(employeeId) {
        fetchDetail()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    fetchDetail()
                }
            },
            state = refreshState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            if (isLoading && !isRefreshing) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (error != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = error!!, color = MaterialTheme.colorScheme.error)
                }
            } else if (employee != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    item {
                        MemberProfileHeader(
                            employee = employee!!,
                            onIdCopied = {
                                scope.launch {
                                    snackbarHostState.showSnackbar("ID copied to clipboard")
                                }
                            }
                        )
                    }

                    item {
                        Button(
                            onClick = { onEditClick(employee!!) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Text("Edit Employee")
                        }
                    }

                    item {
                        Text(
                            text = "Attendance History",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    if (history.isEmpty()) {
                        item {
                            Text(
                                text = "No attendance history found",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    } else {
                        items(history) { record ->
                            AttendanceHistoryItem(
                                record = record,
                                isoFormat = isoFormat,
                                timeFormat = timeFormat,
                                displayFormat = displayFormat,
                                dateFormat = dateFormat
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MemberProfileHeader(employee: Employee, onIdCopied: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    var showQrDialog by remember { mutableStateOf(false) }
    
    if (showQrDialog) {
        QrCodeDialog(
            text = employee.id,
            onDismiss = { showQrDialog = false }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = employee.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = employee.designation,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            ProfileInfoRow(
                label = "ID", 
                value = employee.id,
                onCopyClick = {
                    clipboardManager.setText(AnnotatedString(employee.id))
                    onIdCopied()
                },
                onQrClick = { showQrDialog = true }
            )
            ProfileInfoRow(label = "Email", value = employee.email)
            ProfileInfoRow(label = "ID Number", value = employee.idNumber)
            ProfileInfoRow(label = "Join Date", value = employee.joinDate ?: "N/A")
            ProfileInfoRow(label = "Position", value = employee.position)
        }
    }
}

@Composable
fun QrCodeDialog(text: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Employee ID QR",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                val qrBitmap = remember(text) { QrUtils.generateQrCodeBitmap(text, 512) }
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Employee ID QR Code",
                        modifier = Modifier.size(250.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier.size(250.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Failed to generate QR")
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun ProfileInfoRow(
    label: String, 
    value: String, 
    onCopyClick: (() -> Unit)? = null,
    onQrClick: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            
            if (onQrClick != null) {
                IconButton(
                    onClick = onQrClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = "View QR Code",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (onCopyClick != null) {
                IconButton(
                    onClick = onCopyClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy $label",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}
