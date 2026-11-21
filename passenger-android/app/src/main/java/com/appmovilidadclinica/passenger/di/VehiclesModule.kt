package com.appmovilidadclinica.passenger.di

import com.appmovilidadclinica.passenger.data.remote.KtorApiClient
import com.appmovilidadclinica.passenger.shared.domain.vehicles.VehiclesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Posiciones de vehículos: se construye con la API de NUESTRO backend.
 * (El backend es quien habla con el GPS; la app no tiene credenciales.)
 */
@Module
@InstallIn(SingletonComponent::class)
object VehiclesModule {

    @Provides
    @Singleton
    fun provideVehiclesRepository(apiClient: KtorApiClient): VehiclesRepository =
        VehiclesRepository(apiClient.vehiclesApi)
}
