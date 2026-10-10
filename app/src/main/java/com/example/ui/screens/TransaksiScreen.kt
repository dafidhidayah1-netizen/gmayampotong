package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AyamTransaction
import com.example.data.model.AyamTransactionType
import com.example.ui.components.TransactionItemCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransaksiScreen(
    transactions: List<AyamTransaction>,
    totalTransactionsCount: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedType: String,
    onTypeSelect: (String) -> Unit,
    selectedStatus: String,
    onStatusSelect: (String) -> Unit,
    selectedMonth: String,
    onMonthSelect: (String) -> Unit,
    availableMonths: List<String>,
    onAddTransaction: () -> Unit,
    onEditTransaction: (AyamTransaction) -> Unit,
    onToggleConfirm: (AyamTransaction) -> Unit,
    onDeleteTransaction: (AyamTransaction) -> Unit,
    onConfirmAllDrafts: () -> Unit,
    modifier: Modifier = Modifier
) {
    var monthMenuExpanded by remember { mutableStateOf(false) }
    val draftCount = transactions.count { it.isDraft }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("transactions_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_search_tx"),
                placeholder = { Text("Cari referensi, keterangan, nota…") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Cari")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Bersihkan")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
        }

        // Status Tabs (Semua, Perlu Verifikasi, Terverifikasi)
        item {
            val tabs = listOf("all" to "Semua", "draft" to "Perlu Verifikasi", "confirmed" to "Terverifikasi")
            val selectedIndex = tabs.indexOfFirst { it.first == selectedStatus }.coerceAtLeast(0)

            TabRow(
                selectedTabIndex = selectedIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Color(0xFF176B50)
            ) {
                tabs.forEachIndexed { index, (key, title) ->
                    Tab(
                        selected = selectedIndex == index,
                        onClick = { onStatusSelect(key) },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier.testTag("tab_status_$key")
                    )
                }
            }
        }

        // Horizontal Filters (Types & Months)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Type Filter Chips Scrollable
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedType == "all",
                        onClick = { onTypeSelect("all") },
                        label = { Text("Semua Jenis") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF176B50), selectedLabelColor = Color.White),
                        modifier = Modifier.testTag("filter_type_all")
                    )
                    AyamTransactionType.entries.forEach { t ->
                        FilterChip(
                            selected = selectedType == t.key,
                            onClick = { onTypeSelect(t.key) },
                            label = { Text("${t.icon} ${t.label}") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF176B50), selectedLabelColor = Color.White),
                            modifier = Modifier.testTag("filter_type_${t.key}")
                        )
                    }
                }

                // Month Dropdown if available
                if (availableMonths.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = monthMenuExpanded,
                        onExpandedChange = { monthMenuExpanded = it }
                    ) {
                        val displayMonth = if (selectedMonth == "all") "Semua Bulan" else {
                            val parts = selectedMonth.split("-")
                            if (parts.size == 2) "${parts[1]}/${parts[0]}" else selectedMonth
                        }
                        OutlinedTextField(
                            value = displayMonth,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Filter Bulan") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthMenuExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = monthMenuExpanded,
                            onDismissRequest = { monthMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Semua Bulan") },
                                onClick = {
                                    onMonthSelect("all")
                                    monthMenuExpanded = false
                                }
                            )
                            availableMonths.forEach { m ->
                                val parts = m.split("-")
                                val label = if (parts.size == 2) "${parts[1]}/${parts[0]}" else m
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        onMonthSelect(m)
                                        monthMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Draft banner with Bulk Verification Action
        if (selectedStatus != "confirmed" && draftCount > 0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4D9))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🧾 $draftCount Draf Menunggu Verifikasi",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8B5A0A),
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Draf tidak memengaruhi saldo kas dan laporan sebelum diverifikasi.",
                                color = Color(0xFF694608),
                                fontSize = 11.sp
                            )
                        }
                        Button(
                            onClick = onConfirmAllDrafts,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF176B50)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("btn_confirm_all_drafts")
                        ) {
                            Icon(imageVector = Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Validasi Semua", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Header info count
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Menampilkan ${transactions.size} dari $totalTransactionsCount transaksi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onAddTransaction,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF176B50)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("btn_add_tx_list")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tambah", fontSize = 12.sp)
                }
            }
        }

        // Empty state
        if (transactions.isEmpty()) {
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
                        Text(text = "🔎", fontSize = 36.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Tidak Ada Transaksi yang Cocok",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Coba ubah kata kunci pencarian atau sesuaikan filter jenis/status.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // Transaction cards list
            items(transactions, key = { it.id }) { tx ->
                TransactionItemCard(
                    transaction = tx,
                    onEdit = { onEditTransaction(tx) },
                    onToggleConfirm = { onToggleConfirm(tx) },
                    onDelete = { onDeleteTransaction(tx) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
