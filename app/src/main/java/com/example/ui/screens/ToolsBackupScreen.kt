package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AyamKasFormatters

@Composable
fun ToolsBackupScreen(
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onImportJson: (String) -> Unit,
    onResetData: () -> Unit,
    onShareWhatsApp: () -> Unit,
    cloudUrl: String,
    cloudApiKey: String,
    autoSync: Boolean,
    isCloudSyncing: Boolean,
    cloudStatus: String,
    lastCloudSyncAt: String,
    localTransactionCount: Int,
    draftTransactionCount: Int,
    onSaveCloudSettings: (String, String, Boolean) -> Unit,
    onSyncNow: (String, String, Boolean) -> Unit,
    onBackupToDrive: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var cloudUrlInput by remember { mutableStateOf(cloudUrl) }
    var cloudKeyInput by remember { mutableStateOf(cloudApiKey) }
    var autoSyncInput by remember { mutableStateOf(autoSync) }
    LaunchedEffect(cloudUrl) { cloudUrlInput = cloudUrl }
    LaunchedEffect(cloudApiKey) { cloudKeyInput = cloudApiKey }
    LaunchedEffect(autoSync) { autoSyncInput = autoSync }

    var showImportDialog by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }

    var showResetDialog by remember { mutableStateOf(false) }

    // Use only values entered by the user; never show default/sample prices as business results.
    var liveWeightKg by remember { mutableStateOf("") }
    var livePricePerKg by remember { mutableStateOf("") }
    var carcassYieldPercent by remember { mutableStateOf("") }
    var operatingCostPerKg by remember { mutableStateOf("") }
    var targetMarginPerKg by remember { mutableStateOf("") }

    val liveWeight = liveWeightKg.toDoubleOrNull()
    val livePrice = livePricePerKg.toDoubleOrNull()
    val yieldInput = carcassYieldPercent.toDoubleOrNull()
    val opCost = operatingCostPerKg.toDoubleOrNull()
    val margin = targetMarginPerKg.toDoubleOrNull()
    val calcReady = liveWeight != null && liveWeight > 0.0 &&
        livePrice != null && livePrice > 0.0 &&
        yieldInput != null && yieldInput in 0.01..100.0 &&
        opCost != null && opCost >= 0.0 &&
        margin != null && margin >= 0.0

    val yieldPct = (yieldInput ?: 0.0) / 100.0
    val carcassWeight = if (calcReady) liveWeight!! * yieldPct else 0.0
    val totalLiveCost = if (calcReady) liveWeight!! * livePrice!! else 0.0
    val carcassBaseCostPerKg = if (carcassWeight > 0.0) totalLiveCost / carcassWeight else 0.0
    val bepSellingPrice = carcassBaseCostPerKg + (opCost ?: 0.0)
    val targetSellingPrice = bepSellingPrice + (margin ?: 0.0)
    val estProfitPerChicken = (targetSellingPrice - bepSellingPrice) * carcassWeight

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("tools_backup_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Kalkulator & Cadangan Data",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Analisis HPP karkas ayam potong & kelola cadangan data lokal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Cloud sync panel: connects local Room data to the bound Google Sheets ledger.
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("cloud_sync_panel"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color(0xFFE3F4EB),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FileUpload,
                                    contentDescription = null,
                                    tint = Color(0xFF176B50),
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sinkronisasi Google Sheets & Drive", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Database lokal tetap dipakai saat offline", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(
                        "Masukkan URL Web App Apps Script (/exec) dan kunci yang dibuat melalui menu AyamKas Pro pada spreadsheet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = cloudUrlInput,
                        onValueChange = { cloudUrlInput = it },
                        label = { Text("URL Web App Google Apps Script") },
                        placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                        modifier = Modifier.fillMaxWidth().testTag("input_cloud_url"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = cloudKeyInput,
                        onValueChange = { cloudKeyInput = it },
                        label = { Text("Kunci sinkronisasi") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("input_cloud_key"),
                        singleLine = true
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sinkron otomatis", fontWeight = FontWeight.SemiBold)
                            Text("Saat ada perubahan dan aplikasi sedang terbuka/online", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = autoSyncInput,
                            onCheckedChange = { autoSyncInput = it },
                            modifier = Modifier.testTag("switch_cloud_auto_sync")
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { onSaveCloudSettings(cloudUrlInput, cloudKeyInput, autoSyncInput) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Simpan URL") }
                        Button(
                            onClick = { onSyncNow(cloudUrlInput, cloudKeyInput, autoSyncInput) },
                            enabled = !isCloudSyncing,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isCloudSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Sinkronkan Sekarang")
                        }
                    }
                    OutlinedButton(
                        onClick = { onBackupToDrive(cloudUrlInput, cloudKeyInput) },
                        enabled = !isCloudSyncing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cadangkan Spreadsheet ke Drive")
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Status: $cloudStatus", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            if (lastCloudSyncAt.isNotBlank()) Text("Sinkronisasi terakhir: $lastCloudSyncAt", style = MaterialTheme.typography.bodySmall)
                            Text("Data di HP: $localTransactionCount transaksi · $draftTransactionCount masih draf", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(
                        "Catatan: backup Drive menyalin spreadsheet saja. Gunakan Ekspor JSON di bawah untuk cadangan lokal HP. Jika jaringan gagal, data lokal tidak dihapus.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Jaga kerahasiaan kunci API. Untuk penggunaan banyak orang atau data yang sangat sensitif, deployment perlu autentikasi Google yang lebih ketat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Section: Poultry Slaughter & Carcass HPP Calculator
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("calculator_ayam_potong"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color(0xFFE3F4EB),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = "🐓", fontSize = 18.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Kalkulator HPP & Penetapan Harga Karkas",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Perhitungan akurat rendemen karkas, biaya potong & target marjin",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = liveWeightKg,
                            onValueChange = { liveWeightKg = it },
                            label = { Text("Bobot Hidup (kg)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_calc_live_weight"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = livePricePerKg,
                            onValueChange = { livePricePerKg = it.filter { c -> c.isDigit() } },
                            label = { Text("Harga Hidup / kg") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_calc_live_price"),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = carcassYieldPercent,
                            onValueChange = { carcassYieldPercent = it },
                            label = { Text("Rendemen Karkas (%)") },
                            placeholder = { Text("70-75%") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_calc_yield"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = operatingCostPerKg,
                            onValueChange = { operatingCostPerKg = it.filter { c -> c.isDigit() } },
                            label = { Text("Biaya Potong / kg") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_calc_op_cost"),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = targetMarginPerKg,
                        onValueChange = { targetMarginPerKg = it.filter { c -> c.isDigit() } },
                        label = { Text("Target Laba per kg (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_calc_margin"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Calculation Results Card: render only after every required input is valid.
                    if (calcReady) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF4F7F5)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Hasil Perhitungan Karkas",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Bobot Karkas Bersih", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "${AyamKasFormatters.formatNumber(carcassWeight)} kg / ekor",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "HPP Bahan Pokok Karkas", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "${AyamKasFormatters.formatRupiah(carcassBaseCostPerKg)} / kg",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Harga Pokok Lengkap (BEP)", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "${AyamKasFormatters.formatRupiah(bepSellingPrice)} / kg",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF8B5A0A),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Rekomendasi Jual Target", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${AyamKasFormatters.formatRupiah(targetSellingPrice)} / kg",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF176B50)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Estimasi Laba per Ekor", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "${AyamKasFormatters.formatRupiah(estProfitPerChicken)} / ekor",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF176B50),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    } else {
                        Text(
                            "Masukkan bobot hidup, harga aktual, rendemen hasil timbang, biaya potong/kg, dan target laba. Hasil tidak ditampilkan sebelum semua input valid.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("calculator_missing_real_inputs")
                        )
                    }
                }
            }
        }

        // Section: WhatsApp Share
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "📲", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Kirim Rekap Kas via WhatsApp",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Bagikan ringkasan kas, stok DO, utang dan piutang ke Owner/Admin",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onShareWhatsApp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_share_whatsapp"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF128C7E))
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Bagikan Ringkasan ke WhatsApp")
                    }
                }
            }
        }

        // Section: Backup & Restore
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Cadangkan & Pulihkan Data",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Simpan salinan cadangan agar transaksi tidak hilang saat ganti perangkat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Buttons
                    Button(
                        onClick = onExportJson,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_export_json"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF176B50))
                    ) {
                        Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ekspor Cadangan Lengkap (JSON)")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onExportCsv,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_export_csv")
                    ) {
                        Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ekspor Transaksi ke CSV")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showImportDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_import_json")
                    ) {
                        Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pulihkan Data dari JSON")
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(12.dp))

                    // Reset Data
                    Button(
                        onClick = { showResetDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_reset_all_data"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB33C35))
                    ) {
                        Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Hapus Semua Data Perangkat")
                    }
                }
            }
        }

        // About info
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "AyamKas Pro — Pembukuan Ayam Potong",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Aplikasi pembukuan offline mandiri. Data transaksi disimpan di basis data lokal pada perangkat. Ekspor cadangan JSON secara berkala agar aman saat berganti ponsel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // Import Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Pulihkan dari JSON") },
            text = {
                Column {
                    Text("Tempel teks JSON cadangan AyamKas Pro:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importJsonText,
                        onValueChange = { importJsonText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .testTag("input_import_json_text"),
                        placeholder = { Text("{\"app\": \"AyamKas Pro\", ...}") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importJsonText.isNotBlank()) {
                            onImportJson(importJsonText)
                            showImportDialog = false
                            importJsonText = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF176B50))
                ) {
                    Text("Pulihkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Reset Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Konfirmasi Hapus Semua Data") },
            text = {
                Text("Yakin hapus semua transaksi di perangkat ini? Tindakan ini tidak bisa dibatalkan kecuali Anda memiliki file cadangan JSON.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetData()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB33C35))
                ) {
                    Text("Hapus Semuanya")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

}
