package lab.sabily.pulseid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import lab.sabily.pulseid.data.AuthApi

@Composable
fun AdminScreen(authApi: AuthApi) {
    var showImport by remember { mutableStateOf(false) }

    if (showImport) {
        Column(modifier = Modifier.fillMaxSize()) {
            IconButton(
                onClick = { showImport = false },
                modifier = Modifier.padding(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            ImportEmployeeScreen(authApi = authApi)
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { showImport = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import Employee")
            }
        }
    }
}
