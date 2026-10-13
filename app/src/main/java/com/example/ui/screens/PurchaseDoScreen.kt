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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.example.data.model.AyamTransaction
import com.example.data.model.AyamKasFormatters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PurchaseDoScreen(
    initialTransaction: AyamTransaction? = null,
    onCancel: () -> Unit,
    onSave: (
        date: String,
        doNumber: String,
        quantityKg: Double,
        supplier: String,
        totalPrice: Double,
        paidNow: Double,
        paymentMethod: String,
        cashAccount: String,
        weightLossKg: Double,
        mortalityRatePct: Double,
        fcr: Double?,
        weightGainKg: Double?,
        feedPricePerKg: Double?,
        processingCost: Double,
        notes: String,
        confirmed: Boolean,
        editingId: String?
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    var date by remember(initialTransaction?.id) { mutableStateOf(initialTransaction?.date ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var doNumber by remember(initialTransaction?.id) { mutableStateOf(initialTransaction?.ref ?: "") }
    var quantity by remember(initialTransaction?.id) { mutableStateOf(initialTransaction?.qtyKg?.toString() ?: "") }
    var supplier by remember(initialTransaction?.id) { mutableStateOf(initialTransaction?.supplier ?: "") }
    var totalPrice by remember(initialTransaction?.id) { mutableStateOf(initialTransaction?.amount?.toLong()?.toString() ?: "") }
    var paidNow by remember(initialTransaction?.id) { mutableStateOf(initialTransaction?.paidAmount?.toLong()?.toString() ?: "") }
    var weightLoss by remember(initialTransaction?.id) { mutableStateOf(initialTransaction?.weightLossKg?.toString() ?: "") }
    var mortalityRate by remember(initialTransaction?.id) { mutableStateOf(initialTransaction?.mortalityRatePct?.toString() ?: "") }
    var fcr by remember(initialTransaction?.id) { mutableStateOf(initialTransaction?.fcr?.toString() ?: "") }
    var weightGain by remember(initialTransaction?.id) { mutableStateOf(initialTransaction?.weightGainKg?.toString() ?: "") }
    var feedPrice by remember(initialTransaction?.id) { mutableStateOf(initialTransaction?.feedPricePerKg?.toLong()?.toString() ?: "") }
    var processingCost by remember(initialTransaction?.id) { mutableStateOf(initialTransaction?.processingCost?.toLong()?.toString() ?: "0") }
    var notes by remember(initialTransaction?.id) { mutableStateOf(initialTransaction?.notes ?: "") }
    var method by remember(initialTransaction?.id) { mutableStateOf(initialTransaction?.paymentMethod ?: "CASH") }
    var account by remember(initialTransaction?.id) { mutableStateOf(initialTransaction?.cashAccount ?: "DOMPET") }
    var confirmed by remember(initialTransaction?.id) { mutableStateOf(initialTransaction?.isConfirmed ?: false) }
    var menuMethod by remember { mutableStateOf(false) }
    var menuAccount by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var showFeedDetails by remember(initialTransaction?.id) { mutableStateOf(initialTransaction?.fcr != null || initialTransaction?.weightGainKg != null || initialTransaction?.feedPricePerKg != null) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.testTag("btn_close_purchase_do")) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Kembali")
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(if (initialTransaction == null) "Pembelian DO Ayam" else "Edit Pembelian DO Ayam", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (initialTransaction == null) "Catat berdasarkan DO/faktur sebenarnya; tersimpan ke database lokal." else "Perbaiki data dari DO yang sudah ada. Referensi tetap dipertahankan agar tautan penjualan tidak putus.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.Inventory2, contentDescription = "Pembelian DO", tint = MaterialTheme.colorScheme.primary)
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Data penerimaan", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = date, onValueChange = { date = it }, label = { Text("Tanggal (YYYY-MM-DD) *") },
                    modifier = Modifier.fillMaxWidth().testTag("input_do_date"), singleLine = true
                )
                OutlinedTextField(
                    value = doNumber, onValueChange = { doNumber = it }, label = { Text("Nomor DO / referensi unik *") },
                    modifier = Modifier.fillMaxWidth().testTag("input_do_number"), singleLine = true
                )
                OutlinedTextField(
                    value = supplier, onValueChange = { supplier = it }, label = { Text("Supplier *") },
                    modifier = Modifier.fillMaxWidth().testTag("input_do_supplier"), singleLine = true
                )
                OutlinedTextField(
                    value = quantity, onValueChange = { quantity = it }, label = { Text("Berat pembelian (kg) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("input_do_quantity_kg"), singleLine = true
                )
                OutlinedTextField(
                    value = totalPrice, onValueChange = { totalPrice = it.filter(Char::isDigit) },
                    label = { Text("Harga total DO (Rp) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("input_do_total_price"), singleLine = true
                )
                OutlinedTextField(
                    value = paidNow, onValueChange = { paidNow = it.filter(Char::isDigit) },
                    label = { Text("Dibayar sekarang (Rp) * — isi 0 jika kredit") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("input_do_paid_now"), singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Metode pembayaran")
                        OutlinedButton(onClick = { menuMethod = true }, modifier = Modifier.fillMaxWidth().testTag("select_do_payment_method")) {
                            Text(method)
                        }
                        DropdownMenu(expanded = menuMethod, onDismissRequest = { menuMethod = false }) {
                            listOf("CASH", "TRANSFER", "QRIS").forEach { item ->
                                DropdownMenuItem(text = { Text(item) }, onClick = { method = item; menuMethod = false })
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sumber akun kas")
                        OutlinedButton(onClick = { menuAccount = true }, modifier = Modifier.fillMaxWidth().testTag("select_do_cash_account")) {
                            Text(account)
                        }
                        DropdownMenu(expanded = menuAccount, onDismissRequest = { menuAccount = false }) {
                            listOf("DOMPET", "ATM/BANK", "QRIS").forEach { item ->
                                DropdownMenuItem(text = { Text(item) }, onClick = { account = item; menuAccount = false })
                            }
                        }
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Data HPP batch (wajib agar stok bersih dapat dipercaya)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Isi angka nyata. Untuk tidak ada penyusutan/kematian, masukkan 0. Berat penjualan harus menggunakan basis yang sama dengan berat hasil DO setelah susut.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = weightLoss, onValueChange = { weightLoss = it },
                    label = { Text("Penyusutan berat (kg) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("input_do_weight_loss"), singleLine = true
                )
                OutlinedTextField(
                    value = mortalityRate, onValueChange = { mortalityRate = it },
                    label = { Text("Kematian (% ekuivalen berat DO) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("input_do_mortality_rate"), singleLine = true
                )
                OutlinedButton(
                    onClick = { showFeedDetails = !showFeedDetails },
                    modifier = Modifier.fillMaxWidth().testTag("btn_toggle_feed_details")
                ) {
                    Text(if (showFeedDetails) "Sembunyikan pengaturan pakan lanjutan" else "Tambah pengaturan pakan (opsional)")
                }
                if (showFeedDetails) {
                    Text("Jika batch mendapat pakan, isi ketiga bidang FCR di bawah. Biaya pakan otomatis masuk ke biaya batch sebesar FCR × pertambahan berat × harga pakan/kg.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = fcr, onValueChange = { fcr = it },
                        label = { Text("FCR (opsional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag("input_do_fcr"), singleLine = true
                    )
                    OutlinedTextField(
                        value = weightGain, onValueChange = { weightGain = it },
                        label = { Text("Pertambahan berat karena pakan (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag("input_do_weight_gain"), singleLine = true
                    )
                    OutlinedTextField(
                        value = feedPrice, onValueChange = { feedPrice = it.filter(Char::isDigit) },
                        label = { Text("Harga pakan per kg (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("input_do_feed_price"), singleLine = true
                    )
                }
                OutlinedTextField(
                    value = processingCost, onValueChange = { processingCost = it.filter(Char::isDigit) },
                    label = { Text("Biaya pemotongan/pengolahan batch (Rp) * — isi 0 jika tidak ada") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("input_do_processing_cost"), singleLine = true
                )
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Catatan / nomor faktur / bukti") },
                    modifier = Modifier.fillMaxWidth().testTag("input_do_notes"), minLines = 2
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(checked = confirmed, onCheckedChange = { confirmed = it }, modifier = Modifier.testTag("checkbox_confirm_do"))
            Column(Modifier.weight(1f)) {
                Text("Konfirmasi pembelian", fontWeight = FontWeight.SemiBold)
                Text("Centang hanya jika DO, berat, harga, dan pembayaran sudah diperiksa.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("label_do_error"))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Batal") }
            Button(onClick = {
                val qty = quantity.toDoubleOrNull()
                val price = totalPrice.toDoubleOrNull()
                val paid = paidNow.toDoubleOrNull()
                val loss = weightLoss.toDoubleOrNull()
                val death = mortalityRate.toDoubleOrNull()
                val fcrValue = fcr.toDoubleOrNull()
                val gain = weightGain.toDoubleOrNull()
                val feed = feedPrice.toDoubleOrNull()
                val process = processingCost.toDoubleOrNull()
                error = when {
                    !AyamKasFormatters.isValidIsoDate(date) -> "Tanggal tidak valid. Gunakan tanggal nyata dengan format YYYY-MM-DD."
                    doNumber.isBlank() -> "Nomor DO wajib diisi."
                    supplier.isBlank() -> "Supplier wajib diisi."
                    qty == null || qty <= 0.0 -> "Berat DO harus lebih dari nol."
                    price == null || price <= 0.0 -> "Harga total wajib diisi dan lebih dari nol."
                    paid == null || paid < 0.0 || paid > price -> "Pembayaran wajib diisi antara Rp0 dan total harga."
                    loss == null || loss < 0.0 || loss > qty -> "Penyusutan wajib diisi dan tidak boleh lebih besar dari kg DO."
                    death == null || death !in 0.0..100.0 -> "Kematian wajib diisi dalam rentang 0–100%."
                    process == null || process < 0.0 -> "Biaya pemotongan wajib diisi (boleh 0)."
                    listOf(fcr, weightGain, feedPrice).count { it.isNotBlank() } !in listOf(0, 3) -> "Isi FCR, pertambahan berat, dan harga pakan sekaligus."
                    fcr.isNotBlank() && (fcrValue == null || fcrValue < 0.0 || gain == null || gain < 0.0 || feed == null || feed < 0.0) -> "Data FCR/pakan harus angka valid dan tidak negatif."
                    else -> ""
                }
                if (error.isBlank()) {
                    onSave(date, doNumber.trim(), qty!!, supplier.trim(), price!!, paid!!, method, account, loss!!, death!!, fcrValue, gain, feed, process!!, notes.trim(), confirmed, initialTransaction?.id)
                }
            }, modifier = Modifier.weight(1f).testTag("btn_save_purchase_do")) {
                Text(if (initialTransaction == null) "Simpan DO" else "Simpan Perubahan DO")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
