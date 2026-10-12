package com.example.data.model

object AyamKasCalculator {

    private fun refKey(value: String): String = value.trim().lowercase()

    fun calculate(
        allTransactions: List<AyamTransaction>,
        filteredConfirmed: List<AyamTransaction> = allTransactions.filter { it.isConfirmed }
    ): AyamKasSummary {
        val confirmed = filteredConfirmed.filter { it.isConfirmed }
        val allConfirmed = allTransactions.filter { it.isConfirmed }

        // Transactions within the selected report period.
        val sales = confirmed.filter { it.type == "sale" }
        val purchases = confirmed.filter { it.type == "purchase" }
        val periodExpenses = confirmed.filter { it.type == "expense" }

        // Full ledger is required for historical DO links, stock on hand and current balances.
        val allSales = allConfirmed.filter { it.type == "sale" }
        val allPurchases = allConfirmed.filter { it.type == "purchase" }
        val allExpenses = allConfirmed.filter { it.type == "expense" }
        val purchaseGroups = allPurchases
            .filter { it.ref.isNotBlank() }
            .groupBy { refKey(it.ref) }
        val uniquePurchases = purchaseGroups
            .filterValues { it.size == 1 }
            .mapValues { it.value.single() }
        val duplicateBatchCount = purchaseGroups.values.count { it.size > 1 }

        fun saleableWeight(p: AyamTransaction): Double =
            p.saleableKg ?: p.qtyKg ?: 0.0

        fun directBatchExpenses(p: AyamTransaction): Double {
            val key = refKey(p.ref)
            if (key.isBlank() || uniquePurchases[key]?.id != p.id) return 0.0
            return allExpenses
                .filter { refKey(it.batchRef) == key }
                .sumOf { it.amount }
        }

        fun totalBatchCost(p: AyamTransaction): Double =
            p.amount + directBatchExpenses(p)

        val revenue = sales.sumOf { it.amount }
        val purchaseTotal = purchases.sumOf { it.amount }

        // Batch-linked costs are capitalized in the batch HPP and must not be deducted twice as overhead.
        val expenses = periodExpenses
            .filter { expense ->
                expense.batchRef.isBlank() || purchaseGroups[refKey(expense.batchRef)]?.size != 1
            }
            .sumOf { it.amount }

        var cogs = 0.0
        var knownSales = 0
        var unknownSales = 0
        var unlinkedBatchExpenseCount = allExpenses.count { expense ->
            expense.batchRef.isNotBlank() && purchaseGroups[refKey(expense.batchRef)]?.size != 1
        }

        for (sale in sales) {
            val quantity = sale.qtyKg ?: 0.0
            val key = refKey(sale.batchRef)
            val purchase = uniquePurchases[key]
            val outputKg = purchase?.let(::saleableWeight) ?: 0.0
            if (quantity > 0.0 && purchase != null && outputKg > 0.0) {
                cogs += totalBatchCost(purchase) / outputKg * quantity
                knownSales++
            } else {
                unknownSales++
            }
        }

        var stockKg = 0.0
        var stockValue = 0.0
        val negativeStockBatches = mutableListOf<String>()
        val outputWeightMissingCount = allPurchases.count {
            it.saleableKg == null || (it.saleableKg ?: 0.0) <= 0.0
        }

        for (purchase in allPurchases) {
            val key = refKey(purchase.ref)
            val linkedSales = if (key.isBlank()) emptyList() else allSales.filter {
                refKey(it.batchRef) == key
            }
            val soldKg = linkedSales.sumOf { (it.qtyKg ?: 0.0).coerceAtLeast(0.0) }
            val availableKg = saleableWeight(purchase)
            val remaining = availableKg - soldKg
            val unitCost = if (availableKg > 0.0) totalBatchCost(purchase) / availableKg else 0.0
            stockKg += remaining
            stockValue += remaining.coerceAtLeast(0.0) * unitCost
            if (remaining < -0.0001) {
                negativeStockBatches.add(purchase.ref.ifBlank { purchase.id })
            }
        }

        val paidSales = sales.sumOf { it.paidAmount ?: 0.0 }
        val paidPurchases = purchases.sumOf { it.paidAmount ?: 0.0 }
        val expensesPaid = periodExpenses.sumOf { it.paidAmount ?: 0.0 }
        val customerPayments = confirmed
            .filter { it.type == "receivable_payment" }
            .sumOf { it.amount }
        val supplierPayments = confirmed
            .filter { it.type == "supplier_payment" }
            .sumOf { it.amount }
        val capital = confirmed
            .filter { it.type == "capital_in" }
            .sumOf { it.amount }
        val capitalOut = confirmed
            .filter { it.type == "capital_out" }
            .sumOf { it.amount }

        // Cash movement uses actual payments, never the full invoice amount by default.
        val cash = capital + paidSales + customerPayments -
            paidPurchases - expensesPaid - capitalOut - supplierPayments

        // Outstanding balance is a whole-ledger estimate. Invoice-level links are needed for reliable aging.
        val allRevenue = allSales.sumOf { it.amount }
        val allPaidSales = allSales.sumOf { it.paidAmount ?: 0.0 }
        val allCustomerPayments = allConfirmed
            .filter { it.type == "receivable_payment" }
            .sumOf { it.amount }
        val allPurchaseCost = allPurchases.sumOf { it.amount }
        val allPaidPurchases = allPurchases.sumOf { it.paidAmount ?: 0.0 }
        val allSupplierPayments = allConfirmed
            .filter { it.type == "supplier_payment" }
            .sumOf { it.amount }
        val ar = (allRevenue - allPaidSales - allCustomerPayments).coerceAtLeast(0.0)
        val ap = (allPurchaseCost - allPaidPurchases - allSupplierPayments).coerceAtLeast(0.0)

        val profitBeforeCogs = revenue - expenses
        val profit = profitBeforeCogs - cogs
        val qtyMissingCount = sales.count { sale ->
            val key = refKey(sale.batchRef)
            (sale.qtyKg ?: 0.0) <= 0.0 || uniquePurchases[key] == null ||
                saleableWeight(uniquePurchases[key] ?: sale) <= 0.0
        }
        val paymentMissingCount = allTransactions.count {
            it.isDraft && it.paidAmount == null
        }
        val profitComplete = unknownSales == 0 &&
            unlinkedBatchExpenseCount == 0 &&
            duplicateBatchCount == 0 &&
            outputWeightMissingCount == 0 &&
            negativeStockBatches.isEmpty()

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
            totalCount = allTransactions.size,
            confirmedCount = confirmed.size,
            draftCount = allTransactions.count { it.isDraft },
            salesCount = sales.size,
            purchaseCount = purchases.size,
            expenseCount = periodExpenses.size,
            paidSales = paidSales,
            paidPurchases = paidPurchases,
            qtyMissingCount = qtyMissingCount,
            paymentMissingCount = paymentMissingCount,
            unlinkedBatchExpenseCount = unlinkedBatchExpenseCount,
            duplicateBatchCount = duplicateBatchCount,
            outputWeightMissingCount = outputWeightMissingCount
        )
    }

    fun calculateStockRows(transactions: List<AyamTransaction>): List<DoStockItem> {
        val confirmed = transactions.filter { it.isConfirmed }
        val purchases = confirmed.filter { it.type == "purchase" }
        val sales = confirmed.filter { it.type == "sale" }
        val expenses = confirmed.filter { it.type == "expense" }
        val purchaseGroups = purchases.filter { it.ref.isNotBlank() }
            .groupBy { refKey(it.ref) }

        return purchases.map { purchase ->
            val key = refKey(purchase.ref)
            val linkedSales = if (key.isBlank()) emptyList() else sales.filter {
                refKey(it.batchRef) == key
            }
            val soldKg = linkedSales.sumOf { (it.qtyKg ?: 0.0).coerceAtLeast(0.0) }
            val missingSalesCount = linkedSales.count { (it.qtyKg ?: 0.0) <= 0.0 }
            val totalPurchasedKg = purchase.qtyKg ?: 0.0
            val availableKg = purchase.saleableKg ?: purchase.qtyKg ?: 0.0
            val remainingKg = availableKg - soldKg
            val hasUniqueRef = key.isNotBlank() && purchaseGroups[key]?.size == 1
            val batchExpenses = if (hasUniqueRef) {
                expenses.filter { refKey(it.batchRef) == key }.sumOf { it.amount }
            } else 0.0
            val totalCost = purchase.amount + batchExpenses
            val unitCost = if (availableKg > 0.0) totalCost / availableKg else 0.0
            val remainingValue = remainingKg.coerceAtLeast(0.0) * unitCost

            val status = when {
                key.isBlank() -> "Referensi DO kosong"
                purchaseGroups[key]?.size != 1 -> "Referensi DO ganda"
                remainingKg < -0.0001 -> "Stok Negatif"
                missingSalesCount > 0 -> "HPP Belum Lengkap"
                purchase.saleableKg == null -> "Asumsi kg masuk"
                availableKg <= 0.0 -> "Kg siap jual belum valid"
                else -> "Terhitung"
            }

            DoStockItem(
                purchaseTx = purchase,
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
