package com.appmovilidadclinica.driver.data.mapper

import com.appmovilidadclinica.driver.shared.data.remote.dto.PassengerDto
import com.appmovilidadclinica.driver.shared.domain.model.Passenger
import com.appmovilidadclinica.driver.shared.domain.model.ReservationStatus
import java.time.Instant

fun PassengerDto.toDomain(): Passenger = Passenger(
    reservationId = reservationId,
    reservationCode = reservationCode,
    workerId = workerId,
    workerFullName = workerFullName,
    seatNumber = seatNumber,
    seatLabel = seatLabel,
    originStopOrder = originStopOrder,
    originStopName = originStopName,
    destinationStopOrder = destinationStopOrder,
    destinationStopName = destinationStopName,
    status = ReservationStatus.valueOf(status.uppercase()),
    confirmedAt = confirmedAt?.let { Instant.parse(it) },
    boardedAt = boardedAt?.let { Instant.parse(it) }
)
