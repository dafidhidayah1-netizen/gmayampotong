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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.example.data.model.DoStockItem
import com.example.ui.components.DoStockCard
import com.example.ui.components.MetricCard
import com.example.ui.components.MetricStyle

@Composable
fun StockScreen(
    summary: AyamKasSummary,
    stockItems: List<DoStockItem>,
    onAddPurchase: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalPurchasedKg = stockItems.sumOf { it.totalPurchasedKg }
    val totalSoldKg = stockItems.sumOf { it.soldKg }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("stock_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))

            // Action Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "DO & Stok Ayam Potong",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Perhitungan stok memakai transaksi terverifikasi saja",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onAddPurchase,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF176B50)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("btn_add_purchase_stock")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("DO Baru", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Metrics Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Total Kg Dibeli",
                        value = "${AyamKasFormatters.formatNumber(totalPurchasedKg)} kg",
                        footnote = "DO terverifikasi",
                        icon = "📦",
                        style = MetricStyle.NORMAL,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Kg Terjual Terukur",
                        value = "${AyamKasFormatters.formatNumber(totalSoldKg)} kg",
                        footnote = "Penjualan dengan kg terisi",
                        icon = "🐓",
                        style = MetricStyle.INFO,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Sisa Stok Tercatat",
                        value = "${AyamKasFormatters.formatNumber(summary.stockKg)} kg",
                        footnote = "Periksa jika stok negatif",
                        icon = "▤",
                        style = if (summary.stockKg < 0) MetricStyle.DANGER else MetricStyle.NORMAL,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Nilai Sisa Persediaan",
                        value = AyamKasFormatters.formatRupiah(summary.stockValue),
                        footnote = "Asumsi HPP per kg tiap DO",
                        icon = "💵",
                        style = MetricStyle.NORMAL,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Notice Note Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF176B50),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Penting: Jika penjualan tidak diisi dalam kg atau tidak ditautkan ke DO, stok dan harga pokok untuk penjualan itu belum bisa dihitung secara akurat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF123D32)
                    )
                }
            }
        }

        // Section Title
        item {
            Text(
                text = "Rincian per Delivery Order (DO)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (stockItems.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "📦", fontSize = 36.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Belum Ada Pembelian DO Terverifikasi",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Konfirmasi transaksi pembelian lama atau catat DO pembelian baru.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(stockItems, key = { it.purchaseTx.id }) { item ->
                DoStockCard(item = item)
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
