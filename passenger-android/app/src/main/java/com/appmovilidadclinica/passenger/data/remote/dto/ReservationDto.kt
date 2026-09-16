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
    @SerialName("vehicle_code") val vehicleCode: String = "",
    @SerialName("plate") val plate: String = "",
)

/** GET /api/reservations/{id}/journey — estado de polling del pasajero. */
@Serializable
data class JourneyStateDto(
    @SerialName("reservation_id") val reservationId: Long,
    @SerialName("reservation_status") val reservationStatus: String,
    @SerialName("trip_id") val tripId: Long,
    @SerialName("trip_status") val tripStatus: String,
    @SerialName("last_departed_stop_order") val lastDepartedStopOrder: Int? = null,
    @SerialName("destination_stop_order") val destinationStopOrder: Int,
    val stops: List<JourneyStopDto> = emptyList(),
    @SerialName("can_extend") val canExtend: Boolean = false,
    val extension: ExtensionOfferDto? = null,
)

@Serializable
data class JourneyStopDto(
    @SerialName("trip_stop_time_id") val tripStopTimeId: Long,
    @SerialName("stop_id") val stopId: Long,
    @SerialName("stop_name") val stopName: String,
    @SerialName("stop_order") val stopOrder: Int,
    @SerialName("scheduled_arrival_at") val scheduledArrivalAt: String,
    @SerialName("scheduled_departure_at") val scheduledDepartureAt: String,
    val status: String,
    @SerialName("actual_arrival_at") val actualArrivalAt: String? = null,
    @SerialName("actual_departure_at") val actualDepartureAt: String? = null,
)

@Serializable
data class ExtensionOfferDto(
    @SerialName("current_seat_free") val currentSeatFree: Boolean,
    @SerialName("remaining_stops") val remainingStops: List<ExtensionStopDto> = emptyList(),
)

@Serializable
data class ExtensionStopDto(
    @SerialName("trip_stop_time_id") val tripStopTimeId: Long,
    @SerialName("stop_name") val stopName: String,
    @SerialName("stop_order") val stopOrder: Int,
)

/** POST /api/reservations/{id}/extend. `trip_seat_id` null = mantener el actual. */
@Serializable
data class ExtendRequestDto(
    @SerialName("new_destination_trip_stop_time_id") val newDestinationTripStopTimeId: Long,
    @SerialName("trip_seat_id") val tripSeatId: Long? = null,
)

@Serializable
data class ExtendResponseDto(
    @SerialName("reservation_id") val reservationId: Long,
    @SerialName("destination_stop_order") val destinationStopOrder: Int,
    @SerialName("trip_seat_id") val tripSeatId: Long,
    @SerialName("seat_label") val seatLabel: String,
    val status: String,
)
