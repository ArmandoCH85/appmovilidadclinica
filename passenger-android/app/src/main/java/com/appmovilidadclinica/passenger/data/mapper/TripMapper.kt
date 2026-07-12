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

fun TripSearchResultDto.toDomain(): TripSearchResult = TripSearchResult(
    tripId = tripId,
    tripCode = tripCode,
    routeCode = routeCode,
    routeName = routeName,
    direction = TripDirection.valueOf(direction),
    originName = originName,
    originDepartureAt = Instant.parse(originDepartureAt),
    destinationName = destinationName,
    destinationArrivalAt = Instant.parse(destinationArrivalAt),
    vehicleCode = vehicleCode,
    plate = plate,
    bookingOpensAt = Instant.parse(bookingOpensAt),
    bookingClosesAt = Instant.parse(bookingClosesAt),
    bookingState = BookingState.valueOf(bookingState),
    availableSeats = availableSeats,
)

fun TripDetailResponseDto.toDomain(): TripDetail = TripDetail(
    tripId = trip.id,
    tripCode = trip.tripCode,
    routeId = trip.routeId,
    serviceDate = LocalDate.parse(trip.serviceDate),
    scheduledStartAt = Instant.parse(trip.scheduledStartAt),
    scheduledEndAt = Instant.parse(trip.scheduledEndAt),
    bookingOpensAt = Instant.parse(trip.bookingOpensAt),
    bookingClosesAt = Instant.parse(trip.bookingClosesAt),
    status = TripStatus.valueOf(trip.status),
    stops = stops.sortedBy { it.stopOrder }.map { it.toDomain() },
)

fun TripStopDto.toDomain(): TripStop = TripStop(
    tripStopTimeId = id,
    stopId = stopId,
    stopOrder = stopOrder,
    stopName = stopName,
    scheduledArrivalAt = Instant.parse(scheduledArrivalAt),
    scheduledDepartureAt = Instant.parse(scheduledDepartureAt),
    status = TripStopStatus.valueOf(status),
)

fun SeatResultDto.toDomain(): TripSeat = TripSeat(
    tripSeatId = tripSeatId,
    seatNumber = seatNumber,
    seatLabel = seatLabel,
    availability = SeatAvailability.valueOf(availability),
)
