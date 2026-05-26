package lab.sabily.pulseid.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.FontFactory
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lab.sabily.pulseid.data.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceSheetScreen(authApi: AuthApi, onBack: () -> Unit) {
    var employees by remember { mutableStateOf<List<Employee>>(emptyList()) }
    var selectedEmployee by remember { mutableStateOf<Employee?>(null) }
    var selectedMonth by remember { mutableStateOf(Calendar.getInstance()) }
    
    var isLoading by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var attendanceSheetData by remember { mutableStateOf<AttendanceSheetData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val officeTz = LocalOfficeTz.current

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val response = authApi.getAllMembers(all = true)
            if (response.success) {
                employees = response.employee.filter { it.position != "0" }
            } else {
                error = "Failed to fetch employees"
            }
        } catch (e: Exception) {
            error = e.message
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Monthly Attendance Sheet") },
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
                .padding(16.dp)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (attendanceSheetData == null) {
                Step1Selection(
                    employees = employees,
                    selectedEmployee = selectedEmployee,
                    onEmployeeSelected = { selectedEmployee = it },
                    selectedMonth = selectedMonth,
                    onMonthSelected = { selectedMonth = it },
                    onGenerate = {
                        scope.launch {
                            generateSheet(
                                authApi,
                                selectedEmployee!!,
                                selectedMonth,
                                officeTz,
                                onLoading = { isGenerating = it },
                                onDataGenerated = { attendanceSheetData = it },
                                onError = { error = it }
                            )
                        }
                    },
                    isGenerating = isGenerating
                )
            } else {
                Step2Display(
                    data = attendanceSheetData!!,
                    onBackToSelection = { attendanceSheetData = null },
                    onExportPdf = {
                        scope.launch {
                            exportToPdf(context, attendanceSheetData!!, officeTz)
                        }
                    },
                    onExportCsv = {
                        scope.launch {
                            exportToCsv(context, attendanceSheetData!!, officeTz)
                        }
                    }
                )
            }

            if (error != null) {
                Text(text = error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step1Selection(
    employees: List<Employee>,
    selectedEmployee: Employee?,
    onEmployeeSelected: (Employee) -> Unit,
    selectedMonth: Calendar,
    onMonthSelected: (Calendar) -> Unit,
    onGenerate: () -> Unit,
    isGenerating: Boolean
) {
    var employeeExpanded by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Employee Dropdown
        ExposedDropdownMenuBox(
            expanded = employeeExpanded,
            onExpandedChange = { employeeExpanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedEmployee?.name ?: "Select Employee",
                onValueChange = {},
                label = { Text("Employee") },
                readOnly = true,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = employeeExpanded)
                },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )
            ExposedDropdownMenu(
                expanded = employeeExpanded,
                onDismissRequest = { employeeExpanded = false }
            ) {
                employees.forEach { employee ->
                    DropdownMenuItem(
                        text = { Text(employee.name) },
                        onClick = {
                            onEmployeeSelected(employee)
                            employeeExpanded = false
                        }
                    )
                }
            }
        }

        // Month Picker Button
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(selectedMonth.time),
                onValueChange = {},
                label = { Text("Month") },
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { showMonthPicker = true }) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }
            )
            // Invisible box over the text field to capture clicks
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { showMonthPicker = true }
            )
        }

        Button(
            onClick = onGenerate,
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedEmployee != null && !isGenerating
        ) {
            if (isGenerating) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Text("Generate")
            }
        }
    }

    if (showMonthPicker) {
        MonthPickerDialog(
            initialMonth = selectedMonth,
            onMonthSelected = {
                onMonthSelected(it)
                showMonthPicker = false
            },
            onDismiss = { showMonthPicker = false }
        )
    }
}

@Composable
fun MonthPickerDialog(
    initialMonth: Calendar,
    onMonthSelected: (Calendar) -> Unit,
    onDismiss: () -> Unit
) {
    var year by remember { mutableIntStateOf(initialMonth.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(initialMonth.get(Calendar.MONTH)) }

    val months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                onMonthSelected(cal)
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Select Month and Year") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { year-- }) { Text("-") }
                    Text(year.toString(), fontWeight = FontWeight.Bold)
                    IconButton(onClick = { year++ }) { Text("+") }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.height(200.dp)) {
                    items(months.size) { index ->
                        Text(
                            text = months[index],
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { month = index }
                                .background(if (month == index) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .padding(8.dp),
                            color = if (month == index) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun Step2Display(
    data: AttendanceSheetData,
    onBackToSelection: () -> Unit,
    onExportPdf: () -> Unit,
    onExportCsv: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBackToSelection) {
                Text("Change Selection")
            }
            Row {
                IconButton(onClick = onExportPdf) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF", tint = Color(0xFFF44336))
                }
                IconButton(onClick = onExportCsv) {
                    Icon(Icons.Default.TableChart, contentDescription = "Export CSV", tint = Color(0xFF4CAF50))
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                SummaryCard(data)
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EntryCard(data, modifier = Modifier.weight(1f))
                    ExitCard(data, modifier = Modifier.weight(1f))
                }
            }
            item {
                TimeCard(data)
            }
            item {
                Text(
                    text = "Daily Records",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(data.dailyRecords) { record ->
                DailyRecordRow(record)
            }
        }
    }
}

@Composable
fun SummaryCard(data: AttendanceSheetData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Monthly Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryItem("Office Days", data.officeDays.toString())
                SummaryItem("Present", data.presentCount.toString())
                SummaryItem("Absent", data.absentCount.toString())
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryItem("Leave", data.leaveCount.toString())
                SummaryItem("Holiday", data.holidayCount.toString())
                SummaryItem("", "") // Placeholder
            }
        }
    }
}

@Composable
fun SummaryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EntryCard(data: AttendanceSheetData, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Entry", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("In Time: ${data.onTimeEntryCount}", style = MaterialTheme.typography.bodySmall)
            Text("Late: ${data.lateCount}", style = MaterialTheme.typography.bodySmall)
            Text("Total Late: ${formatDuration(data.totalLateMinutes)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ExitCard(data: AttendanceSheetData, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Exit", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Ok: ${data.onTimeExitCount}", style = MaterialTheme.typography.bodySmall)
            Text("Early: ${data.earlyCount}", style = MaterialTheme.typography.bodySmall)
            Text("Total Extra: ${formatDuration(data.totalOvertimeMinutes)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun TimeCard(data: AttendanceSheetData) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Time Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Office In", style = MaterialTheme.typography.labelSmall)
                    Text(data.policyStartTime, style = MaterialTheme.typography.bodyMedium)
                }
                Column {
                    Text("Office Out", style = MaterialTheme.typography.labelSmall)
                    Text(data.policyEndTime, style = MaterialTheme.typography.bodyMedium)
                }
                Column {
                    Text("Total Time", style = MaterialTheme.typography.labelSmall)
                    Text(formatDuration(data.totalTimeMinutes), style = MaterialTheme.typography.bodyMedium)
                }
                Column {
                    Text("Avg Time", style = MaterialTheme.typography.labelSmall)
                    Text(formatDuration(data.avgTimeMinutes), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun DailyRecordRow(record: AttendanceRecord) {
    val officeTz = LocalOfficeTz.current
    val timeZone = remember(officeTz) { TimeZone.getTimeZone(officeTz) }
    val timeFormat = remember(timeZone) { SimpleDateFormat("hh:mm a", Locale.getDefault()).apply { this.timeZone = timeZone } }
    val dateFormat = remember(timeZone) { SimpleDateFormat("dd MMM", Locale.getDefault()).apply { this.timeZone = timeZone } }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val date = DateUtils.parseFlexibleDate(record.date, officeTz)
                Text(text = date?.let { dateFormat.format(it) } ?: record.date, fontWeight = FontWeight.Bold)
                
                val entry = DateUtils.parseFlexibleTime(record.entryTime, officeTz)
                val exit = DateUtils.parseFlexibleTime(record.exitTime, officeTz)
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("In: ${entry?.let { timeFormat.format(it) } ?: "--:--"}", fontSize = 12.sp)
                    Text("Out: ${exit?.let { timeFormat.format(it) } ?: "--:--"}", fontSize = 12.sp)
                }
                
                if (entry != null && exit != null) {
                    val diff = (exit.time - entry.time) / (1000 * 60)
                    Text("Duration: ${formatDuration(diff.toInt())}", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                if (record.lateEntry != null) {
                    Text("Late ${formatHhMm(record.lateEntry)}", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                if (record.earlyExit != null) {
                    Text("Early ${formatHhMm(record.earlyExit)}", color = Color(0xFFFFA500), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                if (record.overtime != null) {
                    Text("OT ${formatHhMm(record.overtime)}", color = Color(0xFF4CAF50), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return "${h}h ${m}m"
}

fun formatHhMm(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return String.format("%02d:%02d", hours, minutes)
}

suspend fun generateSheet(
    authApi: AuthApi,
    employee: Employee,
    calendar: Calendar,
    officeTz: String,
    onLoading: (Boolean) -> Unit,
    onDataGenerated: (AttendanceSheetData) -> Unit,
    onError: (String) -> Unit
) {
    onLoading(true)
    try {
        val historyResponse = authApi.getAttendanceHistory(all = true)
        val holidayResponse = authApi.getHolidays()
        val policyResponse = authApi.getWorkPolicies()
        val leaveResponse = authApi.getLeaveRecords()

        if (historyResponse.success && holidayResponse.success && policyResponse.success && leaveResponse.success) {
            val month = calendar.get(Calendar.MONTH)
            val year = calendar.get(Calendar.YEAR)
            
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            
            val filteredHistory = historyResponse.records.filter { record ->
                val date = DateUtils.parseFlexibleDate(record.date, officeTz)
                if (date == null) return@filter false
                val cal = Calendar.getInstance().apply { time = date }
                record.employeeId == employee.id && cal.get(Calendar.MONTH) == month && cal.get(Calendar.YEAR) == year
            }

            val filteredLeaves = leaveResponse.body.filter { leave ->
                val date = DateUtils.parseFlexibleDate(leave.date, officeTz)
                if (date == null) return@filter false
                val cal = Calendar.getInstance().apply { time = date }
                leave.employeeId == employee.id && 
                cal.get(Calendar.MONTH) == month && 
                cal.get(Calendar.YEAR) == year && 
                leave.isApproved == "Approved"
            }

            val filteredHolidays = holidayResponse.holidays.filter { holiday ->
                val date = DateUtils.parseFlexibleDate(holiday.date, officeTz)
                if (date == null) return@filter false
                val cal = Calendar.getInstance().apply { time = date }
                cal.get(Calendar.MONTH) == month && cal.get(Calendar.YEAR) == year
            }

            // Sets for unique days
            val officeDaysSet = mutableSetOf<String>()
            val totalDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            for (i in 1..totalDays) {
                val cal = (calendar.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, i) }
                val dayStr = sdf.format(cal.time)
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val isWeekend = dayOfWeek == Calendar.FRIDAY
                val isHoliday = filteredHolidays.any { h ->
                    val hDate = DateUtils.parseFlexibleDate(h.date, officeTz)
                    hDate != null && sdf.format(hDate) == dayStr
                }
                if (!isWeekend && !isHoliday) officeDaysSet.add(dayStr)
            }

            val presentDaysSet = filteredHistory.mapNotNull { record ->
                DateUtils.parseFlexibleDate(record.date, officeTz)?.let { sdf.format(it) }
            }.toSet()

            val leaveDaysSet = filteredLeaves.mapNotNull { leave ->
                DateUtils.parseFlexibleDate(leave.date, officeTz)?.let { sdf.format(it) }
            }.toSet()

            val presentCount = presentDaysSet.size
            val leaveCount = leaveDaysSet.size
            val absentCount = (officeDaysSet - presentDaysSet - leaveDaysSet).size
            
            // Group history by date string for per-day metrics
            val historyByDate = filteredHistory.groupBy { record ->
                DateUtils.parseFlexibleDate(record.date, officeTz)?.let { sdf.format(it) }
            }

            val lateCount = historyByDate.values.count { records -> records.any { it.lateEntry != null } }
            val totalLateMinutes = filteredHistory.sumOf { it.lateEntry ?: 0 }
            val onTimeEntryCount = presentCount - lateCount

            val earlyCount = historyByDate.values.count { records -> records.any { it.earlyExit != null } }
            val totalOvertimeMinutes = filteredHistory.sumOf { it.overtime ?: 0 }
            val onTimeExitCount = historyByDate.values.count { records -> records.any { it.exitTime != null && it.earlyExit == null } }

            // Policy
            val policy = policyResponse.policy.find { p ->
                val start = DateUtils.parseFlexibleDate(p.startDate, officeTz)
                val end = DateUtils.parseFlexibleDate(p.endDate, officeTz)
                if (start == null || end == null) return@find false
                val current = calendar.time
                !current.before(start) && !current.after(end)
            } ?: policyResponse.policy.lastOrNull()

            var totalTimeMinutes = 0
            filteredHistory.forEach { record ->
                val entry = DateUtils.parseFlexibleTime(record.entryTime, officeTz)
                val exit = DateUtils.parseFlexibleTime(record.exitTime, officeTz)
                if (entry != null && exit != null) {
                    totalTimeMinutes += ((exit.time - entry.time) / (1000 * 60)).toInt()
                }
            }

            val avgTimeMinutes = if (presentCount > 0) totalTimeMinutes / presentCount else 0

            onDataGenerated(
                AttendanceSheetData(
                    employee = employee,
                    month = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time),
                    officeDays = officeDaysSet.size,
                    presentCount = presentCount,
                    absentCount = absentCount,
                    leaveCount = leaveCount,
                    holidayCount = filteredHolidays.size,
                    onTimeEntryCount = onTimeEntryCount,
                    lateCount = lateCount,
                    totalLateMinutes = totalLateMinutes,
                    onTimeExitCount = onTimeExitCount,
                    earlyCount = earlyCount,
                    totalOvertimeMinutes = totalOvertimeMinutes,
                    policyStartTime = policy?.officeStart ?: "--:--",
                    policyEndTime = policy?.officeEnd ?: "--:--",
                    totalTimeMinutes = totalTimeMinutes,
                    avgTimeMinutes = avgTimeMinutes,
                    dailyRecords = filteredHistory
                )
            )
        } else {
            onError("Failed to fetch all required data")
        }
    } catch (e: Exception) {
        onError(e.message ?: "An unexpected error occurred")
    } finally {
        onLoading(false)
    }
}

data class AttendanceSheetData(
    val employee: Employee,
    val month: String,
    val officeDays: Int,
    val presentCount: Int,
    val absentCount: Int,
    val leaveCount: Int,
    val holidayCount: Int,
    val onTimeEntryCount: Int,
    val lateCount: Int,
    val totalLateMinutes: Int,
    val onTimeExitCount: Int,
    val earlyCount: Int,
    val totalOvertimeMinutes: Int,
    val policyStartTime: String,
    val policyEndTime: String,
    val totalTimeMinutes: Int,
    val avgTimeMinutes: Int,
    val dailyRecords: List<AttendanceRecord>
)

suspend fun exportToPdf(context: Context, data: AttendanceSheetData, officeTz: String) {
    withContext(Dispatchers.Main) {
        Toast.makeText(context, "Generating PDF...", Toast.LENGTH_SHORT).show()
    }
    withContext(Dispatchers.IO) {
        try {
            val sanitizedEmployeeName = data.employee.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val sanitizedMonth = data.month.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val fileName = "Attendance_${sanitizedEmployeeName}_${sanitizedMonth}.pdf"
            
            val dir = context.getExternalFilesDir(null)
            if (dir == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "External storage not available", Toast.LENGTH_LONG).show()
                }
                return@withContext
            }
            val file = File(dir, fileName)
            
            val document = Document(PageSize.A4)
            PdfWriter.getInstance(document, FileOutputStream(file))
            document.open()

            val boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18f)
            val normalFont = FontFactory.getFont(FontFactory.HELVETICA, 12f)
            val headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f)
            val cellFont = FontFactory.getFont(FontFactory.HELVETICA, 10f)

            document.add(Paragraph("Monthly Attendance Sheet", boldFont))
            document.add(Paragraph("Employee: ${data.employee.name}", normalFont))
            document.add(Paragraph("Month: ${data.month}", normalFont))
            document.add(Paragraph("\nSummary", boldFont))
            
            val summaryTable = PdfPTable(2)
            summaryTable.widthPercentage = 100f
            summaryTable.addCell(Paragraph("Office Days", cellFont)); summaryTable.addCell(Paragraph(data.officeDays.toString(), cellFont))
            summaryTable.addCell(Paragraph("Present", cellFont)); summaryTable.addCell(Paragraph(data.presentCount.toString(), cellFont))
            summaryTable.addCell(Paragraph("Absent", cellFont)); summaryTable.addCell(Paragraph(data.absentCount.toString(), cellFont))
            summaryTable.addCell(Paragraph("Leave", cellFont)); summaryTable.addCell(Paragraph(data.leaveCount.toString(), cellFont))
            summaryTable.addCell(Paragraph("Holiday", cellFont)); summaryTable.addCell(Paragraph(data.holidayCount.toString(), cellFont))
            document.add(summaryTable)

            document.add(Paragraph("\nDaily Records", boldFont))
            val table = PdfPTable(7)
            table.widthPercentage = 100f
            
            val headers = listOf("Date", "Entry", "Exit", "Duration", "Late(min)", "Early(min)", "OT(min)")
            headers.forEach { table.addCell(Paragraph(it, headerFont)) }

            data.dailyRecords.forEach { record ->
                val entry = DateUtils.parseFlexibleTime(record.entryTime, officeTz)
                val exit = DateUtils.parseFlexibleTime(record.exitTime, officeTz)
                val durationStr = if (entry != null && exit != null) {
                    val diff = (exit.time - entry.time) / (1000 * 60)
                    formatDuration(diff.toInt())
                } else if (record.exitTime == null) {
                    "Still In"
                } else "--"

                table.addCell(Paragraph(record.date, cellFont))
                table.addCell(Paragraph(record.entryTime?.substringAfter("T")?.substringBefore(".") ?: "--:--", cellFont))
                table.addCell(Paragraph(record.exitTime?.substringAfter("T")?.substringBefore(".") ?: "--:--", cellFont))
                table.addCell(Paragraph(durationStr, cellFont)) 
                table.addCell(Paragraph((record.lateEntry ?: 0).toString(), cellFont))
                table.addCell(Paragraph((record.earlyExit ?: 0).toString(), cellFont))
                table.addCell(Paragraph((record.overtime ?: 0).toString(), cellFont))
            }
            document.add(table)
            document.close()

            withContext(Dispatchers.Main) {
                shareFile(context, file, "application/pdf")
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "PDF Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

suspend fun exportToCsv(context: Context, data: AttendanceSheetData, officeTz: String) {
    withContext(Dispatchers.Main) {
        Toast.makeText(context, "Generating CSV...", Toast.LENGTH_SHORT).show()
    }
    withContext(Dispatchers.IO) {
        try {
            val sanitizedEmployeeName = data.employee.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val sanitizedMonth = data.month.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val fileName = "Attendance_${sanitizedEmployeeName}_${sanitizedMonth}.csv"
            
            val dir = context.getExternalFilesDir(null)
            if (dir == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "External storage not available", Toast.LENGTH_LONG).show()
                }
                return@withContext
            }
            val file = File(dir, fileName)
            val writer = FileOutputStream(file).bufferedWriter()

            writer.write("Monthly Attendance Sheet\n")
            writer.write("Employee,${data.employee.name}\n")
            writer.write("Month,${data.month}\n\n")
            
            writer.write("Summary\n")
            writer.write("Office Days,${data.officeDays}\n")
            writer.write("Present,${data.presentCount}\n")
            writer.write("Absent,${data.absentCount}\n")
            writer.write("Leave,${data.leaveCount}\n")
            writer.write("Holiday,${data.holidayCount}\n\n")

            writer.write("Daily Records\n")
            writer.write("Date,Entry,Exit,Duration,Late(min),Early(min),OT(min)\n")
            data.dailyRecords.forEach { record ->
                val entry = DateUtils.parseFlexibleTime(record.entryTime, officeTz)
                val exit = DateUtils.parseFlexibleTime(record.exitTime, officeTz)
                val durationStr = if (entry != null && exit != null) {
                    val diff = (exit.time - entry.time) / (1000 * 60)
                    formatDuration(diff.toInt())
                } else if (record.exitTime == null) {
                    "Still In"
                } else "--"

                writer.write("\"${record.date}\",${record.entryTime ?: ""},${record.exitTime ?: ""},$durationStr,${record.lateEntry ?: 0},${record.earlyExit ?: 0},${record.overtime ?: 0}\n")
            }
            writer.close()

            withContext(Dispatchers.Main) {
                shareFile(context, file, "text/csv")
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "CSV Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

fun shareFile(context: Context, file: File, type: String) {
    try {
        if (!file.exists()) {
            Toast.makeText(context, "File was not created", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            this.type = type
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share Attendance Sheet")
        if (context !is Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Share Error: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
