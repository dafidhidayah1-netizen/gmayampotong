package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AyamKasDao
import com.example.data.local.KasDatabase
import com.example.data.local.BusinessLedgerDao
import com.example.data.model.AyamTransaction
import com.example.data.model.CashTransaction
import com.example.data.model.CashFlow
import com.example.data.model.ExternalLoan
import com.example.data.model.EmployeeRecord
import com.example.data.model.AttendanceRecord
import com.example.data.model.PayrollRecord
import com.example.data.model.TransactionAuditLog
import com.example.data.model.AyamTransactionType
import com.example.data.model.AyamKasFormatters
import com.example.data.model.AyamKasCalculator
import com.example.data.model.BusinessLedgerCalculator
import com.example.data.model.DeletedAyamTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import com.example.data.remote.GoogleSheetsSyncClient
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CloudSyncStats(
    val serverAccepted: Int,
    val pulled: Int,
    val totalOnServer: Int,
    val spreadsheetId: String
)

class AyamKasRepository(
    private val database: KasDatabase,
    private val dao: AyamKasDao,
    private val businessDao: BusinessLedgerDao
) {
    val allTransactions: Flow<List<AyamTransaction>> = dao.getAllTransactions()
    val allDeleted: Flow<List<DeletedAyamTransaction>> = dao.getAllDeleted()
    val cashTransactions: Flow<List<CashTransaction>> = businessDao.observeCashTransactions()
    val confirmedCashTransactions: Flow<List<CashTransaction>> = businessDao.observeConfirmedCashTransactions()
    val cashFlows: Flow<List<CashFlow>> = businessDao.observeCashFlows()
    val externalLoans: Flow<List<ExternalLoan>> = businessDao.observeExternalLoans()
    val employees: Flow<List<EmployeeRecord>> = businessDao.observeEmployees()
    val attendance: Flow<List<AttendanceRecord>> = businessDao.observeAttendance()
    val payroll: Flow<List<PayrollRecord>> = businessDao.observePayroll()
    val auditLog: Flow<List<TransactionAuditLog>> = businessDao.observeAuditLog()

    suspend fun saveTransaction(tx: AyamTransaction) {
        // Enforce rules in the repository too, so UI paths cannot bypass validation.
        if (tx.isConfirmed) validateForConfirmation(tx)
        val before = dao.getTransactionById(tx.id)
        val saved = tx.copy(updatedAt = nowIso())
        val action = when {
            before == null -> "BUAT"
            before.status != saved.status -> if (saved.isConfirmed) "KONFIRMASI" else "KEMBALI_DRAF"
            else -> "UBAH"
        }
        // Transaction, cash mirror, and audit must commit together. Never leave a
        // saved transaction without its audit event if any write fails.
        database.withTransaction {
            dao.insertTransaction(saved)
            updateCashMirror(saved)
            appendTransactionAudit(action, before, saved)
        }
    }

    private fun transactionAuditSummary(tx: AyamTransaction?): String {
        if (tx == null) return "—"
        return listOf(
            "Tanggal=${tx.date}",
            "Ref=${tx.ref.ifBlank { "—" }}",
            "Jenis=${AyamTransactionType.fromKey(tx.type).label}",
            "Nominal=${AyamKasFormatters.formatRupiah(tx.amount)}",
            "Kas aktual=${tx.paidAmount?.let(AyamKasFormatters::formatRupiah) ?: "belum diisi"}",
            "Status=${if (tx.isConfirmed) "Terverifikasi" else "Draf"}",
            "DO=${tx.batchRef.ifBlank { "—" }}"
        ).joinToString(" · ")
    }

    private suspend fun appendTransactionAudit(
        action: String,
        before: AyamTransaction?,
        after: AyamTransaction?,
        note: String = ""
    ) {
        val tx = after ?: before ?: return
        businessDao.appendAuditLog(
            TransactionAuditLog(
                id = "AUD-${java.util.UUID.randomUUID()}",
                entityType = "TRANSAKSI",
                entityId = tx.id,
                action = action,
                occurredAt = nowIso(),
                actor = "Perangkat ini",
                summary = note.ifBlank { "${action}: ${tx.description}" },
                beforeData = transactionAuditSummary(before),
                afterData = transactionAuditSummary(after)
            )
        )
    }

    private suspend fun updateCashMirror(tx: AyamTransaction) {
        val amount = if (!tx.isConfirmed) 0.0 else kotlin.math.abs(tx.cashEffect())
        if (amount <= 0.0) {
            businessDao.deleteCashForSource(tx.id)
            return
        }
        val direction = if (tx.cashEffect() >= 0.0) "IN" else "OUT"
        businessDao.saveCashTransaction(
            CashTransaction(
                id = "CASH-${tx.id}",
                sourceTransactionId = tx.id,
                date = tx.date,
                accountName = tx.cashAccount,
                direction = direction,
                amount = amount,
                paymentMethod = tx.paymentMethod,
                category = tx.type,
                description = tx.description,
                notes = tx.notes,
                status = tx.status,
                updatedAt = tx.updatedAt,
                deleted = false
            )
        )
    }

    suspend fun saveCashFlow(flow: CashFlow) {
        require(flow.amount > 0.0) { "Nilai transfer harus lebih besar dari nol" }
        require(flow.fromAccount.isNotBlank() && flow.toAccount.isNotBlank()) { "Rekening asal dan tujuan wajib diisi" }
        require(!flow.fromAccount.equals(flow.toAccount, ignoreCase = true)) { "Rekening asal dan tujuan tidak boleh sama" }
        businessDao.saveCashFlow(flow.copy(updatedAt = nowIso()))
    }

    suspend fun saveExternalLoan(loan: ExternalLoan) {
        require(loan.amount > 0.0) { "Nominal pinjaman/arus harus lebih dari nol" }
        require(loan.paidAmount >= 0.0 && loan.paidAmount <= loan.amount) { "Nominal dibayar harus di antara nol dan nilai pinjaman" }
        require(loan.party.isNotBlank()) { "Nama pihak pinjaman wajib diisi" }
        val debtTypes = setOf("OWNER_LOAN", "BUSINESS_BORROWING")
        val lendingTypes = setOf("BUSINESS_LENDING")
        val allowedDirections = when (loan.loanType) {
            in debtTypes -> setOf("RECEIVED", "REPAYMENT")
            in lendingTypes -> setOf("GIVEN", "COLLECTION")
            "OWNER_INJECTION" -> setOf("RECEIVED")
            "OWNER_PERSONAL", "UNEXPECTED_COST" -> setOf("GIVEN")
            else -> emptySet()
        }
        require(loan.direction in allowedDirections) { "Arah kas tidak sesuai kategori pinjaman/transaksi yang dipilih" }
        val tracksLoanBalance = loan.loanType in debtTypes || loan.loanType in lendingTypes
        val loanRef = loan.loanReference.trim()
        if (tracksLoanBalance) require(loanRef.isNotBlank()) { "Referensi pinjaman wajib diisi untuk menghitung sisa pinjaman" }
        val allLoans = businessDao.observeExternalLoans().first()
        if (loan.direction == "REPAYMENT" || loan.direction == "COLLECTION") {
            require(loanRef.isNotBlank()) { "Pelunasan wajib memakai referensi pinjaman asal" }
            val allowedType = if (loan.direction == "REPAYMENT") debtTypes else lendingTypes
            require(loan.loanType in allowedType) {
                if (loan.direction == "REPAYMENT") "Pelunasan harus memilih kategori pinjaman yang diterima usaha" else "Penagihan harus memilih kategori pinjaman yang diberikan usaha"
            }
            val balance = BusinessLedgerCalculator.loanBalance(
                allLoans.filterNot { it.id == loan.id }, loanRef, loan.loanType
            )
            require(balance.principal > 0.0) { "Referensi pinjaman asal tidak ditemukan atau belum terverifikasi" }
            require(loan.amount <= balance.outstanding + 0.0001) {
                "Nominal pelunasan/penagihan melebihi saldo referensi pinjaman (${AyamKasFormatters.formatRupiah(balance.outstanding)})"
            }
        }
        val saved = loan.copy(loanReference = loanRef, updatedAt = nowIso())
        businessDao.saveExternalLoan(saved)
        val sourceId = "LOAN-${saved.id}"
        val incoming = saved.direction == "RECEIVED" || saved.direction == "COLLECTION"
        if (saved.status == "confirmed") {
            businessDao.saveCashTransaction(
                CashTransaction(
                    id = "CASH-${sourceId}",
                    sourceTransactionId = sourceId,
                    date = saved.date,
                    accountName = saved.cashAccount,
                    direction = if (incoming) "IN" else "OUT",
                    amount = saved.amount,
                    paymentMethod = "CASH",
                    category = saved.loanType,
                    description = "${saved.direction}: ${saved.party}",
                    notes = saved.notes,
                    status = saved.status,
                    updatedAt = saved.updatedAt,
                    deleted = false
                )
            )
        } else {
            businessDao.deleteCashForSource(sourceId)
        }
    }

    suspend fun saveEmployee(employee: EmployeeRecord) {
        require(employee.name.isNotBlank()) { "Nama karyawan wajib diisi" }
        require(employee.dailyRate >= 0.0) { "Tarif harian tidak boleh negatif" }
        require(employee.standardHoursPerShift > 0.0) { "Jam kerja standar per shift harus lebih dari nol" }
        require(employee.overtimeRatePerHour >= 0.0) { "Tarif lembur tidak boleh negatif" }
        businessDao.saveEmployee(employee.copy(updatedAt = nowIso()))
    }

    suspend fun saveAttendance(record: AttendanceRecord) {
        require(record.employeeId.isNotBlank() && record.workDate.isNotBlank()) { "Karyawan dan tanggal wajib diisi" }
        require(record.breakMinutes >= 0) { "Durasi istirahat tidak valid" }
        val employee = businessDao.getEmployee(record.employeeId)
            ?: throw IllegalArgumentException("Data karyawan tidak ditemukan")
        val overtime = if (record.status == "PRESENT") {
            require(record.clockIn.isNotBlank() && record.clockOut.isNotBlank()) { "Jam masuk dan keluar wajib diisi untuk status hadir" }
            val workedHours = calculateWorkedHours(record.clockIn, record.clockOut, record.breakMinutes)
            (workedHours - employee.standardHoursPerShift).coerceAtLeast(0.0)
        } else 0.0
        businessDao.saveAttendance(record.copy(overtimeHours = overtime, updatedAt = nowIso()))
    }

    private fun calculateWorkedHours(clockIn: String, clockOut: String, breakMinutes: Int): Double =
        BusinessLedgerCalculator.workedHours(clockIn, clockOut, breakMinutes)

    suspend fun savePayroll(record: PayrollRecord) {
        require(record.period.matches(Regex("\\d{4}-(0[1-9]|1[0-2])"))) { "Periode payroll harus YYYY-MM yang valid" }
        require(record.daysPresent >= 0.0 && record.overtimeHours >= 0.0) { "Hari hadir/jam lembur tidak boleh negatif" }
        require(record.deductions >= 0.0 && record.paidAmount >= 0.0) { "Potongan/pembayaran tidak boleh negatif" }
        require(record.status in setOf("DRAFT", "APPROVED", "PART_PAID", "PAID")) { "Status payroll tidak valid" }
        val employee = businessDao.getEmployee(record.employeeId)
            ?: throw IllegalArgumentException("Master karyawan tidak ditemukan")
        val amounts = BusinessLedgerCalculator.calculatePayroll(
            daysPresent = record.daysPresent,
            dailyRate = record.dailyRate,
            overtimeHours = record.overtimeHours,
            overtimeRatePerHour = employee.overtimeRatePerHour,
            allowance = record.allowance,
            deductions = record.deductions
        )
        require(amounts.totalPay >= 0.0) { "Total gaji setelah potongan tidak boleh negatif" }
        require(record.paidAmount <= amounts.totalPay) { "Pembayaran melebihi total gaji yang dihitung ulang" }
        if (record.status != "DRAFT") {
            require(amounts.totalPay > 0.0) { "Total gaji harus lebih dari nol sebelum disetujui" }
        }

        // The repository is authoritative for pay math; do not trust totals sent by the UI.
        val saved = record.copy(
            basePay = amounts.basePay,
            overtimePay = amounts.overtimePay,
            allowance = amounts.allowance,
            deductions = amounts.deductions,
            totalPay = amounts.totalPay,
            updatedAt = nowIso()
        )
        val periodDate = "${saved.period}-01"
        val payrollTransaction = AyamTransaction(
            id = "PAYROLL-${saved.id}",
            date = periodDate,
            ref = "PAY-${saved.period}-${saved.employeeId}",
            type = "expense",
            category = "Gaji",
            description = "Upah ${saved.employeeName} periode ${saved.period}",
            amount = saved.totalPay,
            paidAmount = saved.paidAmount,
            notes = "Rekap payroll ${saved.id}. ${saved.notes}",
            status = if (saved.status == "DRAFT") "draft" else "confirmed",
            source = "payroll",
            paymentMethod = saved.paymentMethod,
            cashAccount = saved.cashAccount
        )
        database.withTransaction {
            businessDao.savePayroll(saved)
            saveTransaction(payrollTransaction)
        }
    }

    suspend fun toggleConfirm(id: String, currentStatus: String) {
        val current = dao.getTransactionById(id) ?: return
        val confirming = currentStatus != "confirmed"
        if (confirming) validateForConfirmation(current)
        val saved = current.copy(status = if (confirming) "confirmed" else "draft", updatedAt = nowIso())
        database.withTransaction {
            dao.insertTransaction(saved)
            updateCashMirror(saved)
            appendTransactionAudit(if (confirming) "KONFIRMASI" else "KEMBALI_DRAF", current, saved)
        }
    }

    suspend fun confirmTransaction(id: String) {
        val current = dao.getTransactionById(id) ?: throw IllegalArgumentException("Transaksi tidak ditemukan")
        validateForConfirmation(current)
        val saved = current.copy(status = "confirmed", updatedAt = nowIso())
        database.withTransaction {
            dao.insertTransaction(saved)
            updateCashMirror(saved)
            appendTransactionAudit("KONFIRMASI", current, saved)
        }
    }

    private suspend fun validateForConfirmation(tx: AyamTransaction) {
        require(AyamKasFormatters.isValidIsoDate(tx.date)) { "Tanggal tidak valid. Pilih tanggal nyata dengan format YYYY-MM-DD." }
        require(tx.amount > 0.0) { "Nominal harus lebih dari nol" }
        if (tx.type in listOf("sale", "purchase", "expense")) {
            require(tx.paidAmount != null && tx.paidAmount >= 0.0 && tx.paidAmount <= tx.amount) {
                "Pembayaran aktual wajib diisi; gunakan Rp0 untuk transaksi kredit"
            }
        }
        when (tx.type) {
            "purchase" -> {
                require(tx.ref.isNotBlank()) { "Referensi DO wajib diisi" }
                require(tx.supplier.isNotBlank()) { "Nama supplier wajib diisi" }
                require((tx.qtyKg ?: 0.0) > 0.0) { "Berat DO wajib lebih dari nol" }
                require(tx.weightLossKg != null && tx.weightLossKg >= 0.0 && tx.weightLossKg <= (tx.qtyKg ?: 0.0)) {
                    "Penyusutan aktual wajib dicatat dan tidak boleh melebihi kg DO"
                }
                require(tx.mortalityRatePct != null && tx.mortalityRatePct in 0.0..100.0) {
                    "Persentase kematian wajib dicatat (0–100%)"
                }
                require(tx.processingCost >= 0.0) { "Biaya proses tidak boleh negatif" }
                val all = dao.getAllTransactions().first()
                require(all.none { it.id != tx.id && it.type == "purchase" && it.ref.trim().equals(tx.ref.trim(), ignoreCase = true) }) {
                    "Referensi DO sudah digunakan oleh transaksi lain"
                }
                val hasAllFeedInputs = tx.fcr != null && tx.weightGainKg != null && tx.feedPricePerKg != null
                val hasNoFeedInputs = tx.fcr == null && tx.weightGainKg == null && tx.feedPricePerKg == null
                require(hasAllFeedInputs || hasNoFeedInputs) {
                    "Isi FCR, pertambahan berat, dan harga pakan secara lengkap atau kosongkan semuanya"
                }
            }
            "sale" -> {
                require((tx.qtyKg ?: 0.0) > 0.0) { "Kg penjualan wajib lebih dari nol" }
                require(tx.batchRef.isNotBlank()) { "Penjualan wajib ditautkan ke DO sumber" }
                val all = dao.getAllTransactions().first()
                val purchase = all.firstOrNull {
                    it.type == "purchase" && it.isConfirmed &&
                        it.ref.trim().equals(tx.batchRef.trim(), ignoreCase = true)
                } ?: throw IllegalArgumentException("DO sumber tidak ditemukan atau belum terverifikasi")
                require(tx.date >= purchase.date) { "Tanggal penjualan tidak boleh sebelum tanggal pembelian DO" }
                val salesWithoutThis = all.filterNot { it.id == tx.id }
                val available = AyamKasCalculator.calculateStockRows(salesWithoutThis)
                    .firstOrNull { it.purchaseTx.ref.trim().equals(tx.batchRef.trim(), ignoreCase = true) }
                    ?.remainingKg ?: 0.0
                require((tx.qtyKg ?: 0.0) <= available + 0.0001) {
                    "Stok DO ${tx.batchRef} tidak cukup; tersedia ${AyamKasFormatters.formatNumber(available)} kg"
                }
            }
        }
    }

    /** Merge Room data with the Apps Script ledger by stable ID and update timestamp. */
    suspend fun syncWithGoogleSheets(
        client: GoogleSheetsSyncClient,
        url: String,
        apiKey: String
    ): CloudSyncStats {
        val initialLocal = dao.getAllTransactions().first()
        val localTransactions = initialLocal.map { tx ->
            if (tx.updatedAt.isBlank()) tx.copy(updatedAt = "1970-01-01T00:00:00.000Z").also {
                dao.insertTransaction(it)
            } else tx
        }
        val localDeleted = dao.getAllDeleted().first()
        val activeIds = localTransactions.map { it.id }.toSet()
        val deletionsToSend = localDeleted.filterNot { it.originalId in activeIds }

        val response = client.sync(
            url = url,
            apiKey = apiKey,
            transactions = localTransactions,
            deletedTransactions = deletionsToSend,
            cashTransactions = businessDao.observeCashTransactions().first(),
            cashFlows = businessDao.observeCashFlows().first(),
            externalLoans = businessDao.observeExternalLoans().first(),
            employees = businessDao.observeEmployees().first(),
            attendance = businessDao.observeAttendance().first(),
            payroll = businessDao.observePayroll().first()
        )
        var pulled = 0

        response.transactions.forEach { remote ->
            val local = dao.getTransactionById(remote.id)
            if (local == null || timestamp(remote.updatedAt) > timestamp(local.updatedAt)) {
                dao.insertTransaction(remote)
                updateCashMirror(remote)
                pulled++
            }
        }

        response.deletedTransactions.forEach { remoteDeleted ->
            val local = dao.getTransactionById(remoteDeleted.originalId)
            if (local == null) {
                dao.insertDeleted(remoteDeleted)
            } else if (timestamp(remoteDeleted.deletedAt) >= timestamp(local.updatedAt)) {
                dao.insertDeleted(remoteDeleted)
                dao.deleteTransaction(local.id)
                businessDao.deleteCashForSource(local.id)
                pulled++
            }
        }

        response.cashTransactions.forEach { remote ->
            val existing = businessDao.observeCashTransactions().first().firstOrNull { it.id == remote.id }
            if (existing == null || timestamp(remote.updatedAt) > timestamp(existing.updatedAt)) {
                businessDao.saveCashTransaction(remote)
                pulled++
            }
        }
        response.cashFlows.forEach { remote ->
            val existing = businessDao.observeCashFlows().first().firstOrNull { it.id == remote.id }
            if (existing == null || timestamp(remote.updatedAt) > timestamp(existing.updatedAt)) {
                businessDao.saveCashFlow(remote)
                pulled++
            }
        }
        response.externalLoans.forEach { remote ->
            val existing = businessDao.observeExternalLoans().first().firstOrNull { it.id == remote.id }
            if (existing == null || timestamp(remote.updatedAt) > timestamp(existing.updatedAt)) {
                businessDao.saveExternalLoan(remote)
                pulled++
            }
        }
        response.employees.forEach { remote ->
            val existing = businessDao.observeEmployees().first().firstOrNull { it.id == remote.id }
            if (existing == null || timestamp(remote.updatedAt) > timestamp(existing.updatedAt)) {
                businessDao.saveEmployee(remote)
                pulled++
            }
        }
        response.attendance.forEach { remote ->
            val existing = businessDao.observeAttendance().first().firstOrNull { it.id == remote.id }
            if (existing == null || timestamp(remote.updatedAt) > timestamp(existing.updatedAt)) {
                businessDao.saveAttendance(remote)
                pulled++
            }
        }
        response.payroll.forEach { remote ->
            val existing = businessDao.observePayroll().first().firstOrNull { it.id == remote.id }
            if (existing == null || timestamp(remote.updatedAt) > timestamp(existing.updatedAt)) {
                businessDao.savePayroll(remote)
                pulled++
            }
        }

        return CloudSyncStats(
            serverAccepted = response.accepted,
            pulled = pulled,
            totalOnServer = response.transactions.size,
            spreadsheetId = response.spreadsheetId
        )
    }

    private fun timestamp(value: String): Long {
        if (value.isBlank()) return Long.MIN_VALUE
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
                isLenient = false
            }.parse(value)?.time ?: Long.MIN_VALUE
        } catch (_: Exception) {
            try {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                    isLenient = false
                }.parse(value)?.time ?: Long.MIN_VALUE
            } catch (_: Exception) { Long.MIN_VALUE }
        }
    }

    private fun nowIso(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(Date())

    suspend fun setStatus(id: String, status: String) {
        dao.updateStatus(id, status)
    }

    suspend fun deleteTransaction(tx: AyamTransaction) {
        val nowIso = nowIso()
        val deleted = DeletedAyamTransaction(
            id = "DEL-${System.currentTimeMillis()}",
            originalId = tx.id,
            deletedAt = nowIso,
            date = tx.date,
            ref = tx.ref,
            type = tx.type,
            category = tx.category,
            description = tx.description,
            amount = tx.amount,
            paidAmount = tx.paidAmount,
            qtyKg = tx.qtyKg,
            batchRef = tx.batchRef,
            notes = tx.notes,
            status = tx.status,
            source = tx.source
        )
        database.withTransaction {
            dao.insertDeleted(deleted.copy(
                paymentMethod = tx.paymentMethod,
                cashAccount = tx.cashAccount,
                supplier = tx.supplier,
                weightLossKg = tx.weightLossKg,
                mortalityRatePct = tx.mortalityRatePct,
                fcr = tx.fcr,
                weightGainKg = tx.weightGainKg,
                feedPricePerKg = tx.feedPricePerKg,
                processingCost = tx.processingCost
            ))
            dao.deleteTransaction(tx.id)
            businessDao.deleteCashForSource(tx.id)
            appendTransactionAudit("HAPUS_ARSIP", tx, null, "Transaksi dihapus ke arsip: ${tx.description}")
        }
    }

    suspend fun restoreDeleted(deleted: DeletedAyamTransaction) {
        val restored = AyamTransaction(
            id = deleted.originalId,
            date = deleted.date,
            ref = deleted.ref,
            type = deleted.type,
            category = deleted.category,
            description = deleted.description,
            amount = deleted.amount,
            paidAmount = deleted.paidAmount,
            qtyKg = deleted.qtyKg,
            batchRef = deleted.batchRef,
            notes = deleted.notes,
            status = deleted.status,
            source = deleted.source,
            updatedAt = nowIso(),
            paymentMethod = deleted.paymentMethod,
            cashAccount = deleted.cashAccount,
            supplier = deleted.supplier,
            weightLossKg = deleted.weightLossKg,
            mortalityRatePct = deleted.mortalityRatePct,
            fcr = deleted.fcr,
            weightGainKg = deleted.weightGainKg,
            feedPricePerKg = deleted.feedPricePerKg,
            processingCost = deleted.processingCost
        )
        if (restored.isConfirmed) validateForConfirmation(restored)
        database.withTransaction {
            dao.insertTransaction(restored)
            updateCashMirror(restored)
            appendTransactionAudit(
                "PULIHKAN_ARSIP",
                before = null,
                after = restored,
                note = "Transaksi dipulihkan dari arsip: ${restored.description}"
            )
            dao.deleteDeleted(deleted.id)
        }
    }

    suspend fun clearAll() {
        val now = nowIso()
        val transactions = dao.getAllTransactions().first()
        database.withTransaction {
            transactions.forEach { tx ->
                dao.insertDeleted(
                    DeletedAyamTransaction(
                        id = "DEL-${tx.id}",
                        originalId = tx.id,
                        deletedAt = now,
                        date = tx.date,
                        ref = tx.ref,
                        type = tx.type,
                        category = tx.category,
                        description = tx.description,
                        amount = tx.amount,
                        paidAmount = tx.paidAmount,
                        qtyKg = tx.qtyKg,
                        batchRef = tx.batchRef,
                        notes = tx.notes,
                        status = tx.status,
                        source = tx.source,
                        paymentMethod = tx.paymentMethod,
                        cashAccount = tx.cashAccount,
                        supplier = tx.supplier,
                        weightLossKg = tx.weightLossKg,
                        mortalityRatePct = tx.mortalityRatePct,
                        fcr = tx.fcr,
                        weightGainKg = tx.weightGainKg,
                        feedPricePerKg = tx.feedPricePerKg,
                        processingCost = tx.processingCost
                    )
                )
                appendTransactionAudit(
                    "ARSIP_MASSAL",
                    before = tx,
                    after = null,
                    note = "Transaksi dipindahkan ke arsip massal: ${tx.description}"
                )
            }
            dao.clearAll()
            businessDao.clearMirroredCashTransactions()
        }
    }

    fun generateJsonExport(
        transactions: List<AyamTransaction>,
        deletedTransactions: List<DeletedAyamTransaction> = emptyList(),
        cashTransactions: List<CashTransaction> = emptyList(),
        cashFlows: List<CashFlow> = emptyList(),
        externalLoans: List<ExternalLoan> = emptyList(),
        employees: List<EmployeeRecord> = emptyList(),
        attendance: List<AttendanceRecord> = emptyList(),
        payroll: List<PayrollRecord> = emptyList()
    ): String {
        val root = JSONObject()
        root.put("app", "AyamKas Pro")
        root.put("version", 2)
        root.put("fullBackup", true)
        root.put("exportedAt", nowIso())
        root.put("transactions", JSONArray().apply { transactions.forEach { put(it.toBackupJson()) } })
        root.put("deletedTransactions", JSONArray().apply { deletedTransactions.forEach { put(it.toBackupJson()) } })
        root.put("cashTransactions", JSONArray().apply { cashTransactions.forEach { put(it.toBackupJson()) } })
        root.put("cashFlows", JSONArray().apply { cashFlows.forEach { put(it.toBackupJson()) } })
        root.put("externalLoans", JSONArray().apply { externalLoans.forEach { put(it.toBackupJson()) } })
        root.put("employees", JSONArray().apply { employees.forEach { put(it.toBackupJson()) } })
        root.put("attendance", JSONArray().apply { attendance.forEach { put(it.toBackupJson()) } })
        root.put("payroll", JSONArray().apply { payroll.forEach { put(it.toBackupJson()) } })
        return root.toString(2)
    }

    private fun AyamTransaction.toBackupJson() = JSONObject().apply {
        put("id", id); put("date", date); put("ref", ref); put("type", type); put("category", category)
        put("description", description); put("amount", amount); putNullable("paidAmount", paidAmount)
        putNullable("qtyKg", qtyKg); put("batchRef", batchRef); put("notes", notes); put("status", status)
        put("source", source); put("updatedAt", updatedAt); put("paymentMethod", paymentMethod)
        put("cashAccount", cashAccount); put("supplier", supplier); putNullable("weightLossKg", weightLossKg)
        putNullable("mortalityRatePct", mortalityRatePct); putNullable("fcr", fcr); putNullable("weightGainKg", weightGainKg)
        putNullable("feedPricePerKg", feedPricePerKg); put("processingCost", processingCost)
    }

    private fun DeletedAyamTransaction.toBackupJson() = JSONObject().apply {
        put("id", id); put("originalId", originalId); put("deletedAt", deletedAt); put("date", date)
        put("ref", ref); put("type", type); put("category", category); put("description", description)
        put("amount", amount); putNullable("paidAmount", paidAmount); putNullable("qtyKg", qtyKg)
        put("batchRef", batchRef); put("notes", notes); put("status", status); put("source", source)
        put("paymentMethod", paymentMethod); put("cashAccount", cashAccount); put("supplier", supplier)
        putNullable("weightLossKg", weightLossKg); putNullable("mortalityRatePct", mortalityRatePct)
        putNullable("fcr", fcr); putNullable("weightGainKg", weightGainKg); putNullable("feedPricePerKg", feedPricePerKg)
        put("processingCost", processingCost)
    }

    private fun CashTransaction.toBackupJson() = JSONObject().apply {
        put("id", id); put("sourceTransactionId", sourceTransactionId ?: JSONObject.NULL); put("date", date)
        put("accountName", accountName); put("direction", direction); put("amount", amount)
        put("paymentMethod", paymentMethod); put("category", category); put("description", description)
        put("notes", notes); put("status", status); put("updatedAt", updatedAt); put("deleted", deleted)
    }

    private fun CashFlow.toBackupJson() = JSONObject().apply {
        put("id", id); put("date", date); put("fromAccount", fromAccount); put("toAccount", toAccount)
        put("amount", amount); put("reference", reference); put("notes", notes); put("status", status); put("updatedAt", updatedAt)
    }

    private fun ExternalLoan.toBackupJson() = JSONObject().apply {
        put("id", id); put("date", date); put("party", party); put("loanReference", loanReference)
        put("loanType", loanType); put("direction", direction); put("amount", amount); put("paidAmount", paidAmount)
        put("dueDate", dueDate); put("cashAccount", cashAccount); put("notes", notes); put("status", status); put("updatedAt", updatedAt)
    }

    private fun EmployeeRecord.toBackupJson() = JSONObject().apply {
        put("id", id); put("name", name); put("position", position); put("dailyRate", dailyRate)
        put("standardHoursPerShift", standardHoursPerShift); put("overtimeRatePerHour", overtimeRatePerHour)
        put("active", active); put("updatedAt", updatedAt)
    }

    private fun AttendanceRecord.toBackupJson() = JSONObject().apply {
        put("id", id); put("employeeId", employeeId); put("employeeName", employeeName); put("workDate", workDate)
        put("shiftName", shiftName); put("clockIn", clockIn); put("clockOut", clockOut); put("breakMinutes", breakMinutes)
        put("overtimeHours", overtimeHours); put("status", status); put("notes", notes); put("updatedAt", updatedAt)
    }

    private fun PayrollRecord.toBackupJson() = JSONObject().apply {
        put("id", id); put("employeeId", employeeId); put("employeeName", employeeName); put("period", period)
        put("daysPresent", daysPresent); put("dailyRate", dailyRate); put("basePay", basePay); put("overtimeHours", overtimeHours)
        put("overtimePay", overtimePay); put("allowance", allowance); put("deductions", deductions); put("totalPay", totalPay)
        put("paidAmount", paidAmount); put("paymentMethod", paymentMethod); put("cashAccount", cashAccount)
        put("status", status); put("notes", notes); put("updatedAt", updatedAt)
    }

    private fun JSONObject.putNullable(key: String, value: Double?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.nullableDouble(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key, 0.0)

    suspend fun importJsonData(jsonString: String): Result<Int> = runCatching {
        val root = runCatching { JSONObject(jsonString) }.getOrNull()
        val transactionArray = if (root != null && root.has("transactions")) {
            root.getJSONArray("transactions")
        } else {
            JSONArray(jsonString)
        }

        val transactions = mutableListOf<AyamTransaction>()
        for (i in 0 until transactionArray.length()) {
            val item = transactionArray.getJSONObject(i)
            val id = item.optString("id").ifBlank { "TX-${java.util.UUID.randomUUID()}" }
            val date = item.optString("date", "")
            val type = item.optString("type", "sale")
            val amount = item.optDouble("amount", 0.0)
            require(id.isNotBlank() && date.isNotBlank() && amount >= 0.0) { "Data transaksi dalam file cadangan tidak valid" }
            transactions.add(
                AyamTransaction(
                    id = id,
                    date = date,
                    ref = item.optString("ref", ""),
                    type = type,
                    category = item.optString("category", "Umum"),
                    description = item.optString("description", "Transaksi"),
                    amount = amount,
                    paidAmount = item.nullableDouble("paidAmount"),
                    qtyKg = item.nullableDouble("qtyKg"),
                    batchRef = item.optString("batchRef", ""),
                    notes = item.optString("notes", ""),
                    status = item.optString("status", "draft"),
                    source = item.optString("source", "import"),
                    updatedAt = item.optString("updatedAt", ""),
                    paymentMethod = item.optString("paymentMethod", "UNVERIFIED"),
                    cashAccount = item.optString("cashAccount", "BELUM_DIPETAKAN"),
                    supplier = item.optString("supplier", ""),
                    weightLossKg = item.nullableDouble("weightLossKg"),
                    mortalityRatePct = item.nullableDouble("mortalityRatePct"),
                    fcr = item.nullableDouble("fcr"),
                    weightGainKg = item.nullableDouble("weightGainKg"),
                    feedPricePerKg = item.nullableDouble("feedPricePerKg"),
                    processingCost = item.optDouble("processingCost", 0.0)
                )
            )
        }

        val deletedList = mutableListOf<DeletedAyamTransaction>()
        val deletedArray = root?.optJSONArray("deletedTransactions")
        if (deletedArray != null) for (i in 0 until deletedArray.length()) {
            val item = deletedArray.getJSONObject(i)
            val originalId = item.optString("originalId").ifBlank { item.optString("id") }
            if (originalId.isBlank()) continue
            deletedList.add(DeletedAyamTransaction(
                id = item.optString("id", "DEL-${originalId}"), originalId = originalId,
                deletedAt = item.optString("deletedAt", ""), date = item.optString("date", ""),
                ref = item.optString("ref", ""), type = item.optString("type", "sale"),
                category = item.optString("category", "Umum"), description = item.optString("description", ""),
                amount = item.optDouble("amount", 0.0), paidAmount = item.nullableDouble("paidAmount"),
                qtyKg = item.nullableDouble("qtyKg"), batchRef = item.optString("batchRef", ""),
                notes = item.optString("notes", ""), status = item.optString("status", "draft"),
                source = item.optString("source", "import"), paymentMethod = item.optString("paymentMethod", "UNVERIFIED"),
                cashAccount = item.optString("cashAccount", "BELUM_DIPETAKAN"), supplier = item.optString("supplier", ""),
                weightLossKg = item.nullableDouble("weightLossKg"), mortalityRatePct = item.nullableDouble("mortalityRatePct"),
                fcr = item.nullableDouble("fcr"), weightGainKg = item.nullableDouble("weightGainKg"),
                feedPricePerKg = item.nullableDouble("feedPricePerKg"), processingCost = item.optDouble("processingCost", 0.0)
            ))
        }

        val isFullBackup = root != null && root.optBoolean("fullBackup", false) && root.optInt("version", 1) >= 2
        val cashTransactions = mutableListOf<CashTransaction>()
        val cashFlows = mutableListOf<CashFlow>()
        val externalLoans = mutableListOf<ExternalLoan>()
        val employees = mutableListOf<EmployeeRecord>()
        val attendance = mutableListOf<AttendanceRecord>()
        val payroll = mutableListOf<PayrollRecord>()
        if (isFullBackup && root != null) {
            root.optJSONArray("cashTransactions")?.let { arr -> for (i in 0 until arr.length()) {
                val o=arr.getJSONObject(i)
                cashTransactions.add(CashTransaction(o.optString("id"),if (o.isNull("sourceTransactionId")) null else o.optString("sourceTransactionId"),
                    o.optString("date"),o.optString("accountName","DOMPET"),o.optString("direction","IN"),o.optDouble("amount",0.0),
                    o.optString("paymentMethod","UNVERIFIED"),o.optString("category"),o.optString("description"),o.optString("notes"),
                    o.optString("status","draft"),o.optString("updatedAt"),o.optBoolean("deleted",false)))
            } }
            root.optJSONArray("cashFlows")?.let { arr -> for (i in 0 until arr.length()) {
                val o=arr.getJSONObject(i)
                cashFlows.add(CashFlow(o.optString("id"),o.optString("date"),o.optString("fromAccount"),o.optString("toAccount"),
                    o.optDouble("amount",0.0),o.optString("reference"),o.optString("notes"),o.optString("status","draft"),o.optString("updatedAt")))
            } }
            root.optJSONArray("externalLoans")?.let { arr -> for (i in 0 until arr.length()) {
                val o=arr.getJSONObject(i)
                externalLoans.add(ExternalLoan(o.optString("id"),o.optString("date"),o.optString("party"),o.optString("loanReference"),
                    o.optString("loanType"),o.optString("direction"),o.optDouble("amount",0.0),o.optDouble("paidAmount",0.0),
                    o.optString("dueDate"),o.optString("cashAccount","ATM/BANK"),o.optString("notes"),
                    o.optString("status","draft"),o.optString("updatedAt")))
            } }
            root.optJSONArray("employees")?.let { arr -> for (i in 0 until arr.length()) {
                val o=arr.getJSONObject(i)
                employees.add(EmployeeRecord(o.optString("id"),o.optString("name"),o.optString("position"),o.optDouble("dailyRate",0.0),
                    o.optDouble("standardHoursPerShift",8.0),o.optDouble("overtimeRatePerHour",0.0),o.optBoolean("active",true),o.optString("updatedAt")))
            } }
            root.optJSONArray("attendance")?.let { arr -> for (i in 0 until arr.length()) {
                val o=arr.getJSONObject(i)
                attendance.add(AttendanceRecord(o.optString("id"),o.optString("employeeId"),o.optString("employeeName"),o.optString("workDate"),
                    o.optString("shiftName"),o.optString("clockIn"),o.optString("clockOut"),o.optInt("breakMinutes",0),
                    o.optDouble("overtimeHours",0.0),o.optString("status","PRESENT"),o.optString("notes"),o.optString("updatedAt")))
            } }
            root.optJSONArray("payroll")?.let { arr -> for (i in 0 until arr.length()) {
                val o=arr.getJSONObject(i)
                payroll.add(PayrollRecord(o.optString("id"),o.optString("employeeId"),o.optString("employeeName"),o.optString("period"),
                    o.optDouble("daysPresent",0.0),o.optDouble("dailyRate",0.0),o.optDouble("basePay",0.0),o.optDouble("overtimeHours",0.0),
                    o.optDouble("overtimePay",0.0),o.optDouble("allowance",0.0),o.optDouble("deductions",0.0),o.optDouble("totalPay",0.0),
                    o.optDouble("paidAmount",0.0),o.optString("paymentMethod","TRANSFER"),o.optString("cashAccount","ATM/BANK"),
                    o.optString("status","DRAFT"),o.optString("notes"),o.optString("updatedAt")))
            } }
        }

        // Parse everything before changing the database, so a malformed file cannot
        // wipe the user's current records partway through the restore process.
        dao.clearAll()
        if (transactions.isNotEmpty()) dao.insertAll(transactions)
        dao.clearAllDeleted()
        deletedList.forEach { dao.insertDeleted(it) }

        if (isFullBackup) {
            businessDao.clearAllCashTransactions()
            businessDao.clearAllCashFlows()
            businessDao.clearAllExternalLoans()
            businessDao.clearAllEmployees()
            businessDao.clearAllAttendance()
            businessDao.clearAllPayroll()
            if (cashTransactions.isNotEmpty()) businessDao.saveCashTransactions(cashTransactions)
            if (cashFlows.isNotEmpty()) businessDao.saveCashFlows(cashFlows)
            if (externalLoans.isNotEmpty()) businessDao.saveExternalLoans(externalLoans)
            if (employees.isNotEmpty()) businessDao.saveEmployees(employees)
            if (attendance.isNotEmpty()) businessDao.saveAttendances(attendance)
            if (payroll.isNotEmpty()) businessDao.savePayrollRecords(payroll)
        } else {
            // Older v1 JSON contains transactions only. Existing accounts/loans/payroll
            // are retained, and their cash mirrors are rebuilt from the imported ledger.
            businessDao.clearMirroredCashTransactions()
            transactions.filter { it.isConfirmed }.forEach { updateCashMirror(it) }
        }
        transactions.size
    }

    fun generateCsvExport(transactions: List<AyamTransaction>): String {
        val sb = StringBuilder()
        sb.append("\uFEFF") // UTF-8 BOM for Excel
        sb.append("ID;Tanggal;Referensi;Jenis;Kategori;Keterangan;Nominal;Diterima/Dibayar;KG;Referensi DO;Status;Catatan\r\n")

        for (t in transactions) {
            val typeLabel = AyamTransactionType.fromKey(t.type).label
            val paidStr = if (t.paidAmount != null) t.paidAmount.toLong().toString() else ""
            val qtyStr = if (t.qtyKg != null) t.qtyKg.toString() else ""
            val statusLabel = if (t.isConfirmed) "Terverifikasi" else "Perlu Verifikasi"

            fun escapeCsv(s: String): String {
                return "\"" + s.replace("\"", "\"\"") + "\""
            }

            val row = listOf(
                escapeCsv(t.id),
                escapeCsv(t.date),
                escapeCsv(t.ref),
                escapeCsv(typeLabel),
                escapeCsv(t.category),
                escapeCsv(t.description),
                t.amount.toLong().toString(),
                paidStr,
                qtyStr,
                escapeCsv(t.batchRef),
                escapeCsv(statusLabel),
                escapeCsv(t.notes)
            )
            sb.append(row.joinToString(";")).append("\r\n")
        }
        return sb.toString()
    }
}
