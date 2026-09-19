package com.appmovilidadclinica.driver.shared.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GuestOccupantRequestDto(
    @SerialName("trip_seat_id") val tripSeatId: Long,
    @SerialName("origin_trip_stop_time_id") val originTripStopTimeId: Long,
    @SerialName("destination_trip_stop_time_id") val destinationTripStopTimeId: Long,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
)

@Serializable
data class GuestOccupantResponseDto(val id: Long)
