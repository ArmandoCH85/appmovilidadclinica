package com.appmovilidadclinica.driver.shared.ui.screens.tripdetail

import com.appmovilidadclinica.driver.shared.domain.model.DriverTrip
import com.appmovilidadclinica.driver.shared.domain.model.Passenger
import com.appmovilidadclinica.driver.shared.domain.model.ReservationStatus
import com.appmovilidadclinica.driver.shared.domain.model.TripStatus
import com.appmovilidadclinica.driver.shared.domain.model.TripStop
import com.appmovilidadclinica.driver.shared.domain.model.TripStopStatus
import com.appmovilidadclinica.driver.shared.ui.common.label
import com.appmovilidadclinica.driver.shared.ui.common.toPeruTime
import kotlinx.datetime.Instant

/**
 * Modelo de presentacion de la pantalla "Detalle del viaje".
 *
 * Es puro (sin Compose, sin corrutinas, sin red): recibe el estado crudo del
 * viaje y devuelve exactamente lo que la UI debe pintar. Se testea en
 * commonTest, de modo que el flujo operativo completo (iniciar viaje ->
 * llegada -> abordaje -> salida -> siguiente parada -> finalizar) queda
 * verificado sin necesidad de un dispositivo ni de un backend.
 *
 * Reglas de negocio que aplica (verificadas contra el backend Go y los SPs):
 *
 *  - `POST /driver/trips/{id}/start` = inicio del recorrido. NO marca la
 *    llegada a la primera parada.
 *  - `POST /driver/trip-stops/{id}/arrival` = llegada real a la parada y
 *    habilita el abordaje de los pasajeros cuyo origen es esa parada.
 *  - `POST /driver/trip-stops/{id}/departure` = salida real de la parada.
 *    El SP exige que la llegada este registrada y no admite repetirla.
 *  - `POST /driver/reservations/{id}/board|no-show|alight` = marcas por
 *    pasajero. Son irreversibles (no existe endpoint de correccion).
 *  - `POST /driver/trips/{id}/complete` = fin del viaje (la ultima parada no
 *    tiene "siguiente": su accion es finalizar).
 */

/** Estado de una marca de pasajero mostrada con texto + icono (nunca solo color). */
enum class PassengerMarkState {
    /** Aun no se registro nada: el conductor debe marcar "Subio" o "No se presento". */
    PENDING,
    BOARDED,
    NO_SHOW,
    ALIGHTED,
}

/** Fase operativa del viaje: determina cual es la UNICA accion principal. */
enum class TripPhase {
    LOADING,
    /** Programado: aun no se inicio el recorrido. */
    SCHEDULED,
    /** En curso, en camino a la siguiente parada. */
    EN_ROUTE,
    /** En curso, detenido en una parada (llegada ya registrada). */
    AT_STOP,
    /** En curso, sin paradas pendientes: solo queda finalizar. */
    READY_TO_FINISH,
    FINISHED,
    /** El viaje no admite operacion (borrador o cancelado). */
    UNAVAILABLE,
}

/** La accion principal de la pantalla. Una sola a la vez, siempre en la barra inferior. */
sealed interface PrimaryAction {
    val label: String

    data object StartTrip : PrimaryAction {
        override val label: String = "Iniciar viaje"
    }

    data class ArriveAtStop(val stopId: Long, val stopName: String) : PrimaryAction {
        override val label: String get() = "Llegué a $stopName"
    }

    data class DepartFromStop(val stopId: Long, val stopName: String) : PrimaryAction {
        override val label: String get() = "Salir de $stopName"
    }

    data object FinishTrip : PrimaryAction {
        override val label: String = "Finalizar viaje"
    }
}

/** Marca registrada en esta sesion para un pasajero que el backend ya no devuelve. */
data class LocalMark(
    val passenger: Passenger,
    val state: PassengerMarkState,
    val stopId: Long,
    val at: Instant,
)

/** Manifiesto tal como estaba justo antes de registrar la llegada a una parada. */
data class ArrivalSnapshot(
    val stopId: Long,
    val occupants: List<Passenger>,
)

data class TripHeaderModel(
    val routeName: String,
    val directionLabel: String,
    val scheduleLabel: String,
    val plateLabel: String,
    val tripCodeLabel: String,
    val statusLabel: String,
)

data class BoarderRowModel(
    val key: String,
    val reservationId: Long,
    val displayName: String,
    val seatLabel: String,
    val isGuest: Boolean,
    /** Parada de subida prevista; ayuda cuando el origen ya quedo atras. */
    val boardingStopName: String,
    val boardingStopAlreadyPassed: Boolean,
    val state: PassengerMarkState,
    val timeLabel: String?,
)

data class AlighterRowModel(
    val key: String,
    val reservationId: Long,
    val displayName: String,
    val seatLabel: String,
    val isGuest: Boolean,
    val state: PassengerMarkState,
    val timeLabel: String?,
    /** true = el backend ya la registro (bajada automatica al marcar la llegada). */
    val automatic: Boolean,
)

data class StopRowModel(
    val id: Long,
    val order: Int,
    val name: String,
    val status: TripStopStatus,
    val statusLabel: String,
    val scheduledLabel: String?,
    val arrivalLabel: String?,
    val departureLabel: String?,
    val isFocus: Boolean,
    val isCurrent: Boolean,
    val isLast: Boolean,
    val pendingPassengers: Int,
    val alightingPassengers: Int,
)

data class TripDetailModel(
    val header: TripHeaderModel?,
    val phase: TripPhase,
    val focusStop: StopRowModel?,
    val focusIsLast: Boolean,
    val firstStopLabel: String?,
    val expectedBoarders: Int,
    val expectedAlighters: Int,
    val onboardCount: Int,
    val boarders: List<BoarderRowModel>,
    val alighters: List<AlighterRowModel>,
    val pendingBoarders: Int,
    val primary: PrimaryAction?,
    val primaryBlockedReason: String?,
    val canOverrideDeparture: Boolean,
    val stops: List<StopRowModel>,
    val message: String?,
)

/**
 * Construye el modelo de presentacion. Ordena las paradas por `stop_order`
 * (el backend ya las devuelve ordenadas, pero el modelo no depende de eso).
 */
fun buildTripDetailModel(
    trip: DriverTrip?,
    stops: List<TripStop>,
    passengers: List<Passenger>,
    localMarks: List<LocalMark> = emptyList(),
    arrivalSnapshot: ArrivalSnapshot? = null,
    loading: Boolean = false,
): TripDetailModel {
    if (trip == null) {
        return emptyModel(if (loading) TripPhase.LOADING else TripPhase.UNAVAILABLE)
    }

    val ordered = stops.sortedBy { it.stopOrder }
    val lastStop = ordered.lastOrNull()
    val header = trip.toHeaderModel()

    if (trip.status == TripStatus.DRAFT) {
        return emptyModel(
            phase = TripPhase.UNAVAILABLE,
            header = header,
            stops = ordered,
            passengers = passengers,
            message = "Este viaje todavía no fue publicado. Espere la confirmación del administrador.",
        )
    }

    if (trip.status == TripStatus.CANCELLED) {
        return emptyModel(
            phase = TripPhase.UNAVAILABLE,
            header = header,
            stops = ordered,
            passengers = passengers,
            message = "Este viaje fue cancelado. No requiere ninguna acción.",
        )
    }

    if (trip.status == TripStatus.COMPLETED) {
        return emptyModel(
            phase = TripPhase.FINISHED,
            header = header,
            stops = ordered,
            passengers = passengers,
            message = "Viaje finalizado. Ya no requiere ninguna acción.",
        )
    }

    if (trip.status == TripStatus.PUBLISHED || trip.status == TripStatus.BOARDING) {
        // Programado: la accion es iniciar el recorrido. Iniciar el viaje NO
        // registra la llegada a la primera parada (son eventos distintos).
        val first = ordered.firstOrNull()
        return TripDetailModel(
            header = header,
            phase = TripPhase.SCHEDULED,
            focusStop = first?.toStopRow(focus = true, current = false, last = first.id == lastStop?.id, passengers = passengers),
            focusIsLast = first != null && first.id == lastStop?.id,
            firstStopLabel = first?.let { stop ->
                val time = (stop.scheduledArrivalAt ?: stop.scheduledDepartureAt)?.toPeruTime()
                if (time != null) "${stop.stopName} · $time" else stop.stopName
            },
            expectedBoarders = first?.let { pendingAtStop(it, passengers).size } ?: 0,
            expectedAlighters = first?.let { alightingAtStop(it, passengers).size } ?: 0,
            onboardCount = onboardCount(passengers),
            boarders = emptyList(),
            alighters = emptyList(),
            pendingBoarders = 0,
            primary = PrimaryAction.StartTrip,
            primaryBlockedReason = null,
            canOverrideDeparture = false,
            stops = ordered.mapIndexed { index, stop ->
                stop.toStopRow(
                    focus = stop.id == first?.id,
                    current = false,
                    last = index == ordered.lastIndex,
                    passengers = passengers,
                )
            },
            message = null,
        )
    }

    // IN_PROGRESS
    val current = ordered.firstOrNull { it.status == TripStopStatus.ARRIVED }
    val next = current ?: ordered.firstOrNull { it.status == TripStopStatus.PENDING }

    if (next == null) {
        // Todas las paradas resueltas (o el viaje no tiene paradas).
        return TripDetailModel(
            header = header,
            phase = TripPhase.READY_TO_FINISH,
            focusStop = null,
            focusIsLast = true,
            firstStopLabel = null,
            expectedBoarders = 0,
            expectedAlighters = 0,
            onboardCount = onboardCount(passengers),
            boarders = emptyList(),
            alighters = emptyList(),
            pendingBoarders = 0,
            primary = PrimaryAction.FinishTrip,
            primaryBlockedReason = null,
            canOverrideDeparture = false,
            stops = ordered.mapIndexed { index, stop ->
                stop.toStopRow(focus = false, current = false, last = index == ordered.lastIndex, passengers = passengers)
            },
            message = if (ordered.isEmpty()) {
                "Este viaje no tiene paradas configuradas. Finalícelo cuando termine el recorrido."
            } else {
                "Todas las paradas quedaron registradas. Finalice el viaje para cerrarlo."
            },
        )
    }

    val isCurrent = current != null
    val isLast = next.id == lastStop?.id
    val focusRow = next.toStopRow(focus = true, current = isCurrent, last = isLast, passengers = passengers)

    if (!isCurrent) {
        return TripDetailModel(
            header = header,
            phase = TripPhase.EN_ROUTE,
            focusStop = focusRow,
            focusIsLast = isLast,
            firstStopLabel = null,
            expectedBoarders = pendingAtStop(next, passengers).size,
            expectedAlighters = alightingAtStop(next, passengers).size,
            onboardCount = onboardCount(passengers),
            boarders = emptyList(),
            alighters = emptyList(),
            pendingBoarders = 0,
            primary = PrimaryAction.ArriveAtStop(next.id, next.stopName),
            primaryBlockedReason = null,
            canOverrideDeparture = false,
            stops = ordered.map { stop ->
                stop.toStopRow(
                    focus = stop.id == next.id,
                    current = false,
                    last = stop.id == lastStop?.id,
                    passengers = passengers,
                )
            },
            message = null,
        )
    }

    // AT_STOP: llegada ya registrada. Se muestra UNICAMENTE el manifiesto de
    // esta parada (quienes suben y quienes bajan aqui).
    val boarders = boarderRows(next, passengers, localMarks)
    val alighters = alighterRows(next, passengers, localMarks, arrivalSnapshot)
    val pending = boarders.count { it.state == PassengerMarkState.PENDING }

    // En la ultima parada no hay "siguiente": la accion es finalizar el viaje
    // (que registra tambien su salida real; ver TripDetailViewModel.finishTrip).
    val primary = if (isLast) {
        PrimaryAction.FinishTrip
    } else {
        PrimaryAction.DepartFromStop(next.id, next.stopName)
    }

    val blockedReason = if (pending > 0) {
        if (pending == 1) {
            "Falta registrar 1 pasajero en esta parada."
        } else {
            "Faltan registrar $pending pasajeros en esta parada."
        }
    } else {
        null
    }

    return TripDetailModel(
        header = header,
        phase = TripPhase.AT_STOP,
        focusStop = focusRow,
        focusIsLast = isLast,
        firstStopLabel = null,
        expectedBoarders = boarders.size,
        expectedAlighters = alighters.size,
        onboardCount = onboardCount(passengers),
        boarders = boarders,
        alighters = alighters,
        pendingBoarders = pending,
        primary = primary,
        primaryBlockedReason = blockedReason,
        canOverrideDeparture = pending > 0,
        stops = ordered.map { stop ->
            stop.toStopRow(
                focus = stop.id == next.id,
                current = stop.id == next.id,
                last = stop.id == lastStop?.id,
                passengers = passengers,
            )
        },
        message = null,
    )
}

/** Pasajeros que deben resolverse en una parada: suben aqui o su subida ya quedo atras. */
fun pendingAtStop(stop: TripStop, passengers: List<Passenger>): List<Passenger> =
    passengers.filter { it.status == ReservationStatus.CONFIRMED && needsResolutionAt(it, stop) }

/** Pasajeros que bajan en la parada (marca automatica del backend al llegar). */
fun alightingAtStop(stop: TripStop, passengers: List<Passenger>): List<Passenger> =
    passengers.filter {
        it.status == ReservationStatus.BOARDED && it.destinationStopOrder == stop.stopOrder
    }

fun onboardCount(passengers: List<Passenger>): Int =
    passengers.count { it.status == ReservationStatus.BOARDED }

/**
 * Un pasajero CONFIRMED requiere accion en esta parada cuando sube aqui, o
 * cuando su destino es esta parada pero su punto de subida ya quedo atras
 * (nunca se registro el abordaje). En el segundo caso debe resolverse aqui
 * para no dejar el manifiesto abierto.
 */
private fun needsResolutionAt(passenger: Passenger, stop: TripStop): Boolean =
    passenger.originStopOrder == stop.stopOrder ||
        (passenger.destinationStopOrder == stop.stopOrder && passenger.originStopOrder < stop.stopOrder)

private fun boarderRows(
    stop: TripStop,
    passengers: List<Passenger>,
    localMarks: List<LocalMark>,
): List<BoarderRowModel> {
    val rows = mutableListOf<BoarderRowModel>()

    passengers
        .filter { it.status == ReservationStatus.CONFIRMED && needsResolutionAt(it, stop) }
        .forEach { passenger ->
            val lateness = passenger.originStopOrder < stop.stopOrder
            rows += BoarderRowModel(
                key = passenger.rowKey(),
                reservationId = passenger.reservationId,
                displayName = passenger.displayName(),
                seatLabel = passenger.seatLabel,
                isGuest = passenger.isGuest,
                boardingStopName = passenger.originStopName,
                boardingStopAlreadyPassed = lateness,
                state = PassengerMarkState.PENDING,
                timeLabel = null,
            )
        }

    // Ya subieron en esta parada: el backend lo confirma con boarded_at.
    passengers
        .filter { it.status == ReservationStatus.BOARDED && it.originStopOrder == stop.stopOrder }
        .forEach { passenger ->
            rows += BoarderRowModel(
                key = passenger.rowKey(),
                reservationId = passenger.reservationId,
                displayName = passenger.displayName(),
                seatLabel = passenger.seatLabel,
                isGuest = passenger.isGuest,
                boardingStopName = passenger.originStopName,
                boardingStopAlreadyPassed = false,
                state = PassengerMarkState.BOARDED,
                timeLabel = passenger.boardedAt?.toPeruTime(),
            )
        }

    // Marcas de esta sesion que el backend ya no lista (NO_SHOW).
    localMarks
        .filter { it.stopId == stop.id && it.state == PassengerMarkState.NO_SHOW }
        .forEach { mark ->
            rows += BoarderRowModel(
                key = mark.passenger.rowKey(),
                reservationId = mark.passenger.reservationId,
                displayName = mark.passenger.displayName(),
                seatLabel = mark.passenger.seatLabel,
                isGuest = mark.passenger.isGuest,
                boardingStopName = mark.passenger.originStopName,
                boardingStopAlreadyPassed = false,
                state = PassengerMarkState.NO_SHOW,
                timeLabel = mark.at.toPeruTime(),
            )
        }

    // Pendientes primero (es lo que el conductor debe resolver), luego por asiento.
    return rows.sortedWith(
        compareBy(
            { it.state != PassengerMarkState.PENDING },
            { it.seatLabel },
            { it.displayName },
        ),
    )
}

private fun alighterRows(
    stop: TripStop,
    passengers: List<Passenger>,
    localMarks: List<LocalMark>,
    arrivalSnapshot: ArrivalSnapshot?,
): List<AlighterRowModel> {
    val rows = mutableListOf<AlighterRowModel>()
    val seen = mutableSetOf<String>()

    // 1) Bajada automatica: `sp_mark_trip_stop_arrival` cierra las reservas y
    //    los invitados cuyo destino es esta parada. Se reconstruye desde el
    //    manifiesto capturado antes de marcar la llegada, porque el backend ya
    //    no los devuelve (status COMPLETED).
    val snapshot = arrivalSnapshot?.takeIf { it.stopId == stop.id }?.occupants.orEmpty()
    val alightedAt = stop.actualArrivalAt
    snapshot
        .filter { it.status == ReservationStatus.BOARDED && it.destinationStopOrder == stop.stopOrder }
        .forEach { passenger ->
            val key = passenger.rowKey()
            if (seen.add(key)) {
                rows += AlighterRowModel(
                    key = key,
                    reservationId = passenger.reservationId,
                    displayName = passenger.displayName(),
                    seatLabel = passenger.seatLabel,
                    isGuest = passenger.isGuest,
                    state = PassengerMarkState.ALIGHTED,
                    timeLabel = alightedAt?.toPeruTime(),
                    automatic = true,
                )
            }
        }

    // 2) Todavia BOARDED segun el servidor: la bajada no quedo registrada, el
    //    conductor puede marcarla con el endpoint /alight.
    passengers
        .filter { it.status == ReservationStatus.BOARDED && it.destinationStopOrder == stop.stopOrder }
        .forEach { passenger ->
            val key = passenger.rowKey()
            if (seen.add(key)) {
                rows += AlighterRowModel(
                    key = key,
                    reservationId = passenger.reservationId,
                    displayName = passenger.displayName(),
                    seatLabel = passenger.seatLabel,
                    isGuest = passenger.isGuest,
                    state = PassengerMarkState.BOARDED,
                    timeLabel = null,
                    automatic = false,
                )
            }
        }

    // 3) Marcas locales de bajada (si el backend tarda en reflejarlas).
    localMarks
        .filter { it.stopId == stop.id && it.state == PassengerMarkState.ALIGHTED }
        .forEach { mark ->
            val key = mark.passenger.rowKey()
            if (seen.add(key)) {
                rows += AlighterRowModel(
                    key = key,
                    reservationId = mark.passenger.reservationId,
                    displayName = mark.passenger.displayName(),
                    seatLabel = mark.passenger.seatLabel,
                    isGuest = mark.passenger.isGuest,
                    state = PassengerMarkState.ALIGHTED,
                    timeLabel = mark.at.toPeruTime(),
                    automatic = false,
                )
            }
        }

    return rows.sortedBy { it.seatLabel }
}

private fun TripStop.toStopRow(
    focus: Boolean,
    current: Boolean,
    last: Boolean,
    passengers: List<Passenger>,
): StopRowModel = StopRowModel(
    id = id,
    order = stopOrder,
    name = stopName,
    status = status,
    statusLabel = status.label(),
    scheduledLabel = (scheduledArrivalAt ?: scheduledDepartureAt)?.toPeruTime(),
    arrivalLabel = actualArrivalAt?.toPeruTime(),
    departureLabel = actualDepartureAt?.toPeruTime(),
    isFocus = focus,
    isCurrent = current,
    isLast = last,
    pendingPassengers = pendingAtStop(this, passengers).size,
    alightingPassengers = alightingAtStop(this, passengers).size,
)

private fun DriverTrip.toHeaderModel(): TripHeaderModel = TripHeaderModel(
    routeName = routeName,
    directionLabel = direction.label(),
    scheduleLabel = "${scheduledStartAt.toPeruTime()} – ${scheduledEndAt.toPeruTime()}",
    plateLabel = plate,
    tripCodeLabel = tripCode,
    statusLabel = status.label(),
)

private fun Passenger.displayName(): String =
    guestDisplayName?.takeIf { it.isNotBlank() } ?: workerFullName

/**
 * Clave estable para `LazyColumn`. Los invitados llegan con
 * `reservation_id = 0` (no tienen reserva), asi que se identifican por
 * asiento + nombre.
 */
internal fun Passenger.rowKey(): String =
    if (isGuest) "guest:$seatLabel:$workerFullName" else "res:$reservationId"

private fun emptyModel(
    phase: TripPhase,
    header: TripHeaderModel? = null,
    stops: List<TripStop> = emptyList(),
    passengers: List<Passenger> = emptyList(),
    message: String? = null,
): TripDetailModel = TripDetailModel(
    header = header,
    phase = phase,
    focusStop = null,
    focusIsLast = false,
    firstStopLabel = null,
    expectedBoarders = 0,
    expectedAlighters = 0,
    onboardCount = onboardCount(passengers),
    boarders = emptyList(),
    alighters = emptyList(),
    pendingBoarders = 0,
    primary = null,
    primaryBlockedReason = null,
    canOverrideDeparture = false,
    stops = stops.mapIndexed { index, stop ->
        stop.toStopRow(
            focus = false,
            current = stop.status == TripStopStatus.ARRIVED,
            last = index == stops.lastIndex,
            passengers = passengers,
        )
    },
    message = message,
)
