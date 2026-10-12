package com.appmovilidadclinica.passenger.shared.ui.reportincident

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
import kotlin.test.assertTrue

class ReportIncidentControllerTest {

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
        seatLabel = "3",
        vehicleCode = "V-1",
        plate = "C0C-965",
    )

    private fun controller(
        scope: CoroutineScope,
        reservations: List<Reservation> = listOf(reservation(1)),
        onReport: suspend (Long, String, String) -> AppResult<Long> = { _, _, _ -> AppResult.Success(77L) },
    ) = ReportIncidentController(
        loadReservations = { AppResult.Success(reservations) },
        reportIncident = onReport,
        scope = scope,
    )

    @Test
    fun soloOfreceReservasActivas() = runTest {
        val c = controller(
            scope = this,
            reservations = listOf(
                reservation(1),
                reservation(2, ReservationStatus.CANCELLED),
                reservation(3, ReservationStatus.BOARDED),
                reservation(4, ReservationStatus.COMPLETED),
            ),
        )

        c.load()
        advanceUntilIdle()

        val activas = c.uiState.value.activeReservations.map { it.reservationId }
        assertEquals(listOf(1L, 3L), activas)
    }

    @Test
    fun conUnaSolaReservaQuedaElegida() = runTest {
        val c = controller(this)
        c.load()
        advanceUntilIdle()

        assertEquals(1L, c.uiState.value.selectedReservationId)
    }

    @Test
    fun exigeTipoYDescripcionSuficiente() = runTest {
        var llamadas = 0
        val c = controller(this) { _, _, _ -> llamadas++; AppResult.Success(1L) }
        c.load()
        advanceUntilIdle()

        c.onDescriptionChange("corto")
        c.submit()
        advanceUntilIdle()

        assertEquals("Elige el tipo de incidente.", c.uiState.value.typeError)
        assertEquals("Mínimo 10 caracteres.", c.uiState.value.descriptionError)
        assertEquals(0, llamadas)
    }

    @Test
    fun reporteExitosoMarcaSentYEnviaElTipoCorrecto() = runTest {
        var enviado: Triple<Long, String, String>? = null
        val c = controller(this) { id, tipo, desc ->
            enviado = Triple(id, tipo, desc)
            AppResult.Success(99L)
        }
        c.load()
        advanceUntilIdle()

        c.onTypeChange(IncidentType.DELAY)
        c.onDescriptionChange("El bus pasó 20 minutos tarde")
        c.submit()
        advanceUntilIdle()

        assertTrue(c.uiState.value.sent)
        assertEquals(Triple(1L, "DELAY", "El bus pasó 20 minutos tarde"), enviado)
    }

    @Test
    fun conflictoSeMuestraAlUsuario() = runTest {
        val c = controller(this) { _, _, _ ->
            AppResult.Failure(AppError.Conflict("Ese viaje ya no está activo para reportar."))
        }
        c.load()
        advanceUntilIdle()

        c.onTypeChange(IncidentType.OTHER)
        c.onDescriptionChange("Descripción suficientemente larga")
        c.submit()
        advanceUntilIdle()

        assertEquals("Ese viaje ya no está activo para reportar.", c.uiState.value.formError)
        assertTrue(!c.uiState.value.sent)
    }
}
