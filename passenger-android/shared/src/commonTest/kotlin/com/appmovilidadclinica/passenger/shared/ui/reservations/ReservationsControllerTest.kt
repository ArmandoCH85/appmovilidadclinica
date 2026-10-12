package com.appmovilidadclinica.passenger.shared.ui.reservations

import com.appmovilidadclinica.passenger.shared.domain.error.AppError
import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import com.appmovilidadclinica.passenger.shared.domain.model.Reservation
import com.appmovilidadclinica.passenger.shared.domain.model.ReservationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReservationsControllerTest {

    private fun reservation(id: Long, status: ReservationStatus = ReservationStatus.CONFIRMED) = Reservation(
        reservationId = id,
        reservationCode = "R-000$id",
        qrToken = null,
        tripId = 55,
        tripSeatId = id,
        originTripStopTimeId = 100,
        destinationTripStopTimeId = 200,
        status = status,
        confirmedAt = Instant.parse("2026-07-14T12:00:00Z"),
        routeName = "T-55",
        originName = "Sede Lima",
        destinationName = "Sede Surco",
        originDepartureAt = Instant.parse("2026-07-14T18:20:00Z"),
        seatLabel = "$id",
        vehicleCode = "V-1",
        plate = "C0C-965",
    )

    @Test
    fun cargaLasReservas() = runTest {
        val c = ReservationsController(
            loadReservations = { AppResult.Success(listOf(reservation(1), reservation(2, ReservationStatus.CANCELLED))) },
            cancelReservation = { AppResult.Success(Unit) },
            scope = this,
        )

        c.load()
        advanceUntilIdle()

        assertEquals(2, c.uiState.value.reservations.size)
        assertTrue(!c.uiState.value.loading)
        assertNotNull(c.reservationById(2))
    }

    @Test
    fun errorDeRedSeMuestra() = runTest {
        val c = ReservationsController(
            loadReservations = { AppResult.Failure(AppError.Network("timeout")) },
            cancelReservation = { AppResult.Success(Unit) },
            scope = this,
        )

        c.load()
        advanceUntilIdle()

        assertTrue(c.uiState.value.errorMessage.orEmpty().contains("No se pudo conectar"))
    }

    @Test
    fun cancelarMarcaLaReservaComoCanceladaYMuestraAviso() = runTest {
        var cancelada: Long? = null
        val c = ReservationsController(
            loadReservations = { AppResult.Success(listOf(reservation(7))) },
            cancelReservation = { id -> cancelada = id; AppResult.Success(Unit) },
            scope = this,
        )

        c.load()
        advanceUntilIdle()
        c.cancel(7)
        advanceUntilIdle()

        assertEquals(7L, cancelada)
        assertEquals(ReservationStatus.CANCELLED, c.reservationById(7)?.status)
        assertEquals("Reserva cancelada.", c.uiState.value.notice)
    }

    @Test
    fun conflictoAlCancelarMuestraElMensajeDelBackend() = runTest {
        val c = ReservationsController(
            loadReservations = { AppResult.Success(listOf(reservation(7))) },
            cancelReservation = { AppResult.Failure(AppError.Conflict("La reserva ya fue cancelada.")) },
            scope = this,
        )

        c.load()
        advanceUntilIdle()
        c.cancel(7)
        advanceUntilIdle()

        assertEquals("La reserva ya fue cancelada.", c.uiState.value.notice)
        // No se toco el estado local: sigue como estaba.
        assertEquals(ReservationStatus.CONFIRMED, c.reservationById(7)?.status)
    }

    @Test
    fun noCancelaDosVecesSeguidas() = runTest {
        var llamadas = 0
        val c = ReservationsController(
            loadReservations = { AppResult.Success(listOf(reservation(7))) },
            cancelReservation = { llamadas++; AppResult.Success(Unit) },
            scope = this,
        )

        c.load()
        advanceUntilIdle()
        c.cancel(7)
        c.cancel(7)
        advanceUntilIdle()

        assertEquals(1, llamadas)
    }
}
