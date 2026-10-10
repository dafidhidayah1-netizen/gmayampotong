package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.AyamTransaction
import com.example.data.model.DeletedAyamTransaction

@Database(
    entities = [
        AyamTransaction::class,
        DeletedAyamTransaction::class
    ],
    version = 6,
    exportSchema = false
)
abstract class KasDatabase : RoomDatabase() {
    abstract fun ayamKasDao(): AyamKasDao

    companion object {
        @Volatile
        private var INSTANCE: KasDatabase? = null

        fun getDatabase(context: Context): KasDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KasDatabase::class.java,
                    "ayamkas_pro_real.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
