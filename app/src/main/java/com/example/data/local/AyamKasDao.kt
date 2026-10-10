package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AyamTransaction
import com.example.data.model.DeletedAyamTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AyamKasDao {

    @Query("SELECT * FROM ayam_transactions ORDER BY date DESC, id DESC")
    fun getAllTransactions(): Flow<List<AyamTransaction>>

    @Query("SELECT * FROM ayam_transactions WHERE status = :status ORDER BY date DESC, id DESC")
    fun getTransactionsByStatus(status: String): Flow<List<AyamTransaction>>

    @Query("SELECT * FROM ayam_transactions WHERE status = 'confirmed' AND type = 'purchase' ORDER BY date DESC")
    fun getConfirmedPurchases(): Flow<List<AyamTransaction>>

    @Query("SELECT * FROM ayam_transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: String): AyamTransaction?

    @Query("SELECT COUNT(*) FROM ayam_transactions")
    suspend fun getTransactionCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: AyamTransaction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<AyamTransaction>)

    @Update
    suspend fun updateTransaction(transaction: AyamTransaction)

    @Query("UPDATE ayam_transactions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("DELETE FROM ayam_transactions WHERE id = :id")
    suspend fun deleteTransaction(id: String)

    @Query("DELETE FROM ayam_transactions")
    suspend fun clearAll()

    // Deleted transactions for restore support
    @Query("SELECT * FROM ayam_deleted_transactions ORDER BY deletedAt DESC")
    fun getAllDeleted(): Flow<List<DeletedAyamTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeleted(deleted: DeletedAyamTransaction)

    @Query("DELETE FROM ayam_deleted_transactions WHERE id = :id")
    suspend fun deleteDeleted(id: String)

    @Query("DELETE FROM ayam_deleted_transactions")
    suspend fun clearAllDeleted()
}
