package com.appmovilidadclinica.driver.shared.ui.screens.tripdetail

import com.appmovilidadclinica.driver.shared.domain.model.ReservationStatus
import com.appmovilidadclinica.driver.shared.domain.model.TripStatus
import com.appmovilidadclinica.driver.shared.domain.model.TripStopStatus
import com.appmovilidadclinica.driver.shared.testfakes.TripFixtures
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests del modelo de presentacion (puro). Cubren el flujo operativo completo
 * y las reglas de "una accion principal a la vez".
 */
class TripDetailModelTest {

    @Test
    fun viajeProgramadoMuestraPrimeraParadaYAccionIniciarViaje() {
        val model = buildTripDetailModel(
            trip = TripFixtures.trip(status = TripStatus.PUBLISHED),
            stops = TripFixtures.stops(),
            passengers = listOf(
                TripFixtures.passenger(1L, originOrder = 1),
                TripFixtures.passenger(2L, originOrder = 1, seatLabel = "4B"),
                TripFixtures.passenger(3L, originOrder = 2, seatLabel = "5A"),
            ),
        )

        assertEquals(TripPhase.SCHEDULED, model.phase)
        assertEquals("Iniciar viaje", model.primary?.label)
        assertEquals("Sede Surco · 06:05 AM", model.firstStopLabel)
        assertEquals(2, model.expectedBoarders)
        // Iniciar el viaje no muestra ninguna parada como completada.
        assertTrue(model.stops.none { it.status == TripStopStatus.ARRIVED })
        assertTrue(model.boarders.isEmpty())
        assertEquals("06:05 AM – 07:20 AM", model.header?.scheduleLabel)
        assertEquals("ABC-123", model.header?.plateLabel)
        assertEquals("Ida", model.header?.directionLabel)
    }

    @Test
    fun viajeEnCursoDestacaSiguienteParadaConSuHoraYPasajerosEsperados() {
        val model = buildTripDetailModel(
            trip = TripFixtures.trip(status = TripStatus.IN_PROGRESS),
            stops = TripFixtures.stops(),
            passengers = listOf(
                TripFixtures.passenger(1L, originOrder = 1),
                TripFixtures.passenger(2L, originOrder = 1, seatLabel = "4B"),
            ),
        )

        assertEquals(TripPhase.EN_ROUTE, model.phase)
        assertEquals("Llegué a Sede Surco", model.primary?.label)
        assertEquals("Sede Surco", model.focusStop?.name)
        assertEquals("06:05 AM", model.focusStop?.scheduledLabel)
        assertEquals(2, model.expectedBoarders)
        assertEquals(2, model.focusStop?.pendingPassengers)
        // La accion es de llegada, no de salida: la salida exige llegada previa.
        assertTrue(model.primary is PrimaryAction.ArriveAtStop)
    }

    @Test
    fun llegadaRegistradaMuestraManifiestoDeLaParadaActualYPendientes() {
        val model = buildTripDetailModel(
            trip = TripFixtures.trip(status = TripStatus.IN_PROGRESS),
            stops = TripFixtures.stops(
                firstStatus = TripStopStatus.ARRIVED,
                firstArrival = Instant.parse("2026-09-25T11:07:00Z"),
            ),
            passengers = listOf(
                TripFixtures.passenger(1L, "Ana Quispe", seatLabel = "1A", originOrder = 1),
                TripFixtures.passenger(2L, "Luis Ramos", seatLabel = "2A", originOrder = 1),
                TripFixtures.passenger(
                    3L,
                    "María García",
                    seatLabel = "3A",
                    originOrder = 1,
                    status = ReservationStatus.BOARDED,
                    boardedAt = Instant.parse("2026-09-25T11:08:00Z"),
                ),
                // Pasajero que sube en la parada 2: no debe aparecer aqui.
                TripFixtures.passenger(4L, "Otro Pasajero", originOrder = 2),
            ),
        )

        assertEquals(TripPhase.AT_STOP, model.phase)
        assertEquals(3, model.boarders.size)
        assertEquals(2, model.pendingBoarders)
        assertEquals("Salir de Sede Surco", model.primary?.label)
        // No se puede cerrar la parada con pasajeros por registrar.
        assertEquals("Faltan registrar 2 pasajeros en esta parada.", model.primaryBlockedReason)
        assertTrue(model.canOverrideDeparture)
        assertEquals("Llegada registrada: 06:07 AM", model.focusStop?.arrivalLabel?.let { "Llegada registrada: $it" })

        val boarded = model.boarders.single { it.reservationId == 3L }
        assertEquals(PassengerMarkState.BOARDED, boarded.state)
        assertEquals("06:08 AM", boarded.timeLabel)

        val pending = model.boarders.filter { it.state == PassengerMarkState.PENDING }
        assertEquals(listOf("Ana Quispe", "Luis Ramos"), pending.map { it.displayName })
    }

    @Test
    fun paradaSinPendientesHabilitaSalidaYSinTextoDeBloqueo() {
        val model = buildTripDetailModel(
            trip = TripFixtures.trip(status = TripStatus.IN_PROGRESS),
            stops = TripFixtures.stops(
                firstStatus = TripStopStatus.ARRIVED,
                firstArrival = Instant.parse("2026-09-25T11:07:00Z"),
            ),
            passengers = emptyList(),
        )

        assertEquals(TripPhase.AT_STOP, model.phase)
        assertEquals(0, model.pendingBoarders)
        assertNull(model.primaryBlockedReason)
        assertEquals(false, model.canOverrideDeparture)
        assertEquals("Salir de Sede Surco", model.primary?.label)
    }

    @Test
    fun bajadaSeMuestraComoRegistradaConLaHoraDeLlegada() {
        // La llegada a la parada cierra automaticamente las reservas cuyo
        // destino es esa parada: el manifiesto posterior ya no las incluye.
        val model = buildTripDetailModel(
            trip = TripFixtures.trip(status = TripStatus.IN_PROGRESS),
            stops = TripFixtures.stops(
                firstStatus = TripStopStatus.ARRIVED,
                firstArrival = Instant.parse("2026-09-25T11:07:00Z"),
            ),
            passengers = emptyList(),
            arrivalSnapshot = ArrivalSnapshot(
                stopId = 101L,
                occupants = listOf(
                    TripFixtures.passenger(
                        reservationId = 7L,
                        name = "Rosa Flores",
                        originOrder = 2,
                        destinationOrder = 1,
                        status = ReservationStatus.BOARDED,
                    ),
                ),
            ),
        )

        val alighter = model.alighters.single()
        assertEquals("Rosa Flores", alighter.displayName)
        assertEquals(PassengerMarkState.ALIGHTED, alighter.state)
        assertTrue(alighter.automatic)
        assertEquals("06:07 AM", alighter.timeLabel)
    }

    @Test
    fun bajadaSinRegistrarOfreceElBotonBajó() {
        val model = buildTripDetailModel(
            trip = TripFixtures.trip(status = TripStatus.IN_PROGRESS),
            stops = TripFixtures.stops(
                thirdStatus = TripStopStatus.ARRIVED,
                firstStatus = TripStopStatus.DEPARTED,
                secondStatus = TripStopStatus.DEPARTED,
            ),
            passengers = listOf(
                TripFixtures.passenger(
                    8L,
                    "Carlos Núñez",
                    originOrder = 1,
                    destinationOrder = 3,
                    status = ReservationStatus.BOARDED,
                    boardedAt = Instant.parse("2026-09-25T11:08:00Z"),
                ),
            ),
        )

        val alighter = model.alighters.single()
        assertEquals(PassengerMarkState.BOARDED, alighter.state)
        assertEquals(false, alighter.automatic)
    }

    @Test
    fun marcaLocalNoShowSeMantieneVisibleAunqueElBackendYaNoLoDevuelva() {
        val passenger = TripFixtures.passenger(9L, "Julia Paredes", originOrder = 1)
        val model = buildTripDetailModel(
            trip = TripFixtures.trip(status = TripStatus.IN_PROGRESS),
            stops = TripFixtures.stops(
                firstStatus = TripStopStatus.ARRIVED,
                firstArrival = Instant.parse("2026-09-25T11:07:00Z"),
            ),
            passengers = emptyList(),
            localMarks = listOf(
                LocalMark(
                    passenger = passenger,
                    state = PassengerMarkState.NO_SHOW,
                    stopId = 101L,
                    at = Instant.parse("2026-09-25T11:13:00Z"),
                ),
            ),
        )

        val row = model.boarders.single()
        assertEquals(PassengerMarkState.NO_SHOW, row.state)
        assertEquals("Julia Paredes", row.displayName)
        assertEquals("06:13 AM", row.timeLabel)
        assertEquals(0, model.pendingBoarders)
    }

    @Test
    fun pasajeroConfirmadoConSubidaYaPasadaSeResuelveEnSuParadaDeDestino() {
        val model = buildTripDetailModel(
            trip = TripFixtures.trip(status = TripStatus.IN_PROGRESS),
            stops = TripFixtures.stops(
                thirdStatus = TripStopStatus.ARRIVED,
                firstStatus = TripStopStatus.DEPARTED,
                secondStatus = TripStopStatus.DEPARTED,
            ),
            passengers = listOf(
                TripFixtures.passenger(10L, "Pedro Salas", originOrder = 1, destinationOrder = 3),
            ),
        )

        val row = model.boarders.single()
        assertEquals(PassengerMarkState.PENDING, row.state)
        assertTrue(row.boardingStopAlreadyPassed)
        assertEquals("Sede Surco", row.boardingStopName)
        assertEquals("Finalizar viaje", model.primary?.label)
    }

    @Test
    fun ultimaParadaConPendientesBloqueaFinalizarYOfreceOverride() {
        val model = buildTripDetailModel(
            trip = TripFixtures.trip(status = TripStatus.IN_PROGRESS),
            stops = TripFixtures.stops(
                thirdStatus = TripStopStatus.ARRIVED,
                firstStatus = TripStopStatus.DEPARTED,
                secondStatus = TripStopStatus.DEPARTED,
            ),
            passengers = listOf(
                TripFixtures.passenger(11L, "Elena Torres", originOrder = 3, destinationOrder = 3),
            ),
        )

        assertEquals(TripPhase.AT_STOP, model.phase)
        assertEquals(true, model.focusIsLast)
        assertEquals("Finalizar viaje", model.primary?.label)
        assertEquals("Falta registrar 1 pasajero en esta parada.", model.primaryBlockedReason)
        assertTrue(model.canOverrideDeparture)
    }

    @Test
    fun todasLasParadasResueltasSoloPermiteFinalizar() {
        val model = buildTripDetailModel(
            trip = TripFixtures.trip(status = TripStatus.IN_PROGRESS),
            stops = TripFixtures.stops(
                firstStatus = TripStopStatus.DEPARTED,
                secondStatus = TripStopStatus.DEPARTED,
                thirdStatus = TripStopStatus.DEPARTED,
            ),
            passengers = emptyList(),
        )

        assertEquals(TripPhase.READY_TO_FINISH, model.phase)
        assertEquals("Finalizar viaje", model.primary?.label)
        assertNull(model.primaryBlockedReason)
    }

    @Test
    fun viajeFinalizadoNoTieneAccionPrincipal() {
        val model = buildTripDetailModel(
            trip = TripFixtures.trip(status = TripStatus.COMPLETED),
            stops = TripFixtures.stops(
                firstStatus = TripStopStatus.DEPARTED,
                secondStatus = TripStopStatus.DEPARTED,
                thirdStatus = TripStopStatus.DEPARTED,
            ),
            passengers = emptyList(),
        )

        assertEquals(TripPhase.FINISHED, model.phase)
        assertNull(model.primary)
        assertTrue(model.message.orEmpty().contains("Viaje finalizado"))
    }

    @Test
    fun viajeSinParadasPermiteFinalizarSinAccionesDeParada() {
        val model = buildTripDetailModel(
            trip = TripFixtures.trip(status = TripStatus.IN_PROGRESS),
            stops = emptyList(),
            passengers = emptyList(),
        )

        assertEquals(TripPhase.READY_TO_FINISH, model.phase)
        assertEquals("Finalizar viaje", model.primary?.label)
        assertTrue(model.stops.isEmpty())
    }

    @Test
    fun sinViajeElModeloQuedaEnCargaSinAcciones() {
        val model = buildTripDetailModel(
            trip = null,
            stops = emptyList(),
            passengers = emptyList(),
            loading = true,
        )

        assertEquals(TripPhase.LOADING, model.phase)
        assertNull(model.primary)
    }

    @Test
    fun invitadoQueBajaSeMuestraConBajadaAutomatica() {
        val guest = TripFixtures.guest()
        val model = buildTripDetailModel(
            trip = TripFixtures.trip(status = TripStatus.IN_PROGRESS),
            stops = TripFixtures.stops(
                thirdStatus = TripStopStatus.ARRIVED,
                firstStatus = TripStopStatus.DEPARTED,
                secondStatus = TripStopStatus.DEPARTED,
            ),
            passengers = emptyList(),
            arrivalSnapshot = ArrivalSnapshot(stopId = 103L, occupants = listOf(guest)),
        )

        val alighter = model.alighters.single()
        assertEquals("Invitado Uno", alighter.displayName)
        assertTrue(alighter.automatic)
        assertTrue(alighter.isGuest)
    }
}
