package com.appmovilidadclinica.passenger.shared.ui.seatselection

import com.appmovilidadclinica.passenger.shared.domain.error.AppError
import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import com.appmovilidadclinica.passenger.shared.domain.model.BookingState
import com.appmovilidadclinica.passenger.shared.domain.model.ReservationCreated
import com.appmovilidadclinica.passenger.shared.domain.model.ReservationRequest
import com.appmovilidadclinica.passenger.shared.domain.model.SeatAvailability
import com.appmovilidadclinica.passenger.shared.domain.model.TripDetail
import com.appmovilidadclinica.passenger.shared.domain.model.TripDirection
import com.appmovilidadclinica.passenger.shared.domain.model.TripSeat
import com.appmovilidadclinica.passenger.shared.domain.model.TripStatus
import com.appmovilidadclinica.passenger.shared.domain.model.TripStop
import com.appmovilidadclinica.passenger.shared.domain.model.TripStopStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Seleccion de asiento y confirmacion de reserva en codigo comun (lo que va a
 * correr en iOS). Incluye las validaciones que ya tenia el modulo Android.
 */
class SeatSelectionControllerTest {

    private val originStop = TripStop(
        tripStopTimeId = 100,
        stopId = 1,
        stopOrder = 1,
        stopName = "Sede Lima",
        scheduledArrivalAt = Instant.parse("2026-07-14T13:00:00Z"),
        scheduledDepartureAt = Instant.parse("2026-07-14T13:05:00Z"),
        actualArrivalAt = null,
        actualDepartureAt = null,
        status = TripStopStatus.PENDING,
    )

    private val destinationStop = TripStop(
        tripStopTimeId = 200,
        stopId = 2,
        stopOrder = 2,
        stopName = "Paradero Surco",
        scheduledArrivalAt = Instant.parse("2026-07-14T13:30:00Z"),
        scheduledDepartureAt = Instant.parse("2026-07-14T13:35:00Z"),
        actualArrivalAt = null,
        actualDepartureAt = null,
        status = TripStopStatus.PENDING,
    )

    private val detail = TripDetail(
        tripId = 55,
        tripCode = "T-55",
        routeId = 1,
        serviceDate = LocalDate.parse("2026-07-14"),
        scheduledStartAt = Instant.parse("2026-07-14T13:00:00Z"),
        scheduledEndAt = Instant.parse("2026-07-14T14:00:00Z"),
        bookingOpensAt = Instant.parse("2026-07-13T13:00:00Z"),
        bookingClosesAt = Instant.parse("2026-07-14T12:30:00Z"),
        status = TripStatus.PUBLISHED,
        stops = listOf(originStop, destinationStop),
    )

    private val seats = listOf(
        TripSeat(1, 1, "1", SeatAvailability.AVAILABLE),
        TripSeat(2, 2, "2", SeatAvailability.OCCUPIED_IN_REQUESTED_RANGE),
        TripSeat(3, 3, "3", SeatAvailability.BLOCKED),
    )

    private fun controller(
        scope: CoroutineScope,
        detail: AppResult<TripDetail> = AppResult.Success(this.detail),
        seats: AppResult<List<TripSeat>> = AppResult.Success(this.seats),
        onConfirm: suspend (ReservationRequest) -> AppResult<ReservationCreated> = {
            AppResult.Success(ReservationCreated(9L, "R-0009", "qr-token"))
        },
        originStopId: Long = 1,
        destinationStopId: Long = 2,
    ) = SeatSelectionController(
        tripId = 55,
        originStopId = originStopId,
        destinationStopId = destinationStopId,
        loadTripDetail = { detail },
        listSeats = { _, _, _ -> seats },
        confirmReservation = onConfirm,
        scope = scope,
    )

    @Test
    fun cargaElViajeYLosAsientos() = runTest {
        val c = controller(scope = this)

        c.load()
        advanceUntilIdle()

        val state = c.uiState.value
        assertEquals(3, state.seats.size)
        assertEquals(100L, state.origin?.tripStopTimeId)
        assertEquals(200L, state.destination?.tripStopTimeId)
        assertTrue(!state.loading)
        assertNull(state.errorMessage)
    }

    @Test
    fun paradasQueNoPertenecenAlViajeSeAvisan() = runTest {
        val c = controller(scope = this, originStopId = 999)

        c.load()
        advanceUntilIdle()

        assertEquals(
            "Las paradas elegidas no pertenecen a este viaje.",
            c.uiState.value.errorMessage,
        )
    }

    @Test
    fun origenDespuesDelDestinoSeRechazaAntesDePedirAsientos() = runTest {
        var asientosPedidos = false
        val c = SeatSelectionController(
            tripId = 55,
            // Invertidos a proposito.
            originStopId = 2,
            destinationStopId = 1,
            loadTripDetail = { AppResult.Success(detail) },
            listSeats = { _, _, _ -> asientosPedidos = true; AppResult.Success(seats) },
            confirmReservation = { AppResult.Success(ReservationCreated(1, "R", "qr")) },
            scope = this,
        )

        c.load()
        advanceUntilIdle()

        assertEquals("El origen debe ir antes que el destino en la ruta.", c.uiState.value.errorMessage)
        assertTrue(!asientosPedidos, "No deberia pedir asientos con el tramo invertido")
    }

    @Test
    fun noSePuedeElegirUnAsientoNoDisponible() = runTest {
        val c = controller(scope = this)
        c.load()
        advanceUntilIdle()

        c.selectSeat(2) // ocupado en el tramo
        assertNull(c.uiState.value.selectedSeatId)

        c.selectSeat(3) // bloqueado
        assertNull(c.uiState.value.selectedSeatId)

        c.selectSeat(1) // disponible
        assertEquals(1L, c.uiState.value.selectedSeatId)
    }

    @Test
    fun confirmarSinAsientoElegidoAvisaYNoLlamaAlBackend() = runTest {
        var llamadas = 0
        val c = controller(scope = this, onConfirm = { llamadas++; AppResult.Success(ReservationCreated(1, "R", "qr")) })

        c.load()
        advanceUntilIdle()
        c.confirm()
        advanceUntilIdle()

        assertEquals("Elija un asiento disponible.", c.uiState.value.errorMessage)
        assertEquals(0, llamadas)
    }

    @Test
    fun confirmarDevuelveLaReservaConSuContexto() = runTest {
        val c = controller(scope = this)
        c.load()
        advanceUntilIdle()

        c.selectSeat(1)
        c.confirm()
        advanceUntilIdle()

        val confirmation = c.uiState.value.confirmed
        assertNotNull(confirmation)
        assertEquals(9L, confirmation.reservationId)
        assertEquals("R-0009", confirmation.reservationCode)
        assertEquals("Sede Lima", confirmation.originName)
        assertEquals("Paradero Surco", confirmation.destinationName)
        assertEquals("1", confirmation.seatLabel)
        assertTrue(!c.uiState.value.confirming)
    }

    @Test
    fun noSeConfirmaDosVeces() = runTest {
        var llamadas = 0
        val c = controller(
            scope = this,
            onConfirm = { llamadas++; AppResult.Success(ReservationCreated(1, "R", "qr")) },
        )

        c.load()
        advanceUntilIdle()
        c.selectSeat(1)
        c.confirm()
        c.confirm() // doble toque
        advanceUntilIdle()

        assertEquals(1, llamadas)
    }

    @Test
    fun conflictoDeAsientoMuestraElMensajeDelBackend() = runTest {
        val c = controller(
            scope = this,
            onConfirm = { AppResult.Failure(AppError.Conflict("El asiento ya fue reservado.")) },
        )

        c.load()
        advanceUntilIdle()
        c.selectSeat(1)
        c.confirm()
        advanceUntilIdle()

        assertEquals("El asiento ya fue reservado.", c.uiState.value.errorMessage)
        assertNull(c.uiState.value.confirmed)
    }
}
