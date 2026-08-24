package com.appmovilidadclinica.driver.shared.data.mapper

import com.appmovilidadclinica.driver.shared.data.remote.dto.ReservationDto
import com.appmovilidadclinica.driver.shared.domain.model.Reservation
import com.appmovilidadclinica.driver.shared.domain.model.ReservationStatus
import kotlinx.datetime.Instant

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
