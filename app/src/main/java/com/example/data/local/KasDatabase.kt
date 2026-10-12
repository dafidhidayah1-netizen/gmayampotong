package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.AyamTransaction
import com.example.data.model.DeletedAyamTransaction

@Database(
    entities = [
        AyamTransaction::class,
        DeletedAyamTransaction::class
    ],
    version = 7,
    exportSchema = false
)
abstract class KasDatabase : RoomDatabase() {
    abstract fun ayamKasDao(): AyamKasDao

    companion object {
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE ayam_transactions ADD COLUMN saleableKg REAL")
                database.execSQL("ALTER TABLE ayam_deleted_transactions ADD COLUMN saleableKg REAL")
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
                ).addMigrations(MIGRATION_6_7).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
