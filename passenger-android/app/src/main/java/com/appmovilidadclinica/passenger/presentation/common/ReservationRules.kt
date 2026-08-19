package com.appmovilidadclinica.passenger.presentation.common

import com.appmovilidadclinica.passenger.shared.domain.model.Reservation
import com.appmovilidadclinica.passenger.shared.domain.model.ReservationStatus
import java.time.Duration
import java.time.Instant

/**
 * Ver Specs #5: el boton de auto-confirmacion solo se habilita en una
 * ventana razonable alrededor del horario de salida â€” evita "auto-abordarse"
 * desde cualquier lado en cualquier momento. Ventana sugerida: +-30min.
 * Compartido entre MyReservationDetailViewModel y MyReservationsViewModel
 * (antes duplicado en el primero).
 */
private val SELF_CHECKIN_WINDOW: Duration = Duration.ofMinutes(30)

fun Reservation.canSelfCheckin(now: Instant = Instant.now()): Boolean {
    if (status != ReservationStatus.CONFIRMED) return false
    val windowStart = originDepartureAt.minus(SELF_CHECKIN_WINDOW)
    val windowEnd = originDepartureAt.plus(SELF_CHECKIN_WINDOW)
    return !now.isBefore(windowStart) && !now.isAfter(windowEnd)
}
