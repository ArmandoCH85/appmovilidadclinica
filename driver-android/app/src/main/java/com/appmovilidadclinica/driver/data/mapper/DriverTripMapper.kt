package com.appmovilidadclinica.driver.data.mapper

import com.appmovilidadclinica.driver.data.remote.dto.DriverTripDto
import com.appmovilidadclinica.driver.domain.model.Direction
import com.appmovilidadclinica.driver.domain.model.DriverTrip
import com.appmovilidadclinica.driver.domain.model.TripStatus
import java.time.OffsetDateTime

/** El backend manda offset Lima (`-05:00`); `Instant.parse` solo acepta `Z`.
 *  `OffsetDateTime` acepta ambos y normaliza a UTC. */
private fun parseInstant(raw: String): Instant = OffsetDateTime.parse(raw).toInstant()

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
