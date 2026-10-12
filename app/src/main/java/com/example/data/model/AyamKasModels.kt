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
    val paymentMissingCount: Int = 0
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

object InitialAyamData {
    val REAL_TRANSACTIONS = listOf(
        AyamTransaction(
            id = "TX-001",
            date = "2026-09-28",
            ref = "B-027",
            type = "capital_in",
            category = "Modal owner",
            description = "Saldo awal modal kerja owner",
            amount = 10954000.0,
            paidAmount = 10954000.0,
            qtyKg = null,
            batchRef = "",
            status = "draft",
            source = "real",
            notes = "Modal pembukaan kas operasional GM Ayam Potong"
        ),
        AyamTransaction(
            id = "TX-002",
            date = "2026-09-28",
            ref = "B-027",
            type = "sale",
            category = "Penjualan",
            description = "Penjualan ayam potong segar",
            amount = 658000.0,
            paidAmount = 658000.0,
            qtyKg = 22.0,
            batchRef = "B-027",
            status = "draft",
            source = "real",
            notes = "Penjualan tunai pelanggan lapak"
        ),
        AyamTransaction(
            id = "TX-003",
            date = "2026-09-29",
            ref = "B-028",
            type = "purchase",
            category = "Ayam / DO",
            description = "Pembelian DO ayam 74,95 kg",
            amount = 1731000.0,
            paidAmount = 1731000.0,
            qtyKg = 74.95,
            batchRef = "",
            status = "draft",
            source = "real",
            notes = "DO supplier B-028 lunas transfer"
        ),
        AyamTransaction(
            id = "TX-004",
            date = "2026-09-29",
            ref = "B-028",
            type = "sale",
            category = "Penjualan",
            description = "Penjualan ayam DO B-028",
            amount = 1404000.0,
            paidAmount = 1404000.0,
            qtyKg = 52.0,
            batchRef = "B-028",
            status = "draft",
            source = "real",
            notes = "Penjualan karkas dan potong"
        ),
        AyamTransaction(
            id = "TX-005",
            date = "2026-09-29",
            ref = "EXP-029",
            type = "expense",
            category = "Admin",
            description = "Biaya operasional harian & admin",
            amount = 61500.0,
            paidAmount = 61500.0,
            qtyKg = null,
            batchRef = "",
            status = "draft",
            source = "real",
            notes = "Biaya harian Rp55.000 + biaya admin transfer Rp6.500"
        ),
        AyamTransaction(
            id = "TX-006",
            date = "2026-09-30",
            ref = "B-029",
            type = "purchase",
            category = "Ayam / DO",
            description = "Pembelian DO ayam 25,63 kg",
            amount = 1495000.0,
            paidAmount = 1495000.0,
            qtyKg = 25.63,
            batchRef = "",
            status = "draft",
            source = "real",
            notes = "DO supplier B-029 lunas"
        ),
        AyamTransaction(
            id = "TX-007",
            date = "2026-09-30",
            ref = "B-029",
            type = "sale",
            category = "Penjualan",
            description = "Penjualan ayam DO B-029",
            amount = 870000.0,
            paidAmount = 870000.0,
            qtyKg = 25.63,
            batchRef = "B-029",
            status = "draft",
            source = "real",
            notes = "Penjualan ayam lunas"
        ),
        AyamTransaction(
            id = "TX-008",
            date = "2026-09-30",
            ref = "EXP-030",
            type = "expense",
            category = "Operasional",
            description = "Pengeluaran harian operasional",
            amount = 65000.0,
            paidAmount = 65000.0,
            qtyKg = null,
            batchRef = "",
            status = "draft",
            source = "real",
            notes = "Biaya es balok dan bensin"
        ),
        AyamTransaction(
            id = "TX-009",
            date = "2026-10-01",
            ref = "C-001",
            type = "purchase",
            category = "Ayam / DO",
            description = "Pembelian DO ayam 58,44 kg",
            amount = 1385000.0,
            paidAmount = 1385000.0,
            qtyKg = 58.44,
            batchRef = "",
            status = "draft",
            source = "real",
            notes = "DO supplier C-001 lunas"
        ),
        AyamTransaction(
            id = "TX-010",
            date = "2026-10-01",
            ref = "C-001",
            type = "sale",
            category = "Penjualan",
            description = "Penjualan ayam DO C-001",
            amount = 2400000.0,
            paidAmount = 2400000.0,
            qtyKg = 58.44,
            batchRef = "C-001",
            status = "draft",
            source = "real",
            notes = "Penjualan ayam pasar lunas"
        ),
        AyamTransaction(
            id = "TX-011",
            date = "2026-10-01",
            ref = "EXP-031",
            type = "expense",
            category = "Admin",
            description = "Biaya operasional harian & admin",
            amount = 78000.0,
            paidAmount = 78000.0,
            qtyKg = null,
            batchRef = "",
            status = "draft",
            source = "real",
            notes = "Biaya harian Rp70.000 + biaya admin Rp8.000"
        ),
        AyamTransaction(
            id = "TX-012",
            date = "2026-10-02",
            ref = "C-002",
            type = "purchase",
            category = "Ayam / DO",
            description = "Pembelian DO ayam 200 kg",
            amount = 4897870.0,
            paidAmount = 4897870.0,
            qtyKg = 200.0,
            batchRef = "",
            status = "draft",
            source = "real",
            notes = "DO supplier C-002 lunas transfer"
        ),
        AyamTransaction(
            id = "TX-013",
            date = "2026-10-02",
            ref = "C-002",
            type = "sale",
            category = "Penjualan",
            description = "Penjualan ayam DO C-002 (Tahap 1)",
            amount = 1856500.0,
            paidAmount = 1856500.0,
            qtyKg = 75.0,
            batchRef = "C-002",
            status = "draft",
            source = "real",
            notes = "Penjualan pagi lunas"
        ),
        AyamTransaction(
            id = "TX-014",
            date = "2026-10-02",
            ref = "EXP-032",
            type = "expense",
            category = "Admin",
            description = "Biaya operasional & admin",
            amount = 151000.0,
            paidAmount = 151000.0,
            qtyKg = null,
            batchRef = "",
            status = "draft",
            source = "real",
            notes = "Biaya operasional Rp143.000 + admin Rp8.000"
        ),
        AyamTransaction(
            id = "TX-015",
            date = "2026-10-03",
            ref = "C-003",
            type = "sale",
            category = "Penjualan",
            description = "Penjualan ayam lanjutan DO C-002 (Tahap 2)",
            amount = 3665500.0,
            paidAmount = 3665500.0,
            qtyKg = 125.0,
            batchRef = "C-002",
            status = "draft",
            source = "real",
            notes = "Penjualan sore lunas"
        ),
        AyamTransaction(
            id = "TX-016",
            date = "2026-10-03",
            ref = "EXP-033",
            type = "expense",
            category = "Operasional",
            description = "Biaya operasional harian",
            amount = 163000.0,
            paidAmount = 163000.0,
            qtyKg = null,
            batchRef = "",
            status = "draft",
            source = "real",
            notes = "Biaya potong dan plastik"
        ),
        AyamTransaction(
            id = "TX-017",
            date = "2026-10-04",
            ref = "D001",
            type = "purchase",
            category = "Ayam / DO",
            description = "Pembelian DO ayam 200 kg",
            amount = 4558500.0,
            paidAmount = 4558500.0,
            qtyKg = 200.0,
            batchRef = "",
            status = "draft",
            source = "real",
            notes = "DO supplier D001 lunas"
        ),
        AyamTransaction(
            id = "TX-018",
            date = "2026-10-04",
            ref = "D001",
            type = "sale",
            category = "Penjualan",
            description = "Penjualan ayam DO D001",
            amount = 3533000.0,
            paidAmount = 3533000.0,
            qtyKg = 145.0,
            batchRef = "D001",
            status = "draft",
            source = "real",
            notes = "Penjualan pelanggan langganan lunas"
        ),
        AyamTransaction(
            id = "TX-019",
            date = "2026-10-04",
            ref = "EXP-034",
            type = "expense",
            category = "Operasional",
            description = "Pengeluaran harian operasional",
            amount = 78000.0,
            paidAmount = 78000.0,
            qtyKg = null,
            batchRef = "",
            status = "draft",
            source = "real",
            notes = "Biaya harian dan transport"
        ),
        AyamTransaction(
            id = "TX-020",
            date = "2026-10-05",
            ref = "E-001",
            type = "purchase",
            category = "Ayam / DO",
            description = "Pembelian DO ayam 56,17 kg",
            amount = 1275000.0,
            paidAmount = 1275000.0,
            qtyKg = 56.17,
            batchRef = "",
            status = "draft",
            source = "real",
            notes = "DO supplier E-001 lunas"
        ),
        AyamTransaction(
            id = "TX-021",
            date = "2026-10-05",
            ref = "E-001",
            type = "sale",
            category = "Penjualan",
            description = "Penjualan ayam DO E-001",
            amount = 3355000.0,
            paidAmount = 3355000.0,
            qtyKg = 56.17,
            batchRef = "E-001",
            status = "draft",
            source = "real",
            notes = "Penjualan ayam lunas"
        ),
        AyamTransaction(
            id = "TX-022",
            date = "2026-10-05",
            ref = "EXP-035",
            type = "expense",
            category = "Operasional",
            description = "Pengeluaran operasional harian",
            amount = 117000.0,
            paidAmount = 117000.0,
            qtyKg = null,
            batchRef = "",
            status = "draft",
            source = "real",
            notes = "Biaya operasional harian"
        ),
        AyamTransaction(
            id = "TX-023",
            date = "2026-10-06",
            ref = "F-001",
            type = "purchase",
            category = "Ayam / DO",
            description = "Pembelian DO ayam 241,62 kg",
            amount = 5484000.0,
            paidAmount = 3000000.0,
            qtyKg = 241.62,
            batchRef = "",
            status = "draft",
            source = "real",
            notes = "Dibayar DP tunai Rp3.000.000, sisa utang DO Rp2.484.000"
        ),
        AyamTransaction(
            id = "TX-024",
            date = "2026-10-06",
            ref = "F-002",
            type = "sale",
            category = "Penjualan",
            description = "Penjualan ayam DO F-001 sebanyak 81,22 kg",
            amount = 2758000.0,
            paidAmount = 2758000.0,
            qtyKg = 81.22,
            batchRef = "F-001",
            status = "draft",
            source = "real",
            notes = "Penjualan tunai pelanggan"
        ),
        AyamTransaction(
            id = "TX-025",
            date = "2026-10-06",
            ref = "EXP-036",
            type = "expense",
            category = "Kondangan",
            description = "Pengeluaran kondangan & operasional",
            amount = 150000.0,
            paidAmount = 150000.0,
            qtyKg = null,
            batchRef = "",
            status = "draft",
            source = "real",
            notes = "Kondangan Rp50.000 + operasional Rp100.000"
        ),
        AyamTransaction(
            id = "TX-026",
            date = "2026-10-07",
            ref = "G-001",
            type = "purchase",
            category = "Ayam / DO",
            description = "Pembelian DO ayam 156,72 kg",
            amount = 3557500.0,
            paidAmount = 3557500.0,
            qtyKg = 156.72,
            batchRef = "",
            status = "draft",
            source = "real",
            notes = "DO supplier G-001 lunas"
        ),
        AyamTransaction(
            id = "TX-027",
            date = "2026-10-07",
            ref = "G-002",
            type = "sale",
            category = "Penjualan",
            description = "Penjualan ayam DO G-001",
            amount = 3870000.0,
            paidAmount = 3870000.0,
            qtyKg = 156.72,
            batchRef = "G-001",
            status = "draft",
            source = "real",
            notes = "Penjualan ayam lunas"
        ),
        AyamTransaction(
            id = "TX-028",
            date = "2026-10-07",
            ref = "EXP-037",
            type = "expense",
            category = "Operasional",
            description = "Pengeluaran harian operasional",
            amount = 187000.0,
            paidAmount = 187000.0,
            qtyKg = null,
            batchRef = "",
            status = "draft",
            source = "real",
            notes = "Biaya harian bensin dan tenaga potong"
        ),
        AyamTransaction(
            id = "TX-029",
            date = "2026-10-08",
            ref = "H-001",
            type = "sale",
            category = "Penjualan",
            description = "Penjualan ayam karkas segar",
            amount = 2734000.0,
            paidAmount = 2734000.0,
            qtyKg = 105.0,
            batchRef = "F-001",
            status = "draft",
            source = "real",
            notes = "Penjualan lunas langganan warung"
        ),
        AyamTransaction(
            id = "TX-030",
            date = "2026-10-08",
            ref = "EXP-038",
            type = "expense",
            category = "Operasional",
            description = "Biaya operasional harian",
            amount = 110000.0,
            paidAmount = 110000.0,
            qtyKg = null,
            batchRef = "",
            status = "draft",
            source = "real",
            notes = "Biaya es balok dan kebersihan"
        ),
        AyamTransaction(
            id = "TX-031",
            date = "2026-10-09",
            ref = "I-001",
            type = "purchase",
            category = "Ayam / DO",
            description = "Pembelian DO ayam 150,96 kg",
            amount = 3728500.0,
            paidAmount = 3728500.0,
            qtyKg = 150.96,
            batchRef = "",
            status = "draft",
            source = "real",
            notes = "DO supplier I-001 lunas transfer"
        ),
        AyamTransaction(
            id = "TX-032",
            date = "2026-10-09",
            ref = "I-002",
            type = "sale",
            category = "Penjualan",
            description = "Penjualan ayam DO I-001",
            amount = 1685000.0,
            paidAmount = 1685000.0,
            qtyKg = 68.0,
            batchRef = "I-001",
            status = "draft",
            source = "real",
            notes = "Penjualan ayam potong lunas"
        ),
        AyamTransaction(
            id = "TX-033",
            date = "2026-10-09",
            ref = "EXP-039",
            type = "expense",
            category = "Pakan",
            description = "Pengeluaran pakan dan operasional",
            amount = 512000.0,
            paidAmount = 512000.0,
            qtyKg = null,
            batchRef = "",
            status = "draft",
            source = "real",
            notes = "Biaya pakan ayam hidup Rp512.000"
        )
    )
}
