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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AyamKasFormatters
import com.example.data.model.AyamKasSummary
import com.example.data.model.TypeBreakdownItem
import com.example.ui.components.MetricCard
import com.example.ui.components.MetricStyle

@Composable
fun ReportsScreen(
    summary: AyamKasSummary,
    fromDate: String,
    toDate: String,
    onFromDateChange: (String) -> Unit,
    onToDateChange: (String) -> Unit,
    onClearDates: () -> Unit,
    breakdownItems: List<TypeBreakdownItem>,
    onExportCsv: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("reports_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Laporan Pembukuan Keuangan",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Angka dihitung otomatis dari transaksi terverifikasi",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Date Range Filter Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Filter Periode Tanggal",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = fromDate,
                            onValueChange = onFromDateChange,
                            label = { Text("Dari (YYYY-MM-DD)") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_report_from_date"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = toDate,
                            onValueChange = onToDateChange,
                            label = { Text("Sampai (YYYY-MM-DD)") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_report_to_date"),
                            singleLine = true
                        )
                    }
                    if (fromDate.isNotBlank() || toDate.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onClearDates,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_clear_report_dates")
                        ) {
                            Text("Tampilkan Semua Tanggal")
                        }
                    }
                }
            }
        }

        // Warning banner if profit is incomplete
        if (!summary.profitComplete) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4D9))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Text(text = "ℹ️", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Laba Bersih Belum Dapat Dipastikan",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8B5A0A)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Ada ${summary.unknownSales} transaksi penjualan yang belum memiliki data kg atau belum tertaut ke DO pembelian terverifikasi. Nilai HPP belum penuh.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF694608)
                            )
                        }
                    }
                }
            }
        }

        // 6 Report Financial Metrics
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Omzet Penjualan",
                        value = AyamKasFormatters.formatRupiah(summary.revenue),
                        footnote = "Periode terpilih",
                        icon = "↗",
                        style = MetricStyle.NORMAL,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "HPP Ayam Terjual",
                        value = AyamKasFormatters.formatRupiah(summary.cogs),
                        footnote = "${summary.knownSales} penjualan tertaut",
                        icon = "📦",
                        style = MetricStyle.INFO,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Biaya Operasional",
                        value = AyamKasFormatters.formatRupiah(summary.expenses),
                        footnote = "Biaya harian & pakan",
                        icon = "↘",
                        style = MetricStyle.WARN,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Laba Bersih Periode",
                        value = AyamKasFormatters.formatRupiah(summary.profit),
                        footnote = "Omzet − HPP DO − Biaya",
                        icon = if (summary.profit >= 0) "◈" else "⚠",
                        style = if (summary.profit < 0) MetricStyle.DANGER else MetricStyle.NORMAL,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Modal Owner Masuk",
                        value = AyamKasFormatters.formatRupiah(summary.capital),
                        footnote = "Terpisah dari omzet",
                        icon = "💵",
                        style = MetricStyle.NORMAL,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Pembelian DO",
                        value = AyamKasFormatters.formatRupiah(summary.purchaseTotal),
                        footnote = "Persediaan, bukan biaya laba",
                        icon = "▤",
                        style = MetricStyle.NORMAL,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Breakdown Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ringkasan per Jenis Transaksi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onExportCsv,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF176B50)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("btn_export_report_csv")
                ) {
                    Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ekspor CSV", fontSize = 12.sp)
                }
            }
        }

        if (breakdownItems.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        text = "Belum ada transaksi terverifikasi untuk periode ini.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        breakdownItems.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${item.count} transaksi",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = AyamKasFormatters.formatRupiah(item.totalAmount),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (index < breakdownItems.size - 1) {
                                Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // Accounting formula note
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Prinsip Pembukuan Ayam Potong",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Omzet − HPP Ayam Terjual − Biaya Operasional = Laba/Rugi.\n• Modal owner dan pembayaran utang bukan omzet.\n• Pembelian ayam masuk ke persediaan; hanya biaya ayam yang benar-benar terjual masuk ke Harga Pokok Penjualan (HPP).",
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
}
