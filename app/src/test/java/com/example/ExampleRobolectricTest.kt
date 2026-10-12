package com.example

import com.example.data.model.AyamKasCalculator
import com.example.data.model.AyamTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Plain JVM smoke test; does not require Robolectric or Android SDK runtime emulation. */
class ExampleRobolectricTest {
    @Test
    fun confirmedSaleWithoutLinkedBatchIsNotReportedAsCompleteProfit() {
        val sale = AyamTransaction(
            id = "TEST-SALE",
            date = "2026-10-13",
            ref = "PJ-TEST",
            type = "sale",
            description = "Penjualan tanpa tautan DO",
            amount = 500000.0,
            paidAmount = 500000.0,
            qtyKg = 10.0,
            batchRef = "",
            status = "confirmed"
        )

        val summary = AyamKasCalculator.calculate(listOf(sale))

        assertEquals(1, summary.unknownSales)
        assertFalse(summary.profitComplete)
    }
}
