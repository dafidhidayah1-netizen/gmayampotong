package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.TransactionAuditLog
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
fun AuditLogScreen(
    entries: List<TransactionAuditLog>,
    modifier: Modifier = Modifier
) {
    var search by remember { mutableStateOf("") }
    val query = search.trim().lowercase(Locale.ROOT)
    val filtered = entries.filter { entry ->
        query.isBlank() || listOf(
            entry.entityId, entry.action, entry.summary, entry.beforeData, entry.afterData, entry.actor
        ).any { it.lowercase(Locale.ROOT).contains(query) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("audit_log_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Audit Aktivitas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Riwayat perubahan disimpan lokal: pembuatan, edit, verifikasi, kembali ke draf, dan penghapusan transaksi. Audit ini bukan pengganti backup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Cari riwayat, nomor transaksi, aksi") },
                modifier = Modifier.fillMaxWidth().testTag("input_audit_search"),
                singleLine = true
            )
        }
        item {
            Text("Menampilkan ${filtered.size} dari ${entries.size} catatan terbaru", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (filtered.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("Belum ada catatan audit yang cocok.", fontWeight = FontWeight.SemiBold)
                        Text("Setiap perubahan transaksi baru akan tercatat di sini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(filtered, key = { it.id }) { entry ->
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(entry.action.replace('_', ' '), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(entry.entityType, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(entry.summary, fontWeight = FontWeight.SemiBold)
                        Text("ID: ${entry.entityId}", style = MaterialTheme.typography.bodySmall)
                        Text("Waktu: ${entry.occurredAt} · ${entry.actor}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (entry.beforeData.isNotBlank() && entry.beforeData != "—") {
                            Text("Sebelum: ${entry.beforeData}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (entry.afterData.isNotBlank() && entry.afterData != "—") {
                            Text("Sesudah: ${entry.afterData}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
