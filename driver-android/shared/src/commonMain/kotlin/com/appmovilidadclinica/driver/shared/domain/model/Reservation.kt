package com.appmovilidadclinica.driver.shared.domain.model

import kotlinx.datetime.Instant

data class Reservation(
    val id: Long,
    val reservationCode: String,
    val tripId: Long,
    val workerId: Long,
    val tripSeatId: Long,
    val originTripStopTimeId: Long,
    val destinationTripStopTimeId: Long,
    val status: ReservationStatus,
    val confirmedAt: Instant?
)
