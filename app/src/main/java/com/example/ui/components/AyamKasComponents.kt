package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.example.data.model.AyamTransaction
import com.example.data.model.AyamTransactionType
import com.example.data.model.DoStockItem

enum class MetricStyle {
    NORMAL,
    WARN,
    DANGER,
    INFO
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    footnote: String,
    icon: String,
    style: MetricStyle = MetricStyle.NORMAL,
    modifier: Modifier = Modifier
) {
    val (badgeBg, badgeColor) = when (style) {
        MetricStyle.NORMAL -> Color(0xFFE3F4EB) to Color(0xFF176B50)
        MetricStyle.WARN -> Color(0xFFFFF4D9) to Color(0xFF8B5A0A)
        MetricStyle.DANGER -> Color(0xFFFFF0EE) to Color(0xFFB33C35)
        MetricStyle.INFO -> Color(0xFFEDF4FF) to Color(0xFF285DA8)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("metric_card_${title.lowercase().replace(" ", "_")}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = icon, fontSize = 16.sp)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = when (style) {
                    MetricStyle.DANGER -> Color(0xFFB33C35)
                    MetricStyle.WARN -> Color(0xFF8B5A0A)
                    else -> MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = footnote,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun StatusPill(
    status: String,
    modifier: Modifier = Modifier
) {
    val isConfirmed = status.equals("confirmed", ignoreCase = true) || status == "Terverifikasi"
    val (bg, textColor, text) = if (isConfirmed) {
        Triple(Color(0xFFE3F4EB), Color(0xFF176B50), "Terverifikasi")
    } else {
        Triple(Color(0xFFFFF4D9), Color(0xFF8B5A0A), "Perlu Verifikasi")
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(99.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun TransactionItemCard(
    transaction: AyamTransaction,
    onEdit: () -> Unit,
    onToggleConfirm: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val typeEnum = AyamTransactionType.fromKey(transaction.type)
    val isConfirmed = transaction.isConfirmed
    val cashEffect = transaction.cashEffect()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("tx_card_${transaction.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when (typeEnum.flow) {
                                    "in" -> Color(0xFFE3F4EB)
                                    else -> Color(0xFFFFF0EE)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = typeEnum.icon, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (transaction.ref.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = transaction.ref,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = typeEnum.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = AyamKasFormatters.formatDate(transaction.date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                StatusPill(status = transaction.status)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = transaction.description,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (transaction.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = transaction.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row for financial & weight detail
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total Transaksi",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = AyamKasFormatters.formatRupiah(transaction.amount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (transaction.qtyKg != null && transaction.qtyKg > 0.0) {
                        Text(
                            text = "${AyamKasFormatters.formatNumber(transaction.qtyKg)} kg" +
                                if (transaction.batchRef.isNotBlank()) " · DO: ${transaction.batchRef}" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF176B50),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Kas Diterima/Dibayar",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (transaction.paidAmount == null) {
                        Text(
                            text = "Belum diketahui",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8B5A0A),
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        val paid = transaction.paidAmount
                        Text(
                            text = AyamKasFormatters.formatRupiah(paid),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (cashEffect >= 0) Color(0xFF176B50) else Color(0xFFB33C35)
                        )
                        if (paid < transaction.amount && (transaction.type == "sale" || transaction.type == "purchase")) {
                            val diff = transaction.amount - paid
                            val label = if (transaction.type == "sale") "Piutang" else "Utang DO"
                            Text(
                                text = "Sisa $label: ${AyamKasFormatters.formatRupiah(diff)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF8B5A0A)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("btn_edit_${transaction.id}"),
                    contentPadding = ButtonDefaults.TextButtonContentPadding
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = if (isConfirmed) "Edit" else "Tinjau", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onToggleConfirm,
                    modifier = Modifier.testTag("btn_toggle_confirm_${transaction.id}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConfirmed) MaterialTheme.colorScheme.surfaceVariant else Color(0xFF176B50),
                        contentColor = if (isConfirmed) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                    ),
                    contentPadding = ButtonDefaults.TextButtonContentPadding
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = "Validasi", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = if (isConfirmed) "Batal Verifikasi" else "Konfirmasi", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("btn_delete_${transaction.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Hapus",
                        tint = Color(0xFFB33C35),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DoStockCard(
    item: DoStockItem,
    modifier: Modifier = Modifier
) {
    val p = item.purchaseTx
    val isNegative = item.remainingKg < -0.0001
    val isMissing = item.missingSalesCount > 0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("stock_card_${p.ref}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color(0xFF176B50),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = p.ref.ifBlank { "DO Baru" },
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = AyamKasFormatters.formatDate(p.date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = p.description,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Surface(
                    color = when {
                        isNegative -> Color(0xFFFFF0EE)
                        isMissing -> Color(0xFFFFF4D9)
                        else -> Color(0xFFE3F4EB)
                    },
                    shape = RoundedCornerShape(99.dp)
                ) {
                    Text(
                        text = item.status,
                        color = when {
                            isNegative -> Color(0xFFB33C35)
                            isMissing -> Color(0xFF8B5A0A)
                            else -> Color(0xFF176B50)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4 Column Grid Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Berat Masuk", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${AyamKasFormatters.formatNumber(item.totalPurchasedKg)} kg",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column {
                    Text(text = "Kg Terjual", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${AyamKasFormatters.formatNumber(item.soldKg)} kg",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF285DA8)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Sisa Stok", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${AyamKasFormatters.formatNumber(item.remainingKg)} kg",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isNegative) Color(0xFFB33C35) else Color(0xFF176B50)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pricing & Valuation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Harga Pokok DO / kg", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${AyamKasFormatters.formatRupiah(item.unitCost)} / kg",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Nilai Persediaan Sisa", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = AyamKasFormatters.formatRupiah(item.remainingValue),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF176B50)
                    )
                }
            }

            if (isMissing) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⚠️ Ada ${item.missingSalesCount} transaksi penjualan yang kg-nya belum diisi pada DO ini.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8B5A0A)
                )
            }
        }
    }
}

@Composable
fun QualityProgressCard(
    confirmedCount: Int,
    draftCount: Int,
    qtyMissingCount: Int,
    paymentMissingCount: Int,
    onReviewDrafts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val total = (confirmedCount + draftCount).coerceAtLeast(1)
    val verifiedProgress = (confirmedCount.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Integritas & Kelengkapan Pembukuan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${(verifiedProgress * 100).toInt()}% Terverifikasi",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF176B50),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { verifiedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(99.dp)),
                color = Color(0xFF176B50),
                trackColor = Color(0xFFE0E8E3)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Transaksi Terverifikasi Aktif", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "$confirmedCount", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Transaksi Menunggu Konfirmasi", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8B5A0A))
                Text(text = "$draftCount", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFF8B5A0A))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Penjualan Perlu Tautan DO", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "$qtyMissingCount", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Pembayaran Belum Lengkap", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "$paymentMissingCount", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }

            if (draftCount > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onReviewDrafts,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF176B50))
                ) {
                    Text("Tinjau Transaksi Belum Konfirmasi")
                }
            }
        }
    }
}
