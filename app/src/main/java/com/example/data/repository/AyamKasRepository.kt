package com.example.data.repository

import com.example.data.local.AyamKasDao
import com.example.data.local.BusinessLedgerDao
import com.example.data.model.AyamTransaction
import com.example.data.model.CashTransaction
import com.example.data.model.CashFlow
import com.example.data.model.ExternalLoan
import com.example.data.model.EmployeeRecord
import com.example.data.model.AttendanceRecord
import com.example.data.model.PayrollRecord
import com.example.data.model.AyamTransactionType
import com.example.data.model.DeletedAyamTransaction
import com.example.data.model.InitialAyamData
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

    suspend fun ensureSeededInitialData() {
        val count = dao.getTransactionCount()
        if (count == 0) {
            dao.insertAll(InitialAyamData.REAL_TRANSACTIONS)
        }
    }

    suspend fun saveTransaction(tx: AyamTransaction) {
        val saved = tx.copy(updatedAt = nowIso())
        dao.insertTransaction(saved)
        updateCashMirror(saved)
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
            val sourceDirection = if (loan.direction == "REPAYMENT") "RECEIVED" else "GIVEN"
            val allowedType = if (loan.direction == "REPAYMENT") debtTypes else lendingTypes
            require(loan.loanType in allowedType) {
                if (loan.direction == "REPAYMENT") "Pelunasan harus memilih kategori pinjaman yang diterima usaha" else "Penagihan harus memilih kategori pinjaman yang diberikan usaha"
            }
            val principal = allLoans.filter {
                it.loanReference.trim().equals(loanRef, ignoreCase = true) &&
                    it.loanType == loan.loanType && it.direction == sourceDirection &&
                    it.status == "confirmed" && it.id != loan.id
            }.sumOf { it.amount }
            require(principal > 0.0) { "Referensi pinjaman asal tidak ditemukan atau belum terverifikasi" }
            val previousSettlement = allLoans.filter {
                it.loanReference.trim().equals(loanRef, ignoreCase = true) &&
                    it.loanType == loan.loanType && it.direction == loan.direction &&
                    it.status == "confirmed" && it.id != loan.id
            }.sumOf { it.amount }
            val outstanding = (principal - previousSettlement).coerceAtLeast(0.0)
            require(loan.amount <= outstanding + 0.0001) {
                "Nominal pelunasan/penagihan melebihi saldo referensi pinjaman (${AyamKasFormatters.formatRupiah(outstanding)})"
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

    private fun calculateWorkedHours(clockIn: String, clockOut: String, breakMinutes: Int): Double {
        val start = java.time.LocalTime.parse(clockIn)
        val end = java.time.LocalTime.parse(clockOut)
        var minutes = java.time.Duration.between(start, end).toMinutes()
        if (minutes < 0) minutes += 24 * 60 // overnight shift
        minutes = (minutes - breakMinutes).coerceAtLeast(0)
        return minutes / 60.0
    }

    suspend fun savePayroll(record: PayrollRecord) {
        require(record.daysPresent >= 0.0 && record.overtimeHours >= 0.0) { "Hari hadir/jam lembur tidak boleh negatif" }
        require(record.deductions >= 0.0 && record.paidAmount >= 0.0 && record.paidAmount <= record.totalPay) { "Potongan/pembayaran tidak valid" }
        require(record.totalPay >= 0.0) { "Total upah tidak boleh negatif" }
        val saved = record.copy(updatedAt = nowIso())
        businessDao.savePayroll(saved)
        // Approved payroll becomes one linked expense row. The actual paid amount,
        // not the whole payroll value, is the cash movement.
        val periodDate = if (saved.period.length == 7) "${saved.period}-01" else saved.period
        saveTransaction(
            AyamTransaction(
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
        )
    }

    suspend fun toggleConfirm(id: String, currentStatus: String) {
        val current = dao.getTransactionById(id) ?: return
        val newStatus = if (currentStatus == "confirmed") "draft" else "confirmed"
        val saved = current.copy(status = newStatus, updatedAt = nowIso())
        dao.insertTransaction(saved)
        updateCashMirror(saved)
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
            updatedAt = nowIso()
        )
        saveTransaction(restored.copy(
            paymentMethod = deleted.paymentMethod,
            cashAccount = deleted.cashAccount,
            supplier = deleted.supplier,
            weightLossKg = deleted.weightLossKg,
            mortalityRatePct = deleted.mortalityRatePct,
            fcr = deleted.fcr,
            weightGainKg = deleted.weightGainKg,
            feedPricePerKg = deleted.feedPricePerKg,
            processingCost = deleted.processingCost
        ))
        dao.deleteDeleted(deleted.id)
    }

    suspend fun clearAll() {
        val now = nowIso()
        dao.getAllTransactions().first().forEach { tx ->
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
                    source = tx.source
                )
            )
        }
        dao.clearAll()
        businessDao.clearMirroredCashTransactions()
    }

    suspend fun restoreDefaultData() {
        val old = dao.getAllTransactions().first()
        val defaults = InitialAyamData.REAL_TRANSACTIONS
        val defaultIds = defaults.map { it.id }.toSet()
        val now = nowIso()
        old.filterNot { it.id in defaultIds }.forEach { tx ->
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
                    source = tx.source
                )
            )
        }
        dao.clearAll()
        businessDao.clearMirroredCashTransactions()
        dao.insertAll(defaults.map { it.copy(updatedAt = now) })
    }

    fun generateJsonExport(transactions: List<AyamTransaction>): String {
        val root = JSONObject()
        root.put("app", "AyamKas Pro")
        root.put("version", 1)
        root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))

        val arr = JSONArray()
        for (t in transactions) {
            val item = JSONObject()
            item.put("id", t.id)
            item.put("date", t.date)
            item.put("ref", t.ref)
            item.put("type", t.type)
            item.put("category", t.category)
            item.put("description", t.description)
            item.put("amount", t.amount)
            if (t.paidAmount != null) item.put("paidAmount", t.paidAmount) else item.put("paidAmount", JSONObject.NULL)
            if (t.qtyKg != null) item.put("qtyKg", t.qtyKg) else item.put("qtyKg", JSONObject.NULL)
            item.put("batchRef", t.batchRef)
            item.put("notes", t.notes)
            item.put("status", t.status)
            item.put("source", t.source)
            item.put("updatedAt", t.updatedAt)
            arr.put(item)
        }
        root.put("transactions", arr)
        return root.toString(2)
    }

    suspend fun importJsonData(jsonString: String): Result<Int> {
        return runCatching {
            val root = JSONObject(jsonString)
            val arr = if (root.has("transactions")) {
                root.getJSONArray("transactions")
            } else {
                JSONArray(jsonString)
            }

            val list = mutableListOf<AyamTransaction>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val id = item.optString("id", "TX-${System.currentTimeMillis()}-$i")
                val date = item.optString("date", "")
                val type = item.optString("type", "sale")
                val desc = item.optString("description", "Transaksi")
                val amount = item.optDouble("amount", 0.0)

                val paidAmount = if (item.isNull("paidAmount") || !item.has("paidAmount")) null else item.optDouble("paidAmount")
                val qtyKg = if (item.isNull("qtyKg") || !item.has("qtyKg")) null else item.optDouble("qtyKg")

                list.add(
                    AyamTransaction(
                        id = id,
                        date = date,
                        ref = item.optString("ref", ""),
                        type = type,
                        category = item.optString("category", "Umum"),
                        description = desc,
                        amount = amount,
                        paidAmount = paidAmount,
                        qtyKg = qtyKg,
                        batchRef = item.optString("batchRef", ""),
                        notes = item.optString("notes", ""),
                        status = item.optString("status", "draft"),
                        source = item.optString("source", "import"),
                        updatedAt = item.optString("updatedAt", "")
                    )
                )
            }

            if (list.isNotEmpty()) {
                dao.clearAll()
                dao.insertAll(list)
            }
            list.size
        }
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
