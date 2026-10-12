package com.example.data.remote

import com.example.data.model.AyamTransaction
import com.example.data.model.DeletedAyamTransaction
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
        deletedTransactions: List<DeletedAyamTransaction>
    ): GoogleSheetsSyncResponse = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("action", "sync")
            put("apiKey", apiKey)
            put("deviceName", "AyamKas Pro Android")
            put("transactions", JSONArray().apply { transactions.forEach { put(it.toJson()) } })
            put("deletedTransactions", JSONArray().apply { deletedTransactions.forEach { put(it.toJson()) } })
        }
        val response = post(url, body)
        GoogleSheetsSyncResponse(
            transactions = response.optJSONArray("transactions").toTransactions(),
            deletedTransactions = response.optJSONArray("deletedTransactions").toDeletedTransactions(),
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
            if (!response.isSuccessful) throw IOException("Server Google menolak permintaan (HTTP \${response.code}).")
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
    }

    private fun JSONObject.putNullable(key: String, value: Double?) {
        put(key, value ?: JSONObject.NULL)
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
                    updatedAt = o.optString("updatedAt")
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
                    source = o.optString("source", "cloud")
                ))
            }
        }
    }

    private fun JSONObject.nullableDouble(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key, 0.0)
}