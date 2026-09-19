package com.appmovilidadclinica.driver.shared.domain.model

import kotlinx.datetime.Instant

data class TripStop(
    val id: Long,
    val stopName: String,
    val stopOrder: Int,
    val scheduledArrivalAt: Instant?,
    val scheduledDepartureAt: Instant?,
    val actualArrivalAt: Instant?,
    val actualDepartureAt: Instant?,
    val status: TripStopStatus
)
