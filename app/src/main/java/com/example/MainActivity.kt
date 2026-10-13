package com.example

import android.os.Bundle
import android.content.Intent
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FinanceScreen
import com.example.ui.screens.WorkforceScreen
import com.example.ui.screens.BusinessSettingsScreen
import com.example.ui.screens.AuditLogScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.PurchaseDoScreen
import com.example.ui.screens.StockScreen
import com.example.ui.screens.ToolsBackupScreen
import com.example.ui.screens.TransactionFormDialog
import com.example.ui.screens.TransaksiScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AyamKasViewModel
import com.example.data.model.AyamKasFormatters
import com.example.data.model.AyamTransaction

private fun escapeReceiptHtml(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")

private fun imageUriAsDataUrl(context: Context, value: String): String {
    if (value.isBlank()) return ""
    return runCatching {
        val uri = Uri.parse(value)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Gambar tidak dapat dibuka")
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }.getOrDefault("")
}

/** Builds a print-ready nota or kwitansi from the saved transaction and company profile. */
private fun printTransactionReceipt(
    context: Context,
    transaction: AyamTransaction,
    companyName: String,
    companyAddress: String,
    companyPhone: String,
    companyEmail: String,
    companyTaxId: String,
    companyLogoUri: String,
    companyBannerUri: String
) {
    val manager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val documentRef = transaction.ref.ifBlank { transaction.id }
    val title = when (transaction.type) {
        "sale" -> "Kwitansi-Penjualan-$documentRef"
        "purchase" -> "Nota-Pembelian-$documentRef"
        else -> "Bukti-Transaksi-$documentRef"
    }
    val logo = imageUriAsDataUrl(context, companyLogoUri)
    val banner = imageUriAsDataUrl(context, companyBannerUri)
    val amount = AyamKasFormatters.formatRupiah(transaction.amount)
    val actualCash = transaction.paidAmount ?: kotlin.math.abs(transaction.cashEffect())
    val paid = AyamKasFormatters.formatRupiah(actualCash)
    val outstanding = if (transaction.type in listOf("sale", "purchase")) {
        (transaction.amount - actualCash).coerceAtLeast(0.0)
    } else 0.0
    val docTitle = when (transaction.type) {
        "sale" -> "KWITANSI PENJUALAN"
        "purchase" -> "NOTA PEMBELIAN"
        else -> "BUKTI TRANSAKSI"
    }
    val statusBanner = if (transaction.isConfirmed) "" else "<div class='status'>DRAF — BELUM TERVERIFIKASI</div>"
    val html = """
        <!doctype html><html><head><meta charset="utf-8">
        <style>
          @page { size: A5; margin: 10mm; }
          body { font-family: Arial, sans-serif; color: #18251f; font-size: 12px; }
          .banner { width:100%; max-height:70px; object-fit:cover; margin-bottom:8px; }
          .brand { display:flex; align-items:center; gap:12px; border-bottom:2px solid #176b50; padding-bottom:10px; }
          .logo { width:64px; height:64px; object-fit:contain; }
          .name { font-size:18px; font-weight:bold; margin:0 0 4px; }
          .sub { color:#4c5d54; margin:2px 0; font-size:10px; }
          h2 { text-align:center; font-size:16px; margin:18px 0 12px; }
          .status { background:#fff0d2; padding:7px; font-weight:bold; margin:8px 0; text-align:center; }
          table { width:100%; border-collapse:collapse; margin-top:12px; }
          td { padding:7px 4px; border-bottom:1px solid #dce5df; vertical-align:top; }
          td:first-child { width:38%; color:#526259; }
          .total { font-weight:bold; font-size:14px; }
          .signature { display:flex; justify-content:space-between; margin-top:34px; text-align:center; }
          .sig { width:42%; border-top:1px solid #aab5ae; padding-top:6px; }
          .foot { text-align:center; border-top:1px dashed #aab5ae; margin-top:20px; padding-top:8px; color:#526259; font-size:9px; }
        </style></head><body>
        ${if (banner.isNotBlank()) "<img class='banner' src='$banner' alt='Banner perusahaan'>" else ""}
        <div class="brand">
          ${if (logo.isNotBlank()) "<img class='logo' src='$logo' alt='Logo perusahaan'>" else ""}
          <div>
            <p class="name">${escapeReceiptHtml(companyName.ifBlank { "Nama Usaha Belum Diatur" })}</p>
            ${if (companyAddress.isNotBlank()) "<p class='sub'>${escapeReceiptHtml(companyAddress)}</p>" else ""}
            ${if (companyPhone.isNotBlank()) "<p class='sub'>Telp/WA: ${escapeReceiptHtml(companyPhone)}</p>" else ""}
            ${if (companyEmail.isNotBlank()) "<p class='sub'>Email: ${escapeReceiptHtml(companyEmail)}</p>" else ""}
            ${if (companyTaxId.isNotBlank()) "<p class='sub'>NPWP: ${escapeReceiptHtml(companyTaxId)}</p>" else ""}
          </div>
        </div>
        <h2>$docTitle</h2>
        $statusBanner
        <table>
          <tr><td>Nomor dokumen</td><td>${escapeReceiptHtml(documentRef)}</td></tr>
          <tr><td>Tanggal</td><td>${escapeReceiptHtml(AyamKasFormatters.formatDate(transaction.date))}</td></tr>
          <tr><td>Jenis transaksi</td><td>${escapeReceiptHtml(com.example.data.model.AyamTransactionType.fromKey(transaction.type).label)}</td></tr>
          <tr><td>Keterangan</td><td>${escapeReceiptHtml(transaction.description)}</td></tr>
          ${if (transaction.qtyKg != null) "<tr><td>Berat</td><td>${escapeReceiptHtml(AyamKasFormatters.formatNumber(transaction.qtyKg))} kg</td></tr>" else ""}
          ${if (transaction.batchRef.isNotBlank()) "<tr><td>DO terkait</td><td>${escapeReceiptHtml(transaction.batchRef)}</td></tr>" else ""}
          <tr><td>Total transaksi</td><td class="total">$amount</td></tr>
          <tr><td>Kas diterima/dibayar</td><td>$paid</td></tr>
          ${if (transaction.type in listOf("sale", "purchase") && outstanding > 0) "<tr><td>Sisa piutang/utang</td><td>${AyamKasFormatters.formatRupiah(outstanding)}</td></tr>" else ""}
          <tr><td>Metode pembayaran</td><td>${escapeReceiptHtml(transaction.paymentMethod)}</td></tr>
          <tr><td>Akun kas</td><td>${escapeReceiptHtml(transaction.cashAccount)}</td></tr>
          ${if (transaction.notes.isNotBlank()) "<tr><td>Catatan</td><td>${escapeReceiptHtml(transaction.notes)}</td></tr>" else ""}
        </table>
        <div class="signature"><div class="sig">Penerima / Pembeli</div><div class="sig">Petugas / Penjual</div></div>
        <div class="foot">Dokumen dibuat dari AyamKas Pro. Simpan nota ini bersama bukti pembayaran asli.</div>
        </body></html>
    """.trimIndent()

    val webView = WebView(context)
    webView.settings.javaScriptEnabled = false
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            manager.print(
                title,
                view.createPrintDocumentAdapter(title),
                PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A5)
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()
            )
        }
    }
    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}

enum class AyamNavTab(val title: String, val icon: ImageVector) {
    DASHBOARD("Ringkasan", Icons.Default.Dashboard),
    TRANSAKSI("Transaksi", Icons.Default.ReceiptLong),
    STOCK("DO & Stok", Icons.Default.Inventory2),
    REPORTS("Laporan", Icons.Default.Assessment),
    TOOLS("Cadangan", Icons.Default.Backup),
    FINANCE("Kas & Pinjaman", Icons.Default.AccountBalance),
    WORKFORCE("Karyawan", Icons.Default.Groups),
    SETTINGS("Pengaturan", Icons.Default.Settings),
    AUDIT("Audit Aktivitas", Icons.Default.ReceiptLong)
}

class MainActivity : ComponentActivity() {

    private val viewModel: AyamKasViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val darkTheme = when (themeMode.uppercase()) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }
            MyApplicationTheme(darkTheme = darkTheme) {
                AyamKasApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun AyamKasApp(viewModel: AyamKasViewModel) {
    val context = LocalContext.current
    val companyName by viewModel.companyName.collectAsState()
    val companyLogoUri by viewModel.companyLogoUri.collectAsState()
    val companyBannerUri by viewModel.companyBannerUri.collectAsState()
    val companyAddress by viewModel.companyAddress.collectAsState()
    val companyPhone by viewModel.companyPhone.collectAsState()
    val companyEmail by viewModel.companyEmail.collectAsState()
    val companyTaxId by viewModel.companyTaxId.collectAsState()
    val cashAccountBalances by viewModel.cashAccountBalances.collectAsState()
    val cashFlows by viewModel.cashFlows.collectAsState(emptyList())
    val externalLoans by viewModel.externalLoans.collectAsState(emptyList())
    val employees by viewModel.employees.collectAsState(emptyList())
    val attendance by viewModel.attendance.collectAsState(emptyList())
    val payroll by viewModel.payroll.collectAsState(emptyList())
    val auditLog by viewModel.auditLog.collectAsState(emptyList())
    var showMoreMenu by remember { mutableStateOf(false) }
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.saveCompanyLogoUri(uri.toString())
        }
    }
    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.saveCompanyBannerUri(uri.toString())
        }
    }
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
    val purchaseDoEditingTransaction by viewModel.purchaseDoEditingTransaction.collectAsState()
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
                                if (companyLogoUri.isNotBlank()) {
                                    AsyncImage(
                                        model = Uri.parse(companyLogoUri),
                                        contentDescription = "Logo perusahaan",
                                        modifier = Modifier.size(32.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Icon(imageVector = Icons.Default.Business, contentDescription = "Identitas perusahaan", tint = Color(0xFF123D32), modifier = Modifier.size(22.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = companyName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFF8FFFB)
                            )
                            Text(
                                text = "AyamKas Pro · Buku usaha",
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
                        IconButton(onClick = { showMoreMenu = true }, modifier = Modifier.testTag("btn_top_business_menu")) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu usaha", tint = Color.White)
                        }
                        DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                            DropdownMenuItem(text = { Text("Kas & Pinjaman") }, onClick = { currentTab = AyamNavTab.FINANCE; showMoreMenu = false })
                            DropdownMenuItem(text = { Text("Karyawan & Payroll") }, onClick = { currentTab = AyamNavTab.WORKFORCE; showMoreMenu = false })
                            DropdownMenuItem(text = { Text("Pengaturan Perusahaan & Master Data") }, onClick = { currentTab = AyamNavTab.SETTINGS; showMoreMenu = false })
                            DropdownMenuItem(text = { Text("Audit Aktivitas") }, onClick = { currentTab = AyamNavTab.AUDIT; showMoreMenu = false })
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
                listOf(AyamNavTab.DASHBOARD, AyamNavTab.TRANSAKSI, AyamNavTab.STOCK, AyamNavTab.REPORTS, AyamNavTab.TOOLS).forEach { tab ->
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
                    initialTransaction = purchaseDoEditingTransaction,
                    onCancel = { viewModel.closePurchaseDoScreen() },
                    onSave = { date, doNumber, quantityKg, supplier, totalPrice, paidNow, paymentMethod, cashAccount,
                               weightLossKg, mortalityRatePct, fcr, weightGainKg, feedPricePerKg, processingCost, notes, confirmed, editingId ->
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
                            confirmed = confirmed,
                            editingId = editingId
                        )
                    }
                )
            } else when (currentTab) {
                AyamNavTab.DASHBOARD -> DashboardScreen(
                    summary = summary,
                    cashAccountBalances = cashAccountBalances,
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
                    onPrintTransaction = { tx ->
                        printTransactionReceipt(
                            context = context,
                            transaction = tx,
                            companyName = companyName,
                            companyAddress = companyAddress,
                            companyPhone = companyPhone,
                            companyEmail = companyEmail,
                            companyTaxId = companyTaxId,
                            companyLogoUri = companyLogoUri,
                            companyBannerUri = companyBannerUri
                        )
                    },
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

                AyamNavTab.FINANCE -> FinanceScreen(
                    cashBalances = cashAccountBalances,
                    cashFlows = cashFlows,
                    loans = externalLoans,
                    onSaveCashFlow = { flow, onSaved -> viewModel.saveCashFlow(flow, onSaved) },
                    onSaveLoan = { loan, onSaved -> viewModel.saveExternalLoan(loan, onSaved) }
                )

                AyamNavTab.WORKFORCE -> WorkforceScreen(
                    employees = employees,
                    attendance = attendance,
                    payroll = payroll,
                    onSaveEmployee = { record, onSaved -> viewModel.saveEmployee(record, onSaved) },
                    onSaveAttendance = { record, onSaved -> viewModel.saveAttendance(record, onSaved) },
                    onSavePayroll = { record, onSaved -> viewModel.savePayroll(record, onSaved) }
                )

                AyamNavTab.SETTINGS -> BusinessSettingsScreen(
                    companyName = companyName,
                    companyLogoUri = companyLogoUri,
                    companyBannerUri = companyBannerUri,
                    companyAddress = companyAddress,
                    companyPhone = companyPhone,
                    companyEmail = companyEmail,
                    companyTaxId = companyTaxId,
                    themeMode = viewModel.themeMode.value,
                    onSave = { name, address, phone, email, taxId, mode ->
                        viewModel.saveBusinessSettings(name, address, phone, email, taxId, mode)
                    },
                    onChooseLogo = { logoPicker.launch(arrayOf("image/*")) },
                    onRemoveLogo = { viewModel.saveCompanyLogoUri("") },
                    onChooseBanner = { bannerPicker.launch(arrayOf("image/*")) },
                    onRemoveBanner = { viewModel.saveCompanyBannerUri("") }
                )

                AyamNavTab.AUDIT -> AuditLogScreen(entries = auditLog)

                AyamNavTab.TOOLS -> ToolsBackupScreen(
                    onExportJson = { viewModel.exportJson(context) },
                    onExportCsv = { viewModel.exportCsv(context) },
                    onImportJson = { viewModel.importJson(it) },
                    onResetData = { viewModel.resetAllData() },
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
