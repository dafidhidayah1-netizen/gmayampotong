package com.example.data.model

import java.time.Duration
import java.time.LocalTime

data class PayrollAmounts(
    val basePay: Double,
    val overtimePay: Double,
    val allowance: Double,
    val deductions: Double,
    val totalPay: Double
)

data class LoanBalance(
    val loanReference: String,
    val loanType: String,
    val principal: Double,
    val settled: Double,
    val outstanding: Double
)

/**
 * Pure business calculations shared by Room workflows and Compose summaries.
 * Only confirmed loan events affect outstanding balances.
 */
object BusinessLedgerCalculator {
    fun workedHours(clockIn: String, clockOut: String, breakMinutes: Int): Double {
        require(breakMinutes >= 0) { "Durasi istirahat tidak boleh negatif" }
        val start = LocalTime.parse(clockIn)
        val end = LocalTime.parse(clockOut)
        var minutes = Duration.between(start, end).toMinutes()
        if (minutes < 0) minutes += 24 * 60
        minutes = (minutes - breakMinutes).coerceAtLeast(0)
        return minutes / 60.0
    }

    fun overtimeHours(clockIn: String, clockOut: String, breakMinutes: Int, standardHours: Double): Double {
        require(standardHours > 0.0) { "Jam kerja standar harus lebih dari nol" }
        return (workedHours(clockIn, clockOut, breakMinutes) - standardHours).coerceAtLeast(0.0)
    }

    fun calculatePayroll(
        daysPresent: Double,
        dailyRate: Double,
        overtimeHours: Double,
        overtimeRatePerHour: Double,
        allowance: Double,
        deductions: Double
    ): PayrollAmounts {
        require(daysPresent >= 0.0 && dailyRate >= 0.0) { "Hari hadir dan tarif harian tidak boleh negatif" }
        require(overtimeHours >= 0.0 && overtimeRatePerHour >= 0.0) { "Jam dan tarif lembur tidak boleh negatif" }
        require(allowance >= 0.0 && deductions >= 0.0) { "Tunjangan dan potongan tidak boleh negatif" }
        val base = daysPresent * dailyRate
        val overtime = overtimeHours * overtimeRatePerHour
        // Keep a negative draft visible so an excessive deduction cannot be silently hidden.
        val total = base + overtime + allowance - deductions
        return PayrollAmounts(base, overtime, allowance, deductions, total)
    }

    fun loanBalance(records: List<ExternalLoan>, loanReference: String, loanType: String): LoanBalance {
        val ref = loanReference.trim()
        require(ref.isNotBlank()) { "Referensi pinjaman wajib diisi" }
        val sourceDirection: String
        val settleDirection: String
        when (loanType) {
            "OWNER_LOAN", "BUSINESS_BORROWING" -> {
                sourceDirection = "RECEIVED"
                settleDirection = "REPAYMENT"
            }
            "BUSINESS_LENDING" -> {
                sourceDirection = "GIVEN"
                settleDirection = "COLLECTION"
            }
            else -> return LoanBalance(ref, loanType, 0.0, 0.0, 0.0)
        }
        val linked = records.filter {
            it.status == "confirmed" &&
                it.loanReference.trim().equals(ref, ignoreCase = true) &&
                it.loanType == loanType
        }
        val principal = linked.filter { it.direction == sourceDirection }.sumOf { it.amount.coerceAtLeast(0.0) }
        val settled = linked.filter { it.direction == settleDirection }.sumOf { it.amount.coerceAtLeast(0.0) }
        return LoanBalance(ref, loanType, principal, settled, (principal - settled).coerceAtLeast(0.0))
    }
}
