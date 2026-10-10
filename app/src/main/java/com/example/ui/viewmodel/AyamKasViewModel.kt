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
        batchRef: String,
        notes: String,
        isConfirmed: Boolean
    ) {
        viewModelScope.launch {
            val txId = id?.ifBlank { null } ?: "TX-${System.currentTimeMillis()}"
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
                source = existing?.source ?: "manual"
            )
            repository.saveTransaction(tx)
            closeForm()
            _userMessage.emit(if (existing != null) "Transaksi diperbarui" else "Transaksi disimpan")
        }
    }

    fun toggleConfirm(tx: AyamTransaction) {
        viewModelScope.launch {
            if (tx.isConfirmed) {
                repository.toggleConfirm(tx.id, tx.status)
                _userMessage.emit("Transaksi kembali menjadi Draf")
            } else {
                if (tx.paidAmount == null) {
                    _userMessage.emit("Isi nominal pembayaran terlebih dahulu untuk memverifikasi")
                    openForm(tx)
                    return@launch
                }
                repository.toggleConfirm(tx.id, tx.status)
                _userMessage.emit("Transaksi terverifikasi dan masuk ke laporan")
            }
        }
    }

    fun confirmAllDrafts() {
        viewModelScope.launch {
            val drafts = allTransactions.value.filter { it.isDraft }
            drafts.forEach { t ->
                // Ensure paidAmount is filled if null
                val updated = if (t.paidAmount == null) t.copy(paidAmount = t.amount, status = "confirmed") else t.copy(status = "confirmed")
                repository.saveTransaction(updated)
            }
            _userMessage.emit("${drafts.size} catatan berhasil diverifikasi")
        }
    }

    fun deleteTransaction(tx: AyamTransaction) {
        viewModelScope.launch {
            repository.deleteTransaction(tx)
            _userMessage.emit("Transaksi “${tx.description}” dihapus")
        }
    }

    fun restoreDeleted(deleted: DeletedAyamTransaction) {
        viewModelScope.launch {
            repository.restoreDeleted(deleted)
            _userMessage.emit("Transaksi berhasil dipulihkan")
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            repository.clearAll()
            _userMessage.emit("Semua transaksi lokal telah dihapus")
        }
    }

    fun restoreDefaultData() {
        viewModelScope.launch {
            repository.restoreDefaultData()
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
