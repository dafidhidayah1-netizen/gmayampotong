package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.KasDatabase
import com.example.data.model.AyamKasCalculator
import com.example.data.model.AyamKasFormatters
import com.example.data.model.AyamKasSummary
import com.example.data.model.AyamTransaction
import com.example.data.model.CashFlow
import com.example.data.model.ExternalLoan
import com.example.data.model.EmployeeRecord
import com.example.data.model.AttendanceRecord
import com.example.data.model.PayrollRecord
import com.example.data.model.AyamTransactionType
import com.example.data.model.DeletedAyamTransaction
import com.example.data.model.DoStockItem
import com.example.data.model.TypeBreakdownItem
import com.example.data.repository.AyamKasRepository
import com.example.data.remote.GoogleSheetsSyncClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AyamKasViewModel(application: Application) : AndroidViewModel(application) {

    private val cloudPrefs = application.getSharedPreferences("ayamkaspro_cloud", Context.MODE_PRIVATE)
    private val businessPrefs = application.getSharedPreferences("ayamkaspro_business", Context.MODE_PRIVATE)
    val companyName = MutableStateFlow(businessPrefs.getString("companyName", "GM Ayam Potong").orEmpty().ifBlank { "GM Ayam Potong" })
    val companyLogoUri = MutableStateFlow(businessPrefs.getString("companyLogoUri", "").orEmpty())
    val themeMode = MutableStateFlow(businessPrefs.getString("themeMode", "SYSTEM").orEmpty())
    private val cloudClient = GoogleSheetsSyncClient()
    val cloudUrl = MutableStateFlow(cloudPrefs.getString("url", "").orEmpty())
    val cloudApiKey = MutableStateFlow(cloudPrefs.getString("apiKey", "").orEmpty())
    val autoSyncEnabled = MutableStateFlow(cloudPrefs.getBoolean("autoSync", false))
    val isCloudSyncing = MutableStateFlow(false)
    val cloudStatus = MutableStateFlow("Belum disambungkan")
    val lastCloudSyncAt = MutableStateFlow(cloudPrefs.getString("lastSyncAt", "").orEmpty())
    private var pendingAutoSync: Job? = null
    private val repository: AyamKasRepository by lazy {
        val db = KasDatabase.getDatabase(application)
        AyamKasRepository(db.ayamKasDao(), db.businessLedgerDao())
    }
    val allTransactions: StateFlow<List<AyamTransaction>>
    val allDeleted: StateFlow<List<DeletedAyamTransaction>>
    val cashTransactions = repository.cashTransactions
    val cashFlows = repository.cashFlows
    val externalLoans = repository.externalLoans
    val employees = repository.employees
    val attendance = repository.attendance
    val payroll = repository.payroll

    val cashAccountBalances: StateFlow<Map<String, Double>> = combine(
        repository.confirmedCashTransactions,
        repository.cashFlows
    ) { movements, transfers ->
        val balances = linkedMapOf("DOMPET" to 0.0, "ATM/BANK" to 0.0, "QRIS" to 0.0)
        movements.filter { it.status == "confirmed" && !it.deleted }.forEach { movement ->
            val account = movement.accountName.ifBlank { "DOMPET" }
            val amount = if (movement.direction == "IN") movement.amount else -movement.amount
            balances[account] = (balances[account] ?: 0.0) + amount
        }
        transfers.filter { it.status == "confirmed" && it.amount > 0.0 }.forEach { transfer ->
            balances[transfer.fromAccount] = (balances[transfer.fromAccount] ?: 0.0) - transfer.amount
            balances[transfer.toAccount] = (balances[transfer.toAccount] ?: 0.0) + transfer.amount
        }
        balances
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val searchQuery = MutableStateFlow("")
    val typeFilter = MutableStateFlow("all")
    val statusFilter = MutableStateFlow("all")
    val monthFilter = MutableStateFlow("all")

    val reportFromDate = MutableStateFlow("")
    val reportToDate = MutableStateFlow("")

    val isFormOpen = MutableStateFlow(false)
    val editingTransaction = MutableStateFlow<AyamTransaction?>(null)
    val presetType = MutableStateFlow<String?>(null)
    val isPurchaseDoScreenOpen = MutableStateFlow(false)
    val purchaseDoEditingTransaction = MutableStateFlow<AyamTransaction?>(null)

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage = _userMessage.asSharedFlow()

    init {
        allTransactions = repository.allTransactions.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            emptyList()
        )

        allDeleted = repository.allDeleted.stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            emptyList()
        )

        viewModelScope.launch {
            // Never preload historical transactions silently. New installations start empty;
            // records enter only through explicit user entry or restore/import.
            if (autoSyncEnabled.value && cloudUrl.value.isNotBlank() && cloudApiKey.value.isNotBlank()) {
                delay(900)
                performCloudSync()
            }
        }
    }

    // Dashboard calculations from all transactions
    val dashboardSummary: StateFlow<AyamKasSummary> = allTransactions.combine(MutableStateFlow(Unit)) { txs, _ ->
        AyamKasCalculator.calculate(txs)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AyamKasSummary()
    )

    // Stock rows for DO inventory
    val stockRows: StateFlow<List<DoStockItem>> = allTransactions.combine(MutableStateFlow(Unit)) { txs, _ ->
        AyamKasCalculator.calculateStockRows(txs)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Filtered transactions for the Transaksi screen
    val filteredTransactions: StateFlow<List<AyamTransaction>> = combine(
        allTransactions,
        searchQuery,
        typeFilter,
        statusFilter,
        monthFilter
    ) { txs, q, typ, st, mo ->
        val query = q.trim().lowercase()
        txs.filter { t ->
            val matchQuery = query.isEmpty() || (
                t.ref.lowercase().contains(query) ||
                t.description.lowercase().contains(query) ||
                t.notes.lowercase().contains(query) ||
                t.category.lowercase().contains(query)
            )
            val matchType = typ == "all" || t.type == typ
            val matchStatus = st == "all" || t.status == st
            val matchMonth = mo == "all" || t.date.startsWith(mo)
            matchQuery && matchType && matchStatus && matchMonth
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Available months in transactions
    val availableMonths: StateFlow<List<String>> = allTransactions.combine(MutableStateFlow(Unit)) { txs, _ ->
        txs.map { it.date.take(7) }
            .filter { it.isNotBlank() && it.length == 7 }
            .distinct()
            .sortedDescending()
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Report summary based on report date range
    val reportSummary: StateFlow<AyamKasSummary> = combine(
        allTransactions,
        reportFromDate,
        reportToDate
    ) { txs, from, to ->
        val filtered = txs.filter { t ->
            t.isConfirmed &&
            (from.isBlank() || t.date >= from) &&
            (to.isBlank() || t.date <= to)
        }
        AyamKasCalculator.calculate(txs, filtered)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AyamKasSummary()
    )

    // Report breakdown by transaction type
    val reportBreakdown: StateFlow<List<TypeBreakdownItem>> = combine(
        allTransactions,
        reportFromDate,
        reportToDate
    ) { txs, from, to ->
        val filtered = txs.filter { t ->
            t.isConfirmed &&
            (from.isBlank() || t.date >= from) &&
            (to.isBlank() || t.date <= to)
        }
        AyamTransactionType.entries.map { type ->
            val matches = filtered.filter { it.type == type.key }
            TypeBreakdownItem(
                typeKey = type.key,
                label = type.label,
                count = matches.size,
                totalAmount = matches.sumOf { it.amount }
            )
        }.filter { it.count > 0 }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    fun saveBusinessSettings(nameInput: String, themeModeInput: String) {
        val name = nameInput.trim()
        val theme = themeModeInput.uppercase(Locale.ROOT)
        if (name.isBlank()) {
            viewModelScope.launch { _userMessage.emit("Nama perusahaan tidak boleh kosong") }
            return
        }
        if (theme !in setOf("SYSTEM", "LIGHT", "DARK")) {
            viewModelScope.launch { _userMessage.emit("Mode tema tidak valid") }
            return
        }
        businessPrefs.edit().putString("companyName", name).putString("themeMode", theme).apply()
        companyName.value = name
        themeMode.value = theme
        viewModelScope.launch { _userMessage.emit("Nama perusahaan dan tema tersimpan") }
    }

    fun saveCompanyLogoUri(uri: String) {
        val clean = uri.trim()
        businessPrefs.edit().putString("companyLogoUri", clean).apply()
        companyLogoUri.value = clean
        viewModelScope.launch { _userMessage.emit(if (clean.isBlank()) "Logo perusahaan dihapus" else "Logo perusahaan tersimpan") }
    }

    fun saveCloudSettings(urlInput: String, apiKeyInput: String, autoSync: Boolean) {
        if (!persistCloudSettings(urlInput, apiKeyInput, autoSync)) return
        viewModelScope.launch { _userMessage.emit("Pengaturan cloud tersimpan") }
    }

    fun syncNow(urlInput: String, apiKeyInput: String, autoSync: Boolean) {
        if (!persistCloudSettings(urlInput, apiKeyInput, autoSync)) return
        if (isCloudSyncing.value) return
        viewModelScope.launch { performCloudSync() }
    }

    fun backupSpreadsheetToDrive(urlInput: String, apiKeyInput: String) {
        if (!persistCloudSettings(urlInput, apiKeyInput, autoSyncEnabled.value)) return
        if (isCloudSyncing.value) return
        viewModelScope.launch {
            isCloudSyncing.value = true
            cloudStatus.value = "Membuat cadangan spreadsheet di Google Drive…"
            try {
                val backup = cloudClient.backup(cloudUrl.value, cloudApiKey.value)
                cloudStatus.value = "Cadangan spreadsheet berhasil: ${backup.name}"
                _userMessage.emit("Cadangan tersimpan di Drive: ${backup.url}")
            } catch (e: Exception) {
                cloudStatus.value = "Cadangan gagal: ${e.message ?: "kesalahan koneksi"}"
                _userMessage.emit(cloudStatus.value)
            } finally {
                isCloudSyncing.value = false
            }
        }
    }

    private fun persistCloudSettings(urlInput: String, apiKeyInput: String, autoSync: Boolean): Boolean {
        val url = urlInput.trim()
        val key = apiKeyInput.trim()
        if (!url.startsWith("https://script.google.com/macros/s/") || !url.endsWith("/exec")) {
            cloudStatus.value = "URL tidak valid. Gunakan URL Web App Apps Script yang berakhiran /exec."
            viewModelScope.launch { _userMessage.emit(cloudStatus.value) }
            return false
        }
        if (key.length < 32) {
            cloudStatus.value = "Kunci sinkronisasi belum valid. Buat kunci dari menu AyamKas Pro di Google Sheets."
            viewModelScope.launch { _userMessage.emit(cloudStatus.value) }
            return false
        }
        cloudPrefs.edit()
            .putString("url", url)
            .putString("apiKey", key)
            .putBoolean("autoSync", autoSync)
            .apply()
        cloudUrl.value = url
        cloudApiKey.value = key
        autoSyncEnabled.value = autoSync
        cloudStatus.value = "Pengaturan tersimpan. Data lokal belum berubah."
        return true
    }

    private suspend fun performCloudSync() {
        if (isCloudSyncing.value) return
        val url = cloudUrl.value
        val key = cloudApiKey.value
        if (url.isBlank() || key.isBlank()) {
            cloudStatus.value = "Masukkan URL Web App dan kunci sinkronisasi terlebih dahulu."
            return
        }
        isCloudSyncing.value = true
        cloudStatus.value = "Sinkronisasi berlangsung…"
        try {
            val result = repository.syncWithGoogleSheets(cloudClient, url, key)
            val stamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID")).format(Date())
            lastCloudSyncAt.value = stamp
            cloudPrefs.edit().putString("lastSyncAt", stamp).apply()
            cloudStatus.value = "Berhasil · data server ${result.totalOnServer} transaksi · ditarik ${result.pulled} · diproses server ${result.serverAccepted}"
            _userMessage.emit("Sinkronisasi selesai. Periksa Ringkasan dan Validasi di Google Sheets.")
        } catch (e: Exception) {
            cloudStatus.value = "Sinkronisasi gagal: ${e.message ?: "jaringan atau izin server"}"
            _userMessage.emit(cloudStatus.value)
        } finally {
            isCloudSyncing.value = false
        }
    }

    private fun scheduleAutoSync() {
        if (!autoSyncEnabled.value || cloudUrl.value.isBlank() || cloudApiKey.value.isBlank()) return
        pendingAutoSync?.cancel()
        pendingAutoSync = viewModelScope.launch {
            delay(1200)
            performCloudSync()
        }
    }

    fun openForm(tx: AyamTransaction? = null, kind: String? = null) {
        if ((tx == null && kind == "purchase") || tx?.type == "purchase") {
            openPurchaseDoScreen(tx)
            return
        }
        editingTransaction.value = tx
        presetType.value = kind
        isFormOpen.value = true
    }

    fun openPurchaseDoScreen(transaction: AyamTransaction? = null) {
        isFormOpen.value = false
        editingTransaction.value = null
        presetType.value = null
        purchaseDoEditingTransaction.value = transaction
        isPurchaseDoScreenOpen.value = true
    }

    fun closePurchaseDoScreen() {
        isPurchaseDoScreenOpen.value = false
        purchaseDoEditingTransaction.value = null
    }

    fun savePurchaseDo(
        date: String,
        doNumber: String,
        quantityKg: Double,
        supplier: String,
        totalPrice: Double,
        paidNow: Double,
        paymentMethod: String,
        cashAccount: String,
        weightLossKg: Double,
        mortalityRatePct: Double,
        fcr: Double?,
        weightGainKg: Double?,
        feedPricePerKg: Double?,
        processingCost: Double,
        notes: String,
        confirmed: Boolean,
        editingId: String? = null
    ) {
        val invalid = when {
            date.isBlank() -> "Tanggal pembelian wajib diisi"
            doNumber.isBlank() -> "Nomor DO wajib diisi"
            supplier.isBlank() -> "Nama supplier wajib diisi"
            quantityKg <= 0.0 -> "Berat pembelian harus lebih dari nol"
            totalPrice <= 0.0 -> "Harga total DO harus lebih dari nol"
            paidNow < 0.0 || paidNow > totalPrice -> "Pembayaran harus antara Rp0 dan total DO"
            weightLossKg < 0.0 -> "Penyusutan tidak boleh negatif"
            mortalityRatePct !in 0.0..100.0 -> "Persentase kematian harus 0–100%"
            processingCost < 0.0 -> "Biaya pemotongan tidak boleh negatif"
            fcr != null && fcr < 0.0 -> "FCR tidak boleh negatif"
            weightGainKg != null && weightGainKg < 0.0 -> "Pertambahan berat tidak boleh negatif"
            feedPricePerKg != null && feedPricePerKg < 0.0 -> "Harga pakan tidak boleh negatif"
            (fcr == null) != (weightGainKg == null) || (fcr == null) != (feedPricePerKg == null) ->
                "Isi FCR, pertambahan berat, dan harga pakan bersamaan atau kosongkan semuanya"
            weightLossKg > quantityKg -> "Penyusutan tidak boleh melebihi kg pembelian"
            else -> null
        }
        if (invalid != null) {
            viewModelScope.launch { _userMessage.emit(invalid) }
            return
        }
        if (allTransactions.value.any { it.id != editingId && it.type == "purchase" && it.ref.trim().equals(doNumber.trim(), ignoreCase = true) }) {
            viewModelScope.launch { _userMessage.emit("Nomor DO sudah digunakan. Gunakan referensi unik untuk setiap pengiriman fisik.") }
            return
        }
        saveTransaction(
            id = editingId,
            date = date,
            ref = doNumber,
            type = "purchase",
            category = "Ayam / DO",
            description = "Pembelian DO $doNumber · $supplier",
            amount = totalPrice,
            paidAmount = paidNow,
            qtyKg = quantityKg,
            batchRef = "",
            notes = notes,
            isConfirmed = confirmed,
            paymentMethod = paymentMethod,
            cashAccount = cashAccount,
            supplier = supplier,
            weightLossKg = weightLossKg,
            mortalityRatePct = mortalityRatePct,
            fcr = fcr,
            weightGainKg = weightGainKg,
            feedPricePerKg = feedPricePerKg,
            processingCost = processingCost
        )
        isPurchaseDoScreenOpen.value = false
    }

    fun closeForm() {
        isFormOpen.value = false
        editingTransaction.value = null
        presetType.value = null
    }

    fun saveTransaction(
        id: String?,
        date: String,
        ref: String,
        type: String,
        category: String,
        description: String,
        amount: Double,
        paidAmount: Double?,
        qtyKg: Double?,
        batchRef: String,
        notes: String,
        isConfirmed: Boolean,
        paymentMethod: String = "CASH",
        cashAccount: String = "DOMPET",
        supplier: String = "",
        weightLossKg: Double? = null,
        mortalityRatePct: Double? = null,
        fcr: Double? = null,
        weightGainKg: Double? = null,
        feedPricePerKg: Double? = null,
        processingCost: Double = 0.0
    ) {
        val existingBeforeSave = id?.let { key -> allTransactions.value.firstOrNull { it.id == key } }
        if (date.length != 10 || date[4] != '-' || date[7] != '-') {
            viewModelScope.launch { _userMessage.emit("Tanggal transaksi harus berformat YYYY-MM-DD") }
            return
        }
        if (amount <= 0.0) {
            viewModelScope.launch { _userMessage.emit("Nominal transaksi harus lebih dari nol") }
            return
        }
        if (type in listOf("sale", "purchase", "expense") &&
            (paidAmount == null || paidAmount < 0.0 || paidAmount > amount)) {
            viewModelScope.launch { _userMessage.emit("Pembayaran harus diisi di antara Rp0 dan nominal transaksi") }
            return
        }
        if (type == "sale" && isConfirmed) {
            val quantity = qtyKg ?: 0.0
            if (quantity <= 0.0 || batchRef.isBlank()) {
                viewModelScope.launch { _userMessage.emit("Penjualan terverifikasi wajib memiliki kg terjual dan tautan DO sumber") }
                return
            }
            val transactionsWithoutEditedSale = allTransactions.value.filterNot { it.id == existingBeforeSave?.id }
            val sourcePurchase = transactionsWithoutEditedSale.firstOrNull {
                it.type == "purchase" && it.isConfirmed && it.ref.trim().equals(batchRef.trim(), ignoreCase = true)
            }
            if (sourcePurchase == null) {
                viewModelScope.launch { _userMessage.emit("DO sumber belum ada atau belum terverifikasi; penjualan tidak disimpan sebagai transaksi terverifikasi") }
                return
            }
            if (date < sourcePurchase.date) {
                viewModelScope.launch { _userMessage.emit("Tanggal penjualan tidak boleh sebelum tanggal pembelian DO sumber") }
                return
            }
            val stock = AyamKasCalculator.calculateStockRows(transactionsWithoutEditedSale)
                .firstOrNull { it.purchaseTx.ref.trim().equals(batchRef.trim(), ignoreCase = true) }
            val available = stock?.remainingKg ?: 0.0
            if (quantity > available + 0.0001) {
                viewModelScope.launch { _userMessage.emit("Stok DO ${batchRef.trim()} tidak cukup: tersedia ${AyamKasFormatters.formatNumber(available)} kg") }
                return
            }
        }
        viewModelScope.launch {
            val txId = id?.ifBlank { null } ?: "TX-${java.util.UUID.randomUUID()}"
            val existing = allTransactions.value.firstOrNull { it.id == txId }
            val tx = AyamTransaction(
                id = txId,
                date = date,
                ref = ref.trim(),
                type = type,
                category = category,
                description = description.trim(),
                amount = amount,
                paidAmount = paidAmount,
                qtyKg = qtyKg,
                batchRef = batchRef.trim(),
                notes = notes.trim(),
                status = if (isConfirmed) "confirmed" else "draft",
                source = existing?.source ?: "manual",
                paymentMethod = paymentMethod,
                cashAccount = cashAccount,
                supplier = supplier.trim(),
                weightLossKg = weightLossKg,
                mortalityRatePct = mortalityRatePct,
                fcr = fcr,
                weightGainKg = weightGainKg,
                feedPricePerKg = feedPricePerKg,
                processingCost = processingCost
            )
            repository.saveTransaction(tx)
            scheduleAutoSync()
            closeForm()
            _userMessage.emit(if (existing != null) "Transaksi diperbarui" else "Transaksi disimpan")
        }
    }

    fun toggleConfirm(tx: AyamTransaction) {
        viewModelScope.launch {
            if (tx.isConfirmed) {
                runCatching { repository.toggleConfirm(tx.id, tx.status) }
                    .onSuccess {
                        scheduleAutoSync()
                        _userMessage.emit("Transaksi kembali menjadi Draf")
                    }
                    .onFailure { _userMessage.emit(it.message ?: "Status transaksi gagal diubah") }
                return@launch
            }
            if (tx.paidAmount == null && tx.type in listOf("sale", "purchase", "expense")) {
                _userMessage.emit("Isi jumlah yang benar-benar dibayar/diterima. Gunakan Rp0 untuk kredit, bukan membiarkannya kosong.")
                openForm(tx)
                return@launch
            }
            runCatching { repository.confirmTransaction(tx.id) }
                .onSuccess {
                    scheduleAutoSync()
                    _userMessage.emit("Transaksi lolos validasi dan masuk ke laporan")
                }
                .onFailure { _userMessage.emit("Belum dapat diverifikasi: ${it.message ?: "periksa data transaksi"}") }
        }
    }

    fun confirmAllDrafts() {
        viewModelScope.launch {
            val drafts = allTransactions.value.filter { it.isDraft }
            var confirmed = 0
            var skipped = 0
            drafts.forEach { tx ->
                // Confirmation validates each record independently. It never fills missing
                // payments with the transaction amount and never bypasses DO/HPP checks.
                runCatching { repository.confirmTransaction(tx.id) }
                    .onSuccess { confirmed++ }
                    .onFailure { skipped++ }
            }
            if (confirmed > 0) scheduleAutoSync()
            _userMessage.emit("Verifikasi massal: $confirmed berhasil, $skipped dilewati karena data belum lengkap. Buka dan perbaiki tiap transaksi yang tertunda.")
        }
    }

    fun deleteTransaction(tx: AyamTransaction) {
        viewModelScope.launch {
            repository.deleteTransaction(tx)
            scheduleAutoSync()
            _userMessage.emit("Transaksi “${tx.description}” dihapus")
        }
    }

    fun restoreDeleted(deleted: DeletedAyamTransaction) {
        viewModelScope.launch {
            repository.restoreDeleted(deleted)
            scheduleAutoSync()
            _userMessage.emit("Transaksi berhasil dipulihkan")
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            repository.clearAll()
            scheduleAutoSync()
            _userMessage.emit("Semua transaksi lokal telah dihapus")
        }
    }

    fun saveCashFlow(flow: CashFlow) {
        viewModelScope.launch {
            runCatching { repository.saveCashFlow(flow) }
                .onSuccess {
                    scheduleAutoSync()
                    _userMessage.emit("Transfer antar rekening tersimpan")
                }
                .onFailure { _userMessage.emit(it.message ?: "Transfer gagal disimpan") }
        }
    }

    fun saveExternalLoan(loan: ExternalLoan) {
        viewModelScope.launch {
            runCatching { repository.saveExternalLoan(loan) }
                .onSuccess {
                    scheduleAutoSync()
                    _userMessage.emit("Catatan pinjaman tersimpan terpisah dari laba usaha")
                }
                .onFailure { _userMessage.emit(it.message ?: "Pinjaman gagal disimpan") }
        }
    }

    fun saveEmployee(employee: EmployeeRecord) {
        viewModelScope.launch {
            runCatching { repository.saveEmployee(employee) }
                .onSuccess {
                    scheduleAutoSync()
                    _userMessage.emit("Data karyawan tersimpan")
                }
                .onFailure { _userMessage.emit(it.message ?: "Karyawan gagal disimpan") }
        }
    }

    fun saveAttendance(record: AttendanceRecord) {
        viewModelScope.launch {
            runCatching { repository.saveAttendance(record) }
                .onSuccess {
                    scheduleAutoSync()
                    _userMessage.emit("Absensi tersimpan")
                }
                .onFailure { _userMessage.emit(it.message ?: "Absensi gagal disimpan") }
        }
    }

    fun savePayroll(record: PayrollRecord) {
        viewModelScope.launch {
            runCatching { repository.savePayroll(record) }
                .onSuccess {
                    scheduleAutoSync()
                    _userMessage.emit("Rekap payroll tersimpan dan biaya gaji diposting ke buku usaha")
                }
                .onFailure { _userMessage.emit(it.message ?: "Payroll gagal disimpan") }
        }
    }

    fun exportJson(context: Context) {
        viewModelScope.launch {
            runCatching {
                val jsonStr = repository.generateJsonExport(
                    transactions = allTransactions.value,
                    deletedTransactions = repository.allDeleted.first(),
                    cashTransactions = repository.cashTransactions.first(),
                    cashFlows = repository.cashFlows.first(),
                    externalLoans = repository.externalLoans.first(),
                    employees = repository.employees.first(),
                    attendance = repository.attendance.first(),
                    payroll = repository.payroll.first()
                )
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val fileName = "ayamkaspro-cadangan-lengkap-$today.json"
                saveAndShareFile(context, fileName, jsonStr, "application/json")
                    ?: throw IllegalStateException("File cadangan tidak berhasil dibuat atau dibagikan")
            }.onSuccess {
                _userMessage.emit("Cadangan lokal lengkap dibuat. Simpan file JSON di lokasi aman.")
            }.onFailure {
                _userMessage.emit("Gagal membuat cadangan lengkap: ${it.message ?: "kesalahan tak diketahui"}")
            }
        }
    }

    fun exportCsv(context: Context): Uri? {
        val csvStr = repository.generateCsvExport(allTransactions.value)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val fileName = "ayamkaspro-transaksi-$today.csv"
        return saveAndShareFile(context, fileName, csvStr, "text/csv")
    }

    fun importJson(jsonContent: String) {
        viewModelScope.launch {
            val result = repository.importJsonData(jsonContent)
            if (result.isSuccess) {
                scheduleAutoSync()
                _userMessage.emit("Berhasil memulihkan ${result.getOrNull()} transaksi dan data cadangan yang disertakan")
            } else {
                _userMessage.emit("Gagal memulihkan: ${result.exceptionOrNull()?.message ?: "file tidak sesuai format"}")
            }
        }
    }

    private fun saveAndShareFile(context: Context, filename: String, content: String, mime: String): Uri? {
        return try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, filename)
            file.writeText(content, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, filename)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Bagikan / Simpan File").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            uri
        } catch (e: Exception) {
            viewModelScope.launch {
                _userMessage.emit("Gagal membuat file: ${e.localizedMessage}")
            }
            null
        }
    }

    fun shareSummaryViaWhatsApp(context: Context) {
        val s = dashboardSummary.value
        val today = SimpleDateFormat("dd/MM/yyyy", Locale("id", "ID")).format(Date())
        val msg = """
            *RINGKASAN PEMBUKUAN AYAMKAS PRO*
            _Tanggal: ${today}_
            
            💵 Saldo Kas Tercatat: ${AyamKasFormatters.formatRupiah(s.cash)}
            ↗ Omzet Penjualan: ${AyamKasFormatters.formatRupiah(s.revenue)}
            ↘ Biaya Operasional: ${AyamKasFormatters.formatRupiah(s.expenses)}
            ◈ Laba Bersih: ${if (s.profitComplete) AyamKasFormatters.formatRupiah(s.profit) else "${AyamKasFormatters.formatRupiah(s.profit)} (Sementara)"}
            
            📦 Sisa Stok Ayam: ${AyamKasFormatters.formatNumber(s.stockKg)} kg
            ▤ Estimasi Nilai Stok: ${AyamKasFormatters.formatRupiah(s.stockValue)}
            🧾 Utang DO: ${AyamKasFormatters.formatRupiah(s.ap)}
            ⏳ Piutang Penjualan: ${AyamKasFormatters.formatRupiah(s.ar)}
            
            _Dicatat via AyamKas Pro (Pembukuan Ayam Potong)_
        """.trimIndent()

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://api.whatsapp.com/send?text=" + Uri.encode(msg))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            viewModelScope.launch {
                _userMessage.emit("WhatsApp tidak ditemukan pada perangkat")
            }
        }
    }
}
