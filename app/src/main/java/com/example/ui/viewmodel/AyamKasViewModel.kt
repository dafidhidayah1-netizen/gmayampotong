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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AyamKasViewModel(application: Application) : AndroidViewModel(application) {

    private val cloudPrefs = application.getSharedPreferences("ayamkaspro_cloud", Context.MODE_PRIVATE)
    private val cloudClient = GoogleSheetsSyncClient()
    val cloudUrl = MutableStateFlow(cloudPrefs.getString("url", "").orEmpty())
    val cloudApiKey = MutableStateFlow(cloudPrefs.getString("apiKey", "").orEmpty())
    val autoSyncEnabled = MutableStateFlow(cloudPrefs.getBoolean("autoSync", false))
    val isCloudSyncing = MutableStateFlow(false)
    val cloudStatus = MutableStateFlow("Belum disambungkan")
    val lastCloudSyncAt = MutableStateFlow(cloudPrefs.getString("lastSyncAt", "").orEmpty())
    private var pendingAutoSync: Job? = null
    private val repository: AyamKasRepository
    val allTransactions: StateFlow<List<AyamTransaction>>
    val allDeleted: StateFlow<List<DeletedAyamTransaction>>

    val searchQuery = MutableStateFlow("")
    val typeFilter = MutableStateFlow("all")
    val statusFilter = MutableStateFlow("all")
    val monthFilter = MutableStateFlow("all")

    val reportFromDate = MutableStateFlow("")
    val reportToDate = MutableStateFlow("")

    val isFormOpen = MutableStateFlow(false)
    val editingTransaction = MutableStateFlow<AyamTransaction?>(null)
    val presetType = MutableStateFlow<String?>(null)

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage = _userMessage.asSharedFlow()

    init {
        val db = KasDatabase.getDatabase(application)
        repository = AyamKasRepository(db.ayamKasDao())

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
            repository.ensureSeededInitialData()
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
                cloudStatus.value = "Cadangan spreadsheet berhasil: " + backup.name
                _userMessage.emit("Cadangan tersimpan di Drive: " + backup.url)
            } catch (e: Exception) {
                cloudStatus.value = "Cadangan gagal: " + (e.message ?: "kesalahan koneksi")
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
            cloudStatus.value = "Kunci belum valid. Jalankan configureApiToken() di editor Apps Script dan salin AYAMKAS_API_TOKEN dari log eksekusi."
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
            val warningParts = mutableListOf<String>()
            if (result.conflicts > 0) warningParts.add("${result.conflicts} konflik timestamp perlu ditinjau")
            if (result.rejected > 0) warningParts.add("${result.rejected} catatan ditolak backend")
            val details = "Data server ${result.totalOnServer} transaksi · ditarik ${result.pulled} · diproses server ${result.serverAccepted}"
            cloudStatus.value = if (warningParts.isEmpty()) {
                "Berhasil · $details"
            } else {
                "Sinkron selesai dengan peringatan: " + warningParts.joinToString(" · ") + " · " + details
            }
            _userMessage.emit("Sinkronisasi selesai. Periksa Ringkasan dan Validasi di Google Sheets.")
        } catch (e: Exception) {
            cloudStatus.value = "Sinkronisasi gagal: " + (e.message ?: "jaringan atau izin server")
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
        editingTransaction.value = tx
        presetType.value = kind
        isFormOpen.value = true
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
        saleableKg: Double?,
        batchRef: String,
        notes: String,
        isConfirmed: Boolean
    ) {
        viewModelScope.launch {
            val txId = id?.ifBlank { null } ?: ("TX-" + System.currentTimeMillis())
            val existing = allTransactions.value.firstOrNull { it.id == txId }
            if (existing?.source == "payroll") {
                _userMessage.emit("Pengeluaran gaji dibuat dari rekap Penggajian. Ubah gaji lewat modul Penggajian agar laporan tidak berbeda.")
                closeForm()
                return@launch
            }
            val ledger = allTransactions.value.filterNot { it.id == txId }
            if (isConfirmed && type == "purchase") {
                val duplicate = ledger.any { it.type == "purchase" && it.ref.isNotBlank() && it.ref.equals(ref.trim(), ignoreCase = true) }
                if (ref.isBlank() || (qtyKg ?: 0.0) <= 0.0 || (saleableKg ?: 0.0) <= 0.0 || (saleableKg ?: 0.0) > (qtyKg ?: 0.0) + 0.0001 || duplicate) {
                    _userMessage.emit("Pembelian terverifikasi wajib mempunyai nomor DO unik, kg masuk, dan kg bersih siap jual yang valid. Simpan sebagai draf bila timbang belum selesai.")
                    return@launch
                }
            }
            if (isConfirmed && type == "sale") {
                val linked = ledger.filter { it.isConfirmed && it.type == "purchase" && it.ref.equals(batchRef.trim(), ignoreCase = true) }
                if ((qtyKg ?: 0.0) <= 0.0 || batchRef.isBlank() || linked.size != 1 ||
                    ((linked.firstOrNull()?.saleableKg ?: linked.firstOrNull()?.qtyKg) ?: 0.0) <= 0.0) {
                    _userMessage.emit("Penjualan terverifikasi wajib mempunyai kg terjual dan tautan ke satu DO terverifikasi dengan HPP yang valid.")
                    return@launch
                }
            }
            if (isConfirmed && type == "expense" && batchRef.isNotBlank()) {
                val matches = ledger.count { it.isConfirmed && it.type == "purchase" && it.ref.equals(batchRef.trim(), ignoreCase = true) }
                if (matches != 1) {
                    _userMessage.emit("Biaya batch harus ditautkan ke tepat satu DO terverifikasi. Kosongkan tautan jika biaya operasional umum.")
                    return@launch
                }
            }
            if (isConfirmed && type in listOf("sale","purchase","expense") && paidAmount == null) {
                _userMessage.emit("Isi pembayaran aktual (boleh 0 bila belum dibayar) sebelum memverifikasi transaksi.")
                return@launch
            }
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
                saleableKg = saleableKg,
                batchRef = batchRef.trim(),
                notes = notes.trim(),
                status = if (isConfirmed) "confirmed" else "draft",
                source = existing?.source ?: "manual"
            )
            repository.saveTransaction(tx)
            scheduleAutoSync()
            closeForm()
            _userMessage.emit(if (existing != null) "Transaksi diperbarui" else "Transaksi disimpan")
        }
    }

    fun toggleConfirm(tx: AyamTransaction) {
        viewModelScope.launch {
            if (tx.source == "payroll") {
                _userMessage.emit("Pengeluaran otomatis dari penggajian dikelola melalui modul Penggajian.")
                return@launch
            }
            if (tx.isConfirmed) {
                repository.toggleConfirm(tx.id, tx.status)
                scheduleAutoSync()
                _userMessage.emit("Transaksi kembali menjadi Draf")
                return@launch
            }
            val transactions = allTransactions.value.filterNot { it.id == tx.id }
            if (tx.paidAmount == null && tx.type in listOf("sale","purchase","expense")) {
                _userMessage.emit("Isi nominal pembayaran aktual terlebih dahulu untuk memverifikasi.")
                openForm(tx)
                return@launch
            }
            if (tx.type == "purchase") {
                val duplicate = transactions.any { it.type == "purchase" && it.ref.equals(tx.ref, ignoreCase = true) }
                if (tx.ref.isBlank() || (tx.qtyKg ?: 0.0) <= 0.0 || (tx.saleableKg ?: 0.0) <= 0.0 || duplicate) {
                    _userMessage.emit("DO belum siap diverifikasi. Periksa nomor DO unik, kg masuk dan berat siap jual.")
                    openForm(tx)
                    return@launch
                }
            }
            if (tx.type == "sale") {
                val linked = transactions.filter { it.isConfirmed && it.type == "purchase" && it.ref.equals(tx.batchRef, ignoreCase = true) }
                if ((tx.qtyKg ?: 0.0) <= 0.0 || tx.batchRef.isBlank() || linked.size != 1) {
                    _userMessage.emit("Penjualan belum siap diverifikasi. Isi kg dan tautkan ke satu DO terverifikasi.")
                    openForm(tx)
                    return@launch
                }
            }
            if (tx.type == "expense" && tx.batchRef.isNotBlank()) {
                val matches = transactions.count { it.isConfirmed && it.type == "purchase" && it.ref.equals(tx.batchRef, ignoreCase = true) }
                if (matches != 1) {
                    _userMessage.emit("Biaya batch tidak tertaut ke satu DO valid. Perbaiki tautan terlebih dahulu.")
                    openForm(tx)
                    return@launch
                }
            }
            repository.toggleConfirm(tx.id, tx.status)
            scheduleAutoSync()
            _userMessage.emit("Transaksi terverifikasi dan masuk ke laporan")
        }
    }

    fun confirmAllDrafts() {
        viewModelScope.launch {
            val records = allTransactions.value
            val drafts = records.filter { it.isDraft && it.source != "payroll" }
            val purchases = records.filter { it.type == "purchase" }
            val groups = purchases.filter { it.ref.isNotBlank() }.groupBy { it.ref.trim().lowercase() }
            val eligibleDoRefs = groups.filterValues { it.size == 1 }.filterValues { list ->
                val p = list.single()
                (p.qtyKg ?: 0.0) > 0.0 && (p.saleableKg ?: 0.0) > 0.0 && p.amount > 0.0
            }.keys
            var confirmed = 0
            var skipped = 0
            val sorted = drafts.sortedBy {
                when (it.type) {
                    "purchase" -> 0
                    "expense" -> 1
                    "sale" -> 2
                    else -> 3
                }
            }
            sorted.forEach { tx ->
                val paymentKnown = tx.type !in listOf("sale","purchase","expense") || tx.paidAmount != null
                val typeDataValid = when (tx.type) {
                    "purchase" -> tx.ref.isNotBlank() && tx.ref.trim().lowercase() in eligibleDoRefs &&
                        (tx.qtyKg ?: 0.0) > 0.0 && (tx.saleableKg ?: 0.0) > 0.0
                    "sale" -> (tx.qtyKg ?: 0.0) > 0.0 && tx.batchRef.trim().lowercase() in eligibleDoRefs
                    "expense" -> tx.batchRef.isBlank() || tx.batchRef.trim().lowercase() in eligibleDoRefs
                    else -> true
                }
                if (tx.amount > 0.0 && tx.date.isNotBlank() && tx.description.isNotBlank() && paymentKnown && typeDataValid) {
                    repository.saveTransaction(tx.copy(status = "confirmed"))
                    confirmed++
                } else skipped++
            }
            if (confirmed > 0) scheduleAutoSync()
            _userMessage.emit(confirmed.toString() + " catatan diverifikasi; " + skipped.toString() +
                " tetap draf karena data, pembayaran atau tautan DO belum lengkap.")
        }
    }

    fun deleteTransaction(tx: AyamTransaction) {
        viewModelScope.launch {
            if (tx.source == "payroll") {
                _userMessage.emit("Pengeluaran gaji dibuat otomatis. Arsipkan atau ubah dari modul Penggajian.")
                return@launch
            }
            repository.deleteTransaction(tx)
            scheduleAutoSync()
            _userMessage.emit("Transaksi dihapus dari daftar dan disimpan dalam arsip")
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

    fun restoreDefaultData() {
        viewModelScope.launch {
            repository.restoreDefaultData()
            scheduleAutoSync()
            _userMessage.emit("Data operasional riil berhasil dimuat ulang")
        }
    }

    fun exportJson(context: Context): Uri? {
        val jsonStr = repository.generateJsonExport(allTransactions.value)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val fileName = "ayamkaspro-cadangan-$today.json"
        return saveAndShareFile(context, fileName, jsonStr, "application/json")
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
                _userMessage.emit("Berhasil memulihkan ${result.getOrNull()} transaksi")
            } else {
                _userMessage.emit("Gagal memulihkan: file tidak sesuai format")
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
