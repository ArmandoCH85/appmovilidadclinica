package com.appmovilidadclinica.passenger.data.mapper

import com.appmovilidadclinica.passenger.shared.data.remote.dto.ExtendResponseDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.JourneyStateDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.JourneyStopDto
import com.appmovilidadclinica.passenger.shared.domain.model.ExtendResult
import com.appmovilidadclinica.passenger.shared.domain.model.ExtensionOffer
import com.appmovilidadclinica.passenger.shared.domain.model.ExtensionStop
import com.appmovilidadclinica.passenger.shared.domain.model.JourneyState
import com.appmovilidadclinica.passenger.shared.domain.model.ReservationStatus
import com.appmovilidadclinica.passenger.shared.domain.model.TripStatus
import com.appmovilidadclinica.passenger.shared.domain.model.TripStop
import com.appmovilidadclinica.passenger.shared.domain.model.TripStopStatus
import java.time.OffsetDateTime

private fun parseJourneyInstant(raw: String) = OffsetDateTime.parse(raw).toInstant()

fun JourneyStopDto.toDomain(): TripStop = TripStop(
    tripStopTimeId = tripStopTimeId,
    stopId = stopId,
    stopOrder = stopOrder,
    stopName = stopName,
    scheduledArrivalAt = parseJourneyInstant(scheduledArrivalAt),
    scheduledDepartureAt = parseJourneyInstant(scheduledDepartureAt),
    actualArrivalAt = actualArrivalAt?.let(::parseJourneyInstant),
    actualDepartureAt = actualDepartureAt?.let(::parseJourneyInstant),
    status = TripStopStatus.valueOf(status),
)

fun JourneyStateDto.toDomain(): JourneyState = JourneyState(
    reservationId = reservationId,
    reservationStatus = ReservationStatus.valueOf(reservationStatus),
    tripStatus = TripStatus.valueOf(tripStatus),
    destinationStopOrder = destinationStopOrder,
    stops = stops.sortedBy { it.stopOrder }.map { it.toDomain() },
    canExtend = canExtend,
    extension = extension?.let { offer ->
        ExtensionOffer(
            currentSeatFree = offer.currentSeatFree,
            remainingStops = offer.remainingStops.map {
                ExtensionStop(it.tripStopTimeId, it.stopName, it.stopOrder)
            },
        )
    },
)

fun ExtendResponseDto.toDomain(): ExtendResult = ExtendResult(
    reservationId = reservationId,
    destinationStopOrder = destinationStopOrder,
    tripSeatId = tripSeatId,
    seatLabel = seatLabel,
    status = ReservationStatus.valueOf(status),
)
