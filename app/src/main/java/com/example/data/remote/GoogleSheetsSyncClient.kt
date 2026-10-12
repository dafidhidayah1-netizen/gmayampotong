package com.example.data.remote

import com.example.data.model.AyamTransaction
import com.example.data.model.DeletedAyamTransaction
import com.example.data.model.CashTransaction
import com.example.data.model.CashFlow
import com.example.data.model.ExternalLoan
import com.example.data.model.EmployeeRecord
import com.example.data.model.AttendanceRecord
import com.example.data.model.PayrollRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class GoogleSheetsSyncResponse(
    val transactions: List<AyamTransaction>,
    val deletedTransactions: List<DeletedAyamTransaction>,
    val cashTransactions: List<CashTransaction>,
    val cashFlows: List<CashFlow>,
    val externalLoans: List<ExternalLoan>,
    val employees: List<EmployeeRecord>,
    val attendance: List<AttendanceRecord>,
    val payroll: List<PayrollRecord>,
    val accepted: Int,
    val serverTime: String,
    val spreadsheetId: String
)

data class GoogleSheetsBackupResponse(
    val name: String,
    val url: String
)

/** HTTP bridge for the bound Apps Script Web App. Local data remains available offline. */
class GoogleSheetsSyncClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(75, TimeUnit.SECONDS)
        .build()

    suspend fun sync(
        url: String,
        apiKey: String,
        transactions: List<AyamTransaction>,
        deletedTransactions: List<DeletedAyamTransaction>,
        cashTransactions: List<CashTransaction>,
        cashFlows: List<CashFlow>,
        externalLoans: List<ExternalLoan>,
        employees: List<EmployeeRecord>,
        attendance: List<AttendanceRecord>,
        payroll: List<PayrollRecord>
    ): GoogleSheetsSyncResponse = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("action", "sync")
            put("apiKey", apiKey)
            put("deviceName", "AyamKas Pro Android")
            put("transactions", JSONArray().apply { transactions.forEach { put(it.toJson()) } })
            put("deletedTransactions", JSONArray().apply { deletedTransactions.forEach { put(it.toJson()) } })
            put("cashTransactions", JSONArray().apply { cashTransactions.forEach { put(it.toJson()) } })
            put("cashFlows", JSONArray().apply { cashFlows.forEach { put(it.toJson()) } })
            put("externalLoans", JSONArray().apply { externalLoans.forEach { put(it.toJson()) } })
            put("employees", JSONArray().apply { employees.forEach { put(it.toJson()) } })
            put("attendance", JSONArray().apply { attendance.forEach { put(it.toJson()) } })
            put("payroll", JSONArray().apply { payroll.forEach { put(it.toJson()) } })
        }
        val response = post(url, body)
        GoogleSheetsSyncResponse(
            transactions = response.optJSONArray("transactions").toTransactions(),
            deletedTransactions = response.optJSONArray("deletedTransactions").toDeletedTransactions(),
            cashTransactions = response.optJSONArray("cashTransactions").toCashTransactions(),
            cashFlows = response.optJSONArray("cashFlows").toCashFlows(),
            externalLoans = response.optJSONArray("externalLoans").toExternalLoans(),
            employees = response.optJSONArray("employees").toEmployees(),
            attendance = response.optJSONArray("attendance").toAttendance(),
            payroll = response.optJSONArray("payroll").toPayroll(),
            accepted = response.optInt("accepted", 0),
            serverTime = response.optString("serverTime", ""),
            spreadsheetId = response.optString("spreadsheetId", "")
        )
    }

    suspend fun backup(url: String, apiKey: String): GoogleSheetsBackupResponse = withContext(Dispatchers.IO) {
        val response = post(url, JSONObject().apply {
            put("action", "backup")
            put("apiKey", apiKey)
        })
        GoogleSheetsBackupResponse(
            name = response.optString("name", "Backup AyamKas Pro"),
            url = response.optString("url", "")
        )
    }

    private fun post(url: String, payload: JSONObject): JSONObject {
        if (!url.startsWith("https://script.google.com/macros/s/") || !url.endsWith("/exec")) {
            throw IOException("URL harus berupa URL Web App Apps Script yang berakhiran /exec.")
        }
        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Accept", "application/json")
            .build()
        http.newCall(request).execute().use { response ->
            val content = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Server Google menolak permintaan (HTTP ${response.code}).")
            val json = try {
                JSONObject(content)
            } catch (e: Exception) {
                throw IOException("Jawaban server bukan JSON. Periksa URL /exec dan deployment Apps Script.")
            }
            if (!json.optBoolean("ok", false)) {
                throw IOException(json.optString("error", "Sinkronisasi gagal tanpa keterangan server."))
            }
            return json
        }
    }

    private fun CashTransaction.toJson() = JSONObject().apply {
        put("id", id); put("sourceTransactionId", sourceTransactionId ?: JSONObject.NULL)
        put("date", date); put("accountName", accountName); put("direction", direction)
        put("amount", amount); put("paymentMethod", paymentMethod); put("category", category)
        put("description", description); put("notes", notes); put("status", status)
        put("updatedAt", updatedAt); put("deleted", deleted)
    }

    private fun CashFlow.toJson() = JSONObject().apply {
        put("id", id); put("date", date); put("fromAccount", fromAccount); put("toAccount", toAccount)
        put("amount", amount); put("reference", reference); put("notes", notes); put("status", status); put("updatedAt", updatedAt)
    }

    private fun ExternalLoan.toJson() = JSONObject().apply {
        put("id", id); put("date", date); put("party", party); put("loanType", loanType)
        put("direction", direction); put("amount", amount); put("paidAmount", paidAmount); put("dueDate", dueDate)
        put("cashAccount", cashAccount); put("notes", notes); put("status", status); put("updatedAt", updatedAt)
    }

    private fun EmployeeRecord.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("position", position); put("dailyRate", dailyRate)
        put("standardHoursPerShift", standardHoursPerShift); put("overtimeRatePerHour", overtimeRatePerHour)
        put("active", active); put("updatedAt", updatedAt)
    }

    private fun AttendanceRecord.toJson() = JSONObject().apply {
        put("id", id); put("employeeId", employeeId); put("employeeName", employeeName); put("workDate", workDate)
        put("shiftName", shiftName); put("clockIn", clockIn); put("clockOut", clockOut); put("breakMinutes", breakMinutes)
        put("overtimeHours", overtimeHours); put("status", status); put("notes", notes); put("updatedAt", updatedAt)
    }

    private fun PayrollRecord.toJson() = JSONObject().apply {
        put("id", id); put("employeeId", employeeId); put("employeeName", employeeName); put("period", period)
        put("daysPresent", daysPresent); put("dailyRate", dailyRate); put("basePay", basePay)
        put("overtimeHours", overtimeHours); put("overtimePay", overtimePay); put("allowance", allowance)
        put("deductions", deductions); put("totalPay", totalPay); put("paidAmount", paidAmount)
        put("paymentMethod", paymentMethod); put("cashAccount", cashAccount); put("status", status); put("notes", notes); put("updatedAt", updatedAt)
    }

    private fun AyamTransaction.toJson() = JSONObject().apply {
        put("id", id)
        put("date", date)
        put("ref", ref)
        put("type", type)
        put("category", category)
        put("description", description)
        put("amount", amount)
        putNullable("paidAmount", paidAmount)
        putNullable("qtyKg", qtyKg)
        put("batchRef", batchRef)
        put("notes", notes)
        put("status", status)
        put("source", source)
        put("updatedAt", updatedAt)
        put("paymentMethod", paymentMethod)
        put("cashAccount", cashAccount)
        put("supplier", supplier)
        putNullable("weightLossKg", weightLossKg)
        putNullable("mortalityRatePct", mortalityRatePct)
        putNullable("fcr", fcr)
        putNullable("weightGainKg", weightGainKg)
        putNullable("feedPricePerKg", feedPricePerKg)
        put("processingCost", processingCost)
    }

    private fun DeletedAyamTransaction.toJson() = JSONObject().apply {
        put("id", originalId)
        put("originalId", originalId)
        put("date", date)
        put("ref", ref)
        put("type", type)
        put("category", category)
        put("description", description)
        put("amount", amount)
        putNullable("paidAmount", paidAmount)
        putNullable("qtyKg", qtyKg)
        put("batchRef", batchRef)
        put("notes", notes)
        put("status", status)
        put("source", source)
        put("updatedAt", deletedAt)
        put("deletedAt", deletedAt)
        put("paymentMethod", paymentMethod)
        put("cashAccount", cashAccount)
        put("supplier", supplier)
        putNullable("weightLossKg", weightLossKg)
        putNullable("mortalityRatePct", mortalityRatePct)
        putNullable("fcr", fcr)
        putNullable("weightGainKg", weightGainKg)
        putNullable("feedPricePerKg", feedPricePerKg)
        put("processingCost", processingCost)
    }

    private fun JSONObject.putNullable(key: String, value: Double?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONArray?.toCashTransactions(): List<CashTransaction> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val o = optJSONObject(i) ?: continue
                add(CashTransaction(
                    id=o.optString("id"), sourceTransactionId=if (o.isNull("sourceTransactionId")) null else o.optString("sourceTransactionId"),
                    date=o.optString("date"), accountName=o.optString("accountName","DOMPET"), direction=o.optString("direction","IN"),
                    amount=o.optDouble("amount",0.0), paymentMethod=o.optString("paymentMethod","CASH"),
                    category=o.optString("category","Lainnya"), description=o.optString("description"), notes=o.optString("notes"),
                    status=o.optString("status","draft"), updatedAt=o.optString("updatedAt"), deleted=o.optBoolean("deleted",false)
                ))
            }
        }
    }

    private fun JSONArray?.toCashFlows(): List<CashFlow> {
        if (this == null) return emptyList()
        return buildList { for (i in 0 until length()) {
            val o=optJSONObject(i) ?: continue
            add(CashFlow(o.optString("id"),o.optString("date"),o.optString("fromAccount"),o.optString("toAccount"),
                o.optDouble("amount",0.0),o.optString("reference"),o.optString("notes"),o.optString("status","draft"),o.optString("updatedAt")))
        } }
    }

    private fun JSONArray?.toExternalLoans(): List<ExternalLoan> {
        if (this == null) return emptyList()
        return buildList { for (i in 0 until length()) {
            val o=optJSONObject(i) ?: continue
            add(ExternalLoan(o.optString("id"),o.optString("date"),o.optString("party"),o.optString("loanType"),
                o.optString("direction"),o.optDouble("amount",0.0),o.optDouble("paidAmount",0.0),o.optString("dueDate"),
                o.optString("cashAccount","ATM/BANK"),o.optString("notes"),o.optString("status","draft"),o.optString("updatedAt")))
        } }
    }

    private fun JSONArray?.toEmployees(): List<EmployeeRecord> {
        if (this == null) return emptyList()
        return buildList { for (i in 0 until length()) {
            val o=optJSONObject(i) ?: continue
            add(EmployeeRecord(o.optString("id"),o.optString("name"),o.optString("position"),o.optDouble("dailyRate",0.0),
                o.optDouble("standardHoursPerShift",8.0),o.optDouble("overtimeRatePerHour",0.0),o.optBoolean("active",true),o.optString("updatedAt")))
        } }
    }

    private fun JSONArray?.toAttendance(): List<AttendanceRecord> {
        if (this == null) return emptyList()
        return buildList { for (i in 0 until length()) {
            val o=optJSONObject(i) ?: continue
            add(AttendanceRecord(o.optString("id"),o.optString("employeeId"),o.optString("employeeName"),o.optString("workDate"),
                o.optString("shiftName"),o.optString("clockIn"),o.optString("clockOut"),o.optInt("breakMinutes",0),
                o.optDouble("overtimeHours",0.0),o.optString("status","PRESENT"),o.optString("notes"),o.optString("updatedAt")))
        } }
    }

    private fun JSONArray?.toPayroll(): List<PayrollRecord> {
        if (this == null) return emptyList()
        return buildList { for (i in 0 until length()) {
            val o=optJSONObject(i) ?: continue
            add(PayrollRecord(o.optString("id"),o.optString("employeeId"),o.optString("employeeName"),o.optString("period"),
                o.optDouble("daysPresent",0.0),o.optDouble("dailyRate",0.0),o.optDouble("basePay",0.0),o.optDouble("overtimeHours",0.0),
                o.optDouble("overtimePay",0.0),o.optDouble("allowance",0.0),o.optDouble("deductions",0.0),o.optDouble("totalPay",0.0),
                o.optDouble("paidAmount",0.0),o.optString("paymentMethod","TRANSFER"),o.optString("cashAccount","ATM/BANK"),
                o.optString("status","DRAFT"),o.optString("notes"),o.optString("updatedAt")))
        } }
    }

    private fun JSONArray?.toTransactions(): List<AyamTransaction> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val o = optJSONObject(i) ?: continue
                add(AyamTransaction(
                    id = o.optString("id"),
                    date = o.optString("date"),
                    ref = o.optString("ref"),
                    type = o.optString("type"),
                    category = o.optString("category", "Umum"),
                    description = o.optString("description"),
                    amount = o.optDouble("amount", 0.0),
                    paidAmount = o.nullableDouble("paidAmount"),
                    qtyKg = o.nullableDouble("qtyKg"),
                    batchRef = o.optString("batchRef"),
                    notes = o.optString("notes"),
                    status = o.optString("status", "draft"),
                    source = o.optString("source", "cloud"),
                    updatedAt = o.optString("updatedAt"),
                    paymentMethod = o.optString("paymentMethod", "CASH"),
                    cashAccount = o.optString("cashAccount", "DOMPET"),
                    supplier = o.optString("supplier", ""),
                    weightLossKg = o.nullableDouble("weightLossKg"),
                    mortalityRatePct = o.nullableDouble("mortalityRatePct"),
                    fcr = o.nullableDouble("fcr"),
                    weightGainKg = o.nullableDouble("weightGainKg"),
                    feedPricePerKg = o.nullableDouble("feedPricePerKg"),
                    processingCost = o.optDouble("processingCost", 0.0)
                ))
            }
        }
    }

    private fun JSONArray?.toDeletedTransactions(): List<DeletedAyamTransaction> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val o = optJSONObject(i) ?: continue
                val originalId = o.optString("originalId").ifBlank { o.optString("id").removePrefix("CLOUD-") }
                if (originalId.isBlank()) continue
                val deletedAt = o.optString("deletedAt").ifBlank { o.optString("updatedAt") }
                add(DeletedAyamTransaction(
                    id = "CLOUD-$originalId",
                    originalId = originalId,
                    deletedAt = deletedAt,
                    date = o.optString("date"),
                    ref = o.optString("ref"),
                    type = o.optString("type"),
                    category = o.optString("category", "Umum"),
                    description = o.optString("description"),
                    amount = o.optDouble("amount", 0.0),
                    paidAmount = o.nullableDouble("paidAmount"),
                    qtyKg = o.nullableDouble("qtyKg"),
                    batchRef = o.optString("batchRef"),
                    notes = o.optString("notes"),
                    status = o.optString("status", "draft"),
                    source = o.optString("source", "cloud"),
                    paymentMethod = o.optString("paymentMethod", "CASH"),
                    cashAccount = o.optString("cashAccount", "DOMPET"),
                    supplier = o.optString("supplier", ""),
                    weightLossKg = o.nullableDouble("weightLossKg"),
                    mortalityRatePct = o.nullableDouble("mortalityRatePct"),
                    fcr = o.nullableDouble("fcr"),
                    weightGainKg = o.nullableDouble("weightGainKg"),
                    feedPricePerKg = o.nullableDouble("feedPricePerKg"),
                    processingCost = o.optDouble("processingCost", 0.0)
                ))
            }
        }
    }

    private fun JSONObject.nullableDouble(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key, 0.0)
}