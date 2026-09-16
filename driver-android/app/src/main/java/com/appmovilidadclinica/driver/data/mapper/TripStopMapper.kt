package com.appmovilidadclinica.driver.data.mapper

import com.appmovilidadclinica.driver.data.remote.dto.TripStopDto
import com.appmovilidadclinica.driver.domain.model.TripStop
import com.appmovilidadclinica.driver.domain.model.TripStopStatus
import java.time.Instant
import java.time.OffsetDateTime

private fun parseInstant(raw: String): Instant = OffsetDateTime.parse(raw).toInstant()

fun TripStopDto.toDomain(): TripStop = TripStop(
    id = id,
    stopName = stopName,
    stopOrder = stopOrder,
    scheduledArrivalAt = scheduledArrivalAt?.let { parseInstant(it) },
    scheduledDepartureAt = scheduledDepartureAt?.let { parseInstant(it) },
    actualArrivalAt = actualArrivalAt?.let { parseInstant(it) },
    actualDepartureAt = actualDepartureAt?.let { parseInstant(it) },
    status = TripStopStatus.valueOf(status.uppercase())
)
