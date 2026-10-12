package com.example.data.model

object AyamKasCalculator {
    private data class DoBatchCost(
        val reference: String,
        val label: String,
        val purchaseKg: Double,
        val purchaseCost: Double,
        val weightLossKg: Double,
        val mortalityKg: Double,
        val processingCost: Double,
        val feedCost: Double,
        val soldKg: Double,
        val productionDataComplete: Boolean
    ) {
        val saleableKg: Double get() = (purchaseKg - weightLossKg - mortalityKg).coerceAtLeast(0.0)
        val totalCost: Double get() = purchaseCost + processingCost + feedCost
        val remainingKg: Double get() = saleableKg - soldKg
        val costPerKg: Double get() = if (saleableKg > 0) totalCost / saleableKg else 0.0
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
            val weightLossKg = batchPurchases.sumOf { (it.weightLossKg ?: 0.0).coerceAtLeast(0.0) }
            val mortalityKg = batchPurchases.sumOf { p ->
                val rate = (p.mortalityRatePct ?: 0.0).coerceIn(0.0, 100.0)
                (p.qtyKg ?: 0.0).coerceAtLeast(0.0) * rate / 100.0
            }
            val processingCost = batchPurchases.sumOf { it.processingCost.coerceAtLeast(0.0) }
            val feedCost = batchPurchases.sumOf { p ->
                val fcr = p.fcr
                val gain = p.weightGainKg
                val feedPrice = p.feedPricePerKg
                if (fcr != null && gain != null && feedPrice != null &&
                    fcr >= 0.0 && gain >= 0.0 && feedPrice >= 0.0
                ) fcr * gain * feedPrice else 0.0
            }
            val productionDataComplete = batchPurchases.all { p ->
                p.weightLossKg != null && p.mortalityRatePct != null &&
                    (p.fcr == null && p.weightGainKg == null && p.feedPricePerKg == null ||
                     p.fcr != null && p.weightGainKg != null && p.feedPricePerKg != null &&
                     p.fcr >= 0.0 && p.weightGainKg >= 0.0 && p.feedPricePerKg >= 0.0)
            }
            val soldKg = if (batchRef.isBlank()) 0.0 else allSales
                .filter { it.batchRef.trim().equals(batchRef, ignoreCase = true) }
                .sumOf { (it.qtyKg ?: 0.0).coerceAtLeast(0.0) }
            DoBatchCost(
                reference = batchRef,
                label = batchPurchases.firstOrNull()?.ref?.takeIf { it.isNotBlank() }
                    ?: "Tanpa ref (${batchPurchases.firstOrNull()?.id.orEmpty()})",
                purchaseKg = purchaseKg,
                purchaseCost = purchaseCost,
                weightLossKg = weightLossKg,
                mortalityKg = mortalityKg,
                processingCost = processingCost,
                feedCost = feedCost,
                soldKg = soldKg,
                productionDataComplete = productionDataComplete
            )
        }
        var cogs = 0.0
        var knownSales = 0
        var unknownSales = 0
        var stockKg = 0.0
        var stockValue = 0.0
        var feedCost = 0.0
        var batchDataIncomplete = 0
        val negativeStockBatches = mutableListOf<String>()

        batchCosts.values.forEach { batch ->
            val remaining = batch.remainingKg
            stockKg += remaining
            stockValue += remaining.coerceAtLeast(0.0) * batch.costPerKg
            feedCost += batch.feedCost
            if (!batch.productionDataComplete || batch.saleableKg <= 0.0 || batch.totalCost <= 0.0) {
                batchDataIncomplete++
            }
            if (remaining < -0.0001) {
                negativeStockBatches.add(batch.reference.ifBlank { batch.label })
            }
        }

        for (sale in revenueTx) {
            val batchKey = sale.batchRef.trim().lowercase()
            val batch = batchCosts[batchKey]
            val saleQty = sale.qtyKg ?: 0.0
            if (saleQty > 0.0 && batchKey.isNotBlank() && batch != null &&
                batch.saleableKg > 0.0 && batch.totalCost > 0.0
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
        val grossProfit = revenue - cogs
        val profit = grossProfit - expenses
        val grossMarginPercent = if (revenue > 0.0) grossProfit / revenue * 100.0 else 0.0
        val profitComplete = (unknownSales == 0 && negativeStockBatches.isEmpty() && batchDataIncomplete == 0)

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
            paymentMissingCount = paymentMissingCount,
            salesCash = revenueTx.filter { it.paymentMethod.equals("CASH", true) }.sumOf { it.paidAmount ?: 0.0 },
            salesTransfer = revenueTx.filter { it.paymentMethod.equals("TRANSFER", true) }.sumOf { it.paidAmount ?: 0.0 },
            salesQris = revenueTx.filter { it.paymentMethod.equals("QRIS", true) }.sumOf { it.paidAmount ?: 0.0 },
            grossProfit = grossProfit,
            grossMarginPercent = grossMarginPercent,
            batchDataIncomplete = batchDataIncomplete,
            feedCost = feedCost
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
            val baseCost = batchPurchases.sumOf { it.amount.coerceAtLeast(0.0) }
            val weightLossKg = batchPurchases.sumOf { (it.weightLossKg ?: 0.0).coerceAtLeast(0.0) }
            val mortalityKg = batchPurchases.sumOf { p ->
                (p.qtyKg ?: 0.0).coerceAtLeast(0.0) *
                    (p.mortalityRatePct ?: 0.0).coerceIn(0.0, 100.0) / 100.0
            }
            val processCost = batchPurchases.sumOf { it.processingCost.coerceAtLeast(0.0) }
            val feedCost = batchPurchases.sumOf { p ->
                if (p.fcr != null && p.weightGainKg != null && p.feedPricePerKg != null)
                    p.fcr.coerceAtLeast(0.0) * p.weightGainKg.coerceAtLeast(0.0) * p.feedPricePerKg.coerceAtLeast(0.0)
                else 0.0
            }
            val totalCost = baseCost + processCost + feedCost
            val saleableKg = (purchasedKg - weightLossKg - mortalityKg).coerceAtLeast(0.0)
            val linkedSales = if (ref.isBlank()) emptyList() else sales.filter {
                it.batchRef.trim().equals(ref, ignoreCase = true)
            }
            val soldKg = linkedSales.sumOf { (it.qtyKg ?: 0.0).coerceAtLeast(0.0) }
            val missingSalesCount = linkedSales.count { it.qtyKg == null || it.qtyKg <= 0.0 }
            val remainingKg = saleableKg - soldKg
            val unitCost = if (saleableKg > 0) totalCost / saleableKg else 0.0
            val remainingValue = remainingKg.coerceAtLeast(0.0) * unitCost
            val productionComplete = batchPurchases.all { p ->
                p.weightLossKg != null && p.mortalityRatePct != null &&
                    (p.fcr == null && p.weightGainKg == null && p.feedPricePerKg == null ||
                     p.fcr != null && p.weightGainKg != null && p.feedPricePerKg != null)
            }

            val status = when {
                saleableKg <= 0.0 || totalCost <= 0.0 -> "HPP Belum Lengkap"
                remainingKg < -0.0001 -> "Stok Negatif"
                !productionComplete || missingSalesCount > 0 -> "HPP Belum Lengkap"
                else -> "Terhitung"
            }

            DoStockItem(
                purchaseTx = first.copy(ref = ref, qtyKg = purchasedKg, amount = baseCost),
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
