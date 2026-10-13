package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One mirrored cash movement for each confirmed ledger transaction with an actual
 * payment amount. This is a cash-control ledger, not a second expense ledger.
 */
@Entity(
    tableName = "cash_transactions",
    indices = [Index(value = ["sourceTransactionId"], unique = true)]
)
data class CashTransaction(
    @PrimaryKey val id: String,
    val sourceTransactionId: String?,
    val date: String,
    val accountName: String,
    val direction: String, // IN or OUT
    val amount: Double,
    val paymentMethod: String, // CASH, TRANSFER, QRIS
    val category: String,
    val description: String,
    val notes: String,
    val status: String, // confirmed or draft, mirrors source
    val updatedAt: String,
    val deleted: Boolean
)

@Entity(tableName = "cash_flows")
data class CashFlow(
    @PrimaryKey val id: String,
    val date: String,
    val fromAccount: String,
    val toAccount: String,
    val amount: Double,
    val reference: String,
    val notes: String,
    val status: String,
    val updatedAt: String
)

@Entity(tableName = "external_loans")
data class ExternalLoan(
    @PrimaryKey val id: String,
    val date: String,
    val party: String,
    @androidx.room.ColumnInfo(defaultValue = "''") val loanReference: String,
    val loanType: String, // OWNER_LOAN, BUSINESS_BORROWING, BUSINESS_LENDING, OWNER_PERSONAL, OWNER_INJECTION, UNEXPECTED_COST
    val direction: String, // RECEIVED, REPAYMENT, GIVEN, COLLECTION
    val amount: Double,
    val paidAmount: Double,
    val dueDate: String,
    val cashAccount: String,
    val notes: String,
    val status: String,
    val updatedAt: String
)

@Entity(tableName = "employees")
data class EmployeeRecord(
    @PrimaryKey val id: String,
    val name: String,
    val position: String,
    val dailyRate: Double,
    @androidx.room.ColumnInfo(defaultValue = "8.0") val standardHoursPerShift: Double,
    @androidx.room.ColumnInfo(defaultValue = "0.0") val overtimeRatePerHour: Double,
    val active: Boolean,
    val updatedAt: String
)

@Entity(
    tableName = "attendance_records",
    indices = [Index(value = ["employeeId", "workDate"], unique = true)]
)
data class AttendanceRecord(
    @PrimaryKey val id: String,
    val employeeId: String,
    val employeeName: String,
    val workDate: String,
    val shiftName: String,
    val clockIn: String,
    val clockOut: String,
    val breakMinutes: Int,
    val overtimeHours: Double,
    val status: String, // PRESENT, LEAVE, SICK, ABSENT, DAY_OFF
    val notes: String,
    val updatedAt: String
)

@Entity(
    tableName = "payroll_records",
    indices = [Index(value = ["employeeId", "period"], unique = true)]
)
data class PayrollRecord(
    @PrimaryKey val id: String,
    val employeeId: String,
    val employeeName: String,
    val period: String, // yyyy-MM
    val daysPresent: Double,
    val dailyRate: Double,
    val basePay: Double,
    val overtimeHours: Double,
    val overtimePay: Double,
    val allowance: Double,
    val deductions: Double,
    val totalPay: Double,
    val paidAmount: Double,
    val paymentMethod: String,
    val cashAccount: String,
    val status: String, // DRAFT, APPROVED, PART_PAID, PAID
    val notes: String,
    val updatedAt: String
)


/**
 * Append-only local audit record. It intentionally survives deletion of the source
 * transaction so edits, verification changes and deletions remain reviewable.
 */
@Entity(tableName = "transaction_audit_log", indices = [Index(value = ["entityId"]), Index(value = ["occurredAt"])])
data class TransactionAuditLog(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val occurredAt: String,
    val actor: String,
    val summary: String,
    val beforeData: String,
    val afterData: String
)
