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

/**
 * GET /api/reservations — fila enriquecida de la lista del WORKER. NO trae
 * `qr_token`: el backend no lo expone despues del confirm inicial, asi que
 * las reservas sincronizadas tendran qrToken=null en la cache local.
 */
@Serializable
data class ReservationListItemDto(
    @SerialName("id") val id: Long,
    @SerialName("reservation_code") val reservationCode: String,
    @SerialName("trip_id") val tripId: Long,
    @SerialName("trip_seat_id") val tripSeatId: Long,
    @SerialName("origin_trip_stop_time_id") val originTripStopTimeId: Long,
    @SerialName("destination_trip_stop_time_id") val destinationTripStopTimeId: Long,
    val status: String,
    @SerialName("confirmed_at") val confirmedAt: String,
    @SerialName("trip_code") val tripCode: String,
    @SerialName("scheduled_start_at") val scheduledStartAt: String,
    @SerialName("origin_name") val originName: String,
    @SerialName("destination_name") val destinationName: String,
    @SerialName("seat_label") val seatLabel: String,
)
