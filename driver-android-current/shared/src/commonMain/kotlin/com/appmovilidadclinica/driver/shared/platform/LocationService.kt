package com.appmovilidadclinica.driver.shared.platform

import kotlinx.coroutines.flow.Flow

enum class PermissionStatus { GRANTED, DENIED, NEVER_ASK_AGAIN }

data class Coordinates(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float = 0f,
)

/**
 * Servicio multiplatform de geolocalizacion del conductor.
 *
 * Android: FusedLocationProvider + ActivityCompat.requestPermissions.
 * iOS: stub hasta Fase 7 (CoreLocation CLLocationManager).
 */
interface LocationService {
    /**
     * Solicita el permiso. Devuelve un Flow que emite una vez con el resultado.
     */
    fun requestPermissions(): Flow<PermissionStatus>

    /**
     * Ubicacion actual o null si no hay permiso o no se pudo obtener.
     */
    suspend fun currentLocation(): Coordinates?

    /**
     * Stream de ubicaciones. Termina cuando se cancele el Job del consumidor.
     */
    fun observeLocation(): Flow<Coordinates>

    /**
     * False en iOS stub. La UI lo consulta para ocultar features de GPS.
     */
    fun isAvailable(): Boolean = false
}
