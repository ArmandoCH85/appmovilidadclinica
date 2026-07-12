package com.appmovilidadclinica.passenger.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReservationRequestDto(
    @SerialName("trip_id") val tripId: Long,
    @SerialName("trip_seat_id") val tripSeatId: Long,
    @SerialName("origin_trip_stop_time_id") val originTripStopTimeId: Long,
    @SerialName("destination_trip_stop_time_id") val destinationTripStopTimeId: Long,
)

/** POST /api/reservations — 201. `qr_token` viaja UNA sola vez, ver dominio. */
@Serializable
data class ReservationResponseDto(
    @SerialName("reservation_id") val reservationId: Long,
    @SerialName("reservation_code") val reservationCode: String,
    @SerialName("qr_token") val qrToken: String,
    val status: String,
)

/**
 * POST /api/reservations/{id}/self-checkin — CONTRATO NUEVO propuesto (no
 * existe en el backend hoy, ver diseño técnico). Shape especulativo,
 * simetrico al de confirmacion — a ajustar cuando el backend lo implemente.
 */
@Serializable
data class SelfCheckinResponseDto(
    @SerialName("reservation_id") val reservationId: Long,
    val status: String,
    @SerialName("boarded_at") val boardedAt: String,
)
