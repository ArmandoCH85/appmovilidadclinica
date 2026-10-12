package com.appmovilidadclinica.passenger.shared.domain.model

/**
 * Resultado de confirmar una reserva (`POST /reservations`, 201).
 * Es lo minimo que necesita la UI compartida: el id para ir al detalle, el
 * codigo para mostrarlo y el `qrToken` (que el backend entrega UNA sola vez).
 */
data class ReservationCreated(
    val reservationId: Long,
    val reservationCode: String,
    val qrToken: String,
)
