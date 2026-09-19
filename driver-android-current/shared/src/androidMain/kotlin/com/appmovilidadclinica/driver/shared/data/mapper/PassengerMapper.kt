package com.appmovilidadclinica.driver.shared.data.mapper

import com.appmovilidadclinica.driver.shared.data.remote.dto.PassengerDto
import com.appmovilidadclinica.driver.shared.domain.model.Passenger
import com.appmovilidadclinica.driver.shared.domain.model.ReservationStatus
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import java.time.OffsetDateTime

/**
 * Backend manda offset Lima (-05:00), pero kotlinx Instant.parse() SOLO acepta UTC (Z).
 * OffsetDateTime acepta Z y offsets, toKotlinInstant() normaliza a kotlinx Instant.
 */
private fun parseInstant(raw: String): Instant =
    OffsetDateTime.parse(raw).toInstant().toKotlinInstant()

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
    confirmedAt = confirmedAt?.let(::parseInstant),
    boardedAt = boardedAt?.let(::parseInstant),
    isGuest = isGuest,
    guestDisplayName = guestDisplayName
)
