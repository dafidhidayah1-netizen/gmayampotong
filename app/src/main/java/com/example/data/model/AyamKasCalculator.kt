package com.example.data.model

object AyamKasCalculator {

    fun calculate(
        allTransactions: List<AyamTransaction>,
        filteredConfirmed: List<AyamTransaction> = allTransactions.filter { it.isConfirmed }
    ): AyamKasSummary {
        val confirmedList = filteredConfirmed
        val allConfirmed = allTransactions.filter { it.isConfirmed }

        val capital = confirmedList.filter { it.type == "capital_in" }.sumOf { it.amount }
        val revenueTx = confirmedList.filter { it.type == "sale" }
        val revenue = revenueTx.sumOf { it.amount }

        val purchases = confirmedList.filter { it.type == "purchase" }
        val allPurchases = allConfirmed.filter { it.type == "purchase" }
        val allSales = allConfirmed.filter { it.type == "sale" }
        val purchaseTotal = purchases.sumOf { it.amount }

        val expensesTx = confirmedList.filter { it.type == "expense" }
        val expenses = expensesTx.sumOf { it.amount }

        // Sales for this period may draw stock from DOs purchased in a previous period.
        val knownPurchases = allPurchases
            .filter { it.ref.isNotBlank() }
            .associateBy { it.ref.trim().lowercase() }

        var cogs = 0.0
        var knownSales = 0
        var unknownSales = 0
        var stockKg = 0.0
        var stockValue = 0.0
        val negativeStockBatches = mutableListOf<String>()

        for (p in allPurchases) {
            val pRefKey = p.ref.trim().lowercase()
            val sold = allSales
                .filter { it.batchRef.trim().lowercase() == pRefKey }
                .sumOf { (it.qtyKg ?: 0.0).coerceAtLeast(0.0) }
            val kg = p.qtyKg ?: 0.0
            val cost = p.amount
            val remaining = kg - sold
            stockKg += remaining
            val unitCost = if (kg > 0) cost / kg else 0.0
            stockValue += remaining.coerceAtLeast(0.0) * unitCost
            if (remaining < -0.0001) {
                negativeStockBatches.add(p.ref.ifBlank { "Tanpa ref" })
            }
        }

        for (s in revenueTx) {
            val br = s.batchRef.trim().lowercase()
            val p = knownPurchases[br]
            val saleQty = s.qtyKg ?: 0.0
            if (saleQty > 0 && p != null && (p.qtyKg ?: 0.0) > 0) {
                val unitCost = p.amount / (p.qtyKg ?: 1.0)
                cogs += unitCost * saleQty
                knownSales++
            } else {
                unknownSales++
            }
        }

        val paidSales = revenueTx.sumOf { it.paidAmount ?: 0.0 }
        val paidPurchases = purchases.sumOf { it.paidAmount ?: 0.0 }
        val expensesPaid = expensesTx.sumOf { it.paidAmount ?: 0.0 }

        val customerPayments = confirmedList.filter { it.type == "receivable_payment" }.sumOf { it.amount }
        val supplierPayments = confirmedList.filter { it.type == "supplier_payment" }.sumOf { it.amount }
        val capitalOut = confirmedList.filter { it.type == "capital_out" }.sumOf { it.amount }

        // Kas = modal + kas penjualan diterima + terima piutang - bayar DO - biaya dibayar - prive - bayar utang DO
        val cash = capital + paidSales + customerPayments - paidPurchases - expensesPaid - capitalOut - supplierPayments

        // Piutang = omzet penjualan - penjualan dibayar tunai - pembayaran piutang berikutnya
        val ar = (revenue - paidSales - customerPayments).coerceAtLeast(0.0)

        // Utang DO = pembelian DO - pembelian dibayar tunai - pembayaran utang DO berikutnya
        val ap = (purchaseTotal - paidPurchases - supplierPayments).coerceAtLeast(0.0)

        val profitBeforeCogs = revenue - expenses
        val profit = profitBeforeCogs - cogs
        val profitComplete = (unknownSales == 0)

        val confirmedCount = confirmedList.size
        val draftCount = allTransactions.count { it.isDraft }
        val totalCount = allTransactions.size

        val qtyMissingCount = confirmedList.count { t ->
            t.type == "sale" && (
                (t.qtyKg == null || t.qtyKg <= 0.0) ||
                t.batchRef.isBlank() ||
                !allPurchases.any { it.ref.trim().equals(t.batchRef.trim(), ignoreCase = true) }
            )
        }

        val paymentMissingCount = allTransactions.count { it.isDraft && it.paidAmount == null }

        return AyamKasSummary(
            capital = capital,
            revenue = revenue,
            purchaseTotal = purchaseTotal,
            expenses = expenses,
            cogs = cogs,
            knownSales = knownSales,
            unknownSales = unknownSales,
            profit = profit,
            profitBeforeCogs = profitBeforeCogs,
            profitComplete = profitComplete,
            stockKg = stockKg,
            stockValue = stockValue,
            negativeStockBatches = negativeStockBatches,
            cash = cash,
            ar = ar,
            ap = ap,
            totalCount = totalCount,
            confirmedCount = confirmedCount,
            draftCount = draftCount,
            salesCount = revenueTx.size,
            purchaseCount = purchases.size,
            expenseCount = expensesTx.size,
            paidSales = paidSales,
            paidPurchases = paidPurchases,
            qtyMissingCount = qtyMissingCount,
            paymentMissingCount = paymentMissingCount
        )
    }

    fun calculateStockRows(transactions: List<AyamTransaction>): List<DoStockItem> {
        val confirmed = transactions.filter { it.isConfirmed }
        val purchases = confirmed.filter { it.type == "purchase" }
        val sales = confirmed.filter { it.type == "sale" }

        return purchases.map { p ->
            val pRefKey = p.ref.trim().lowercase()
            val linkedSales = sales.filter { it.batchRef.trim().lowercase() == pRefKey }
            val soldKg = linkedSales.sumOf { (it.qtyKg ?: 0.0).coerceAtLeast(0.0) }
            val missingSalesCount = linkedSales.count { it.qtyKg == null || it.qtyKg <= 0.0 }
            val totalPurchasedKg = p.qtyKg ?: 0.0
            val remainingKg = totalPurchasedKg - soldKg
            val totalCost = p.amount
            val unitCost = if (totalPurchasedKg > 0) totalCost / totalPurchasedKg else 0.0
            val remainingValue = (remainingKg * unitCost).coerceAtLeast(0.0)

            val status = when {
                remainingKg < -0.0001 -> "Stok Negatif"
                missingSalesCount > 0 -> "HPP Belum Lengkap"
                else -> "Terhitung"
            }

            DoStockItem(
                purchaseTx = p,
                soldKg = soldKg,
                missingSalesCount = missingSalesCount,
                totalPurchasedKg = totalPurchasedKg,
                remainingKg = remainingKg,
                totalCost = totalCost,
                unitCost = unitCost,
                remainingValue = remainingValue,
                status = status
            )
        }
    }
}
