package com.appmovilidadclinica.driver.data.mapper

import com.appmovilidadclinica.driver.data.remote.dto.ReservationDto
import com.appmovilidadclinica.driver.domain.model.Reservation
import com.appmovilidadclinica.driver.domain.model.ReservationStatus
import java.time.Instant

fun ReservationDto.toDomain(): Reservation = Reservation(
    id = id,
    reservationCode = reservationCode,
    tripId = tripId,
    workerId = workerId,
    tripSeatId = tripSeatId,
    originTripStopTimeId = originTripStopTimeId,
    destinationTripStopTimeId = destinationTripStopTimeId,
    status = ReservationStatus.valueOf(status.uppercase()),
    confirmedAt = confirmedAt?.let { Instant.parse(it) }
)
