package com.example.data.model

object AyamKasCalculator {
    private data class DoBatchCost(
        val reference: String,
        val label: String,
        val purchaseKg: Double,
        val purchaseCost: Double,
        val soldKg: Double
    ) {
        val remainingKg: Double get() = purchaseKg - soldKg
        val costPerKg: Double get() = if (purchaseKg > 0) purchaseCost / purchaseKg else 0.0
    }


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

        // Build a weighted cost per DO across all confirmed purchases, including DOs
        // acquired in earlier months. Reused DO references are grouped to avoid charging
        // the same batch's sales against each purchase row more than once.
        val purchaseGroups = allPurchases.groupBy { p ->
            p.ref.trim().lowercase().ifBlank { "#${p.id.lowercase()}" }
        }
        val batchCosts = purchaseGroups.mapValues { (_, batchPurchases) ->
            val batchRef = batchPurchases.firstOrNull { it.ref.isNotBlank() }?.ref?.trim().orEmpty()
            val purchaseKg = batchPurchases.sumOf { (it.qtyKg ?: 0.0).coerceAtLeast(0.0) }
            val purchaseCost = batchPurchases.sumOf { it.amount.coerceAtLeast(0.0) }
            val soldKg = if (batchRef.isBlank()) 0.0 else allSales
                .filter { it.batchRef.trim().equals(batchRef, ignoreCase = true) }
                .sumOf { (it.qtyKg ?: 0.0).coerceAtLeast(0.0) }
            DoBatchCost(
                reference = batchRef,
                label = batchPurchases.firstOrNull()?.ref?.takeIf { it.isNotBlank() }
                    ?: "Tanpa ref (${batchPurchases.firstOrNull()?.id.orEmpty()})",
                purchaseKg = purchaseKg,
                purchaseCost = purchaseCost,
                soldKg = soldKg
            )
        }

        var cogs = 0.0
        var knownSales = 0
        var unknownSales = 0
        var stockKg = 0.0
        var stockValue = 0.0
        val negativeStockBatches = mutableListOf<String>()

        batchCosts.values.forEach { batch ->
            val remaining = batch.remainingKg
            stockKg += remaining
            stockValue += remaining.coerceAtLeast(0.0) * batch.costPerKg
            if (remaining < -0.0001) {
                negativeStockBatches.add(batch.reference.ifBlank { batch.label })
            }
        }

        for (s in revenueTx) {
            val br = s.batchRef.trim().lowercase()
            val batch = batchCosts[br]
            val saleQty = s.qtyKg ?: 0.0
            if (saleQty > 0 && br.isNotBlank() && batch != null &&
                batch.purchaseKg > 0 && batch.purchaseCost > 0
            ) {
                cogs += batch.costPerKg * saleQty
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
        val profitComplete = (unknownSales == 0 && negativeStockBatches.isEmpty())

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

        val purchaseGroups = purchases.groupBy { p ->
            p.ref.trim().lowercase().ifBlank { "#${p.id.lowercase()}" }
        }

        return purchaseGroups.values.map { batchPurchases ->
            val first = batchPurchases.first()
            val ref = batchPurchases.firstOrNull { it.ref.isNotBlank() }?.ref?.trim().orEmpty()
            val purchasedKg = batchPurchases.sumOf { (it.qtyKg ?: 0.0).coerceAtLeast(0.0) }
            val totalCost = batchPurchases.sumOf { it.amount.coerceAtLeast(0.0) }
            val linkedSales = if (ref.isBlank()) emptyList() else sales.filter {
                it.batchRef.trim().equals(ref, ignoreCase = true)
            }
            val soldKg = linkedSales.sumOf { (it.qtyKg ?: 0.0).coerceAtLeast(0.0) }
            val missingSalesCount = linkedSales.count { it.qtyKg == null || it.qtyKg <= 0.0 }
            val remainingKg = purchasedKg - soldKg
            val unitCost = if (purchasedKg > 0) totalCost / purchasedKg else 0.0
            val remainingValue = remainingKg.coerceAtLeast(0.0) * unitCost

            val status = when {
                purchasedKg <= 0 || totalCost <= 0 -> "HPP Belum Lengkap"
                remainingKg < -0.0001 -> "Stok Negatif"
                missingSalesCount > 0 -> "HPP Belum Lengkap"
                else -> "Terhitung"
            }

            DoStockItem(
                // Aggregate rows sharing one DO reference and calculate a weighted unit cost.
                purchaseTx = first.copy(ref = ref, qtyKg = purchasedKg, amount = totalCost),
                soldKg = soldKg,
                missingSalesCount = missingSalesCount,
                totalPurchasedKg = purchasedKg,
                remainingKg = remainingKg,
                totalCost = totalCost,
                unitCost = unitCost,
                remainingValue = remainingValue,
                status = status
            )
        }
    }
}
