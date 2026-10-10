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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AyamKasFormatters
import com.example.data.model.AyamKasSummary
import com.example.data.model.AyamTransaction
import com.example.data.model.AyamTransactionType
import com.example.ui.components.MetricCard
import com.example.ui.components.MetricStyle
import com.example.ui.components.QualityProgressCard
import com.example.ui.components.StatusPill

@Composable
fun DashboardScreen(
    summary: AyamKasSummary,
    recentConfirmed: List<AyamTransaction>,
    onNavigateToTransactions: () -> Unit,
    onNavigateToStock: () -> Unit,
    onQuickAddTransaction: () -> Unit,
    onQuickAddExpense: () -> Unit,
    onQuickAddPurchase: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("dashboard_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))

            // Quick Actions Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onQuickAddTransaction,
                    modifier = Modifier
                        .weight(1.3f)
                        .testTag("btn_quick_add_tx"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF176B50)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Transaksi", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onQuickAddExpense,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_quick_add_expense"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("+ Biaya", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onQuickAddPurchase,
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("btn_quick_add_purchase"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("+ DO Ayam", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Draft Alert Banner
        if (summary.draftCount > 0) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("banner_draft_alert"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4D9))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(text = "⚠️", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${summary.draftCount} Transaksi Menunggu Konfirmasi",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8B5A0A)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Terdapat transaksi yang belum dikonfirmasi masuk ke buku besar. Tinjau untuk memastikan akurasi saldo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF694608)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onNavigateToTransactions,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5A0A)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("btn_review_drafts_banner")
                            ) {
                                Text("Tinjau Transaksi →", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // 8 Key Metrics
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Saldo Kas Tercatat",
                        value = AyamKasFormatters.formatRupiah(summary.cash),
                        footnote = "Kas dari transaksi valid",
                        icon = "💵",
                        style = MetricStyle.NORMAL,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Omzet Penjualan",
                        value = AyamKasFormatters.formatRupiah(summary.revenue),
                        footnote = "Total omzet ayam",
                        icon = "↗",
                        style = MetricStyle.NORMAL,
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
                        title = "Laba Bersih Operasional",
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
                        title = "Stok Tercatat",
                        value = "${AyamKasFormatters.formatNumber(summary.stockKg)} kg",
                        footnote = "Berdasarkan DO & penjualan",
                        icon = "📦",
                        style = MetricStyle.INFO,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Nilai Persediaan DO",
                        value = AyamKasFormatters.formatRupiah(summary.stockValue),
                        footnote = "Harga pokok per kg DO",
                        icon = "▤",
                        style = MetricStyle.NORMAL,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Utang DO",
                        value = AyamKasFormatters.formatRupiah(summary.ap),
                        footnote = "Sisa pembelian blm dibayar",
                        icon = "🧾",
                        style = MetricStyle.WARN,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Piutang Penjualan",
                        value = AyamKasFormatters.formatRupiah(summary.ar),
                        footnote = "Omzet belum diterima kas",
                        icon = "⏳",
                        style = MetricStyle.INFO,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Quality Progress Panel
        item {
            QualityProgressCard(
                confirmedCount = summary.confirmedCount,
                draftCount = summary.draftCount,
                qtyMissingCount = summary.qtyMissingCount,
                paymentMissingCount = summary.paymentMissingCount,
                onReviewDrafts = onNavigateToTransactions
            )
        }

        // Recent Mutations Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Mutasi Kas Terbaru",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Transaksi terverifikasi yang memengaruhi kas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(99.dp),
                    modifier = Modifier.clickable { onNavigateToTransactions() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Lihat Semua", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }

        // Recent items list
        if (recentConfirmed.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "🗂️", fontSize = 32.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Belum Ada Transaksi Terverifikasi",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Konfirmasi catatan lama atau buat transaksi baru untuk memulai laporan kas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(recentConfirmed.take(6)) { tx ->
                val typeEnum = AyamTransactionType.fromKey(tx.type)
                val cashEffect = tx.cashEffect()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (cashEffect >= 0) Color(0xFFE3F4EB) else Color(0xFFFFF0EE)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = typeEnum.icon, fontSize = 16.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = tx.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${AyamKasFormatters.formatDate(tx.date)} · ${tx.ref.ifBlank { typeEnum.label }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = (if (cashEffect >= 0) "+" else "−") + AyamKasFormatters.formatRupiah(Math.abs(cashEffect)),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = if (cashEffect >= 0) Color(0xFF176B50) else Color(0xFFB33C35)
                        )
                    }
                }
            }
        }

        // 3 Langkah Alur Pembukuan Ayam Potong Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToStock() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Alur Pembukuan Ayam Potong yang Benar",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "1. Pembelian DO: Catat kg & harga total. Sisa belum dibayar menjadi Utang DO.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "2. Penjualan Ayam: Catat kg terjual & tautkan ke DO agar HPP dan stok berkurang.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "3. Tutup Hari: Masukkan biaya pakan/admin/transport; rekonsiliasi saldo kas dan stok.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
