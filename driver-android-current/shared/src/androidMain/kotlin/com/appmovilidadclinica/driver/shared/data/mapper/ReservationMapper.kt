package com.appmovilidadclinica.driver.shared.data.mapper

import com.appmovilidadclinica.driver.shared.data.remote.dto.ReservationDto
import com.appmovilidadclinica.driver.shared.domain.model.Reservation
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

fun ReservationDto.toDomain(): Reservation = Reservation(
    id = id,
    reservationCode = reservationCode,
    tripId = tripId,
    workerId = workerId,
    tripSeatId = tripSeatId,
    originTripStopTimeId = originTripStopTimeId,
    destinationTripStopTimeId = destinationTripStopTimeId,
    status = ReservationStatus.valueOf(status.uppercase()),
    confirmedAt = confirmedAt?.let(::parseInstant)
)
