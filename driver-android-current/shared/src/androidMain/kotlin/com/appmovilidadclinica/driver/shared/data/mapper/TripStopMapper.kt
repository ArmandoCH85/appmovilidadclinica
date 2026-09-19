package com.appmovilidadclinica.driver.shared.data.mapper

import com.appmovilidadclinica.driver.shared.data.remote.dto.TripStopDto
import com.appmovilidadclinica.driver.shared.domain.model.TripStop
import com.appmovilidadclinica.driver.shared.domain.model.TripStopStatus
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import java.time.OffsetDateTime

/**
 * Backend manda offset Lima (-05:00), pero kotlinx Instant.parse() SOLO acepta UTC (Z).
 * OffsetDateTime acepta Z y offsets, toKotlinInstant() normaliza a kotlinx Instant.
 */
private fun parseInstant(raw: String): Instant =
    OffsetDateTime.parse(raw).toInstant().toKotlinInstant()

fun TripStopDto.toDomain(): TripStop = TripStop(
    id = id,
    stopName = stopName,
    stopOrder = stopOrder,
    scheduledArrivalAt = scheduledArrivalAt?.let(::parseInstant),
    scheduledDepartureAt = scheduledDepartureAt?.let(::parseInstant),
    actualArrivalAt = actualArrivalAt?.let(::parseInstant),
    actualDepartureAt = actualDepartureAt?.let(::parseInstant),
    status = TripStopStatus.valueOf(status.uppercase())
)
