package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.AttendanceRecord
import com.example.data.model.CashFlow
import com.example.data.model.CashTransaction
import com.example.data.model.EmployeeRecord
import com.example.data.model.ExternalLoan
import com.example.data.model.PayrollRecord
import com.example.data.model.TransactionAuditLog
import kotlinx.coroutines.flow.Flow

@Dao
interface BusinessLedgerDao {
    @Query("SELECT * FROM transaction_audit_log ORDER BY occurredAt DESC, id DESC LIMIT 1000")
    fun observeAuditLog(): Flow<List<TransactionAuditLog>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun appendAuditLog(entry: TransactionAuditLog)

    @Query("SELECT * FROM cash_transactions WHERE deleted = 0 ORDER BY date DESC, id DESC")
    fun observeCashTransactions(): Flow<List<CashTransaction>>

    @Query("SELECT * FROM cash_transactions WHERE deleted = 0 AND status = 'confirmed' ORDER BY date DESC, id DESC")
    fun observeConfirmedCashTransactions(): Flow<List<CashTransaction>>

    @Query("SELECT * FROM cash_transactions WHERE sourceTransactionId = :sourceId LIMIT 1")
    suspend fun findCashBySource(sourceId: String): CashTransaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCashTransaction(transaction: CashTransaction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCashTransactions(transactions: List<CashTransaction>)

    @Query("DELETE FROM cash_transactions")
    suspend fun clearAllCashTransactions()

    @Query("DELETE FROM cash_transactions WHERE sourceTransactionId = :sourceId")
    suspend fun deleteCashForSource(sourceId: String)

    @Query("DELETE FROM cash_transactions WHERE sourceTransactionId IS NOT NULL AND sourceTransactionId NOT LIKE 'LOAN-%'")
    suspend fun clearMirroredCashTransactions()

    @Query("SELECT * FROM cash_flows ORDER BY date DESC, id DESC")
    fun observeCashFlows(): Flow<List<CashFlow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCashFlow(cashFlow: CashFlow)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCashFlows(cashFlows: List<CashFlow>)

    @Query("DELETE FROM cash_flows")
    suspend fun clearAllCashFlows()

    @Query("DELETE FROM cash_flows WHERE id = :id")
    suspend fun deleteCashFlow(id: String)

    @Query("SELECT * FROM external_loans ORDER BY date DESC, id DESC")
    fun observeExternalLoans(): Flow<List<ExternalLoan>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveExternalLoan(loan: ExternalLoan)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveExternalLoans(loans: List<ExternalLoan>)

    @Query("DELETE FROM external_loans")
    suspend fun clearAllExternalLoans()

    @Query("DELETE FROM external_loans WHERE id = :id")
    suspend fun deleteExternalLoan(id: String)

    @Query("SELECT * FROM employees ORDER BY active DESC, name COLLATE NOCASE")
    fun observeEmployees(): Flow<List<EmployeeRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEmployee(employee: EmployeeRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEmployees(employees: List<EmployeeRecord>)

    @Query("DELETE FROM employees")
    suspend fun clearAllEmployees()

    @Query("SELECT * FROM employees WHERE id = :id LIMIT 1")
    suspend fun getEmployee(id: String): EmployeeRecord?

    @Query("SELECT * FROM attendance_records ORDER BY workDate DESC, employeeName COLLATE NOCASE")
    fun observeAttendance(): Flow<List<AttendanceRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAttendance(attendance: AttendanceRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAttendances(records: List<AttendanceRecord>)

    @Query("DELETE FROM attendance_records")
    suspend fun clearAllAttendance()

    @Query("SELECT * FROM attendance_records WHERE employeeId = :employeeId AND workDate BETWEEN :fromDate AND :toDate ORDER BY workDate")
    suspend fun getAttendanceForPeriod(employeeId: String, fromDate: String, toDate: String): List<AttendanceRecord>

    @Query("SELECT * FROM payroll_records ORDER BY period DESC, employeeName COLLATE NOCASE")
    fun observePayroll(): Flow<List<PayrollRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePayroll(payroll: PayrollRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePayrollRecords(payrollRecords: List<PayrollRecord>)

    @Query("DELETE FROM payroll_records")
    suspend fun clearAllPayroll()

    @Query("DELETE FROM payroll_records WHERE id = :id")
    suspend fun deletePayroll(id: String)
}
