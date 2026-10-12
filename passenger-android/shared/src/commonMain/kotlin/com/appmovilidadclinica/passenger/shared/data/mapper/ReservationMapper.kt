package com.appmovilidadclinica.passenger.shared.data.mapper

import com.appmovilidadclinica.passenger.shared.data.remote.dto.ReservationListItemDto
import com.appmovilidadclinica.passenger.shared.domain.model.Reservation
import com.appmovilidadclinica.passenger.shared.domain.model.ReservationStatus

/**
 * DTO → dominio de una reserva de `GET /reservations`.
 *
 * Mismas reglas que el modulo Android:
 *  - `routeName` se toma del `trip_code` (es lo que la app ya mostraba);
 *  - `originDepartureAt` cae a `scheduledStartAt` si el backend todavia no
 *    expone la salida de la parada de origen;
 *  - `qrToken` queda en null: el backend NUNCA lo devuelve despues del confirm.
 */
fun ReservationListItemDto.toDomain(): Reservation = Reservation(
    reservationId = id,
    reservationCode = reservationCode,
    qrToken = null,
    tripId = tripId,
    tripSeatId = tripSeatId,
    originTripStopTimeId = originTripStopTimeId,
    destinationTripStopTimeId = destinationTripStopTimeId,
    status = ReservationStatus.valueOf(status),
    confirmedAt = parseInstant(confirmedAt),
    routeName = tripCode,
    originName = originName,
    destinationName = destinationName,
    originDepartureAt = parseInstant(originDepartureAt ?: scheduledStartAt),
    seatLabel = seatLabel,
    vehicleCode = vehicleCode,
    plate = plate,
)
