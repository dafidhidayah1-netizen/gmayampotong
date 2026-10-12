package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.AyamTransaction
import com.example.data.model.DeletedAyamTransaction
import com.example.data.model.CashTransaction
import com.example.data.model.CashFlow
import com.example.data.model.ExternalLoan
import com.example.data.model.EmployeeRecord
import com.example.data.model.AttendanceRecord
import com.example.data.model.PayrollRecord

@Database(
    entities = [
        AyamTransaction::class,
        DeletedAyamTransaction::class,
        CashTransaction::class,
        CashFlow::class,
        ExternalLoan::class,
        EmployeeRecord::class,
        AttendanceRecord::class,
        PayrollRecord::class
    ],
    version = 7,
    exportSchema = false
)
abstract class KasDatabase : RoomDatabase() {
    abstract fun ayamKasDao(): AyamKasDao
    abstract fun businessLedgerDao(): BusinessLedgerDao

    companion object {
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Extend the transaction table without deleting the user's existing ledger.
                db.execSQL("ALTER TABLE ayam_transactions ADD COLUMN paymentMethod TEXT NOT NULL DEFAULT 'CASH'")
                db.execSQL("ALTER TABLE ayam_transactions ADD COLUMN cashAccount TEXT NOT NULL DEFAULT 'DOMPET'")
                db.execSQL("ALTER TABLE ayam_transactions ADD COLUMN supplier TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE ayam_transactions ADD COLUMN weightLossKg REAL")
                db.execSQL("ALTER TABLE ayam_transactions ADD COLUMN mortalityRatePct REAL")
                db.execSQL("ALTER TABLE ayam_transactions ADD COLUMN fcr REAL")
                db.execSQL("ALTER TABLE ayam_transactions ADD COLUMN weightGainKg REAL")
                db.execSQL("ALTER TABLE ayam_transactions ADD COLUMN feedPricePerKg REAL")
                db.execSQL("ALTER TABLE ayam_transactions ADD COLUMN processingCost REAL NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE ayam_deleted_transactions ADD COLUMN paymentMethod TEXT NOT NULL DEFAULT 'CASH'")
                db.execSQL("ALTER TABLE ayam_deleted_transactions ADD COLUMN cashAccount TEXT NOT NULL DEFAULT 'DOMPET'")
                db.execSQL("ALTER TABLE ayam_deleted_transactions ADD COLUMN supplier TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE ayam_deleted_transactions ADD COLUMN weightLossKg REAL")
                db.execSQL("ALTER TABLE ayam_deleted_transactions ADD COLUMN mortalityRatePct REAL")
                db.execSQL("ALTER TABLE ayam_deleted_transactions ADD COLUMN fcr REAL")
                db.execSQL("ALTER TABLE ayam_deleted_transactions ADD COLUMN weightGainKg REAL")
                db.execSQL("ALTER TABLE ayam_deleted_transactions ADD COLUMN feedPricePerKg REAL")
                db.execSQL("ALTER TABLE ayam_deleted_transactions ADD COLUMN processingCost REAL NOT NULL DEFAULT 0")

                db.execSQL("""CREATE TABLE IF NOT EXISTS cash_transactions (
                    id TEXT NOT NULL, sourceTransactionId TEXT, date TEXT NOT NULL, accountName TEXT NOT NULL,
                    direction TEXT NOT NULL, amount REAL NOT NULL, paymentMethod TEXT NOT NULL, category TEXT NOT NULL,
                    description TEXT NOT NULL, notes TEXT NOT NULL, status TEXT NOT NULL, updatedAt TEXT NOT NULL,
                    deleted INTEGER NOT NULL, PRIMARY KEY(id))""")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_cash_transactions_sourceTransactionId ON cash_transactions(sourceTransactionId)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS cash_flows (
                    id TEXT NOT NULL, date TEXT NOT NULL, fromAccount TEXT NOT NULL, toAccount TEXT NOT NULL,
                    amount REAL NOT NULL, reference TEXT NOT NULL, notes TEXT NOT NULL, status TEXT NOT NULL,
                    updatedAt TEXT NOT NULL, PRIMARY KEY(id))""")

                db.execSQL("""CREATE TABLE IF NOT EXISTS external_loans (
                    id TEXT NOT NULL, date TEXT NOT NULL, party TEXT NOT NULL, loanType TEXT NOT NULL,
                    direction TEXT NOT NULL, amount REAL NOT NULL, paidAmount REAL NOT NULL, dueDate TEXT NOT NULL,
                    cashAccount TEXT NOT NULL, notes TEXT NOT NULL, status TEXT NOT NULL, updatedAt TEXT NOT NULL,
                    PRIMARY KEY(id))""")

                db.execSQL("""CREATE TABLE IF NOT EXISTS employees (
                    id TEXT NOT NULL, name TEXT NOT NULL, position TEXT NOT NULL, dailyRate REAL NOT NULL,
                    active INTEGER NOT NULL, updatedAt TEXT NOT NULL, PRIMARY KEY(id))""")

                db.execSQL("""CREATE TABLE IF NOT EXISTS attendance_records (
                    id TEXT NOT NULL, employeeId TEXT NOT NULL, employeeName TEXT NOT NULL, workDate TEXT NOT NULL,
                    shiftName TEXT NOT NULL, clockIn TEXT NOT NULL, clockOut TEXT NOT NULL, breakMinutes INTEGER NOT NULL,
                    overtimeHours REAL NOT NULL, status TEXT NOT NULL, notes TEXT NOT NULL, updatedAt TEXT NOT NULL,
                    PRIMARY KEY(id))""")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_attendance_records_employeeId_workDate ON attendance_records(employeeId, workDate)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS payroll_records (
                    id TEXT NOT NULL, employeeId TEXT NOT NULL, employeeName TEXT NOT NULL, period TEXT NOT NULL,
                    daysPresent REAL NOT NULL, dailyRate REAL NOT NULL, basePay REAL NOT NULL, overtimeHours REAL NOT NULL,
                    overtimePay REAL NOT NULL, allowance REAL NOT NULL, deductions REAL NOT NULL, totalPay REAL NOT NULL,
                    paidAmount REAL NOT NULL, paymentMethod TEXT NOT NULL, cashAccount TEXT NOT NULL, status TEXT NOT NULL,
                    notes TEXT NOT NULL, updatedAt TEXT NOT NULL, PRIMARY KEY(id))""")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_payroll_records_employeeId_period ON payroll_records(employeeId, period)")

                // Backfill cash movements from existing confirmed ledger rows once.
                db.execSQL("""
                    INSERT OR IGNORE INTO cash_transactions
                    (id, sourceTransactionId, date, accountName, direction, amount, paymentMethod, category,
                     description, notes, status, updatedAt, deleted)
                    SELECT 'CASH-' || id, id, date, cashAccount,
                      CASE WHEN type IN ('capital_in','sale','receivable_payment') THEN 'IN' ELSE 'OUT' END,
                      CASE type
                        WHEN 'capital_in' THEN amount
                        WHEN 'sale' THEN COALESCE(paidAmount,0)
                        WHEN 'receivable_payment' THEN amount
                        WHEN 'purchase' THEN COALESCE(paidAmount,0)
                        WHEN 'expense' THEN COALESCE(paidAmount,0)
                        WHEN 'capital_out' THEN amount
                        WHEN 'supplier_payment' THEN amount
                        ELSE 0 END,
                      paymentMethod, type, description, notes, status, updatedAt, 0
                    FROM ayam_transactions
                    WHERE status='confirmed' AND type IN
                      ('capital_in','sale','receivable_payment','purchase','expense','capital_out','supplier_payment')
                      AND CASE type
                        WHEN 'capital_in' THEN amount
                        WHEN 'sale' THEN COALESCE(paidAmount,0)
                        WHEN 'receivable_payment' THEN amount
                        WHEN 'purchase' THEN COALESCE(paidAmount,0)
                        WHEN 'expense' THEN COALESCE(paidAmount,0)
                        WHEN 'capital_out' THEN amount
                        WHEN 'supplier_payment' THEN amount
                        ELSE 0 END > 0
                """)
            }
        }

        @Volatile
        private var INSTANCE: KasDatabase? = null

        fun getDatabase(context: Context): KasDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KasDatabase::class.java,
                    "ayamkas_pro_real.db"
                ).addMigrations(MIGRATION_6_7).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
