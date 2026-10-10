package com.example.data.repository

import com.example.data.local.AyamKasDao
import com.example.data.model.AyamTransaction
import com.example.data.model.AyamTransactionType
import com.example.data.model.DeletedAyamTransaction
import com.example.data.model.InitialAyamData
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AyamKasRepository(
    private val dao: AyamKasDao
) {
    val allTransactions: Flow<List<AyamTransaction>> = dao.getAllTransactions()
    val allDeleted: Flow<List<DeletedAyamTransaction>> = dao.getAllDeleted()

    suspend fun ensureSeededInitialData() {
        val count = dao.getTransactionCount()
        if (count == 0) {
            dao.insertAll(InitialAyamData.REAL_TRANSACTIONS)
        }
    }

    suspend fun saveTransaction(tx: AyamTransaction) {
        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        dao.insertTransaction(tx.copy(updatedAt = nowIso))
    }

    suspend fun toggleConfirm(id: String, currentStatus: String) {
        val newStatus = if (currentStatus == "confirmed") "draft" else "confirmed"
        dao.updateStatus(id, newStatus)
    }

    suspend fun setStatus(id: String, status: String) {
        dao.updateStatus(id, status)
    }

    suspend fun deleteTransaction(tx: AyamTransaction) {
        val nowIso = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
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
        dao.insertDeleted(deleted)
        dao.deleteTransaction(tx.id)
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
            updatedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        )
        dao.insertTransaction(restored)
        dao.deleteDeleted(deleted.id)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }

    suspend fun restoreDefaultData() {
        dao.clearAll()
        dao.insertAll(InitialAyamData.REAL_TRANSACTIONS)
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
