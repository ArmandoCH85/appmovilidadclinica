package com.appmovilidadclinica.passenger.di

import com.appmovilidadclinica.passenger.data.repository.AuthRepositoryImpl
import com.appmovilidadclinica.passenger.data.repository.ReservationsRepositoryImpl
import com.appmovilidadclinica.passenger.data.repository.StopsRepositoryImpl
import com.appmovilidadclinica.passenger.data.repository.TripsRepositoryImpl
import com.appmovilidadclinica.passenger.domain.repository.AuthRepository
import com.appmovilidadclinica.passenger.domain.repository.ReservationsRepository
import com.appmovilidadclinica.passenger.domain.repository.StopsRepository
import com.appmovilidadclinica.passenger.domain.repository.TripsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Unico lugar donde se conecta la interfaz de `domain` con su implementacion
 * de `data` — el resto del codigo (ViewModels, UseCases) solo inyecta la
 * interfaz, nunca `AuthRepositoryImpl` directo (regla de dependencia de
 * Clean Architecture, ver diseño técnico).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    abstract fun bindTripsRepository(impl: TripsRepositoryImpl): TripsRepository

    @Binds
    abstract fun bindReservationsRepository(impl: ReservationsRepositoryImpl): ReservationsRepository

    @Binds
    abstract fun bindStopsRepository(impl: StopsRepositoryImpl): StopsRepository
}
