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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
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
import com.example.data.model.AyamKasFormatters
import com.example.data.model.CashFlow
import com.example.data.model.ExternalLoan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun FinanceScreen(
    cashBalances: Map<String, Double>,
    cashFlows: List<CashFlow>,
    loans: List<ExternalLoan>,
    onSaveCashFlow: (CashFlow) -> Unit,
    onSaveLoan: (ExternalLoan) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    var date by remember { mutableStateOf(today) }
    var fromAccount by remember { mutableStateOf("ATM/BANK") }
    var toAccount by remember { mutableStateOf("DOMPET") }
    var amountText by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var transferNotes by remember { mutableStateOf("") }
    var fromMenu by remember { mutableStateOf(false) }
    var toMenu by remember { mutableStateOf(false) }
    var confirmedTransfer by remember { mutableStateOf(true) }

    var loanDate by remember { mutableStateOf(today) }
    var party by remember { mutableStateOf("") }
    var loanReference by remember { mutableStateOf("") }
    var loanType by remember { mutableStateOf("OWNER_LOAN") }
    var direction by remember { mutableStateOf("RECEIVED") }
    var loanAmount by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf("") }
    var loanAccount by remember { mutableStateOf("ATM/BANK") }
    var loanNotes by remember { mutableStateOf("") }
    var loanTypeMenu by remember { mutableStateOf(false) }
    var loanDirectionMenu by remember { mutableStateOf(false) }
    var loanAccountMenu by remember { mutableStateOf(false) }
    var confirmedLoan by remember { mutableStateOf(true) }

    val accounts = listOf("DOMPET", "ATM/BANK", "QRIS")
    val loanTypeLabels = linkedMapOf(
        "OWNER_LOAN" to "Pinjaman dari pemilik",
        "BUSINESS_BORROWING" to "Pinjaman usaha dari pihak lain",
        "BUSINESS_LENDING" to "Pinjaman usaha ke pihak lain",
        "OWNER_PERSONAL" to "Pengeluaran pribadi pemilik",
        "UNEXPECTED_COST" to "Pengeluaran lain di luar operasional",
        "OWNER_INJECTION" to "Tambahan modal pemilik"
    )
    val directionLabels = linkedMapOf(
        "RECEIVED" to "Dana diterima",
        "REPAYMENT" to "Bayar angsuran / pelunasan",
        "GIVEN" to "Dana diberikan / dikeluarkan",
        "COLLECTION" to "Terima angsuran / penagihan"
    )
    val loanDirectionOptions = when (loanType) {
        "OWNER_LOAN", "BUSINESS_BORROWING" -> listOf("RECEIVED", "REPAYMENT")
        "BUSINESS_LENDING" -> listOf("GIVEN", "COLLECTION")
        "OWNER_INJECTION" -> listOf("RECEIVED")
        "OWNER_PERSONAL", "UNEXPECTED_COST" -> listOf("GIVEN")
        else -> emptyList()
    }
    val balanceTotal = cashBalances.values.sum()
    val loanPositionRows = loans
        .filter { it.status == "confirmed" && it.loanReference.isNotBlank() }
        .groupBy { it.loanReference.trim() }
        .map { (reference, records) ->
            val liabilityPrincipal = records.filter {
                it.loanType in listOf("OWNER_LOAN", "BUSINESS_BORROWING") && it.direction == "RECEIVED"
            }.sumOf { it.amount }
            val liabilityPaid = records.filter {
                it.loanType in listOf("OWNER_LOAN", "BUSINESS_BORROWING") && it.direction == "REPAYMENT"
            }.sumOf { it.amount }
            val receivablePrincipal = records.filter {
                it.loanType == "BUSINESS_LENDING" && it.direction == "GIVEN"
            }.sumOf { it.amount }
            val receivableCollected = records.filter {
                it.loanType == "BUSINESS_LENDING" && it.direction == "COLLECTION"
            }.sumOf { it.amount }
            Triple(reference, (liabilityPrincipal - liabilityPaid).coerceAtLeast(0.0), (receivablePrincipal - receivableCollected).coerceAtLeast(0.0))
        }.filter { it.second > 0.0 || it.third > 0.0 }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("finance_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Kas, Rekening & Pinjaman", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Arus akun dipisahkan dari laba usaha. Transfer antar rekening tidak dihitung sebagai pendapatan atau biaya.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Saldo kas menurut akun", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Total kas tercatat: ${AyamKasFormatters.formatRupiah(balanceTotal)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Total ini mencakup transaksi terverifikasi di semua akun; rekonsiliasi dengan rekening koran dan kas fisik.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf("DOMPET", "ATM/BANK", "QRIS", "BELUM_DIPETAKAN").forEach { account ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (account == "BELUM_DIPETAKAN") "BELUM DIPETAKAN · data historis" else account, style = MaterialTheme.typography.bodyMedium)
                            Text(AyamKasFormatters.formatRupiah(cashBalances[account] ?: 0.0), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text("Saldo ini berasal dari transaksi kas terverifikasi, catatan pinjaman yang terverifikasi, dan transfer akun yang tersimpan. Rekonsiliasi terhadap rekening koran dan uang fisik tetap diperlukan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Transfer antar rekening", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Contoh: pindah uang dari ATM ke dompet. Ini bukan pemasukan atau biaya baru.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Tanggal (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth().testTag("input_cashflow_date"), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("Dari")
                            OutlinedButton(onClick = { fromMenu = true }, modifier = Modifier.fillMaxWidth().testTag("select_cashflow_from")) { Text(fromAccount) }
                            DropdownMenu(expanded = fromMenu, onDismissRequest = { fromMenu = false }) {
                                accounts.forEach { a -> DropdownMenuItem(text = { Text(a) }, onClick = { fromAccount = a; fromMenu = false }) }
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Ke")
                            OutlinedButton(onClick = { toMenu = true }, modifier = Modifier.fillMaxWidth().testTag("select_cashflow_to")) { Text(toAccount) }
                            DropdownMenu(expanded = toMenu, onDismissRequest = { toMenu = false }) {
                                accounts.forEach { a -> DropdownMenuItem(text = { Text(a) }, onClick = { toAccount = a; toMenu = false }) }
                            }
                        }
                    }
                    OutlinedTextField(value = amountText, onValueChange = { amountText = it.filter(Char::isDigit) }, label = { Text("Nominal transfer (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("input_cashflow_amount"), singleLine = true)
                    OutlinedTextField(value = reference, onValueChange = { reference = it }, label = { Text("Referensi (mis. mutasi ATM)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = transferNotes, onValueChange = { transferNotes = it }, label = { Text("Catatan") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = confirmedTransfer, onCheckedChange = { confirmedTransfer = it }, modifier = Modifier.testTag("checkbox_confirm_cashflow"))
                        Text("Transfer sudah diverifikasi", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = {
                        val amount = amountText.toDoubleOrNull() ?: 0.0
                        if (com.example.data.model.AyamKasFormatters.isValidIsoDate(date) && amount > 0 && fromAccount != toAccount) {
                            onSaveCashFlow(CashFlow(UUID.randomUUID().toString(), date, fromAccount, toAccount, amount, reference.trim(), transferNotes.trim(), if (confirmedTransfer) "confirmed" else "draft", ""))
                            amountText = ""; reference = ""; transferNotes = ""
                        }
                    }, modifier = Modifier.fillMaxWidth().testTag("btn_save_cashflow")) { Text("Simpan Transfer") }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Pinjaman / transaksi di luar operasional", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Catatan ini tidak otomatis menambah omzet atau biaya operasional. Arah kasnya tetap masuk/keluar sesuai jenis peristiwa.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = loanDate, onValueChange = { loanDate = it }, label = { Text("Tanggal (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth().testTag("input_loan_date"), singleLine = true)
                    OutlinedTextField(value = party, onValueChange = { party = it }, label = { Text("Pihak / sumber pinjaman") }, modifier = Modifier.fillMaxWidth().testTag("input_loan_party"), singleLine = true)
                    OutlinedTextField(
                        value = loanReference, onValueChange = { loanReference = it },
                        label = { Text("Referensi pinjaman (gunakan kode sama saat pelunasan)") },
                        supportingText = { Text("Gunakan referensi yang sama untuk pencairan dan setiap angsuran agar sisa pinjaman dihitung akurat.") },
                        modifier = Modifier.fillMaxWidth().testTag("input_loan_reference"), singleLine = true
                    )
                    Text("Jenis transaksi")
                    OutlinedButton(onClick = { loanTypeMenu = true }, modifier = Modifier.fillMaxWidth().testTag("select_loan_type")) { Text(loanTypeLabels[loanType] ?: loanType) }
                    DropdownMenu(expanded = loanTypeMenu, onDismissRequest = { loanTypeMenu = false }) {
                        listOf("OWNER_LOAN", "BUSINESS_BORROWING", "BUSINESS_LENDING", "OWNER_PERSONAL", "UNEXPECTED_COST", "OWNER_INJECTION").forEach { a ->
                            DropdownMenuItem(text = { Text(loanTypeLabels[a] ?: a) }, onClick = {
                                loanType = a
                                val allowed = when (a) {
                                    "OWNER_LOAN", "BUSINESS_BORROWING" -> listOf("RECEIVED", "REPAYMENT")
                                    "BUSINESS_LENDING" -> listOf("GIVEN", "COLLECTION")
                                    "OWNER_INJECTION" -> listOf("RECEIVED")
                                    else -> listOf("GIVEN")
                                }
                                if (direction !in allowed) direction = allowed.first()
                                loanTypeMenu = false
                            })
                        }
                    }
                    Text("Arah kas")
                    OutlinedButton(onClick = { loanDirectionMenu = true }, modifier = Modifier.fillMaxWidth().testTag("select_loan_direction")) { Text(directionLabels[direction] ?: direction) }
                    DropdownMenu(expanded = loanDirectionMenu, onDismissRequest = { loanDirectionMenu = false }) {
                        loanDirectionOptions.forEach { a ->
                            DropdownMenuItem(text = { Text(directionLabels[a] ?: a) }, onClick = { direction = a; loanDirectionMenu = false })
                        }
                    }
                    OutlinedTextField(value = loanAmount, onValueChange = { loanAmount = it.filter(Char::isDigit) }, label = { Text("Nominal peristiwa kas (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("input_loan_amount"), singleLine = true)
                    OutlinedTextField(value = dueDate, onValueChange = { dueDate = it }, label = { Text("Jatuh tempo (opsional YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text("Akun kas")
                    OutlinedButton(onClick = { loanAccountMenu = true }, modifier = Modifier.fillMaxWidth().testTag("select_loan_account")) { Text(loanAccount) }
                    DropdownMenu(expanded = loanAccountMenu, onDismissRequest = { loanAccountMenu = false }) {
                        accounts.forEach { a -> DropdownMenuItem(text = { Text(a) }, onClick = { loanAccount = a; loanAccountMenu = false }) }
                    }
                    OutlinedTextField(value = loanNotes, onValueChange = { loanNotes = it }, label = { Text("Tujuan / keterangan / bukti") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = confirmedLoan, onCheckedChange = { confirmedLoan = it }, modifier = Modifier.testTag("checkbox_confirm_loan"))
                        Text("Peristiwa kas diverifikasi", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = {
                        val amount = loanAmount.toDoubleOrNull() ?: 0.0
                        val balanceTracked = loanType in listOf("OWNER_LOAN", "BUSINESS_BORROWING", "BUSINESS_LENDING")
                        if (com.example.data.model.AyamKasFormatters.isValidIsoDate(loanDate) && party.isNotBlank() && amount > 0.0 &&
                            (!balanceTracked || loanReference.isNotBlank()) &&
                            (direction !in listOf("REPAYMENT", "COLLECTION") || loanReference.isNotBlank())
                        ) {
                            onSaveLoan(ExternalLoan(
                                UUID.randomUUID().toString(), loanDate, party.trim(), loanReference.trim(),
                                loanType, direction, amount, amount, dueDate, loanAccount, loanNotes.trim(),
                                if (confirmedLoan) "confirmed" else "draft", ""
                            ))
                            party = ""; loanReference = ""; loanAmount = ""; dueDate = ""; loanNotes = ""
                        }
                    }, modifier = Modifier.fillMaxWidth().testTag("btn_save_loan")) { Text("Simpan Catatan Kas") }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sisa Pinjaman per Referensi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Saldo hanya dihitung dari peristiwa kas berstatus confirmed dan referensi yang sama.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (loanPositionRows.isEmpty()) {
                        Text("Belum ada saldo pinjaman terverifikasi yang tertaut ke referensi.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        loanPositionRows.forEach { row ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(row.first, fontWeight = FontWeight.SemiBold)
                                if (row.second > 0.0) Text("Utang pinjaman usaha/owner: ${AyamKasFormatters.formatRupiah(row.second)}", style = MaterialTheme.typography.bodySmall)
                                if (row.third > 0.0) Text("Piutang pinjaman ke pihak lain: ${AyamKasFormatters.formatRupiah(row.third)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        item {
            Text("Transfer tersimpan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (cashFlows.isEmpty()) item { Text("Belum ada transfer yang dicatat.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else items(cashFlows.take(30), key = { it.id }) { flow ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("${flow.fromAccount} → ${flow.toAccount}", fontWeight = FontWeight.Bold)
                    Text("${flow.date} · ${AyamKasFormatters.formatRupiah(flow.amount)}")
                    Text("${flow.reference} · ${flow.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (flow.notes.isNotBlank()) Text(flow.notes, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Text("Catatan pinjaman / kas luar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (loans.isEmpty()) item { Text("Belum ada catatan pinjaman atau biaya di luar operasional.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else items(loans.take(30), key = { it.id }) { loan ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("${loan.direction} · ${loan.party}", fontWeight = FontWeight.Bold)
                    Text("${loan.date} · ${AyamKasFormatters.formatRupiah(loan.amount)}")
                    Text("${loan.loanType} · ref ${loan.loanReference.ifBlank { "—" }} · akun ${loan.cashAccount} · ${loan.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (loan.notes.isNotBlank()) Text(loan.notes, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
