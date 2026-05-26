package lab.sabily.pulseid.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lab.sabily.pulseid.data.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtAGlanceScreen(authApi: AuthApi, onBack: () -> Unit) {
    var selectedMonth by remember { mutableStateOf(Calendar.getInstance()) }
    var startDate by remember { mutableStateOf<Calendar?>(null) }
    var endDate by remember { mutableStateOf<Calendar?>(null) }
    
    var isGenerating by remember { mutableStateOf(false) }
    var reportData by remember { mutableStateOf<List<AtAGlanceData>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val officeTz = LocalOfficeTz.current

    // Initialize dates when month changes
    LaunchedEffect(selectedMonth) {
        val start = (selectedMonth.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val end = (selectedMonth.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH)) }
        startDate = start
        endDate = end
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("At A Glance Sheet") },
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
            if (reportData == null) {
                AtAGlanceSelection(
                    selectedMonth = selectedMonth,
                    onMonthSelected = { selectedMonth = it },
                    startDate = startDate,
                    onStartDateSelected = { startDate = it },
                    endDate = endDate,
                    onEndDateSelected = { endDate = it },
                    onGenerate = {
                        scope.launch {
                            generateAtAGlance(
                                authApi,
                                startDate!!,
                                endDate!!,
                                officeTz,
                                onLoading = { isGenerating = it },
                                onDataGenerated = { reportData = it },
                                onError = { error = it }
                            )
                        }
                    },
                    isGenerating = isGenerating
                )
            } else {
                AtAGlanceDisplay(
                    dataList = reportData!!,
                    dateRange = "${formatDate(startDate!!)} - ${formatDate(endDate!!)}",
                    onBackToSelection = { reportData = null },
                    onExportPdf = {
                        scope.launch {
                            exportAtAGlancePdf(context, reportData!!, "${formatDate(startDate!!)} - ${formatDate(endDate!!)}")
                        }
                    },
                    onExportCsv = {
                        scope.launch {
                            exportAtAGlanceCsv(context, reportData!!, "${formatDate(startDate!!)} - ${formatDate(endDate!!)}")
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

@Composable
fun AtAGlanceSelection(
    selectedMonth: Calendar,
    onMonthSelected: (Calendar) -> Unit,
    startDate: Calendar?,
    onStartDateSelected: (Calendar) -> Unit,
    endDate: Calendar?,
    onEndDateSelected: (Calendar) -> Unit,
    onGenerate: () -> Unit,
    isGenerating: Boolean
) {
    var showMonthPicker by remember { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Month Picker
        OutlinedTextField(
            value = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(selectedMonth.time),
            onValueChange = {},
            label = { Text("Select Month") },
            readOnly = true,
            modifier = Modifier.fillMaxWidth().clickable { showMonthPicker = true },
            trailingIcon = { IconButton(onClick = { showMonthPicker = true }) { Icon(Icons.Default.ArrowDropDown, contentDescription = null) } },
            enabled = false,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Start Date
            OutlinedTextField(
                value = startDate?.let { formatDate(it) } ?: "Select Start",
                onValueChange = {},
                label = { Text("Start Date") },
                readOnly = true,
                modifier = Modifier.weight(1f).clickable { showStartDatePicker = true },
                enabled = false,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            // End Date
            OutlinedTextField(
                value = endDate?.let { formatDate(it) } ?: "Select End",
                onValueChange = {},
                label = { Text("End Date") },
                readOnly = true,
                modifier = Modifier.weight(1f).clickable { showEndDatePicker = true },
                enabled = false,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        Button(
            onClick = onGenerate,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isGenerating && startDate != null && endDate != null
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
            onMonthSelected = onMonthSelected,
            onDismiss = { showMonthPicker = false }
        )
    }

    if (showStartDatePicker) {
        DatePickerDialogWrapper(
            initialDate = startDate ?: selectedMonth,
            onDateSelected = onStartDateSelected,
            onDismiss = { showStartDatePicker = false }
        )
    }

    if (showEndDatePicker) {
        DatePickerDialogWrapper(
            initialDate = endDate ?: selectedMonth,
            onDateSelected = onEndDateSelected,
            onDismiss = { showEndDatePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogWrapper(
    initialDate: Calendar,
    onDateSelected: (Calendar) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDate.timeInMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let {
                    val cal = Calendar.getInstance().apply { timeInMillis = it }
                    onDateSelected(cal)
                }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
fun AtAGlanceDisplay(
    dataList: List<AtAGlanceData>,
    dateRange: String,
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
            TextButton(onClick = onBackToSelection) { Text("Change Selection") }
            Row {
                IconButton(onClick = onExportPdf) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF", tint = Color(0xFFF44336))
                }
                IconButton(onClick = onExportCsv) {
                    Icon(Icons.Default.TableChart, contentDescription = "Export CSV", tint = Color(0xFF4CAF50))
                }
            }
        }
        
        Text(text = "Range: $dateRange", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))

        Box(modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            Column {
                // Table Header
                Row(modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer)) {
                    val headers = listOf(
                        "Pos", "Name", "Off Days", "Pres", "Avg Time", "Leave", "Abs", "Hol",
                        "Short", "Req", "Ext Day", "In Time", "Late", "Tot Late",
                        "Exit OK", "Early", "Tot Ext", "Off In", "Off Out", "Tot Time"
                    )
                    headers.forEach { header ->
                        TableCell(text = header, weight = 1f, isHeader = true)
                    }
                }
                
                // Table Rows
                LazyColumn(modifier = Modifier.fillMaxHeight()) {
                    items(dataList) { data ->
                        Row {
                            TableCell(text = data.position, weight = 1f)
                            TableCell(text = data.name, weight = 1f)
                            TableCell(text = data.officeDays.toString(), weight = 1f)
                            TableCell(text = data.presentCount.toString(), weight = 1f)
                            TableCell(text = data.avgTime, weight = 1f)
                            TableCell(text = data.leaveCount.toString(), weight = 1f)
                            TableCell(text = data.absentCount.toString(), weight = 1f)
                            TableCell(text = data.holidayCount.toString(), weight = 1f)
                            TableCell(text = data.shortTimeCount.toString(), weight = 1f)
                            TableCell(text = data.requiredTimeCount.toString(), weight = 1f)
                            TableCell(text = data.extraDayCount.toString(), weight = 1f)
                            TableCell(text = data.inTimeCount.toString(), weight = 1f)
                            TableCell(text = data.lateCount.toString(), weight = 1f)
                            TableCell(text = data.totalLate, weight = 1f)
                            TableCell(text = data.exitOkCount.toString(), weight = 1f)
                            TableCell(text = data.earlyExitCount.toString(), weight = 1f)
                            TableCell(text = data.totalExtraTime, weight = 1f)
                            TableCell(text = data.officeIn, weight = 1f)
                            TableCell(text = data.officeOut, weight = 1f)
                            TableCell(text = data.totalTime, weight = 1f)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.TableCell(text: String, weight: Float, isHeader: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier.border(1.dp, Color.LightGray).padding(8.dp).width(80.dp),
        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
        fontSize = 12.sp,
        textAlign = TextAlign.Center
    )
}

data class AtAGlanceData(
    val position: String,
    val name: String,
    val officeDays: Int,
    val presentCount: Int,
    val avgTime: String,
    val leaveCount: Int,
    val absentCount: Int,
    val holidayCount: Int,
    val shortTimeCount: Int,
    val requiredTimeCount: Int,
    val extraDayCount: Int,
    val inTimeCount: Int,
    val lateCount: Int,
    val totalLate: String,
    val exitOkCount: Int,
    val earlyExitCount: Int,
    val totalExtraTime: String,
    val officeIn: String,
    val officeOut: String,
    val totalTime: String
)

suspend fun generateAtAGlance(
    authApi: AuthApi,
    startDate: Calendar,
    endDate: Calendar,
    officeTz: String,
    onLoading: (Boolean) -> Unit,
    onDataGenerated: (List<AtAGlanceData>) -> Unit,
    onError: (String) -> Unit
) {
    onLoading(true)
    try {
        val timeZone = TimeZone.getTimeZone(officeTz)

        val historyDeferred = withContext(Dispatchers.IO) { async { authApi.getAttendanceHistory(all = true) } }
        val holidayDeferred = withContext(Dispatchers.IO) { async { authApi.getHolidays() } }
        val policyDeferred = withContext(Dispatchers.IO) { async { authApi.getWorkPolicies() } }
        val leaveDeferred = withContext(Dispatchers.IO) { async { authApi.getLeaveRecords() } }
        val profileDeferred = withContext(Dispatchers.IO) { async { authApi.getAllMembers(all = true) } }

        val history = historyDeferred.await()
        val holidays = holidayDeferred.await()
        val policies = policyDeferred.await()
        val leaves = leaveDeferred.await()
        val profiles = profileDeferred.await()

        if (history.success && holidays.success && policies.success && leaves.success && profiles.success) {
            val startMillis = startDate.timeInMillis
            val endMillis = endDate.timeInMillis
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            // Filter holidays in range
            val filteredHolidays = holidays.holidays.filter {
                val d = DateUtils.parseFlexibleDate(it.date, officeTz)?.time ?: 0L
                d in startMillis..endMillis
            }

            // Calculate Office Days in range (Friday only weekend)
            val officeDaysSet = mutableSetOf<String>()
            val cal = startDate.clone() as Calendar
            while (cal.timeInMillis <= endMillis) {
                val dayStr = sdf.format(cal.time)
                val isWeekend = cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY
                val isHoliday = filteredHolidays.any { h ->
                    val hDate = DateUtils.parseFlexibleDate(h.date, officeTz)
                    hDate != null && sdf.format(hDate) == dayStr
                }
                if (!isWeekend && !isHoliday) officeDaysSet.add(dayStr)
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }

            val resultList = profiles.employee
                .filter { it.position != "0" }
                .map { emp ->
                val empHistory = history.records.filter {
                    val d = DateUtils.parseFlexibleDate(it.date, officeTz)?.time ?: 0L
                    it.employeeId == emp.id && d in startMillis..endMillis
                }
                val empLeaves = leaves.body.filter {
                    val d = DateUtils.parseFlexibleDate(it.date, officeTz)?.time ?: 0L
                    it.employeeId == emp.id && d in startMillis..endMillis && it.isApproved == "Approved"
                }

                // Match policy for the range (using first day of range as reference)
                val policy = policies.policy.find { p ->
                    val pStart = DateUtils.parseFlexibleDate(p.startDate, officeTz)?.time ?: 0L
                    val pEnd = DateUtils.parseFlexibleDate(p.endDate, officeTz)?.time ?: 0L
                    startMillis in pStart..pEnd
                } ?: policies.policy.lastOrNull()

                val requiredTimeMin = if (policy != null) {
                    val start = policy.officeStart.split(":")
                    val end = policy.officeEnd.split(":")
                    val startMin = start[0].toInt() * 60 + start[1].toInt()
                    val endMin = end[0].toInt() * 60 + end[1].toInt()
                    endMin - startMin
                } else 0

                val presentDaysSet = empHistory.mapNotNull { record ->
                    DateUtils.parseFlexibleDate(record.date, officeTz)?.let { sdf.format(it) }
                }.toSet()

                val leaveDaysSet = empLeaves.mapNotNull { leave ->
                    DateUtils.parseFlexibleDate(leave.date, officeTz)?.let { sdf.format(it) }
                }.toSet()

                val historyByDate = empHistory.groupBy { record ->
                    DateUtils.parseFlexibleDate(record.date, officeTz)?.let { sdf.format(it) }
                }

                var totalTimeMin = 0
                var totalLateMin = 0
                var totalOvertimeMin = 0
                var shortTimeCount = 0
                var requiredTimeCount = 0
                var extraDayCount = 0
                var lateCount = 0
                var exitOkCount = 0
                var earlyExitCount = 0

                historyByDate.forEach { (date, records) ->
                    // For metrics, we'll use cumulative values but count unique days for some
                    var dayDuration = 0
                    var dayLate = 0
                    var dayOvertime = 0
                    var isDayLate = false
                    var hasExit = false
                    var isEarlyExit = false

                    records.forEach { record ->
                        val entry = parseIso(record.entryTime, timeZone)
                        val exit = parseIso(record.exitTime, timeZone)
                        
                        if (entry != null && exit != null) {
                            dayDuration += ((exit.time - entry.time) / (1000 * 60)).toInt()
                        }
                        
                        if (record.lateEntry != null) {
                            isDayLate = true
                            dayLate += record.lateEntry
                        }
                        
                        if (record.exitTime != null) {
                            hasExit = true
                            if (record.earlyExit != null) {
                                isEarlyExit = true
                            }
                        }
                        
                        if (record.overtime != null) {
                            dayOvertime += record.overtime
                        }
                    }

                    totalTimeMin += dayDuration
                    totalLateMin += dayLate
                    totalOvertimeMin += dayOvertime
                    
                    if (dayDuration < requiredTimeMin) shortTimeCount++
                    else if (dayDuration >= requiredTimeMin) {
                        requiredTimeCount++
                        if (dayDuration > requiredTimeMin) extraDayCount++
                    }

                    if (isDayLate) lateCount++
                    if (hasExit) {
                        if (isEarlyExit) earlyExitCount++
                        else exitOkCount++
                    }
                }

                val presentCount = presentDaysSet.size
                val leaveCount = leaveDaysSet.size
                val absentCount = (officeDaysSet - presentDaysSet - leaveDaysSet).size
                val avgTimeMin = if (presentCount > 0) totalTimeMin / presentCount else 0

                AtAGlanceData(
                    position = emp.position,
                    name = emp.name,
                    officeDays = officeDaysSet.size,
                    presentCount = presentCount,
                    avgTime = formatDuration(avgTimeMin),
                    leaveCount = leaveCount,
                    absentCount = absentCount,
                    holidayCount = filteredHolidays.size,
                    shortTimeCount = shortTimeCount,
                    requiredTimeCount = requiredTimeCount,
                    extraDayCount = extraDayCount,
                    inTimeCount = presentCount - lateCount,
                    lateCount = lateCount,
                    totalLate = formatDuration(totalLateMin),
                    exitOkCount = exitOkCount,
                    earlyExitCount = earlyExitCount,
                    totalExtraTime = formatDuration(totalOvertimeMin),
                    officeIn = policy?.officeStart ?: "--:--",
                    officeOut = policy?.officeEnd ?: "--:--",
                    totalTime = formatDuration(totalTimeMin)
                )
            }.sortedBy { it.position.toIntOrNull() ?: Int.MAX_VALUE }

            onDataGenerated(resultList)
        } else {
            onError("Failed to fetch data")
        }
    } catch (e: Exception) {
        onError(e.message ?: "Error generating report")
    } finally {
        onLoading(false)
    }
}

fun parseIso(s: String?, zone: TimeZone): Date? {
    if (s == null) return null
    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss.SSSSSS", "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm:ss.SSSSSS", "yyyy-MM-dd HH:mm:ss.SSS"
    )
    for (f in formats) {
        try {
            val sdf = SimpleDateFormat(f, Locale.getDefault()).apply { timeZone = zone }
            val d = sdf.parse(s)
            if (d != null) return d
        } catch (e: Exception) {}
    }
    return null
}

fun formatDate(cal: Calendar): String = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()).format(cal.time)

suspend fun exportAtAGlancePdf(context: Context, dataList: List<AtAGlanceData>, dateRange: String) {
    withContext(Dispatchers.Main) {
        Toast.makeText(context, "Generating PDF...", Toast.LENGTH_SHORT).show()
    }
    withContext(Dispatchers.IO) {
        try {
            val sanitizedDateRange = dateRange.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val fileName = "At_A_Glance_${sanitizedDateRange}.pdf"
            val dir = context.getExternalFilesDir(null)
            if (dir == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "External storage not available", Toast.LENGTH_LONG).show()
                }
                return@withContext
            }
            val file = File(dir, fileName)
            val document = Document(PageSize.A4.rotate()) // Landscape
            PdfWriter.getInstance(document, FileOutputStream(file))
            document.open()

            val boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14f)
            val normalFont = FontFactory.getFont(FontFactory.HELVETICA, 8f)
            val headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f)

            document.add(Paragraph("At A Glance Attendance Sheet", boldFont))
            document.add(Paragraph("Date Range: $dateRange", normalFont))
            document.add(Paragraph("\n"))

            val table = PdfPTable(20)
            table.widthPercentage = 100f
            val headers = listOf(
                "Serial", "Name", "Office Days", "Present", "Average Time", "Leave Day", "Absent", "Holiday",
                "Short Time", "Required Time", "Extra Day", "In Time", "Late", "Total Late",
                "Exit OK", "Early Exit", "Total Exit", "Office In", "Office Out", "Total Time"
            )
            headers.forEach { table.addCell(Paragraph(it, headerFont)) }

            dataList.forEach { data ->
                table.addCell(Paragraph(data.position, normalFont))
                table.addCell(Paragraph(data.name, normalFont))
                table.addCell(Paragraph(data.officeDays.toString(), normalFont))
                table.addCell(Paragraph(data.presentCount.toString(), normalFont))
                table.addCell(Paragraph(data.avgTime, normalFont))
                table.addCell(Paragraph(data.leaveCount.toString(), normalFont))
                table.addCell(Paragraph(data.absentCount.toString(), normalFont))
                table.addCell(Paragraph(data.holidayCount.toString(), normalFont))
                table.addCell(Paragraph(data.shortTimeCount.toString(), normalFont))
                table.addCell(Paragraph(data.requiredTimeCount.toString(), normalFont))
                table.addCell(Paragraph(data.extraDayCount.toString(), normalFont))
                table.addCell(Paragraph(data.inTimeCount.toString(), normalFont))
                table.addCell(Paragraph(data.lateCount.toString(), normalFont))
                table.addCell(Paragraph(data.totalLate, normalFont))
                table.addCell(Paragraph(data.exitOkCount.toString(), normalFont))
                table.addCell(Paragraph(data.earlyExitCount.toString(), normalFont))
                table.addCell(Paragraph(data.totalExtraTime, normalFont))
                table.addCell(Paragraph(data.officeIn, normalFont))
                table.addCell(Paragraph(data.officeOut, normalFont))
                table.addCell(Paragraph(data.totalTime, normalFont))
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

suspend fun exportAtAGlanceCsv(context: Context, dataList: List<AtAGlanceData>, dateRange: String) {
    withContext(Dispatchers.Main) {
        Toast.makeText(context, "Generating CSV...", Toast.LENGTH_SHORT).show()
    }
    withContext(Dispatchers.IO) {
        try {
            val sanitizedDateRange = dateRange.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val fileName = "At_A_Glance_${sanitizedDateRange}.csv"
            val dir = context.getExternalFilesDir(null)
            if (dir == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "External storage not available", Toast.LENGTH_LONG).show()
                }
                return@withContext
            }
            val file = File(dir, fileName)
            val writer = FileOutputStream(file).bufferedWriter()

            writer.write("At A Glance Attendance Sheet\n")
            writer.write("Date Range,\"$dateRange\"\n\n")
            
            val headers = listOf(
                "Position", "Name", "Office Days", "Total Present", "Avg Time", "Leave", "Absent", "Holiday",
                "Short Time", "Required Time", "Extra Day", "Entry In Time", "Entry Late", "Entry Total Late",
                "Exit OK", "Exit Early", "Total Extra Time", "Office In", "Office Out", "Total Time"
            )
            writer.write(headers.joinToString(",") + "\n")

            dataList.forEach { data ->
                val row = listOf(
                    data.position, data.name, data.officeDays, data.presentCount, data.avgTime, data.leaveCount, data.absentCount, data.holidayCount,
                    data.shortTimeCount, data.requiredTimeCount, data.extraDayCount, data.inTimeCount, data.lateCount, data.totalLate,
                    data.exitOkCount, data.earlyExitCount, data.totalExtraTime, data.officeIn, data.officeOut, data.totalTime
                )
                writer.write(row.joinToString(",") + "\n")
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
