package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun BusinessSettingsScreen(
    companyName: String,
    companyLogoUri: String,
    companyBannerUri: String,
    companyAddress: String,
    companyPhone: String,
    companyEmail: String,
    companyTaxId: String,
    themeMode: String,
    onSave: (String, String, String, String, String, String) -> Unit,
    onChooseLogo: () -> Unit,
    onRemoveLogo: () -> Unit,
    onChooseBanner: () -> Unit,
    onRemoveBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember(companyName) { mutableStateOf(companyName) }
    var address by remember(companyAddress) { mutableStateOf(companyAddress) }
    var phone by remember(companyPhone) { mutableStateOf(companyPhone) }
    var email by remember(companyEmail) { mutableStateOf(companyEmail) }
    var taxId by remember(companyTaxId) { mutableStateOf(companyTaxId) }
    var selectedTheme by remember(themeMode) { mutableStateOf(themeMode) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Pengaturan Usaha & Master Profil", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Profil ini menjadi sumber identitas untuk header aplikasi dan format nota/kwitansi cetak. Isi yang diketahui saja; jangan mengarang nomor pajak.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Logo untuk aplikasi dan dokumen", fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (companyLogoUri.isNotBlank()) {
                        AsyncImage(
                            model = companyLogoUri,
                            contentDescription = "Logo perusahaan",
                            modifier = Modifier.size(76.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(Icons.Default.Business, contentDescription = "Identitas perusahaan", modifier = Modifier.size(76.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Logo / ikon perusahaan", fontWeight = FontWeight.Bold)
                        Text("Digunakan pada header aplikasi dan nota atau kwitansi cetak.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onChooseLogo, modifier = Modifier.weight(1f).testTag("btn_choose_company_logo")) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Text("Pilih Logo")
                    }
                    if (companyLogoUri.isNotBlank()) {
                        OutlinedButton(onClick = onRemoveLogo, modifier = Modifier.testTag("btn_remove_company_logo")) { Text("Hapus") }
                    }
                }

                Text("Banner perusahaan", fontWeight = FontWeight.Bold)
                if (companyBannerUri.isNotBlank()) {
                    AsyncImage(
                        model = companyBannerUri,
                        contentDescription = "Banner perusahaan",
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text("Banner belum dipilih. Pilih gambar mendatar untuk header dan dokumen cetak.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onChooseBanner, modifier = Modifier.weight(1f).testTag("btn_choose_company_banner")) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Text("Pilih Banner")
                    }
                    if (companyBannerUri.isNotBlank()) {
                        OutlinedButton(onClick = onRemoveBanner, modifier = Modifier.testTag("btn_remove_company_banner")) { Text("Hapus Banner") }
                    }
                }

                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nama perusahaan / usaha *") },
                    modifier = Modifier.fillMaxWidth().testTag("input_company_name"), singleLine = true
                )
                OutlinedTextField(
                    value = address, onValueChange = { address = it },
                    label = { Text("Alamat usaha") },
                    modifier = Modifier.fillMaxWidth().testTag("input_company_address"), minLines = 2
                )
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("Telepon / WhatsApp") },
                    modifier = Modifier.fillMaxWidth().testTag("input_company_phone"), singleLine = true
                )
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth().testTag("input_company_email"), singleLine = true
                )
                OutlinedTextField(
                    value = taxId, onValueChange = { taxId = it },
                    label = { Text("NPWP (opsional)") },
                    modifier = Modifier.fillMaxWidth().testTag("input_company_tax_id"), singleLine = true
                )
            }
        }

        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Tema aplikasi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                listOf("SYSTEM" to "Ikuti pengaturan perangkat", "LIGHT" to "Mode terang", "DARK" to "Mode malam").forEach { (value, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(selected = selectedTheme == value, onClick = { selectedTheme = value })
                        Text(label)
                    }
                }
            }
        }
        Button(
            onClick = { onSave(name, address, phone, email, taxId, selectedTheme) },
            modifier = Modifier.fillMaxWidth().testTag("btn_save_business_settings")
        ) {
            Text("Simpan Profil & Pengaturan")
        }
        Text(
            "Catatan: gambar dipilih dari perangkat ini. Profil cetak belum tersinkron lintas perangkat sampai master profil dimasukkan ke sinkronisasi cloud.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
    }
}
