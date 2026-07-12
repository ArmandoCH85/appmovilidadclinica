package com.appmovilidadclinica.driver.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReservationDto(
    val id: Long,
    @SerialName("reservation_code") val reservationCode: String,
    @SerialName("trip_id") val tripId: Long,
    @SerialName("worker_id") val workerId: Long,
    @SerialName("trip_seat_id") val tripSeatId: Long,
    @SerialName("origin_trip_stop_time_id") val originTripStopTimeId: Long,
    @SerialName("destination_trip_stop_time_id") val destinationTripStopTimeId: Long,
    val status: String,
    @SerialName("confirmed_at") val confirmedAt: String? = null
)
