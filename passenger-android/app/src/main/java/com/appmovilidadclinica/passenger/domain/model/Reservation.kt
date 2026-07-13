package com.appmovilidadclinica.passenger.domain.model

import java.time.Instant

enum class ReservationStatus { CONFIRMED, BOARDED, COMPLETED, NO_SHOW, CANCELLED }

/**
 * Reserva confirmada, persistida localmente (Room) desde el momento en que
 * `POST /reservations` responde 201 — `qrToken` es la UNICA vez que el
 * backend lo entrega en claro (guarda `SHA256(qrToken)`, nunca el original).
 * Si este registro se pierde, el QR no se puede volver a generar.
 *
 * `qrToken` es nullable: las reservas sincronizadas desde el backend
 * (endpoint `GET /api/reservations`) vienen sin qrToken porque el server
 * NUNCA lo expone despues del confirm inicial. La UI distingue el caso
 * (mostrando "QR no disponible" en la pantalla de detalle) para que el
 * usuario sepa que tiene que reconfirmar si quiere ver el QR.
 */
data class Reservation(
    val reservationId: Long,
    val reservationCode: String,
    val qrToken: String?,
    val tripId: Long,
    val tripSeatId: Long,
    val originTripStopTimeId: Long,
    val destinationTripStopTimeId: Long,
    val status: ReservationStatus,
    val confirmedAt: Instant,
    val routeName: String,
    val originName: String,
    val destinationName: String,
    val originDepartureAt: Instant,
    val seatLabel: String,
    val vehicleCode: String = "",
    val plate: String = "",
)

/** Parametros para confirmar una reserva — ver Specs #3. */
data class ReservationRequest(
    val tripId: Long,
    val tripSeatId: Long,
    val originTripStopTimeId: Long,
    val destinationTripStopTimeId: Long,
)
