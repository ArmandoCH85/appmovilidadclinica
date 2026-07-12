package com.appmovilidadclinica.driver.di

import com.appmovilidadclinica.driver.data.repository.AuthRepositoryImpl
import com.appmovilidadclinica.driver.data.repository.BookingRepositoryImpl
import com.appmovilidadclinica.driver.data.repository.DriverRepositoryImpl
import com.appmovilidadclinica.driver.domain.repository.AuthRepository
import com.appmovilidadclinica.driver.domain.repository.BookingRepository
import com.appmovilidadclinica.driver.domain.repository.DriverRepository
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
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindDriverRepository(
        impl: DriverRepositoryImpl
    ): DriverRepository

    @Binds
    @Singleton
    abstract fun bindBookingRepository(
        impl: BookingRepositoryImpl
    ): BookingRepository
}
