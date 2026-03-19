package com.smartgas.app.di

import com.smartgas.app.data.repository.FuelTransactionRepositoryImpl
import com.smartgas.app.domain.repository.FuelTransactionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindFuelTransactionRepository(
        impl: FuelTransactionRepositoryImpl,
    ): FuelTransactionRepository
}
