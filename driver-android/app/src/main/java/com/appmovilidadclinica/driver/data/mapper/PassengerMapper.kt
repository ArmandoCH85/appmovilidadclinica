package com.appmovilidadclinica.driver.data.mapper

import com.appmovilidadclinica.driver.data.remote.dto.PassengerDto
import com.appmovilidadclinica.driver.domain.model.Passenger
import com.appmovilidadclinica.driver.domain.model.ReservationStatus
import java.time.Instant
import java.time.OffsetDateTime

private fun parseInstant(raw: String): Instant = OffsetDateTime.parse(raw).toInstant()

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
    confirmedAt = confirmedAt?.let { parseInstant(it) },
    boardedAt = boardedAt?.let { parseInstant(it) }
)
