package com.appmovilidadclinica.driver.data.mapper

import com.appmovilidadclinica.driver.shared.data.remote.dto.TripStopDto
import com.appmovilidadclinica.driver.shared.domain.model.TripStop
import com.appmovilidadclinica.driver.shared.domain.model.TripStopStatus
import kotlinx.datetime.Instant

fun TripStopDto.toDomain(): TripStop = TripStop(
    id = id,
    stopName = stopName,
    stopOrder = stopOrder,
    scheduledArrivalAt = scheduledArrivalAt?.let { Instant.parse(it) },
    scheduledDepartureAt = scheduledDepartureAt?.let { Instant.parse(it) },
    actualArrivalAt = actualArrivalAt?.let { Instant.parse(it) },
    actualDepartureAt = actualDepartureAt?.let { Instant.parse(it) },
    status = TripStopStatus.valueOf(status.uppercase())
)
