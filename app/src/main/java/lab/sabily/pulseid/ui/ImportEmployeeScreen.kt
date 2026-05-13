package lab.sabily.pulseid.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lab.sabily.pulseid.data.AuthApi
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader

data class CSVEmployee(
    val name: String,
    val email: String,
    val idNumber: String,
    val joinDate: String,
    val designation: String,
    val position: String
)

@Composable
fun ImportEmployeeScreen(authApi: AuthApi) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var previewEmployees by remember { mutableStateOf<List<CSVEmployee>>(emptyList()) }
    var isUploading by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        selectedUri = uri
        uri?.let {
            scope.launch {
                previewEmployees = parseCSV(it, context)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Import Employees",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Button(
            onClick = { 
                filePickerLauncher.launch(
                    arrayOf(
                        "text/csv", 
                        "text/comma-separated-values", 
                        "application/csv", 
                        "application/vnd.ms-excel",
                        "text/plain",
                        "*/*"
                    )
                ) 
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FileUpload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Select CSV File")
        }

        Spacer(Modifier.height(16.dp))

        if (previewEmployees.isNotEmpty()) {
            Text(
                text = "Preview (${previewEmployees.size} employees)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            HeaderCell("Name", Modifier.weight(1f))
                            HeaderCell("Email", Modifier.weight(1.5f))
                            HeaderCell("ID No", Modifier.weight(1f))
                        }
                    }
                    items(previewEmployees) { emp ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(emp.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(emp.email, Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall)
                            Text(emp.idNumber, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    selectedUri?.let { uri ->
                        scope.launch {
                            isUploading = true
                            try {
                                val result = uploadCSV(uri, context, authApi)
                                val message = result.message ?: if (result.success) "Employees imported successfully" else "Import failed"
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                if (result.success) {
                                    selectedUri = null
                                    previewEmployees = emptyList()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isUploading = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isUploading
            ) {
                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Upload and Import")
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("No file selected or empty CSV", color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
fun HeaderCell(text: String, modifier: Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold
    )
}

suspend fun parseCSV(uri: Uri, context: android.content.Context): List<CSVEmployee> = withContext(Dispatchers.IO) {
    val employees = mutableListOf<CSVEmployee>()
    try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                // Skip header if it exists (assuming it starts with "name")
                val firstLine = reader.readLine()
                if (firstLine != null && !firstLine.startsWith("name", ignoreCase = true)) {
                    // Process first line as data if it's not a header
                    processLine(firstLine)?.let { employees.add(it) }
                }
                
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    processLine(line!!)?.let { employees.add(it) }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    employees
}

private fun processLine(line: String): CSVEmployee? {
    val parts = line.split(",")
    if (parts.size >= 7) {
        return CSVEmployee(
            name = parts[0].trim(),
            email = parts[1].trim(),
            idNumber = parts[2].trim(),
            joinDate = parts[3].trim(),
            designation = parts[4].trim(),
            position = parts[6].trim()
        )
    }
    return null
}

suspend fun uploadCSV(uri: Uri, context: android.content.Context, authApi: AuthApi) = withContext(Dispatchers.IO) {
    val inputStream = context.contentResolver.openInputStream(uri)
    val bytes = inputStream?.readBytes() ?: throw Exception("Could not read file")
    
    val requestBody = bytes.toRequestBody("text/csv".toMediaTypeOrNull())
    val body = MultipartBody.Part.createFormData("file", "employees.csv", requestBody)
    
    authApi.importEmployees(body)
}