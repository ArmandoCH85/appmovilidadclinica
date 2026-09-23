package com.appmovilidadclinica.driver.shared.ui.screens.tripdetail

import com.appmovilidadclinica.driver.shared.domain.model.AppError
import com.appmovilidadclinica.driver.shared.domain.model.ReservationStatus
import com.appmovilidadclinica.driver.shared.domain.model.TripStatus
import com.appmovilidadclinica.driver.shared.domain.model.TripStopStatus
import com.appmovilidadclinica.driver.shared.testfakes.FakeDriverRepository
import com.appmovilidadclinica.driver.shared.testfakes.TripFixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests del ViewModel del "Detalle del viaje": flujo operativo completo,
 * anti-duplicado, mensajes de error del backend y confirmaciones por estado.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repo(
        status: TripStatus = TripStatus.PUBLISHED,
        stops: List<com.appmovilidadclinica.driver.shared.domain.model.TripStop> = TripFixtures.stops(),
        passengers: List<com.appmovilidadclinica.driver.shared.domain.model.Passenger> = emptyList(),
        noShowToleranceMinutes: Int = 0,
    ) = FakeDriverRepository(
        trip = TripFixtures.trip(status = status),
        stops = stops,
        passengers = passengers,
        noShowToleranceMinutes = noShowToleranceMinutes,
    )

    /** Ana baja en la parada 2, Luis y María en la 3; María sube en la 2. */
    private fun manifest() = listOf(
        TripFixtures.passenger(
            1L,
            "Ana Quispe",
            seatLabel = "1A",
            originOrder = 1,
            originName = "Sede Surco",
            destinationOrder = 2,
            destinationName = "Paradero Cultura",
        ),
        TripFixtures.passenger(
            2L,
            "Luis Ramos",
            seatLabel = "2A",
            originOrder = 1,
            originName = "Sede Surco",
            destinationOrder = 3,
            destinationName = "Sede Lima",
        ),
        TripFixtures.passenger(
            3L,
            "María García",
            seatLabel = "3A",
            originOrder = 2,
            originName = "Paradero Cultura",
            destinationOrder = 3,
            destinationName = "Sede Lima",
        ),
    )

    @Test
    fun cargaInicialPublicaViajeProgramado() = runTest(testDispatcher) {
        val vm = TripDetailViewModel(TripFixtures.TRIP_ID, null, repo(passengers = manifest()))
        vm.load()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(false, state.loading)
        assertNull(state.loadErrorMessage)
        assertEquals(TripPhase.SCHEDULED, state.model.phase)
        assertEquals("Iniciar viaje", state.model.primary?.label)
        assertEquals(2, state.model.expectedBoarders)
        vm.dispose()
    }

    @Test
    fun cargaFallidaMuestraErrorSinPerderLaPantalla() = runTest(testDispatcher) {
        val fake = repo()
        fake.forcedErrorOperation = "getPassengers"
        fake.forcedError = AppError.Network("timeout")
        val vm = TripDetailViewModel(TripFixtures.TRIP_ID, null, fake)
        vm.load()
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.loading)
        assertEquals("Sin conexión. Verifique los datos móviles e intente otra vez.", vm.uiState.value.loadErrorMessage)
        vm.dispose()
    }

    @Test
    fun flujoCompletoIniciarLlegarAbordarSalirLlegarYFinalizar() = runTest(testDispatcher) {
        val fake = repo(passengers = manifest())
        val vm = TripDetailViewModel(TripFixtures.TRIP_ID, null, fake)
        vm.load()
        advanceUntilIdle()

        // 1. Viaje programado -> Iniciar viaje (registra el inicio, NO la llegada).
        assertEquals(TripPhase.SCHEDULED, vm.uiState.value.model.phase)
        vm.startTrip()
        advanceUntilIdle()
        assertEquals(TripStatus.IN_PROGRESS, fake.trip.status)
        assertEquals(TripStopStatus.PENDING, fake.stopState(1))
        assertEquals(TripPhase.EN_ROUTE, vm.uiState.value.model.phase)
        assertEquals("Llegué a Sede Surco", vm.uiState.value.model.primary?.label)
        assertEquals("Viaje iniciado", vm.uiState.value.notice?.title)
        assertEquals(1, fake.countCalls("startTrip"))

        // 2. Llegada a la primera parada.
        vm.markArrival(101L)
        advanceUntilIdle()
        assertEquals(TripStopStatus.ARRIVED, fake.stopState(1))
        assertEquals(TripPhase.AT_STOP, vm.uiState.value.model.phase)
        assertEquals(2, vm.uiState.value.model.pendingBoarders)
        // La parada no se puede cerrar con pasajeros por registrar.
        assertNotNull(vm.uiState.value.model.primaryBlockedReason)
        assertTrue(vm.uiState.value.notice?.detail.orEmpty().contains("Hora real de llegada"))

        // 3. Abordaje de los dos pasajeros de esta parada.
        vm.board(1L)
        advanceUntilIdle()
        assertEquals(ReservationStatus.BOARDED, fake.passengerState(1L))
        vm.board(2L)
        advanceUntilIdle()
        assertEquals(ReservationStatus.BOARDED, fake.passengerState(2L))
        assertEquals(0, vm.uiState.value.model.pendingBoarders)
        assertNull(vm.uiState.value.model.primaryBlockedReason)
        assertEquals("Salir de Sede Surco", vm.uiState.value.model.primary?.label)

        // 4. Salida de la parada -> se guarda la hora real y la siguiente parada queda destacada.
        vm.markDeparture(101L)
        advanceUntilIdle()
        assertEquals(TripStopStatus.DEPARTED, fake.stopState(1))
        val afterDeparture = vm.uiState.value.model
        assertEquals(TripPhase.EN_ROUTE, afterDeparture.phase)
        assertEquals("Paradero Cultura", afterDeparture.focusStop?.name)
        assertEquals("Llegué a Paradero Cultura", afterDeparture.primary?.label)
        assertEquals(1, afterDeparture.expectedBoarders)
        assertEquals(1, afterDeparture.expectedAlighters)
        assertTrue(vm.uiState.value.notice?.title.orEmpty().contains("Salida registrada"))

        // 5. Llegada a la segunda parada: Ana ya bajo (bajada automatica) y Maria sube aqui.
        vm.markArrival(102L)
        advanceUntilIdle()
        val atSecondStop = vm.uiState.value.model
        assertEquals(TripPhase.AT_STOP, atSecondStop.phase)
        assertEquals(listOf("María García"), atSecondStop.boarders.map { it.displayName })
        val ana = atSecondStop.alighters.single()
        assertEquals("Ana Quispe", ana.displayName)
        assertTrue(ana.automatic)
        // Luis sigue a bordo y su destino no es esta parada: no aparece aqui.
        assertTrue(atSecondStop.boarders.none { it.displayName == "Luis Ramos" })
        assertTrue(atSecondStop.alighters.none { it.displayName == "Luis Ramos" })

        vm.board(3L)
        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.model.pendingBoarders)
        assertEquals("Salir de Paradero Cultura", vm.uiState.value.model.primary?.label)

        // 6. Salida y llegada a la ultima parada.
        vm.markDeparture(102L)
        advanceUntilIdle()
        assertEquals("Sede Lima", vm.uiState.value.model.focusStop?.name)
        assertEquals(2, vm.uiState.value.model.expectedAlighters)

        vm.markArrival(103L)
        advanceUntilIdle()
        val atLastStop = vm.uiState.value.model
        assertEquals(TripPhase.AT_STOP, atLastStop.phase)
        assertTrue(atLastStop.focusIsLast)
        assertEquals(2, atLastStop.alighters.size)
        assertTrue(atLastStop.alighters.all { it.automatic })
        assertEquals(0, atLastStop.pendingBoarders)
        // En la ultima parada la accion es finalizar el viaje.
        assertEquals("Finalizar viaje", atLastStop.primary?.label)

        // 7. Finalizar: registra la salida real de la ultima parada y cierra el viaje.
        vm.finishTrip()
        advanceUntilIdle()
        assertEquals(TripStopStatus.DEPARTED, fake.stopState(3))
        assertEquals(TripStatus.COMPLETED, fake.trip.status)
        assertEquals(TripPhase.FINISHED, vm.uiState.value.model.phase)
        assertNull(vm.uiState.value.model.primary)
        val notice = vm.uiState.value.notice
        assertEquals("Viaje finalizado", notice?.title)
        assertTrue(notice?.detail.orEmpty().contains("Salida de Sede Lima"))
        assertTrue(notice?.detail.orEmpty().contains("Fin del viaje"))
        assertEquals(1, fake.countCalls("completeTrip"))
        vm.dispose()
    }

    @Test
    fun dosToquesRapidosNoDuplicanElRegistro() = runTest(testDispatcher) {
        val fake = repo(passengers = manifest())
        fake.latencyMillis = 2_000
        val vm = TripDetailViewModel(TripFixtures.TRIP_ID, null, fake)
        vm.load()
        advanceUntilIdle()

        vm.startTrip()
        vm.startTrip()
        vm.startTrip()
        advanceUntilIdle()

        assertEquals(1, fake.countCalls("startTrip"))
        assertNull(vm.uiState.value.pending)
        vm.dispose()
    }

    @Test
    fun accionConErrorMuestraElMensajeDelBackendYNoCambiaElEstadoLocal() = runTest(testDispatcher) {
        val fake = repo(status = TripStatus.IN_PROGRESS, passengers = manifest())
        val vm = TripDetailViewModel(TripFixtures.TRIP_ID, null, fake)
        vm.load()
        advanceUntilIdle()

        fake.forcedErrorOperation = "markArrival"
        fake.forcedError = AppError.Conflict("El viaje debe estar iniciado para marcar la llegada")
        vm.markArrival(101L)
        advanceUntilIdle()

        assertEquals(TripPhase.EN_ROUTE, vm.uiState.value.model.phase)
        assertEquals(TripStopStatus.PENDING, fake.stopState(1))
        val notice = vm.uiState.value.notice
        assertEquals(NoticeKind.ERROR, notice?.kind)
        assertEquals("No se registró la llegada", notice?.title)
        assertEquals("El viaje debe estar iniciado para marcar la llegada", notice?.detail)
        assertNull(vm.uiState.value.pending)
        vm.dispose()
    }

    @Test
    fun noShowAntesDeLaToleranciaExplicaElMotivoYSiguePendiente() = runTest(testDispatcher) {
        val fake = repo(
            status = TripStatus.IN_PROGRESS,
            stops = TripFixtures.stops(
                firstStatus = TripStopStatus.ARRIVED,
                // Llegada registrada antes del reloj del backend falso (11:00Z),
                // como ocurre en la operacion real.
                firstArrival = kotlinx.datetime.Instant.parse("2026-09-25T10:57:00Z"),
            ),
            passengers = manifest(),
            noShowToleranceMinutes = 5,
        )
        val vm = TripDetailViewModel(TripFixtures.TRIP_ID, null, fake)
        vm.load()
        advanceUntilIdle()

        vm.noShow(1L)
        advanceUntilIdle()

        val notice = vm.uiState.value.notice
        assertEquals(NoticeKind.ERROR, notice?.kind)
        assertEquals("Aún no terminó el tiempo de tolerancia de NO_SHOW", notice?.detail)
        assertEquals(ReservationStatus.CONFIRMED, fake.passengerState(1L))
        assertEquals(2, vm.uiState.value.model.pendingBoarders)
        vm.dispose()
    }

    @Test
    fun noShowExitosoSeConfirmaConEstadoYHoraAunqueElBackendYaNoLoListe() = runTest(testDispatcher) {
        val fake = repo(
            status = TripStatus.IN_PROGRESS,
            stops = TripFixtures.stops(
                firstStatus = TripStopStatus.ARRIVED,
                // Llegada registrada antes del reloj del backend falso (11:00Z),
                // como ocurre en la operacion real.
                firstArrival = kotlinx.datetime.Instant.parse("2026-09-25T10:57:00Z"),
            ),
            passengers = manifest(),
        )
        val vm = TripDetailViewModel(TripFixtures.TRIP_ID, null, fake)
        vm.load()
        advanceUntilIdle()

        vm.noShow(1L)
        advanceUntilIdle()

        assertEquals(ReservationStatus.NO_SHOW, fake.passengerState(1L))
        assertEquals(NoticeKind.SUCCESS, vm.uiState.value.notice?.kind)
        assertTrue(vm.uiState.value.notice?.title.orEmpty().contains("Ana Quispe"))
        // El pasajero sale del manifiesto del backend, pero la pantalla lo
        // sigue mostrando como resuelto para confirmar la marca.
        val row = vm.uiState.value.model.boarders.single { it.reservationId == 1L }
        assertEquals(PassengerMarkState.NO_SHOW, row.state)
        assertNotNull(row.timeLabel)
        assertEquals(1, vm.uiState.value.model.pendingBoarders)
        vm.dispose()
    }

    @Test
    fun refrescoDetectaResolucionAutomaticaDelSistema() = runTest(testDispatcher) {
        val fake = repo(
            status = TripStatus.IN_PROGRESS,
            stops = TripFixtures.stops(
                firstStatus = TripStopStatus.ARRIVED,
                // Llegada registrada antes del reloj del backend falso (11:00Z),
                // como ocurre en la operacion real.
                firstArrival = kotlinx.datetime.Instant.parse("2026-09-25T10:57:00Z"),
            ),
            passengers = manifest(),
        )
        val vm = TripDetailViewModel(TripFixtures.TRIP_ID, null, fake)
        vm.load()
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.model.pendingBoarders)

        // El job automatico del backend marca NO_SHOW al vencer la tolerancia.
        fake.advanceBackendClock(minutes = 10)
        fake.markNoShow(1L)
        vm.refreshNow()
        advanceUntilIdle()

        val notice = vm.uiState.value.notice
        assertEquals(NoticeKind.INFO, notice?.kind)
        assertTrue(notice?.detail.orEmpty().contains("No se presentó"))
        assertEquals(1, vm.uiState.value.model.pendingBoarders)
        vm.dispose()
    }

    @Test
    fun finalizarFallaSiNoSePuedeRegistrarLaSalidaYElViajeSigueEnCurso() = runTest(testDispatcher) {
        val fake = repo(
            status = TripStatus.IN_PROGRESS,
            stops = TripFixtures.stops(
                firstStatus = TripStopStatus.ARRIVED,
                secondStatus = TripStopStatus.DEPARTED,
                thirdStatus = TripStopStatus.DEPARTED,
                // Llegada registrada antes del reloj del backend falso (11:00Z),
                // como ocurre en la operacion real.
                firstArrival = kotlinx.datetime.Instant.parse("2026-09-25T10:57:00Z"),
            ),
            passengers = emptyList(),
        )
        val vm = TripDetailViewModel(TripFixtures.TRIP_ID, null, fake)
        vm.load()
        advanceUntilIdle()

        fake.forcedErrorOperation = "markDeparture"
        fake.forcedError = AppError.Conflict("El paradero ya fue marcado como salido")
        vm.finishTrip()
        advanceUntilIdle()

        assertEquals(TripStatus.IN_PROGRESS, fake.trip.status)
        assertEquals(0, fake.countCalls("completeTrip"))
        val notice = vm.uiState.value.notice
        assertEquals(NoticeKind.ERROR, notice?.kind)
        assertTrue(notice?.title.orEmpty().contains("No se registró la salida"))
        assertTrue(notice?.detail.orEmpty().contains("El viaje sigue en curso"))
        vm.dispose()
    }

    @Test
    fun errorDeRedEnUnaAccionSeExplicaEnLenguajeClaro() = runTest(testDispatcher) {
        val fake = repo(status = TripStatus.IN_PROGRESS, passengers = manifest())
        val vm = TripDetailViewModel(TripFixtures.TRIP_ID, null, fake)
        vm.load()
        advanceUntilIdle()

        fake.forcedErrorOperation = "startTrip"
        fake.forcedError = AppError.Network("timeout")
        vm.startTrip()
        advanceUntilIdle()

        assertEquals(
            "Sin conexión. Verifique los datos móviles e intente otra vez.",
            vm.uiState.value.notice?.detail,
        )
        vm.dispose()
    }

    @Test
    fun marcasLocalesDeUnaParadaNoSeArrastranALaSiguiente() = runTest(testDispatcher) {
        val fake = repo(
            status = TripStatus.IN_PROGRESS,
            stops = TripFixtures.stops(
                firstStatus = TripStopStatus.ARRIVED,
                // Llegada registrada antes del reloj del backend falso (11:00Z),
                // como ocurre en la operacion real.
                firstArrival = kotlinx.datetime.Instant.parse("2026-09-25T10:57:00Z"),
            ),
            passengers = manifest(),
        )
        val vm = TripDetailViewModel(TripFixtures.TRIP_ID, null, fake)
        vm.load()
        advanceUntilIdle()

        vm.noShow(1L)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.model.boarders.count { it.state == PassengerMarkState.NO_SHOW })

        vm.board(2L)
        advanceUntilIdle()
        vm.markDeparture(101L)
        advanceUntilIdle()

        // Ya en la siguiente parada: sin manifiesto de abordaje ni marcas viejas.
        assertEquals(TripPhase.EN_ROUTE, vm.uiState.value.model.phase)
        assertTrue(vm.uiState.value.model.boarders.isEmpty())
        vm.dispose()
    }
}
