package com.appmovilidadclinica.passenger.shared.data.remote.traccar

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Dispositivo de Traccar (`GET /api/devices`).
 * Solo los campos que usa la app; el resto se ignora (`ignoreUnknownKeys`).
 */
@Serializable
data class TraccarDeviceDto(
    val id: Long,
    val name: String = "",
    val uniqueId: String? = null,
    val status: String? = null,
    @SerialName("lastUpdate") val lastUpdate: String? = null,
    val positionId: Long? = null,
)

/**
 * Posición de Traccar (`GET /api/positions`).
 *
 * `speed` viene en **nudos** (se convierte a km/h en el dominio) y `course`
 * en grados. `fixTime` es la hora del GPS y `serverTime` la de recepción:
 * no son lo mismo y `valid=false` significa que el fix no es confiable.
 */
@Serializable
data class TraccarPositionDto(
    val id: Long? = null,
    val deviceId: Long,
    val latitude: Double,
    val longitude: Double,
    @SerialName("fixTime") val fixTime: String? = null,
    @SerialName("serverTime") val serverTime: String? = null,
    val valid: Boolean? = null,
    val speed: Double? = null,
    val course: Double? = null,
)
