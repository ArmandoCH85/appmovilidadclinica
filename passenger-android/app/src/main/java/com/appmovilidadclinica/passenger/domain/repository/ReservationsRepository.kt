package com.appmovilidadclinica.passenger.domain.repository

import com.appmovilidadclinica.passenger.domain.error.AppResult
import com.appmovilidadclinica.passenger.domain.model.Reservation
import com.appmovilidadclinica.passenger.domain.model.ReservationRequest
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface ReservationsRepository {
    /**
     * POST /api/reservations. Contrato: la implementacion DEBE persistir el
     * `qr_token` recibido en Room como primera accion al recibir 201, antes
     * de devolver el resultado — es la unica vez que el backend lo entrega
     * en claro (ver Reservation.qrToken, dominio).
     */
    suspend fun confirm(request: ReservationRequest, tripContext: ReservationTripContext): AppResult<Reservation>

    /** POST /api/reservations/{id}/cancel — 204, sin body. */
    suspend fun cancel(reservationId: Long): AppResult<Unit>

    /**
     * POST /api/reservations/{id}/self-checkin — CONTRATO NUEVO, no existe
     * en el backend todavia (ver diseño técnico, seccion "Contratos nuevos
     * requeridos"). La implementacion esta lista contra el contrato
     * propuesto; hasta que el backend lo tenga, esta llamada devuelve
     * AppError.NotFound (404) real del servidor.
     */
    suspend fun selfCheckin(reservationId: Long): AppResult<Reservation>

    /** Reservas propias persistidas localmente (fuente de verdad: Room, no red). */
    fun observeReservations(): Flow<List<Reservation>>

    fun observeReservation(reservationId: Long): Flow<Reservation?>
}

/**
 * Datos de contexto (ruta/paradas/horario) que YA tiene la app en pantalla
 * al confirmar (vinieron de TripDetail/TripSearchResult) — se persisten
 * junto a la reserva para que "Mi reserva" no dependa de una llamada de red
 * extra para mostrarlos.
 */
data class ReservationTripContext(
    val routeName: String,
    val originName: String,
    val destinationName: String,
    val originDepartureAt: Instant,
    val seatLabel: String,
)
