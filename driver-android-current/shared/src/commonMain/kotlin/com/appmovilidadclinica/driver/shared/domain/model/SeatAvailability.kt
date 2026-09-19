package com.appmovilidadclinica.driver.shared.domain.model

data class SeatAvailability(
    val tripSeatId: Long,
    val seatNumber: Int,
    val seatLabel: String,
    val availability: String,
) {
    val isAvailable: Boolean get() = availability == "AVAILABLE"
}
