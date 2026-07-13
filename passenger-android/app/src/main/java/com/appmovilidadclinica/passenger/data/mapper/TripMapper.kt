package com.appmovilidadclinica.passenger.data.mapper

import com.appmovilidadclinica.passenger.data.remote.dto.SeatResultDto
import com.appmovilidadclinica.passenger.data.remote.dto.TripDetailResponseDto
import com.appmovilidadclinica.passenger.data.remote.dto.TripSearchResultDto
import com.appmovilidadclinica.passenger.data.remote.dto.TripStopDto
import com.appmovilidadclinica.passenger.domain.model.BookingState
import com.appmovilidadclinica.passenger.domain.model.SeatAvailability
import com.appmovilidadclinica.passenger.domain.model.TripDetail
import com.appmovilidadclinica.passenger.domain.model.TripDirection
import com.appmovilidadclinica.passenger.domain.model.TripSearchResult
import com.appmovilidadclinica.passenger.domain.model.TripSeat
import com.appmovilidadclinica.passenger.domain.model.TripStatus
import com.appmovilidadclinica.passenger.domain.model.TripStop
import com.appmovilidadclinica.passenger.domain.model.TripStopStatus
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Parsea un timestamp del backend a Instant. El backend serializa con offset
 * local (`-05:00` hora Lima), pero `Instant.parse()` SOLO acepta UTC (`Z`).
 * `OffsetDateTime` acepta cualquier offset y `.toInstant()` normaliza a UTC.
 * Funciona con `Z`, `-05:00`, `+00:00`, etc.
 */
private fun parseInstant(raw: String): Instant = OffsetDateTime.parse(raw).toInstant()

/**
 * Parsea un campo fecha-hora del backend a LocalDate. El backend manda
 * `service_date` con hora+offset (`2026-07-14T00:00:00-05:00`), no fecha
 * pura — `LocalDate.parse()` reventaba con DateTimeParseException porque
 * espera solo la parte de fecha. Aceptamos los dos formatos: si trae hora,
 * la descartamos; si es fecha pura, la usamos tal cual.
 */
private fun parseLocalDate(raw: String): LocalDate =
    if (raw.length >= 10 && raw[10] == 'T') LocalDate.parse(raw.substring(0, 10))
    else LocalDate.parse(raw)

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
    status = TripStopStatus.valueOf(status),
)

fun SeatResultDto.toDomain(): TripSeat = TripSeat(
    tripSeatId = tripSeatId,
    seatNumber = seatNumber,
    seatLabel = seatLabel,
    availability = SeatAvailability.valueOf(availability),
)
