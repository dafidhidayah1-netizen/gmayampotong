package com.example

import com.example.data.model.AyamKasCalculator
import com.example.data.model.AyamKasFormatters
import com.example.data.model.AyamTransaction
import com.example.data.model.InitialAyamData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testRupiahFormat() {
        val formatted = AyamKasFormatters.formatRupiah(1500000.0)
        assertTrue(formatted.contains("1.500.000") || formatted.contains("1,500,000") || formatted.contains("1500000"))
        assertTrue(formatted.startsWith("Rp"))
    }

    @Test
    fun testRealOperationalDataAndCalculator() {
        val realData = InitialAyamData.REAL_TRANSACTIONS
        assertEquals(33, realData.size)
        assertTrue(realData.all { it.isConfirmed }) // The repository imports these operational examples as drafts until checked.

        val summary = AyamKasCalculator.calculate(realData)
        assertEquals(33, summary.confirmedCount)
        assertEquals(0, summary.draftCount)
        assertTrue(summary.cash > 0.0)
        assertTrue(summary.revenue > 0.0)
        assertTrue(summary.expenses > 0.0)
        assertTrue(summary.cogs > 0.0)
        assertTrue(summary.profit > 0.0)
        assertTrue(summary.stockKg > 0.0)
        assertTrue(summary.stockValue > 0.0)
        // Utang DO from DO F-001 (5.484.000 - 3.000.000 = 2.484.000)
        assertEquals(2484000.0, summary.ap, 0.001)
    }

    @Test
    fun testPurchaseAndSaleStockHppCalculation() {
        val transactions = listOf(
            AyamTransaction(
                id = "TX-1",
                date = "2026-10-01",
                ref = "DO-100",
                type = "purchase",
                description = "Pembelian DO 100 kg",
                amount = 2000000.0,
                paidAmount = 2000000.0,
                qtyKg = 100.0,
                saleableKg = 100.0,
                status = "confirmed"
            ),
            AyamTransaction(
                id = "TX-2",
                date = "2026-10-01",
                ref = "PJ-100",
                type = "sale",
                description = "Penjualan ayam 40 kg",
                amount = 1200000.0,
                paidAmount = 1200000.0,
                qtyKg = 40.0,
                batchRef = "DO-100",
                status = "confirmed"
            ),
            AyamTransaction(
                id = "TX-3",
                date = "2026-10-01",
                ref = "EXP-1",
                type = "expense",
                description = "Biaya operasional",
                amount = 100000.0,
                paidAmount = 100000.0,
                status = "confirmed"
            )
        )

        val summary = AyamKasCalculator.calculate(transactions)
        assertEquals(3, summary.confirmedCount)
        assertEquals(1200000.0, summary.revenue, 0.001)
        assertEquals(60.0, summary.stockKg, 0.001) // 100 - 40 = 60 kg remaining
        assertEquals(800000.0, summary.cogs, 0.001) // (2.000.000 / 100) * 40 = 800.000
        assertEquals(100000.0, summary.expenses, 0.001)
        // Profit = 1.200.000 - 800.000 - 100.000 = 300.000
        assertEquals(300000.0, summary.profit, 0.001)
        assertTrue(summary.outputWeightMissingCount > 0)
        assertTrue(!summary.profitComplete)

        val stockRows = AyamKasCalculator.calculateStockRows(transactions)
        assertEquals(1, stockRows.size)
        val row = stockRows[0]
        assertEquals("DO-100", row.purchaseTx.ref)
        assertEquals(100.0, row.totalPurchasedKg, 0.001)
        assertEquals(40.0, row.soldKg, 0.001)
        assertEquals(60.0, row.remainingKg, 0.001)
        assertEquals(20000.0, row.unitCost, 0.001) // 2.000.000 / 100 = 20.000 per kg
        assertEquals(1200000.0, row.remainingValue, 0.001) // 60 * 20.000 = 1.200.000
        assertEquals("Terhitung", row.status)
    }
 
    @Test
    fun testBatchCostIncludesDirectExpenseAndUsesOutputWeightAcrossMonths() {
        val purchase = AyamTransaction(
            id = "PUR-SEP", date = "2026-09-30", ref = "DO-HIST",
            type = "purchase", description = "Pembelian sebelum periode laporan",
            amount = 1000000.0, paidAmount = 1000000.0,
            qtyKg = 100.0, saleableKg = 80.0, status = "confirmed"
        )
        val batchCost = AyamTransaction(
            id = "EXP-SEP", date = "2026-09-30", ref = "EXP-HIST",
            type = "expense", category = "Biaya potong",
            description = "Biaya langsung DO", amount = 200000.0,
            paidAmount = 200000.0, batchRef = "DO-HIST", status = "confirmed"
        )
        val sale = AyamTransaction(
            id = "SALE-OCT", date = "2026-10-02", ref = "INV-OCT",
            type = "sale", description = "Penjualan dari DO sebelumnya",
            amount = 1600000.0, paidAmount = 1600000.0,
            qtyKg = 40.0, batchRef = "DO-HIST", status = "confirmed"
        )

        // The report filter only contains October sales, while historical purchase and batch costs
        // remain available to derive the correct HPP.
        val summary = AyamKasCalculator.calculate(listOf(purchase, batchCost, sale), listOf(sale))
        assertEquals(1600000.0, summary.revenue, 0.001)
        assertEquals(600000.0, summary.cogs, 0.001) // ((1,000,000 + 200,000) / 80) * 40
        assertEquals(1600000.0, summary.expenses, 0.001) // No unrelated operating expenses in this period.
        assertEquals(1000000.0, summary.profit, 0.001)
        assertEquals(40.0, summary.stockKg, 0.001)
    }

    @Test
    fun testMissingPaymentIsNotAssumedToBeCash() {
        val purchase = AyamTransaction(
            id = "PUR-CASH", date = "2026-10-01", ref = "DO-CASH",
            type = "purchase", description = "DO belum dibayar",
            amount = 500000.0, paidAmount = 0.0,
            qtyKg = 25.0, saleableKg = 25.0, status = "confirmed"
        )
        val sale = AyamTransaction(
            id = "SALE-CASH", date = "2026-10-01", ref = "INV-CASH",
            type = "sale", description = "Penjualan belum diterima",
            amount = 700000.0, paidAmount = 0.0,
            qtyKg = 10.0, batchRef = "DO-CASH", status = "confirmed"
        )
        val summary = AyamKasCalculator.calculate(listOf(purchase, sale))
        assertEquals(0.0, summary.cash, 0.001)
        assertEquals(700000.0, summary.revenue, 0.001)
    }

    @Test
    fun testDateFormatting() {
        val formatted = AyamKasFormatters.formatDate("2026-10-10")
        assertEquals("10/10/2026", formatted)
    }
}
