package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.PurchaseDoScreen
import com.example.ui.screens.StockScreen
import com.example.ui.screens.ToolsBackupScreen
import com.example.ui.screens.TransactionFormDialog
import com.example.ui.screens.TransaksiScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AyamKasViewModel

enum class AyamNavTab(val title: String, val icon: ImageVector) {
    DASHBOARD("Ringkasan", Icons.Default.Dashboard),
    TRANSAKSI("Transaksi", Icons.Default.ReceiptLong),
    STOCK("DO & Stok", Icons.Default.Inventory2),
    REPORTS("Laporan", Icons.Default.Assessment),
    TOOLS("Cadangan", Icons.Default.Backup)
}

class MainActivity : ComponentActivity() {

    private val viewModel: AyamKasViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                AyamKasApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun AyamKasApp(viewModel: AyamKasViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var currentTab by remember { mutableStateOf(AyamNavTab.DASHBOARD) }

    val allTransactions by viewModel.allTransactions.collectAsState()
    val cloudUrl by viewModel.cloudUrl.collectAsState()
    val cloudApiKey by viewModel.cloudApiKey.collectAsState()
    val autoSync by viewModel.autoSyncEnabled.collectAsState()
    val isCloudSyncing by viewModel.isCloudSyncing.collectAsState()
    val cloudStatus by viewModel.cloudStatus.collectAsState()
    val lastCloudSyncAt by viewModel.lastCloudSyncAt.collectAsState()
    val summary by viewModel.dashboardSummary.collectAsState()
    val stockRows by viewModel.stockRows.collectAsState()
    val filteredTransactions by viewModel.filteredTransactions.collectAsState()
    val availableMonths by viewModel.availableMonths.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedType by viewModel.typeFilter.collectAsState()
    val selectedStatus by viewModel.statusFilter.collectAsState()
    val selectedMonth by viewModel.monthFilter.collectAsState()

    val reportFromDate by viewModel.reportFromDate.collectAsState()
    val reportToDate by viewModel.reportToDate.collectAsState()
    val reportSummary by viewModel.reportSummary.collectAsState()
    val reportBreakdown by viewModel.reportBreakdown.collectAsState()

    val isFormOpen by viewModel.isFormOpen.collectAsState()
    val isPurchaseDoScreenOpen by viewModel.isPurchaseDoScreenOpen.collectAsState()
    val editingTransaction by viewModel.editingTransaction.collectAsState()
    val presetType by viewModel.presetType.collectAsState()

    // Snackbars
    LaunchedEffect(viewModel) {
        viewModel.userMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Back handling
    BackHandler(enabled = isFormOpen || isPurchaseDoScreenOpen || currentTab != AyamNavTab.DASHBOARD) {
        when {
            isPurchaseDoScreenOpen -> viewModel.closePurchaseDoScreen()
            isFormOpen -> viewModel.closeForm()
            currentTab != AyamNavTab.DASHBOARD -> currentTab = AyamNavTab.DASHBOARD
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Surface(
                color = Color(0xFF123D32),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFD8F2E5),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(imageVector = Icons.Default.Business, contentDescription = "Identitas perusahaan", tint = Color(0xFF123D32), modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "AyamKas Pro",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFF8FFFB)
                            )
                            Text(
                                text = "GM Ayam Potong · Offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFB7D2C7)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.shareSummaryViaWhatsApp(context) },
                            modifier = Modifier.testTag("btn_top_share_wa")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Bagikan Ringkasan",
                                tint = Color(0xFF7DE0AC)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.openForm() },
                            modifier = Modifier.testTag("btn_top_add_tx")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Tambah Transaksi",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                AyamNavTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.title) },
                        label = { Text(text = tab.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF176B50),
                            selectedTextColor = Color(0xFF176B50),
                            indicatorColor = Color(0xFFE3F4EB)
                        ),
                        modifier = Modifier.testTag("nav_item_${tab.name.lowercase()}")
                    )
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !isPurchaseDoScreenOpen && (currentTab == AyamNavTab.DASHBOARD || currentTab == AyamNavTab.TRANSAKSI || currentTab == AyamNavTab.STOCK)
            ) {
                FloatingActionButton(
                    onClick = {
                        if (currentTab == AyamNavTab.STOCK) {
                            viewModel.openForm(kind = "purchase")
                        } else {
                            viewModel.openForm()
                        }
                    },
                    containerColor = Color(0xFF176B50),
                    contentColor = Color.White,
                    modifier = Modifier.testTag("fab_add_tx")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah Transaksi")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isPurchaseDoScreenOpen) {
                PurchaseDoScreen(
                    onCancel = { viewModel.closePurchaseDoScreen() },
                    onSave = { date, doNumber, quantityKg, supplier, totalPrice, paidNow, paymentMethod, cashAccount,
                               weightLossKg, mortalityRatePct, fcr, weightGainKg, feedPricePerKg, processingCost, notes, confirmed ->
                        viewModel.savePurchaseDo(
                            date = date,
                            doNumber = doNumber,
                            quantityKg = quantityKg,
                            supplier = supplier,
                            totalPrice = totalPrice,
                            paidNow = paidNow,
                            paymentMethod = paymentMethod,
                            cashAccount = cashAccount,
                            weightLossKg = weightLossKg,
                            mortalityRatePct = mortalityRatePct,
                            fcr = fcr,
                            weightGainKg = weightGainKg,
                            feedPricePerKg = feedPricePerKg,
                            processingCost = processingCost,
                            notes = notes,
                            confirmed = confirmed
                        )
                    }
                )
            } else when (currentTab) {
                AyamNavTab.DASHBOARD -> DashboardScreen(
                    summary = summary,
                    recentConfirmed = allTransactions.filter { it.isConfirmed },
                    onNavigateToTransactions = { currentTab = AyamNavTab.TRANSAKSI },
                    onNavigateToStock = { currentTab = AyamNavTab.STOCK },
                    onQuickAddTransaction = { viewModel.openForm() },
                    onQuickAddExpense = { viewModel.openForm(kind = "expense") },
                    onQuickAddPurchase = { viewModel.openForm(kind = "purchase") }
                )

                AyamNavTab.TRANSAKSI -> TransaksiScreen(
                    transactions = filteredTransactions,
                    totalTransactionsCount = allTransactions.size,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.searchQuery.value = it },
                    selectedType = selectedType,
                    onTypeSelect = { viewModel.typeFilter.value = it },
                    selectedStatus = selectedStatus,
                    onStatusSelect = { viewModel.statusFilter.value = it },
                    selectedMonth = selectedMonth,
                    onMonthSelect = { viewModel.monthFilter.value = it },
                    availableMonths = availableMonths,
                    onAddTransaction = { viewModel.openForm() },
                    onEditTransaction = { viewModel.openForm(it) },
                    onToggleConfirm = { viewModel.toggleConfirm(it) },
                    onDeleteTransaction = { viewModel.deleteTransaction(it) },
                    onConfirmAllDrafts = { viewModel.confirmAllDrafts() }
                )

                AyamNavTab.STOCK -> StockScreen(
                    summary = summary,
                    stockItems = stockRows,
                    onAddPurchase = { viewModel.openForm(kind = "purchase") }
                )

                AyamNavTab.REPORTS -> ReportsScreen(
                    summary = reportSummary,
                    fromDate = reportFromDate,
                    toDate = reportToDate,
                    onFromDateChange = { viewModel.reportFromDate.value = it },
                    onToDateChange = { viewModel.reportToDate.value = it },
                    onClearDates = {
                        viewModel.reportFromDate.value = ""
                        viewModel.reportToDate.value = ""
                    },
                    breakdownItems = reportBreakdown,
                    onExportCsv = { viewModel.exportCsv(context) }
                )

                AyamNavTab.TOOLS -> ToolsBackupScreen(
                    onExportJson = { viewModel.exportJson(context) },
                    onExportCsv = { viewModel.exportCsv(context) },
                    onImportJson = { viewModel.importJson(it) },
                    onResetData = { viewModel.resetAllData() },
                    onRestoreDefaultDrafts = { viewModel.restoreDefaultData() },
                    onShareWhatsApp = { viewModel.shareSummaryViaWhatsApp(context) },
                    cloudUrl = cloudUrl,
                    cloudApiKey = cloudApiKey,
                    autoSync = autoSync,
                    isCloudSyncing = isCloudSyncing,
                    cloudStatus = cloudStatus,
                    lastCloudSyncAt = lastCloudSyncAt,
                    localTransactionCount = allTransactions.size,
                    draftTransactionCount = allTransactions.count { it.isDraft },
                    onSaveCloudSettings = { url, key, enabled -> viewModel.saveCloudSettings(url, key, enabled) },
                    onSyncNow = { url, key, enabled -> viewModel.syncNow(url, key, enabled) },
                    onBackupToDrive = { url, key -> viewModel.backupSpreadsheetToDrive(url, key) }
                )
            }
        }
    }

    // Modal Form Dialog
    if (isFormOpen) {
        val confirmedPurchases = allTransactions.filter { it.isConfirmed && it.type == "purchase" }
        TransactionFormDialog(
            transaction = editingTransaction,
            presetType = presetType,
            availablePurchaseBatches = confirmedPurchases,
            onDismiss = { viewModel.closeForm() },
            onSave = { id, date, ref, type, category, description, amount, paidAmount, qtyKg, batchRef, notes, isConfirmed, paymentMethod, cashAccount ->
                viewModel.saveTransaction(
                    id = id,
                    date = date,
                    ref = ref,
                    type = type,
                    category = category,
                    description = description,
                    amount = amount,
                    paidAmount = paidAmount,
                    qtyKg = qtyKg,
                    batchRef = batchRef,
                    notes = notes,
                    isConfirmed = isConfirmed,
                    paymentMethod = paymentMethod,
                    cashAccount = cashAccount,
                    supplier = editingTransaction?.supplier.orEmpty(),
                    weightLossKg = editingTransaction?.weightLossKg,
                    mortalityRatePct = editingTransaction?.mortalityRatePct,
                    fcr = editingTransaction?.fcr,
                    weightGainKg = editingTransaction?.weightGainKg,
                    feedPricePerKg = editingTransaction?.feedPricePerKg,
                    processingCost = editingTransaction?.processingCost ?: 0.0
                )
            }
        )
    }
}
