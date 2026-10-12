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
import kotlinx.coroutines.flow.Flow

@Dao
interface BusinessLedgerDao {
    @Query("SELECT * FROM cash_transactions WHERE deleted = 0 ORDER BY date DESC, id DESC")
    fun observeCashTransactions(): Flow<List<CashTransaction>>

    @Query("SELECT * FROM cash_transactions WHERE deleted = 0 AND status = 'confirmed' ORDER BY date DESC, id DESC")
    fun observeConfirmedCashTransactions(): Flow<List<CashTransaction>>

    @Query("SELECT * FROM cash_transactions WHERE sourceTransactionId = :sourceId LIMIT 1")
    suspend fun findCashBySource(sourceId: String): CashTransaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCashTransaction(transaction: CashTransaction)

    @Query("DELETE FROM cash_transactions WHERE sourceTransactionId = :sourceId")
    suspend fun deleteCashForSource(sourceId: String)

    @Query("DELETE FROM cash_transactions WHERE sourceTransactionId IS NOT NULL AND sourceTransactionId NOT LIKE 'LOAN-%'")
    suspend fun clearMirroredCashTransactions()

    @Query("SELECT * FROM cash_flows ORDER BY date DESC, id DESC")
    fun observeCashFlows(): Flow<List<CashFlow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCashFlow(cashFlow: CashFlow)

    @Query("DELETE FROM cash_flows WHERE id = :id")
    suspend fun deleteCashFlow(id: String)

    @Query("SELECT * FROM external_loans ORDER BY date DESC, id DESC")
    fun observeExternalLoans(): Flow<List<ExternalLoan>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveExternalLoan(loan: ExternalLoan)

    @Query("DELETE FROM external_loans WHERE id = :id")
    suspend fun deleteExternalLoan(id: String)

    @Query("SELECT * FROM employees ORDER BY active DESC, name COLLATE NOCASE")
    fun observeEmployees(): Flow<List<EmployeeRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEmployee(employee: EmployeeRecord)

    @Query("SELECT * FROM employees WHERE id = :id LIMIT 1")
    suspend fun getEmployee(id: String): EmployeeRecord?

    @Query("SELECT * FROM attendance_records ORDER BY workDate DESC, employeeName COLLATE NOCASE")
    fun observeAttendance(): Flow<List<AttendanceRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAttendance(attendance: AttendanceRecord)

    @Query("SELECT * FROM attendance_records WHERE employeeId = :employeeId AND workDate BETWEEN :fromDate AND :toDate ORDER BY workDate")
    suspend fun getAttendanceForPeriod(employeeId: String, fromDate: String, toDate: String): List<AttendanceRecord>

    @Query("SELECT * FROM payroll_records ORDER BY period DESC, employeeName COLLATE NOCASE")
    fun observePayroll(): Flow<List<PayrollRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePayroll(payroll: PayrollRecord)

    @Query("DELETE FROM payroll_records WHERE id = :id")
    suspend fun deletePayroll(id: String)
}
