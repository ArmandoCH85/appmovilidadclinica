package com.appmovilidadclinica.driver.domain.model

import java.time.Instant

data class Passenger(
    val reservationId: Long,
    val reservationCode: String,
    val workerId: Long,
    val workerFullName: String,
    val seatNumber: Int,
    val seatLabel: String,
    val originStopOrder: Int,
    val originStopName: String,
    val destinationStopOrder: Int,
    val destinationStopName: String,
    val status: ReservationStatus,
    val confirmedAt: Instant?,
    val boardedAt: Instant?
)
