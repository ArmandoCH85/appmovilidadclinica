package com.appmovilidadclinica.passenger.shared.data.remote.vehicles

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Posición de un vehículo según NUESTRO backend (`GET /api/vehicles/positions`).
 *
 * El backend es el que habla con Traccar usando sus credenciales de servidor:
 * la app no conoce ni necesita saber que existe Traccar.
 */
@Serializable
data class VehiclePositionDto(
    val plate: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("fix_time") val fixTime: String? = null,
    @SerialName("server_time") val serverTime: String? = null,
    @SerialName("speed_kmh") val speedKmh: Double? = null,
    val course: Double? = null,
    val online: Boolean? = null,
    @SerialName("has_position") val hasPosition: Boolean = false,
    @SerialName("is_stale") val isStale: Boolean = true,
)
