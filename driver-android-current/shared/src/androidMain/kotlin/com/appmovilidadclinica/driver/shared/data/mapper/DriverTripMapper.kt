package com.appmovilidadclinica.driver.shared.data.mapper

import com.appmovilidadclinica.driver.shared.data.remote.dto.DriverTripDto
import com.appmovilidadclinica.driver.shared.domain.model.Direction
import com.appmovilidadclinica.driver.shared.domain.model.DriverTrip
import com.appmovilidadclinica.driver.shared.domain.model.TripStatus
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import java.time.OffsetDateTime

/**
 * Backend manda offset Lima (-05:00), pero kotlinx Instant.parse() SOLO acepta UTC (Z).
 * OffsetDateTime acepta Z y offsets, toKotlinInstant() normaliza a kotlinx Instant.
 */
private fun parseInstant(raw: String): Instant =
    OffsetDateTime.parse(raw).toInstant().toKotlinInstant()

fun DriverTripDto.toDomain(): DriverTrip = DriverTrip(
    id = id,
    tripCode = tripCode,
    routeName = routeName,
    direction = when (direction.uppercase()) {
        "VUELTA" -> Direction.VUELTA
        else -> Direction.IDA
    },
    scheduledStartAt = parseInstant(scheduledStartAt),
    scheduledEndAt = parseInstant(scheduledEndAt),
    vehicleCode = vehicleCode,
    plate = plate,
    seatCapacity = seatCapacity,
    status = TripStatus.valueOf(status.uppercase())
)
