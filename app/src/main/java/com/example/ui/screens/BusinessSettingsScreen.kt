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
    themeMode: String,
    onSave: (String, String) -> Unit,
    onChooseLogo: () -> Unit,
    onRemoveLogo: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember(companyName) { mutableStateOf(companyName) }
    var selectedTheme by remember(themeMode) { mutableStateOf(themeMode) }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Pengaturan Perusahaan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Identitas usaha dan tampilan disimpan di perangkat ini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        Text("Pilih gambar dari perangkat. Izin akses gambar akan disimpan untuk pemakaian berikutnya.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onChooseLogo, modifier = Modifier.weight(1f).testTag("btn_choose_company_logo")) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Text("Pilih Logo")
                    }
                    if (companyLogoUri.isNotBlank()) {
                        OutlinedButton(onClick = onRemoveLogo, modifier = Modifier.testTag("btn_remove_company_logo")) { Text("Hapus Logo") }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama perusahaan / usaha") },
                    modifier = Modifier.fillMaxWidth().testTag("input_company_name"),
                    singleLine = true
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
                Spacer(Modifier.height(4.dp))
                Text("Palet bawaan memakai hijau emerald, hijau gelap, dan aksen emas. Mode malam mengikuti pilihan yang Anda simpan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Button(onClick = { onSave(name, selectedTheme) }, modifier = Modifier.fillMaxWidth().testTag("btn_save_business_settings")) {
            Text("Simpan Pengaturan")
        }
    }
}
