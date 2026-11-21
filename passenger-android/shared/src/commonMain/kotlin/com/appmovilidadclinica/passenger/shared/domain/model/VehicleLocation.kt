package com.appmovilidadclinica.passenger.shared.domain.model

import kotlinx.datetime.Instant

/**
 * Ubicación conocida de un vehículo de la flota, tal como la reporta el GPS
 * (Traccar / MS Global GPS).
 *
 * Reglas de este modelo:
 *  - Si no hay posición, `latitude`/`longitude` quedan en **null**: nunca se
 *    inventan coordenadas ni se usa (0,0).
 *  - `fixTime` es la hora del GPS; `serverTime` la de recepción del servidor.
 *  - `isStale` marca posiciones viejas: se muestran como "última ubicación
 *    conocida", no como "en vivo".
 */
data class VehicleLocation(
    val plate: String,
    val deviceId: Long,
    val deviceName: String?,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val fixTime: Instant? = null,
    val serverTime: Instant? = null,
    val speedKmh: Double? = null,
    val courseDegrees: Double? = null,
    val online: Boolean? = null,
    val isStale: Boolean = true,
) {
    /** true solo si hay coordenadas utilizables. */
    val hasPosition: Boolean get() = latitude != null && longitude != null
}
