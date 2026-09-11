package com.appmovilidadclinica.driver.shared.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SeatAvailabilityDto(
    @SerialName("trip_seat_id") val tripSeatId: Long,
    @SerialName("seat_number") val seatNumber: Int,
    @SerialName("seat_label") val seatLabel: String,
    val availability: String,
)
