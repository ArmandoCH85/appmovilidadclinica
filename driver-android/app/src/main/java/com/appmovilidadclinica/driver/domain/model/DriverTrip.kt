package com.appmovilidadclinica.driver.domain.model

import java.time.Instant

data class DriverTrip(
    val id: Long,
    val tripCode: String,
    val routeName: String,
    val direction: Direction,
    val scheduledStartAt: Instant,
    val scheduledEndAt: Instant,
    val vehicleCode: String,
    val plate: String,
    val seatCapacity: Int,
    val status: TripStatus
)
