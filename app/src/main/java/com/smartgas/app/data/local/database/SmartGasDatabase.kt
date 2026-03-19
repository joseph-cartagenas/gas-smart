package com.smartgas.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.smartgas.app.data.local.dao.FuelTransactionDao
import com.smartgas.app.data.local.entity.FuelTransactionEntity
import com.smartgas.app.domain.model.TransactionStatus
import com.smartgas.app.domain.model.TransactionType

@Database(
    entities = [FuelTransactionEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(SmartGasDatabase.Converters::class)
abstract class SmartGasDatabase : RoomDatabase() {

    abstract fun fuelTransactionDao(): FuelTransactionDao

    /** Room type converters for enum values. */
    class Converters {
        @TypeConverter
        fun fromTransactionType(value: TransactionType): String = value.name

        @TypeConverter
        fun toTransactionType(value: String): TransactionType =
            TransactionType.valueOf(value)

        @TypeConverter
        fun fromTransactionStatus(value: TransactionStatus): String = value.name

        @TypeConverter
        fun toTransactionStatus(value: String): TransactionStatus =
            TransactionStatus.valueOf(value)
    }

    companion object {
        const val DATABASE_NAME = "smartgas.db"
    }
}
