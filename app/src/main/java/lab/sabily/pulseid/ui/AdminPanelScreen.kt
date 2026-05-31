package lab.sabily.pulseid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    onNavigateToAdminSubScreen: (AdminSubScreen) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Panel") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                AdminMenuRow(
                    icon = Icons.Default.Groups,
                    label = "Members",
                    onClick = { onNavigateToAdminSubScreen(AdminSubScreen.ALL_EMPLOYEES) }
                )
            }
            item {
                AdminMenuRow(
                    icon = Icons.Default.Insights,
                    label = "Attendance Report",
                    onClick = { onNavigateToAdminSubScreen(AdminSubScreen.REPORTS) }
                )
            }
            item {
                AdminMenuRow(
                    icon = Icons.Default.CalendarToday,
                    label = "Holidays",
                    onClick = { onNavigateToAdminSubScreen(AdminSubScreen.HOLIDAYS) }
                )
            }
            item {
                AdminMenuRow(
                    icon = Icons.Default.Gavel,
                    label = "Work Policy",
                    onClick = { onNavigateToAdminSubScreen(AdminSubScreen.WORK_POLICY) }
                )
            }
            item {
                AdminMenuRow(
                    icon = Icons.Default.EventNote,
                    label = "Leave Management",
                    onClick = { onNavigateToAdminSubScreen(AdminSubScreen.LEAVE_REQUESTS) }
                )
            }
            item {
                AdminMenuRow(
                    icon = Icons.Default.Devices,
                    label = "ESP32 Management",
                    onClick = { onNavigateToAdminSubScreen(AdminSubScreen.ESP32_MANAGEMENT) }
                )
            }
        }
    }
}

@Composable
fun AdminMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
