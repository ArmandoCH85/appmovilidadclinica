package com.appmovilidadclinica.passenger.data.mapper

import com.appmovilidadclinica.passenger.data.remote.dto.ExtendResponseDto
import com.appmovilidadclinica.passenger.data.remote.dto.JourneyStateDto
import com.appmovilidadclinica.passenger.data.remote.dto.JourneyStopDto
import com.appmovilidadclinica.passenger.domain.model.ExtendResult
import com.appmovilidadclinica.passenger.domain.model.ExtensionOffer
import com.appmovilidadclinica.passenger.domain.model.ExtensionStop
import com.appmovilidadclinica.passenger.domain.model.JourneyState
import com.appmovilidadclinica.passenger.domain.model.ReservationStatus
import com.appmovilidadclinica.passenger.domain.model.TripStatus
import com.appmovilidadclinica.passenger.domain.model.TripStop
import com.appmovilidadclinica.passenger.domain.model.TripStopStatus
import java.time.OffsetDateTime

private fun parseJourneyInstant(raw: String) = OffsetDateTime.parse(raw).toInstant()

fun JourneyStopDto.toDomain(): TripStop = TripStop(
    tripStopTimeId = tripStopTimeId,
    stopId = stopId,
    stopOrder = stopOrder,
    stopName = stopName,
    scheduledArrivalAt = parseJourneyInstant(scheduledArrivalAt),
    scheduledDepartureAt = parseJourneyInstant(scheduledDepartureAt),
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
