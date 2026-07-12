package com.appmovilidadclinica.driver.data.mapper

import com.appmovilidadclinica.driver.data.remote.dto.DriverTripDto
import com.appmovilidadclinica.driver.domain.model.Direction
import com.appmovilidadclinica.driver.domain.model.DriverTrip
import com.appmovilidadclinica.driver.domain.model.TripStatus
import java.time.Instant

fun DriverTripDto.toDomain(): DriverTrip = DriverTrip(
    id = id,
    tripCode = tripCode,
    routeName = routeName,
    direction = when (direction.uppercase()) {
        "VUELTA" -> Direction.VUELTA
        else -> Direction.IDA
    },
    scheduledStartAt = Instant.parse(scheduledStartAt),
    scheduledEndAt = Instant.parse(scheduledEndAt),
    vehicleCode = vehicleCode,
    plate = plate,
    seatCapacity = seatCapacity,
    status = TripStatus.valueOf(status.uppercase())
)
