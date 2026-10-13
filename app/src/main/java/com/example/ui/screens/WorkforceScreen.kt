package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.AttendanceRecord
import com.example.data.model.BusinessLedgerCalculator
import com.example.data.model.AyamKasFormatters
import com.example.data.model.EmployeeRecord
import com.example.data.model.PayrollRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun WorkforceScreen(
    employees: List<EmployeeRecord>,
    attendance: List<AttendanceRecord>,
    payroll: List<PayrollRecord>,
    onSaveEmployee: (EmployeeRecord) -> Unit,
    onSaveAttendance: (AttendanceRecord) -> Unit,
    onSavePayroll: (PayrollRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    val currentPeriod = remember { SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date()) }
    var activeSection by remember { mutableStateOf("employee") }
    var name by remember { mutableStateOf("") }
    var position by remember { mutableStateOf("") }
    var dailyRateText by remember { mutableStateOf("") }
    var standardHoursText by remember { mutableStateOf("8") }
    var overtimeRateText by remember { mutableStateOf("") }

    var attendanceDate by remember { mutableStateOf(today) }
    var attendanceEmployeeId by remember { mutableStateOf("") }
    var shiftName by remember { mutableStateOf("Pagi") }
    var clockIn by remember { mutableStateOf("") }
    var clockOut by remember { mutableStateOf("") }
    var breakMinutesText by remember { mutableStateOf("0") }
    var attendanceStatus by remember { mutableStateOf("PRESENT") }
    var attendanceNotes by remember { mutableStateOf("") }
    var attendanceEmployeeMenu by remember { mutableStateOf(false) }
    var statusMenu by remember { mutableStateOf(false) }

    var payrollPeriod by remember { mutableStateOf(currentPeriod) }
    var payrollEmployeeId by remember { mutableStateOf("") }
    var payrollEmployeeMenu by remember { mutableStateOf(false) }
    var allowanceText by remember { mutableStateOf("0") }
    var deductionsText by remember { mutableStateOf("0") }
    var paidNowText by remember { mutableStateOf("0") }
    var payrollPaymentMethod by remember { mutableStateOf("TRANSFER") }
    var payrollCashAccount by remember { mutableStateOf("ATM/BANK") }
    var payrollPaymentMenu by remember { mutableStateOf(false) }
    var payrollAccountMenu by remember { mutableStateOf(false) }
    var payrollNotes by remember { mutableStateOf("") }
    var approvePayroll by remember { mutableStateOf(false) }

    val attendanceStatuses = listOf("PRESENT", "LEAVE", "SICK", "ABSENT", "DAY_OFF")
    val selectedAttendanceEmployee = employees.firstOrNull { it.id == attendanceEmployeeId }
    val selectedPayrollEmployee = employees.firstOrNull { it.id == payrollEmployeeId }
    val payrollAttendance = attendance.filter {
        it.employeeId == payrollEmployeeId && it.workDate.startsWith(payrollPeriod) && it.status == "PRESENT"
    }
    val daysPresent = payrollAttendance.size.toDouble()
    val overtimeHours = payrollAttendance.sumOf { it.overtimeHours }
    val allowance = allowanceText.toDoubleOrNull() ?: 0.0
    val deductions = deductionsText.toDoubleOrNull() ?: 0.0
    val payrollAmounts = BusinessLedgerCalculator.calculatePayroll(
        daysPresent = daysPresent,
        dailyRate = selectedPayrollEmployee?.dailyRate ?: 0.0,
        overtimeHours = overtimeHours,
        overtimeRatePerHour = selectedPayrollEmployee?.overtimeRatePerHour ?: 0.0,
        allowance = allowance,
        deductions = deductions
    )
    val basePay = payrollAmounts.basePay
    val overtimePay = payrollAmounts.overtimePay
    val totalPay = payrollAmounts.totalPay
    val paidNow = paidNowText.toDoubleOrNull() ?: 0.0

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("workforce_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Karyawan, Absensi & Payroll", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Data dicatat ke Room Database lokal. Total gaji berasal dari absensi, tarif harian, dan jam lembur.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            TabRow(selectedTabIndex = when (activeSection) { "employee" -> 0; "attendance" -> 1; else -> 2 }) {
                listOf("employee" to "Karyawan", "attendance" to "Absensi", "payroll" to "Payroll").forEachIndexed { index, pair ->
                    Tab(
                        selected = activeSection == pair.first,
                        onClick = { activeSection = pair.first },
                        text = { Text(pair.second) },
                        modifier = Modifier.testTag("workforce_tab_${pair.first}")
                    )
                }
            }
        }
        if (activeSection == "employee") {
        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Tambah karyawan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama karyawan *") }, modifier = Modifier.fillMaxWidth().testTag("input_employee_name"), singleLine = true)
                    OutlinedTextField(value = position, onValueChange = { position = it }, label = { Text("Jabatan / bagian *") }, modifier = Modifier.fillMaxWidth().testTag("input_employee_position"), singleLine = true)
                    OutlinedTextField(value = dailyRateText, onValueChange = { dailyRateText = it.filter(Char::isDigit) }, label = { Text("Tarif per hari (Rp) *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("input_employee_daily_rate"), singleLine = true)
                    OutlinedTextField(value = standardHoursText, onValueChange = { standardHoursText = it }, label = { Text("Jam standar per shift *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("input_employee_standard_hours"), singleLine = true)
                    OutlinedTextField(value = overtimeRateText, onValueChange = { overtimeRateText = it.filter(Char::isDigit) }, label = { Text("Tarif lembur per jam (Rp) *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("input_employee_overtime_rate"), singleLine = true)
                    Button(onClick = {
                        val rate = dailyRateText.toDoubleOrNull()
                        val standard = standardHoursText.toDoubleOrNull()
                        val overtimeRate = overtimeRateText.toDoubleOrNull()
                        if (name.isNotBlank() && position.isNotBlank() && rate != null && rate >= 0 && standard != null && standard > 0 && overtimeRate != null && overtimeRate >= 0) {
                            onSaveEmployee(EmployeeRecord(UUID.randomUUID().toString(), name.trim(), position.trim(), rate, standard, overtimeRate, true, ""))
                            name = ""; position = ""; dailyRateText = ""; standardHoursText = "8"; overtimeRateText = ""
                        }
                    }, modifier = Modifier.fillMaxWidth().testTag("btn_save_employee")) { Text("Simpan Karyawan") }
                }
            }
        }
        if (activeSection == "attendance") {
        }
        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Absensi shift", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = attendanceDate, onValueChange = { attendanceDate = it }, label = { Text("Tanggal (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth().testTag("input_attendance_date"), singleLine = true)
                    Text("Karyawan")
                    OutlinedButton(onClick = { attendanceEmployeeMenu = true }, modifier = Modifier.fillMaxWidth().testTag("select_attendance_employee")) { Text(selectedAttendanceEmployee?.name ?: "Pilih karyawan") }
                    DropdownMenu(expanded = attendanceEmployeeMenu, onDismissRequest = { attendanceEmployeeMenu = false }) {
                        employees.filter { it.active }.forEach { emp -> DropdownMenuItem(text = { Text(emp.name) }, onClick = { attendanceEmployeeId = emp.id; attendanceEmployeeMenu = false }) }
                    }
                    OutlinedTextField(value = shiftName, onValueChange = { shiftName = it }, label = { Text("Nama shift") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(value = clockIn, onValueChange = { clockIn = it }, label = { Text("Jam masuk (HH:mm)") }, modifier = Modifier.weight(1f).testTag("input_attendance_in"), singleLine = true)
                        OutlinedTextField(value = clockOut, onValueChange = { clockOut = it }, label = { Text("Jam keluar (HH:mm)") }, modifier = Modifier.weight(1f).testTag("input_attendance_out"), singleLine = true)
                    }
                    OutlinedTextField(value = breakMinutesText, onValueChange = { breakMinutesText = it.filter(Char::isDigit) }, label = { Text("Istirahat (menit)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("input_attendance_break"), singleLine = true)
                    Text("Status")
                    OutlinedButton(onClick = { statusMenu = true }, modifier = Modifier.fillMaxWidth().testTag("select_attendance_status")) { Text(attendanceStatus) }
                    DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                        attendanceStatuses.forEach { st -> DropdownMenuItem(text = { Text(st) }, onClick = { attendanceStatus = st; statusMenu = false }) }
                    }
                    OutlinedTextField(value = attendanceNotes, onValueChange = { attendanceNotes = it }, label = { Text("Catatan") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Text("Jam lembur dihitung dari jam masuk/keluar dikurangi istirahat dan jam standar karyawan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = {
                        if (attendanceEmployeeId.isNotBlank() && attendanceDate.length == 10 && (attendanceStatus != "PRESENT" || (clockIn.length == 5 && clockOut.length == 5)) ) {
                            val existing = attendance.firstOrNull { it.employeeId == attendanceEmployeeId && it.workDate == attendanceDate }
                            onSaveAttendance(
                                AttendanceRecord(
                                    id = existing?.id ?: UUID.randomUUID().toString(),
                                    employeeId = attendanceEmployeeId,
                                    employeeName = selectedAttendanceEmployee?.name.orEmpty(),
                                    workDate = attendanceDate,
                                    shiftName = shiftName.trim(),
                                    clockIn = clockIn,
                                    clockOut = clockOut,
                                    breakMinutes = breakMinutesText.toIntOrNull() ?: 0,
                                    overtimeHours = 0.0,
                                    status = attendanceStatus,
                                    notes = attendanceNotes.trim(),
                                    updatedAt = ""
                                )
                            )
                            clockIn = ""; clockOut = ""; attendanceNotes = ""
                        }
                    }, modifier = Modifier.fillMaxWidth().testTag("btn_save_attendance")) { Text("Simpan Absensi") }
                }
            }
        }
        if (activeSection == "payroll") {
        }
        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Hitung payroll", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = payrollPeriod, onValueChange = { payrollPeriod = it }, label = { Text("Periode (YYYY-MM)") }, modifier = Modifier.fillMaxWidth().testTag("input_payroll_period"), singleLine = true)
                    Text("Karyawan")
                    OutlinedButton(onClick = { payrollEmployeeMenu = true }, modifier = Modifier.fillMaxWidth().testTag("select_payroll_employee")) { Text(selectedPayrollEmployee?.name ?: "Pilih karyawan") }
                    DropdownMenu(expanded = payrollEmployeeMenu, onDismissRequest = { payrollEmployeeMenu = false }) {
                        employees.filter { it.active }.forEach { emp -> DropdownMenuItem(text = { Text(emp.name) }, onClick = { payrollEmployeeId = emp.id; payrollEmployeeMenu = false }) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Hari hadir", style = MaterialTheme.typography.bodyMedium)
                        Text(AyamKasFormatters.formatNumber(daysPresent), fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Akumulasi lembur", style = MaterialTheme.typography.bodyMedium)
                        Text("${AyamKasFormatters.formatNumber(overtimeHours)} jam", fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Gaji pokok", style = MaterialTheme.typography.bodyMedium)
                        Text(AyamKasFormatters.formatRupiah(basePay), fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Upah lembur otomatis", style = MaterialTheme.typography.bodyMedium)
                        Text(AyamKasFormatters.formatRupiah(overtimePay), fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedTextField(value = allowanceText, onValueChange = { allowanceText = it.filter(Char::isDigit) }, label = { Text("Tunjangan (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("input_payroll_allowance"), singleLine = true)
                    OutlinedTextField(value = deductionsText, onValueChange = { deductionsText = it.filter(Char::isDigit) }, label = { Text("Potongan disetujui (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("input_payroll_deductions"), singleLine = true)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total gaji bersih", fontWeight = FontWeight.Bold)
                        Text(AyamKasFormatters.formatRupiah(totalPay), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }
                    OutlinedTextField(value = paidNowText, onValueChange = { paidNowText = it.filter(Char::isDigit) }, label = { Text("Dibayar sekarang (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("input_payroll_paid"), singleLine = true)
                    Text("Metode pembayaran upah")
                    OutlinedButton(onClick = { payrollPaymentMenu = true }, modifier = Modifier.fillMaxWidth().testTag("select_payroll_payment_method")) { Text(payrollPaymentMethod) }
                    DropdownMenu(expanded = payrollPaymentMenu, onDismissRequest = { payrollPaymentMenu = false }) {
                        listOf("CASH", "TRANSFER", "QRIS").forEach { method ->
                            DropdownMenuItem(text = { Text(method) }, onClick = {
                                payrollPaymentMethod = method
                                payrollCashAccount = when (method) { "CASH" -> "DOMPET"; "QRIS" -> "QRIS"; else -> "ATM/BANK" }
                                payrollPaymentMenu = false
                            })
                        }
                    }
                    Text("Akun asal pembayaran")
                    OutlinedButton(onClick = { payrollAccountMenu = true }, modifier = Modifier.fillMaxWidth().testTag("select_payroll_cash_account")) { Text(payrollCashAccount) }
                    DropdownMenu(expanded = payrollAccountMenu, onDismissRequest = { payrollAccountMenu = false }) {
                        listOf("DOMPET", "ATM/BANK", "QRIS").forEach { account ->
                            DropdownMenuItem(text = { Text(account) }, onClick = { payrollCashAccount = account; payrollAccountMenu = false })
                        }
                    }
                    OutlinedTextField(value = payrollNotes, onValueChange = { payrollNotes = it }, label = { Text("Catatan payroll") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = approvePayroll, onCheckedChange = { approvePayroll = it }, modifier = Modifier.testTag("checkbox_approve_payroll"))
                        Column {
                            Text("Setujui dan posting biaya gaji", fontWeight = FontWeight.SemiBold)
                            Text("Jika belum disetujui, payroll tetap draf dan tidak masuk laporan laba.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Button(onClick = {
                        if (selectedPayrollEmployee != null && payrollPeriod.length == 7 && daysPresent > 0 && allowance >= 0 && deductions >= 0 && paidNow >= 0 && paidNow <= totalPay && totalPay >= 0) {
                            val existing = payroll.firstOrNull { it.employeeId == payrollEmployeeId && it.period == payrollPeriod }
                            val status = if (!approvePayroll) "DRAFT" else when {
                                paidNow <= 0.0 -> "APPROVED"
                                paidNow + 0.0001 >= totalPay -> "PAID"
                                else -> "PART_PAID"
                            }
                            onSavePayroll(
                                PayrollRecord(
                                    id = existing?.id ?: UUID.randomUUID().toString(),
                                    employeeId = selectedPayrollEmployee.id,
                                    employeeName = selectedPayrollEmployee.name,
                                    period = payrollPeriod,
                                    daysPresent = daysPresent,
                                    dailyRate = selectedPayrollEmployee.dailyRate,
                                    basePay = basePay,
                                    overtimeHours = overtimeHours,
                                    overtimePay = overtimePay,
                                    allowance = allowance,
                                    deductions = deductions,
                                    totalPay = totalPay,
                                    paidAmount = paidNow,
                                    paymentMethod = payrollPaymentMethod,
                                    cashAccount = payrollCashAccount,
                                    status = status,
                                    notes = payrollNotes.trim(),
                                    updatedAt = ""
                                )
                            )
                            payrollNotes = ""; approvePayroll = false
                        }
                    }, modifier = Modifier.fillMaxWidth().testTag("btn_save_payroll")) { Text("Simpan Payroll") }
                }
            }
        }
        if (activeSection == "attendance") {
        }
        item { Text("Absensi terbaru", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (attendance.isEmpty()) item { Text("Belum ada absensi.") }
        else items(attendance.take(30), key = { it.id }) { row ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("${row.employeeName} · ${row.workDate}", fontWeight = FontWeight.Bold)
                    Text("${row.status} · ${row.shiftName} · ${row.clockIn}–${row.clockOut}")
                    Text("Lembur terhitung: ${AyamKasFormatters.formatNumber(row.overtimeHours)} jam", style = MaterialTheme.typography.bodySmall)
                    if (row.notes.isNotBlank()) Text(row.notes, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (activeSection == "payroll") {
        }
        item { Text("Payroll tersimpan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (payroll.isEmpty()) item { Text("Belum ada rekap payroll.") }
        else items(payroll.take(30), key = { it.id }) { row ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("${row.employeeName} · ${row.period}", fontWeight = FontWeight.Bold)
                    Text("Gaji ${AyamKasFormatters.formatRupiah(row.totalPay)} · dibayar ${AyamKasFormatters.formatRupiah(row.paidAmount)}")
                    Text("${row.status} · hadir ${AyamKasFormatters.formatNumber(row.daysPresent)} hari · lembur ${AyamKasFormatters.formatNumber(row.overtimeHours)} jam", style = MaterialTheme.typography.bodySmall)
                    if (row.notes.isNotBlank()) Text(row.notes, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
