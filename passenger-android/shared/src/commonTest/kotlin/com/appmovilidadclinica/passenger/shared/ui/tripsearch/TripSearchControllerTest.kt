package com.appmovilidadclinica.passenger.shared.ui.tripsearch

import com.appmovilidadclinica.passenger.shared.domain.error.AppError
import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import com.appmovilidadclinica.passenger.shared.domain.model.BookingState
import com.appmovilidadclinica.passenger.shared.domain.model.Stop
import com.appmovilidadclinica.passenger.shared.domain.model.StopType
import com.appmovilidadclinica.passenger.shared.domain.model.TripDirection
import com.appmovilidadclinica.passenger.shared.domain.model.TripSearchResult
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reglas de negocio de la busqueda de viajes, las mismas que ya tenia el
 * modulo Android. Corre en comun: es el codigo que usa iOS.
 */
class TripSearchControllerTest {

    private val sede = Stop(id = 1, code = "SEDE-LIMA", name = "Sede Lima", stopType = StopType.SEDE)
    private val paradero = Stop(id = 2, code = "PAR-01", name = "Paradero Surco", stopType = StopType.PARADERO)
    private val otroParadero =
        Stop(id = 4, code = "PAR-02", name = "Paradero San Isidro", stopType = StopType.PARADERO)
    private val otraSede = Stop(id = 3, code = "SEDE-SB", name = "Sede San Borja", stopType = StopType.SEDE)

    private fun trip(id: Long, direction: TripDirection = TripDirection.IDA) = TripSearchResult(
        tripId = id,
        tripCode = "T-$id",
        routeCode = "R-1",
        routeName = "Lima → Surco",
        direction = direction,
        originName = "Sede Lima",
        originDepartureAt = Instant.parse("2026-07-14T13:00:00Z"),
        destinationName = "Paradero Surco",
        destinationArrivalAt = Instant.parse("2026-07-14T14:00:00Z"),
        vehicleCode = "V-1",
        plate = "CJI-579",
        bookingOpensAt = Instant.parse("2026-07-13T13:00:00Z"),
        bookingClosesAt = Instant.parse("2026-07-14T12:30:00Z"),
        bookingState = BookingState.OPEN,
        availableSeats = 12,
    )

    private fun controller(
        stops: List<Stop> = listOf(sede, paradero, otroParadero, otraSede),
        onSearch: (suspend (LocalDate, TripDirection, Long, Long) -> AppResult<List<TripSearchResult>>)? = null,
        scope: kotlinx.coroutines.CoroutineScope,
    ) = TripSearchController(
        loadStops = { AppResult.Success(stops) },
        searchTrips = onSearch ?: { _, _, _, _ -> AppResult.Success(emptyList()) },
        scope = scope,
    )

    @Test
    fun cargaElCatalogoDeParadas() = runTest {
        val c = controller(scope = this)

        c.load()
        advanceUntilIdle()

        assertEquals(4, c.uiState.value.stops.size)
        assertTrue(!c.uiState.value.loadingStops)
    }

    @Test
    fun sinOrigenNiDestinoAvisaYNoBusca() = runTest {
        var llamadas = 0
        val c = controller(onSearch = { _, _, _, _ -> llamadas++; AppResult.Success(emptyList()) }, scope = this)

        c.load()
        advanceUntilIdle()
        c.search()
        advanceUntilIdle()

        assertEquals("Seleccione el origen y el destino.", c.uiState.value.errorMessage)
        assertEquals(0, llamadas)
    }

    @Test
    fun origenIgualADestinoSeRechaza() = runTest {
        val c = controller(scope = this)
        c.load()
        advanceUntilIdle()

        c.onOriginChange(sede.id)
        c.onDestinationChange(sede.id)
        c.search()
        advanceUntilIdle()

        assertEquals(
            "Origen y destino no pueden ser la misma parada.",
            c.uiState.value.errorMessage,
        )
    }

    @Test
    fun paraderoASedeBuscaSoloIda() = runTest {
        val direcciones = mutableListOf<TripDirection>()
        val c = controller(
            onSearch = { _, dir, _, _ -> direcciones += dir; AppResult.Success(listOf(trip(1))) },
            scope = this,
        )
        c.load()
        advanceUntilIdle()

        c.onOriginChange(paradero.id)
        c.onDestinationChange(sede.id)
        c.search()
        advanceUntilIdle()

        assertEquals(listOf(TripDirection.IDA), direcciones)
        assertEquals(1, c.uiState.value.results.size)
        assertTrue(c.uiState.value.hasSearched)
    }

    @Test
    fun sedeAParaderoBuscaSoloVuelta() = runTest {
        val direcciones = mutableListOf<TripDirection>()
        val c = controller(
            onSearch = { _, dir, _, _ -> direcciones += dir; AppResult.Success(listOf(trip(2, dir))) },
            scope = this,
        )
        c.load()
        advanceUntilIdle()

        c.onOriginChange(sede.id)
        c.onDestinationChange(paradero.id)
        c.search()
        advanceUntilIdle()

        assertEquals(listOf(TripDirection.VUELTA), direcciones)
    }

    @Test
    fun sedeASedeBuscaLasDosDireccionesYUneResultados() = runTest {
        val direcciones = mutableListOf<TripDirection>()
        val c = controller(
            onSearch = { _, dir, _, _ ->
                direcciones += dir
                AppResult.Success(listOf(trip(if (dir == TripDirection.IDA) 10 else 11, dir)))
            },
            scope = this,
        )
        c.load()
        advanceUntilIdle()

        c.onOriginChange(sede.id)
        c.onDestinationChange(otraSede.id)
        c.search()
        advanceUntilIdle()

        assertEquals(listOf(TripDirection.IDA, TripDirection.VUELTA), direcciones)
        assertEquals(2, c.uiState.value.results.size)
    }

    @Test
    fun paraderoAParaderoEsInvalidoYNoLlamaAlBackend() = runTest {
        var llamadas = 0
        val c = controller(
            onSearch = { _, _, _, _ -> llamadas++; AppResult.Success(emptyList()) },
            scope = this,
        )
        c.load()
        advanceUntilIdle()

        c.onOriginChange(paradero.id)
        c.onDestinationChange(otroParadero.id)
        c.search()
        advanceUntilIdle()

        assertEquals(
            "No hay viajes configurados para esa combinación de paradas.",
            c.uiState.value.errorMessage,
        )
        assertEquals(0, llamadas)
    }

    @Test
    fun deduplicaLosViajesRepetidos() = runTest {
        val c = controller(
            onSearch = { _, dir, _, _ ->
                // El mismo viaje repetido: no debe aparecer dos veces.
                AppResult.Success(listOf(trip(99, dir), trip(99, dir)))
            },
            scope = this,
        )
        c.load()
        advanceUntilIdle()

        c.onOriginChange(sede.id)
        c.onDestinationChange(otraSede.id)
        c.search()
        advanceUntilIdle()

        assertEquals(1, c.uiState.value.results.size)
    }

    @Test
    fun alCambiarElOrigenSeLimpiaElDestinoQueDejoDeSerValido() = runTest {
        val c = controller(scope = this)
        c.load()
        advanceUntilIdle()

        // sede → paradero es valido (VUELTA)...
        c.onOriginChange(sede.id)
        c.onDestinationChange(paradero.id)

        // ...pero con origen paradero, el destino paradero ya no es valido (IDA).
        c.onOriginChange(paradero.id)

        assertNull(c.uiState.value.destinationStopId)
    }

    @Test
    fun errorDeRedAlCargarParadasSeMuestraAlUsuario() = runTest {
        val c = TripSearchController(
            loadStops = { AppResult.Failure(AppError.Network("timeout")) },
            searchTrips = { _, _, _, _ -> AppResult.Success(emptyList()) },
            scope = this,
        )

        c.load()
        advanceUntilIdle()

        assertTrue(c.uiState.value.errorMessage.orEmpty().contains("No se pudieron cargar las paradas"))
        assertTrue(!c.uiState.value.loadingStops)
    }
}
