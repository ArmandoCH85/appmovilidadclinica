package com.appmovilidadclinica.passenger.presentation.common

import com.appmovilidadclinica.passenger.shared.domain.model.Reservation
import com.appmovilidadclinica.passenger.shared.domain.model.ReservationStatus
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Ver Specs #5: el boton de auto-confirmacion solo se habilita en una
 * ventana razonable alrededor del horario de salida ” evita "auto-abordarse"
 * desde cualquier lado en cualquier momento. Ventana sugerida: +-30min.
 * Compartido entre MyReservationDetailViewModel y MyReservationsViewModel
 * (antes duplicado en el primero).
 *
 * Multiplataforma: usa Instant/Duration de kotlinx (antes java.time).
 */
private val SELF_CHECKIN_WINDOW: Duration = 30.minutes

fun Reservation.canSelfCheckin(now: Instant = Clock.System.now()): Boolean {
    if (status != ReservationStatus.CONFIRMED) return false
    val windowStart = originDepartureAt - SELF_CHECKIN_WINDOW
    val windowEnd = originDepartureAt + SELF_CHECKIN_WINDOW
    return now >= windowStart && now <= windowEnd
}
