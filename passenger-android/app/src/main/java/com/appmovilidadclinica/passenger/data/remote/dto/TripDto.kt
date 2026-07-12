package com.appmovilidadclinica.passenger.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /api/trips — un elemento del array que devuelve sp_search_trips. */
@Serializable
data class TripSearchResultDto(
    @SerialName("trip_id") val tripId: Long,
    @SerialName("trip_code") val tripCode: String,
    @SerialName("route_code") val routeCode: String,
    @SerialName("route_name") val routeName: String,
    val direction: String,
    @SerialName("origin_order") val originOrder: Int,
    @SerialName("origin_name") val originName: String,
    @SerialName("origin_departure_at") val originDepartureAt: String,
    @SerialName("destination_order") val destinationOrder: Int,
    @SerialName("destination_name") val destinationName: String,
    @SerialName("destination_arrival_at") val destinationArrivalAt: String,
    @SerialName("vehicle_code") val vehicleCode: String,
    val plate: String,
    @SerialName("booking_opens_at") val bookingOpensAt: String,
    @SerialName("booking_closes_at") val bookingClosesAt: String,
    @SerialName("booking_state") val bookingState: String,
    @SerialName("available_seats") val availableSeats: Int,
)

/** GET /api/trips/{id} */
@Serializable
data class TripDetailResponseDto(
    val trip: TripHeaderDto,
    val stops: List<TripStopDto>,
)

@Serializable
data class TripHeaderDto(
    val id: Long,
    @SerialName("trip_code") val tripCode: String,
    val source: String,
    @SerialName("trip_template_id") val tripTemplateId: Long? = null,
    @SerialName("route_id") val routeId: Long,
    @SerialName("service_date") val serviceDate: String,
    @SerialName("scheduled_start_at") val scheduledStartAt: String,
    @SerialName("scheduled_end_at") val scheduledEndAt: String,
    @SerialName("booking_opens_at") val bookingOpensAt: String,
    @SerialName("booking_closes_at") val bookingClosesAt: String,
    @SerialName("vehicle_id") val vehicleId: Long,
    @SerialName("driver_id") val driverId: Long,
    @SerialName("seat_capacity_snapshot") val seatCapacitySnapshot: Int,
    @SerialName("no_show_tolerance_minutes") val noShowToleranceMinutes: Int,
    val status: String,
    @SerialName("actual_start_at") val actualStartAt: String? = null,
    @SerialName("actual_end_at") val actualEndAt: String? = null,
    @SerialName("cancellation_reason") val cancellationReason: String? = null,
)

@Serializable
data class TripStopDto(
    val id: Long,
    @SerialName("stop_id") val stopId: Long,
    @SerialName("stop_order") val stopOrder: Int,
    @SerialName("scheduled_arrival_at") val scheduledArrivalAt: String,
    @SerialName("scheduled_departure_at") val scheduledDepartureAt: String,
    @SerialName("actual_arrival_at") val actualArrivalAt: String? = null,
    @SerialName("actual_departure_at") val actualDepartureAt: String? = null,
    val status: String,
    @SerialName("stop_name") val stopName: String,
    @SerialName("stop_type") val stopType: String,
)

/** GET /api/trips/{id}/seats */
@Serializable
data class SeatResultDto(
    @SerialName("trip_seat_id") val tripSeatId: Long,
    @SerialName("seat_number") val seatNumber: Int,
    @SerialName("seat_label") val seatLabel: String,
    val availability: String,
)
