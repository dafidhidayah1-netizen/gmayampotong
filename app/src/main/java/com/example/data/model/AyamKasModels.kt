package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

@Entity(tableName = "ayam_transactions")
data class AyamTransaction(
    @PrimaryKey
    val id: String,
    val date: String, // YYYY-MM-DD
    val ref: String = "", // e.g. "B-027", "C-001", "DO-010"
    val type: String, // "capital_in", "purchase", "sale", "expense", "capital_out", "supplier_payment", "receivable_payment"
    val category: String = "Umum",
    val description: String,
    val amount: Double,
    val paidAmount: Double? = null,
    val qtyKg: Double? = null,
    val batchRef: String = "", // Links to purchase DO ref
    val notes: String = "",
    val status: String = "confirmed", // "confirmed" or "draft"
    val source: String = "real",
    val updatedAt: String = "",
    @androidx.room.ColumnInfo(defaultValue = "'CASH'") val paymentMethod: String = "CASH",
    @androidx.room.ColumnInfo(defaultValue = "'DOMPET'") val cashAccount: String = "DOMPET",
    @androidx.room.ColumnInfo(defaultValue = "''") val supplier: String = "",
    val weightLossKg: Double? = null,
    val mortalityRatePct: Double? = null,
    val fcr: Double? = null,
    val weightGainKg: Double? = null,
    val feedPricePerKg: Double? = null,
    @androidx.room.ColumnInfo(defaultValue = "0") val processingCost: Double = 0.0
) {
    val isConfirmed: Boolean get() = status == "confirmed"
    val isDraft: Boolean get() = status == "draft"

    fun cashEffect(): Double {
        val amt = amount
        val paid = paidAmount ?: 0.0
        return when (type) {
            "capital_in" -> amt
            "sale" -> paid
            "receivable_payment" -> amt
            "purchase", "expense" -> -paid
            "capital_out", "supplier_payment" -> -amt
            else -> 0.0
        }
    }
}

@Entity(tableName = "ayam_deleted_transactions")
data class DeletedAyamTransaction(
    @PrimaryKey
    val id: String,
    val originalId: String,
    val deletedAt: String,
    val date: String,
    val ref: String,
    val type: String,
    val category: String,
    val description: String,
    val amount: Double,
    val paidAmount: Double?,
    val qtyKg: Double?,
    val batchRef: String,
    val notes: String,
    val status: String,
    val source: String,
    @androidx.room.ColumnInfo(defaultValue = "'CASH'") val paymentMethod: String = "CASH",
    @androidx.room.ColumnInfo(defaultValue = "'DOMPET'") val cashAccount: String = "DOMPET",
    @androidx.room.ColumnInfo(defaultValue = "''") val supplier: String = "",
    val weightLossKg: Double? = null,
    val mortalityRatePct: Double? = null,
    val fcr: Double? = null,
    val weightGainKg: Double? = null,
    val feedPricePerKg: Double? = null,
    @androidx.room.ColumnInfo(defaultValue = "0") val processingCost: Double = 0.0
)

enum class AyamTransactionType(
    val key: String,
    val label: String,
    val icon: String,
    val flow: String,
    val description: String
) {
    CAPITAL_IN("capital_in", "Modal Masuk", "↗", "in", "Modal owner / saldo awal"),
    PURCHASE("purchase", "Pembelian DO", "📦", "out", "Pembelian ayam / DO supplier"),
    SALE("sale", "Penjualan / Omzet", "↗", "in", "Penjualan ayam potong"),
    EXPENSE("expense", "Biaya Operasional", "↘", "out", "Biaya operasional harian"),
    CAPITAL_OUT("capital_out", "Prive / Modal Keluar", "↘", "out", "Prive atau pengembalian modal"),
    SUPPLIER_PAYMENT("supplier_payment", "Bayar Utang DO", "↘", "out", "Pembayaran sisa utang pembelian"),
    RECEIVABLE_PAYMENT("receivable_payment", "Terima Piutang", "↗", "in", "Penerimaan sisa piutang penjualan");

    companion object {
        fun fromKey(key: String): AyamTransactionType {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: SALE
        }
    }
}

data class DoStockItem(
    val purchaseTx: AyamTransaction,
    val soldKg: Double,
    val missingSalesCount: Int,
    val totalPurchasedKg: Double,
    val remainingKg: Double,
    val totalCost: Double,
    val unitCost: Double,
    val remainingValue: Double,
    val status: String
)

data class AyamKasSummary(
    val capital: Double = 0.0,
    val revenue: Double = 0.0,
    val purchaseTotal: Double = 0.0,
    val expenses: Double = 0.0,
    val cogs: Double = 0.0,
    val knownSales: Int = 0,
    val unknownSales: Int = 0,
    val profit: Double = 0.0,
    val profitBeforeCogs: Double = 0.0,
    val profitComplete: Boolean = true,
    val stockKg: Double = 0.0,
    val stockValue: Double = 0.0,
    val negativeStockBatches: List<String> = emptyList(),
    val cash: Double = 0.0,
    val ar: Double = 0.0, // Piutang penjualan
    val ap: Double = 0.0, // Utang DO
    val totalCount: Int = 0,
    val confirmedCount: Int = 0,
    val draftCount: Int = 0,
    val salesCount: Int = 0,
    val purchaseCount: Int = 0,
    val expenseCount: Int = 0,
    val paidSales: Double = 0.0,
    val paidPurchases: Double = 0.0,
    val qtyMissingCount: Int = 0,
    val paymentMissingCount: Int = 0,
    val salesCash: Double = 0.0,
    val salesTransfer: Double = 0.0,
    val salesQris: Double = 0.0,
    val salesUnclassified: Double = 0.0,
    val grossProfit: Double = 0.0,
    val grossMarginPercent: Double = 0.0,
    val batchDataIncomplete: Int = 0,
    val feedCost: Double = 0.0
)

data class TypeBreakdownItem(
    val typeKey: String,
    val label: String,
    val count: Int,
    val totalAmount: Double
)

object AyamKasFormatters {
    private val symbols = DecimalFormatSymbols(Locale("id", "ID")).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }

    private val rupiahFormat = DecimalFormat("Rp #,##0", symbols)
    private val numberFormat = DecimalFormat("#,##0.###", symbols)

    fun formatRupiah(amount: Double): String {
        return rupiahFormat.format(amount)
    }

    fun formatNumber(value: Double): String {
        return numberFormat.format(value)
    }

    fun formatDate(isoDate: String): String {
        if (isoDate.isBlank()) return "—"
        val parts = isoDate.split("-")
        return if (parts.size == 3) {
            "${parts[2]}/${parts[1]}/${parts[0]}"
        } else {
            isoDate
        }
    }
}
