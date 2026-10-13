package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AyamTransaction
import com.example.data.model.AyamTransactionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormDialog(
    transaction: AyamTransaction?,
    presetType: String?,
    availablePurchaseBatches: List<AyamTransaction>,
    onDismiss: () -> Unit,
    onSave: (
        id: String?,
        date: String,
        ref: String,
        type: String,
        category: String,
        description: String,
        amount: Double,
        paidAmount: Double?,
        qtyKg: Double?,
        batchRef: String,
        notes: String,
        isConfirmed: Boolean,
        paymentMethod: String,
        cashAccount: String
    ) -> Unit
) {
    val defaultDate = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    var date by remember { mutableStateOf(transaction?.date ?: defaultDate) }
    var type by remember { mutableStateOf(presetType ?: transaction?.type ?: "sale") }
    var ref by remember { mutableStateOf(transaction?.ref ?: "") }
    var category by remember { mutableStateOf(transaction?.category ?: if (type == "purchase") "Ayam / DO" else "Penjualan") }
    var description by remember { mutableStateOf(transaction?.description ?: "") }
    var amountText by remember { mutableStateOf(transaction?.amount?.toLong()?.toString() ?: "") }
    var paidAmountText by remember {
        mutableStateOf(
            if (transaction?.paidAmount != null) transaction.paidAmount.toLong().toString() else ""
        )
    }
    var qtyKgText by remember {
        mutableStateOf(
            if (transaction?.qtyKg != null) transaction.qtyKg.toString() else ""
        )
    }
    var batchRef by remember { mutableStateOf(transaction?.batchRef ?: "") }
    var notes by remember { mutableStateOf(transaction?.notes ?: "") }
    var isConfirmed by remember { mutableStateOf(transaction?.isConfirmed ?: true) }
    var paymentMethod by remember { mutableStateOf(transaction?.paymentMethod ?: "CASH") }
    var cashAccount by remember { mutableStateOf(transaction?.cashAccount ?: "DOMPET") }

    var typeMenuExpanded by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var batchMenuExpanded by remember { mutableStateOf(false) }
    var paymentMenuExpanded by remember { mutableStateOf(false) }
    var accountMenuExpanded by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val categories = listOf(
        "Umum", "Ayam / DO", "Penjualan", "Admin", "Pakan", "Transportasi",
        "Kondangan", "Gaji", "Listrik / air", "Peralatan", "Modal owner",
        "Pembayaran utang", "Lainnya"
    )

    val showQty = type == "purchase" || type == "sale"
    val showBatch = type == "sale" || type == "supplier_payment" || type == "receivable_payment"
    val isCashOnly = type in listOf("capital_in", "capital_out", "supplier_payment", "receivable_payment")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .padding(vertical = 24.dp)
                .testTag("tx_form_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (transaction == null) "Transaksi Baru" else if (transaction.isConfirmed) "Edit Transaksi" else "Tinjau Draf Catatan",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Catat transaksi dengan nominal dan data aktual",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("btn_close_dialog")) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Error message
                if (errorMessage != null) {
                    Surface(
                        color = Color(0xFFFFF0EE),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = Color(0xFFB33C35),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Tanggal
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Tanggal Transaksi (YYYY-MM-DD) *") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_tx_date"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Jenis Transaksi Dropdown
                ExposedDropdownMenuBox(
                    expanded = typeMenuExpanded,
                    onExpandedChange = { typeMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = AyamTransactionType.fromKey(type).label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Jenis Transaksi *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .testTag("dropdown_tx_type")
                    )
                    ExposedDropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false }
                    ) {
                        AyamTransactionType.entries.forEach { t ->
                            DropdownMenuItem(
                                text = { Text("${t.icon} ${t.label}") },
                                onClick = {
                                    if (t.key == "purchase" && transaction == null) {
                                        errorMessage = "Untuk DO baru gunakan tombol DO Baru agar supplier, susut, kematian, dan HPP batch tercatat lengkap."
                                    } else {
                                        type = t.key
                                        typeMenuExpanded = false
                                        if (t.key == "purchase") category = "Ayam / DO"
                                        if (t.key == "sale") category = "Penjualan"
                                        if (t.key == "expense") category = "Admin"
                                        if (t.key == "capital_in") category = "Modal owner"
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // No Referensi
                OutlinedTextField(
                    value = ref,
                    onValueChange = { ref = it },
                    label = { Text("No. Referensi (contoh: DO-010 / K-001)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_tx_ref"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Kategori Dropdown
                ExposedDropdownMenuBox(
                    expanded = categoryMenuExpanded,
                    onExpandedChange = { categoryMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Kategori") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .testTag("dropdown_tx_category")
                    )
                    ExposedDropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false }
                    ) {
                        categories.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c) },
                                onClick = {
                                    category = c
                                    categoryMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Keterangan
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Keterangan *") },
                    placeholder = { Text("Contoh: Pembelian ayam 100 kg dari supplier") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_tx_description"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Nominal Transaksi
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it.filter { char -> char.isDigit() }
                        if (isCashOnly) {
                            paidAmountText = amountText
                        }
                    },
                    label = { Text("Nominal Transaksi (Rp) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_tx_amount"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Nominal Kas Diterima/Dibayar
                OutlinedTextField(
                    value = if (isCashOnly) amountText else paidAmountText,
                    onValueChange = { paidAmountText = it.filter { char -> char.isDigit() } },
                    enabled = !isCashOnly,
                    label = {
                        Text(if (isCashOnly) "Kas Diterima / Dibayar (Otomatis)" else "Diterima / Dibayar Tunai Saat Ini (Rp) *")
                    },
                    placeholder = { Text("Boleh 0 atau sebagian (DP)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        Text("Selisih penjualan jadi piutang; selisih pembelian DO jadi utang.")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_tx_paid_amount"),
                    singleLine = true
                )

                // Actual payment channel/account for cash-book reconciliation.
                if (!isCashOnly) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Metode Pembayaran", style = MaterialTheme.typography.labelMedium)
                            ExposedDropdownMenuBox(
                                expanded = paymentMenuExpanded,
                                onExpandedChange = { paymentMenuExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = paymentMethod,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Metode") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = paymentMenuExpanded) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor().testTag("dropdown_payment_method")
                                )
                                ExposedDropdownMenu(expanded = paymentMenuExpanded, onDismissRequest = { paymentMenuExpanded = false }) {
                                    listOf("CASH", "TRANSFER", "QRIS").forEach { option ->
                                        DropdownMenuItem(text = { Text(option) }, onClick = {
                                            paymentMethod = option
                                            paymentMenuExpanded = false
                                            if (option == "QRIS") cashAccount = "QRIS"
                                            if (option == "TRANSFER") cashAccount = "ATM/BANK"
                                            if (option == "CASH") cashAccount = "DOMPET"
                                        })
                                    }
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Akun Kas", style = MaterialTheme.typography.labelMedium)
                            ExposedDropdownMenuBox(
                                expanded = accountMenuExpanded,
                                onExpandedChange = { accountMenuExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = cashAccount,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Sumber/Tujuan") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountMenuExpanded) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor().testTag("dropdown_cash_account")
                                )
                                ExposedDropdownMenu(expanded = accountMenuExpanded, onDismissRequest = { accountMenuExpanded = false }) {
                                    listOf("DOMPET", "ATM/BANK", "QRIS").forEach { option ->
                                        DropdownMenuItem(text = { Text(option) }, onClick = {
                                            cashAccount = option
                                            accountMenuExpanded = false
                                        })
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Berat (Kg)
                if (showQty) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = qtyKgText,
                        onValueChange = { qtyKgText = it },
                        label = { Text(if (type == "purchase") "Berat Pembelian (kg) *" else "Berat Penjualan (kg)") },
                        placeholder = { Text("Contoh: 75.25") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = {
                            Text(if (type == "purchase") "Wajib diisi agar HPP/kg dan stok dapat dihitung." else "Isi kg terjual agar harga pokok dan laba dihitung.")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_tx_qty"),
                        singleLine = true
                    )
                }

                // Batch DO linkage
                if (showBatch) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ExposedDropdownMenuBox(
                        expanded = batchMenuExpanded,
                        onExpandedChange = { batchMenuExpanded = it }
                    ) {
                        val selectedLabel = if (batchRef.isBlank()) {
                            "— Tidak Ditautkan ke DO —"
                        } else {
                            val found = availablePurchaseBatches.firstOrNull { it.ref.equals(batchRef, ignoreCase = true) }
                            if (found != null) "DO ${found.ref} (${found.qtyKg ?: 0.0} kg) - ${found.date}" else "DO $batchRef"
                        }

                        OutlinedTextField(
                            value = selectedLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Hubungkan ke DO Pembelian") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = batchMenuExpanded) },
                            supportingText = {
                                Text("Tautkan penjualan ke DO agar HPP dan stok berkurang.")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .testTag("dropdown_tx_batch")
                        )
                        ExposedDropdownMenu(
                            expanded = batchMenuExpanded,
                            onDismissRequest = { batchMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("— Tidak Ditautkan —") },
                                onClick = {
                                    batchRef = ""
                                    batchMenuExpanded = false
                                }
                            )
                            availablePurchaseBatches.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text("DO ${p.ref} (${p.qtyKg ?: 0.0} kg) · ${p.date}") },
                                    onClick = {
                                        batchRef = p.ref
                                        batchMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Catatan
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Catatan / Bukti / No. Nota") },
                    placeholder = { Text("Catatan tambahan atau detail pembayaran...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_tx_notes"),
                    minLines = 2
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Checkbox status konfirmasi
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isConfirmed = !isConfirmed },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isConfirmed,
                        onCheckedChange = { isConfirmed = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF176B50)),
                        modifier = Modifier.testTag("checkbox_confirm_tx")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Konfirmasi transaksi dan masukkan ke laporan",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Jika masih draf atau belum pasti, hilangkan centang.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.testTag("btn_cancel_tx")) {
                        Text("Batal")
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            val amt = amountText.toDoubleOrNull()
                            if (amt == null || amt <= 0.0) {
                                errorMessage = "Nominal transaksi harus lebih dari nol."
                                return@Button
                            }
                            val paidAmt = if (isCashOnly) amt else paidAmountText.toDoubleOrNull()
                            if (paidAmt == null || paidAmt < 0.0) {
                                errorMessage = "Nominal yang dibayar/diterima wajib diisi (boleh 0)."
                                return@Button
                            }
                            if (paidAmt > amt && (type == "sale" || type == "purchase" || type == "expense")) {
                                errorMessage = "Pembayaran tunai tidak boleh melebihi nominal transaksi."
                                return@Button
                            }
                            val qty = qtyKgText.toDoubleOrNull()
                            if (type == "purchase" && (qty == null || qty <= 0.0)) {
                                errorMessage = "Pembelian DO wajib mengisi berat kg ayam."
                                return@Button
                            }
                            if (description.isBlank()) {
                                errorMessage = "Keterangan transaksi wajib diisi."
                                return@Button
                            }

                            onSave(
                                transaction?.id,
                                date,
                                ref,
                                type,
                                category,
                                description,
                                amt,
                                paidAmt,
                                qty,
                                batchRef,
                                notes,
                                isConfirmed,
                                paymentMethod,
                                cashAccount
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF176B50)),
                        modifier = Modifier.testTag("btn_submit_tx")
                    ) {
                        Text("Simpan Transaksi")
                    }
                }
            }
        }
    }
}
