package com.smartgas.app.di

import android.content.Context
import androidx.room.Room
import com.smartgas.app.data.local.dao.FuelTransactionDao
import com.smartgas.app.data.local.database.SmartGasDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SmartGasDatabase =
        Room.databaseBuilder(
            context,
            SmartGasDatabase::class.java,
            SmartGasDatabase.DATABASE_NAME,
        )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideFuelTransactionDao(database: SmartGasDatabase): FuelTransactionDao =
        database.fuelTransactionDao()
}
