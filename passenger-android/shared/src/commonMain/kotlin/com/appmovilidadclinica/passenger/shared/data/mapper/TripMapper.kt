package com.appmovilidadclinica.passenger.shared.data.mapper

import com.appmovilidadclinica.passenger.shared.data.remote.dto.SeatResultDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.TripDetailResponseDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.TripSearchResultDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.TripStopDto
import com.appmovilidadclinica.passenger.shared.domain.model.BookingState
import com.appmovilidadclinica.passenger.shared.domain.model.SeatAvailability
import com.appmovilidadclinica.passenger.shared.domain.model.TripDetail
import com.appmovilidadclinica.passenger.shared.domain.model.TripDirection
import com.appmovilidadclinica.passenger.shared.domain.model.TripSearchResult
import com.appmovilidadclinica.passenger.shared.domain.model.TripSeat
import com.appmovilidadclinica.passenger.shared.domain.model.TripStatus
import com.appmovilidadclinica.passenger.shared.domain.model.TripStop
import com.appmovilidadclinica.passenger.shared.domain.model.TripStopStatus

/**
 * DTO → dominio de viajes (`GET /trips`, `/trips/{id}`, `/trips/{id}/seats`).
 *
 * Movido desde el modulo Android: la unica diferencia es que el parseo de
 * fechas ahora es multiplataforma (ver `DateTimeParsing.kt`), asi que iOS usa
 * exactamente la misma conversion que Android.
 */
fun TripSearchResultDto.toDomain(): TripSearchResult = TripSearchResult(
    tripId = tripId,
    tripCode = tripCode,
    routeCode = routeCode,
    routeName = routeName,
    direction = TripDirection.valueOf(direction),
    originName = originName,
    originDepartureAt = parseInstant(originDepartureAt),
    destinationName = destinationName,
    destinationArrivalAt = parseInstant(destinationArrivalAt),
    vehicleCode = vehicleCode,
    plate = plate,
    bookingOpensAt = parseInstant(bookingOpensAt),
    bookingClosesAt = parseInstant(bookingClosesAt),
    bookingState = BookingState.valueOf(bookingState),
    availableSeats = availableSeats,
)

fun TripDetailResponseDto.toDomain(): TripDetail = TripDetail(
    tripId = trip.id,
    tripCode = trip.tripCode,
    routeId = trip.routeId,
    serviceDate = parseLocalDate(trip.serviceDate),
    scheduledStartAt = parseInstant(trip.scheduledStartAt),
    scheduledEndAt = parseInstant(trip.scheduledEndAt),
    bookingOpensAt = parseInstant(trip.bookingOpensAt),
    bookingClosesAt = parseInstant(trip.bookingClosesAt),
    status = TripStatus.valueOf(trip.status),
    stops = stops.sortedBy { it.stopOrder }.map { it.toDomain() },
)

fun TripStopDto.toDomain(): TripStop = TripStop(
    tripStopTimeId = id,
    stopId = stopId,
    stopOrder = stopOrder,
    stopName = stopName,
    scheduledArrivalAt = parseInstant(scheduledArrivalAt),
    scheduledDepartureAt = parseInstant(scheduledDepartureAt),
    actualArrivalAt = parseInstantOrNull(actualArrivalAt),
    actualDepartureAt = parseInstantOrNull(actualDepartureAt),
    status = TripStopStatus.valueOf(status),
)

fun SeatResultDto.toDomain(): TripSeat = TripSeat(
    tripSeatId = tripSeatId,
    seatNumber = seatNumber,
    seatLabel = seatLabel,
    availability = parseSeatAvailability(availability),
)

/**
 * Parsea un valor de disponibilidad de asiento del backend a SeatAvailability.
 * Si el backend manda un string desconocido (ej. `OCCUPIED` en lugar de
 * `OCCUPIED_IN_REQUESTED_RANGE`), cae a `OCCUPIED_IN_REQUESTED_RANGE` para
 * que el asiento se renderice como no-seleccionable sin romper la lista entera.
 */
private fun parseSeatAvailability(raw: String): SeatAvailability =
    runCatching { SeatAvailability.valueOf(raw) }
        .getOrElse { SeatAvailability.OCCUPIED_IN_REQUESTED_RANGE }
