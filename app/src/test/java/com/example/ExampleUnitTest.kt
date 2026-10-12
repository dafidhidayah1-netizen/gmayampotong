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
        // Historical rows are loaded as drafts until their actual source documents,
        // weights, payment amounts, and DO links have been checked by the owner.
        assertTrue(realData.all { it.isDraft })

        val summary = AyamKasCalculator.calculate(realData)
        assertEquals(0, summary.confirmedCount)
        assertEquals(33, summary.draftCount)
        assertEquals(0.0, summary.cash, 0.001)
        assertEquals(0.0, summary.revenue, 0.001)
        assertEquals(0.0, summary.cogs, 0.001)
        assertEquals(0.0, summary.profit, 0.001)
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
                weightLossKg = 0.0,
                mortalityRatePct = 0.0,
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
        assertTrue(summary.profitComplete)

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
    fun testReportUsesPurchaseFromEarlierPeriodForSaleHpp() {
        val transactions = listOf(
            AyamTransaction(
                id = "TX-SEP-DO",
                date = "2026-09-30",
                ref = "DO-SEP-001",
                type = "purchase",
                description = "DO ayam September",
                amount = 2000000.0,
                paidAmount = 2000000.0,
                qtyKg = 100.0,
                status = "confirmed"
            ),
            AyamTransaction(
                id = "TX-OCT-SALE",
                date = "2026-10-02",
                ref = "SALE-OCT-001",
                type = "sale",
                description = "Penjualan Oktober dari DO September",
                amount = 1200000.0,
                paidAmount = 1200000.0,
                qtyKg = 40.0,
                batchRef = "DO-SEP-001",
                status = "confirmed"
            ),
            AyamTransaction(
                id = "TX-OCT-EXP",
                date = "2026-10-02",
                ref = "EXP-OCT-001",
                type = "expense",
                description = "Biaya operasional Oktober",
                amount = 100000.0,
                paidAmount = 100000.0,
                status = "confirmed"
            )
        )
        val october = transactions.filter { it.isConfirmed && it.date.startsWith("2026-10") }
        val summary = AyamKasCalculator.calculate(transactions, october)

        assertEquals(1200000.0, summary.revenue, 0.001)
        assertEquals(800000.0, summary.cogs, 0.001)
        assertEquals(100000.0, summary.expenses, 0.001)
        assertEquals(300000.0, summary.profit, 0.001)
        assertTrue(summary.profitComplete)
        assertEquals(60.0, summary.stockKg, 0.001)
    }

    @Test
    fun testActualBatchHppSubtractsLossAndMortalityAndIncludesFeedAndProcessingCost() {
        val transactions = listOf(
            AyamTransaction(
                id = "BATCH-HPP",
                date = "2026-10-13",
                ref = "DO-HPP-1",
                type = "purchase",
                description = "DO untuk uji rumus HPP",
                amount = 2000000.0,
                paidAmount = 2000000.0,
                qtyKg = 100.0,
                weightLossKg = 5.0,
                mortalityRatePct = 2.0,
                fcr = 2.0,
                weightGainKg = 10.0,
                feedPricePerKg = 5000.0,
                processingCost = 50000.0,
                status = "confirmed"
            ),
            AyamTransaction(
                id = "BATCH-SALE",
                date = "2026-10-13",
                ref = "SALE-HPP-1",
                type = "sale",
                description = "Penjualan batch uji",
                amount = 500000.0,
                paidAmount = 500000.0,
                qtyKg = 10.0,
                batchRef = "DO-HPP-1",
                status = "confirmed"
            )
        )
        val summary = AyamKasCalculator.calculate(transactions)

        // Saleable kg = 100 - 5 kg weight loss - (100 * 2%) mortality = 93 kg.
        // Batch cost = Rp2,000,000 + (FCR 2 * gain 10 kg * feed Rp5,000) + processing Rp50,000.
        val expectedUnitCost = 2150000.0 / 93.0
        assertEquals(83.0, summary.stockKg, 0.001)
        assertEquals(expectedUnitCost * 10.0, summary.cogs, 0.001)
        assertEquals(500000.0 - expectedUnitCost * 10.0, summary.grossProfit, 0.001)
        assertTrue(summary.profitComplete)
    }

    @Test
    fun testDateFormatting() {
        val formatted = AyamKasFormatters.formatDate("2026-10-10")
        assertEquals("10/10/2026", formatted)
    }
}
