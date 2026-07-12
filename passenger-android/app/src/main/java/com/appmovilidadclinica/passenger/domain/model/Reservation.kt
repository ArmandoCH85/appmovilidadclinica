package com.appmovilidadclinica.passenger.domain.model

import java.time.Instant

enum class ReservationStatus { CONFIRMED, BOARDED, COMPLETED, NO_SHOW, CANCELLED }

/**
 * Reserva confirmada, persistida localmente (Room) desde el momento en que
 * `POST /reservations` responde 201 — `qrToken` es la UNICA vez que el
 * backend lo entrega en claro (guarda `SHA256(qrToken)`, nunca el original).
 * Si este registro se pierde, el QR no se puede volver a generar.
 */
data class Reservation(
    val reservationId: Long,
    val reservationCode: String,
    val qrToken: String,
    val tripId: Long,
    val tripSeatId: Long,
    val originTripStopTimeId: Long,
    val destinationTripStopTimeId: Long,
    val status: ReservationStatus,
    val confirmedAt: Instant,
    /** Datos de contexto para mostrar en "Mi reserva" sin otra llamada de red. */
    val routeName: String,
    val originName: String,
    val destinationName: String,
    val originDepartureAt: Instant,
    val seatLabel: String,
)

/** Parametros para confirmar una reserva — ver Specs #3. */
data class ReservationRequest(
    val tripId: Long,
    val tripSeatId: Long,
    val originTripStopTimeId: Long,
    val destinationTripStopTimeId: Long,
)
