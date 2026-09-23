package com.appmovilidadclinica.driver.shared.ui.screens.tripdetail

import com.appmovilidadclinica.driver.shared.domain.model.AppError
import com.appmovilidadclinica.driver.shared.domain.model.DriverTrip
import com.appmovilidadclinica.driver.shared.domain.model.Passenger
import com.appmovilidadclinica.driver.shared.domain.model.TripStatus
import com.appmovilidadclinica.driver.shared.domain.model.TripStop
import com.appmovilidadclinica.driver.shared.domain.model.TripStopStatus
import com.appmovilidadclinica.driver.shared.domain.repository.DriverRepository
import com.appmovilidadclinica.driver.shared.ui.common.toPeruTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/** Identifica la accion en vuelo. La UI deshabilita todo mientras haya una pendiente. */
sealed interface TripActionKey {
    data object StartTrip : TripActionKey
    data object FinishTrip : TripActionKey
    data class Arrival(val stopId: Long) : TripActionKey
    data class Departure(val stopId: Long) : TripActionKey
    data class Board(val reservationId: Long) : TripActionKey
    data class NoShow(val reservationId: Long) : TripActionKey
    data class Alight(val reservationId: Long) : TripActionKey
}

enum class NoticeKind { SUCCESS, ERROR, INFO }

/**
 * Confirmacion de la ultima accion. No se auto-oculta: el conductor decide
 * cuando dejar de mirarla (un toast de 2 segundos no es accesible para un
 * conductor de 60-70 años). Se reemplaza con la siguiente accion.
 */
data class ActionNotice(
    val kind: NoticeKind,
    val title: String,
    val detail: String? = null,
)

data class TripDetailUiState(
    val trip: DriverTrip? = null,
    val model: TripDetailModel = buildTripDetailModel(
        trip = null,
        stops = emptyList(),
        passengers = emptyList(),
        loading = true,
    ),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val pending: TripActionKey? = null,
    val notice: ActionNotice? = null,
    val loadErrorMessage: String? = null,
    val stopsErrorMessage: String? = null,
)

/**
 * ViewModel del "Detalle del viaje".
 *
 * Reglas de secuencia que garantiza (y que se verifican en
 * `TripDetailViewModelTest`):
 *
 *  1. Una sola accion en vuelo: mientras [TripDetailUiState.pending] no sea
 *     null, cualquier otra accion se ignora. Asi un toque repetido con
 *     respuesta lenta no genera registros duplicados.
 *  2. "Iniciar viaje", "Llegué a [parada]" y "Salir de [parada]" son eventos
 *     distintos: cada uno llama a su propio endpoint y el estado que muestra
 *     la UI sale de la respuesta del backend, nunca de una suposicion local.
 *  3. Nada se muestra como registrado antes de que el backend confirme: los
 *     cambios de estado se aplican despues de la recarga.
 *  4. La hora que se confirma al conductor es la del backend cuando el
 *     endpoint la devuelve (llegada/salida, `boarded_at`); si el DTO no la
 *     expone (inicio y fin del viaje) se usa la hora del dispositivo al
 *     momento de la accion.
 */
class TripDetailViewModel(
    private val tripId: Long,
    initialTrip: DriverTrip? = null,
    private val driverRepository: DriverRepository,
    private val now: () -> Instant = { Clock.System.now() },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var trip: DriverTrip? = initialTrip?.takeIf { it.id == tripId }
    private var stops: List<TripStop> = emptyList()
    private var passengers: List<Passenger> = emptyList()
    private var stopsErrorMessage: String? = null

    /**
     * Marcas hechas en esta sesion para pasajeros que el backend deja de
     * listar (`GET /driver/trips/{id}/passengers` solo devuelve CONFIRMED y
     * BOARDED). Permiten confirmar al conductor lo que acaba de registrar.
     */
    private val localMarks = mutableListOf<LocalMark>()

    /** Manifiesto previo a marcar la llegada: con el se explica quien bajo. */
    private var arrivalSnapshot: ArrivalSnapshot? = null

    /** Pendientes vistos en la ultima publicacion, para detectar auto-resoluciones. */
    private var pendingSeen: Set<Long> = emptySet()

    private val _uiState = MutableStateFlow(
        TripDetailUiState(
            trip = trip,
            model = buildTripDetailModel(
                trip = trip,
                stops = emptyList(),
                passengers = emptyList(),
                loading = true,
            ),
            loading = true,
        ),
    )
    val uiState: StateFlow<TripDetailUiState> = _uiState

    fun load() {
        _uiState.update { it.copy(loading = true, loadErrorMessage = null, stopsErrorMessage = null) }
        scope.launch {
            // Los tres recursos son independientes: si el cronograma de paradas
            // falla, el conductor igual ve cabecera y pasajeros en vez de una
            // pantalla de error completa.
            val tripResult = driverRepository.getTrip(tripId)
            val passengersResult = driverRepository.getPassengers(tripId)
            val stopsResult = driverRepository.getTripStops(tripId)

            val passengersError = passengersResult.exceptionOrNull()
            if (passengersError != null) {
                _uiState.update {
                    it.copy(loading = false, loadErrorMessage = messageFor(passengersError))
                }
                return@launch
            }

            trip = tripResult.getOrNull() ?: trip
            passengers = passengersResult.getOrDefault(emptyList()).sortedBy { it.originStopOrder }
            stops = stopsResult.getOrDefault(emptyList()).sortedBy { it.stopOrder }
            stopsErrorMessage = stopsResult.exceptionOrNull()?.let(::messageFor)
            pendingSeen = emptySet()
            publish()
        }
    }

    /**
     * Refresco silencioso: se usa despues de cada accion y por el refresco
     * periodico. No pisa la pantalla con estados de carga ni pierde las
     * marcas locales.
     */
    private suspend fun refresh(announceAutoResolved: Boolean = false) {
        val tripResult = driverRepository.getTrip(tripId)
        val passengersResult = driverRepository.getPassengers(tripId)
        val stopsResult = driverRepository.getTripStops(tripId)

        tripResult.getOrNull()?.let { trip = it }
        stopsResult.getOrNull()?.let { freshStops ->
            stops = freshStops.sortedBy { it.stopOrder }
            stopsErrorMessage = null
        }
        stopsResult.exceptionOrNull()?.let { stopsErrorMessage = messageFor(it) }

        passengersResult.getOrNull()?.let { fresh ->
            if (announceAutoResolved) {
                val freshIds = fresh.map { it.reservationId }.toSet()
                val autoResolved = pendingSeen.filter { id ->
                    id != 0L &&
                        id !in freshIds &&
                        localMarks.none { it.passenger.reservationId == id }
                }
                if (autoResolved.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            notice = ActionNotice(
                                kind = NoticeKind.INFO,
                                title = "Actualización automática del manifiesto",
                                detail = if (autoResolved.size == 1) {
                                    "El sistema marcó 1 pasajero como “No se presentó” al vencer el tiempo de tolerancia."
                                } else {
                                    "El sistema marcó ${autoResolved.size} pasajeros como “No se presentó” al vencer el tiempo de tolerancia."
                                },
                            ),
                        )
                    }
                }
            }
            passengers = fresh.sortedBy { it.originStopOrder }
        }

        publish()
    }

    /** Recalcula el modelo de presentacion y limpia lo que ya no aplica. */
    private fun publish() {
        val model = buildTripDetailModel(
            trip = trip,
            stops = stops,
            passengers = passengers,
            localMarks = localMarks.toList(),
            arrivalSnapshot = arrivalSnapshot,
            loading = _uiState.value.loading,
        )

        val focusStopId = model.focusStop?.id
        if (model.phase != TripPhase.AT_STOP) {
            arrivalSnapshot = null
        }
        // Las marcas locales solo sostienen la parada activa: al salir de ella
        // el backend vuelve a ser la unica fuente de verdad.
        localMarks.removeAll { mark -> mark.stopId != focusStopId }
        pendingSeen = model.boarders
            .filter { it.state == PassengerMarkState.PENDING }
            .map { it.reservationId }
            .toSet()

        _uiState.update {
            it.copy(
                trip = trip,
                model = model,
                loading = false,
                stopsErrorMessage = stopsErrorMessage,
            )
        }
    }

    // ---------------------------------------------------------------- acciones

    fun startTrip() {
        runAction(
            key = TripActionKey.StartTrip,
            action = { driverRepository.startTrip(tripId) },
            successNotice = {
                ActionNotice(
                    kind = NoticeKind.SUCCESS,
                    title = "Viaje iniciado",
                    detail = "Hora de registro: ${now().toPeruTime()}. Marque “Llegué” al llegar a la primera parada.",
                )
            },
        )
    }

    fun markArrival(stopId: Long) {
        val stopName = stops.firstOrNull { it.id == stopId }?.stopName
        // Se guarda el manifiesto ANTES de marcar la llegada: el backend cierra
        // automaticamente las reservas cuyo destino es esta parada, y sin este
        // respaldo no habria como mostrar quien bajo aqui.
        arrivalSnapshot = ArrivalSnapshot(stopId = stopId, occupants = passengers)
        runAction(
            key = TripActionKey.Arrival(stopId),
            action = { driverRepository.markArrival(stopId) },
            successNotice = { state ->
                val time = state.model.stops.firstOrNull { it.id == stopId }?.arrivalLabel
                ActionNotice(
                    kind = NoticeKind.SUCCESS,
                    title = "Llegada registrada${stopName?.let { ": $it" }.orEmpty()}",
                    detail = time?.let { "Hora real de llegada: $it" }
                        ?: "El servidor no devolvió la hora; actualice con el botón de refresco.",
                )
            },
        )
    }

    fun markDeparture(stopId: Long) {
        val stopName = stops.firstOrNull { it.id == stopId }?.stopName
        runAction(
            key = TripActionKey.Departure(stopId),
            action = { driverRepository.markDeparture(stopId) },
            successNotice = { state ->
                val time = state.model.stops.firstOrNull { it.id == stopId }?.departureLabel
                ActionNotice(
                    kind = NoticeKind.SUCCESS,
                    title = "Salida registrada${stopName?.let { ": $it" }.orEmpty()}",
                    detail = time?.let { "Hora real de salida: $it" },
                )
            },
        )
    }

    /**
     * Finaliza el viaje. Si la parada activa ya tiene llegada registrada y
     * todavia no tiene salida, primero registra la salida real: son dos
     * eventos distintos y se informan por separado, nunca se muestra uno como
     * hecho antes de que el backend lo confirme. Si la salida falla se aborta
     * y se informa el error (el viaje queda en curso, tal como esta en el
     * backend).
     */
    fun finishTrip() {
        if (_uiState.value.pending != null) return

        val stopToDepart = stops.firstOrNull { it.status == TripStopStatus.ARRIVED }
        val tripStatus = trip?.status

        if (stopToDepart == null || tripStatus != TripStatus.IN_PROGRESS) {
            runAction(
                key = TripActionKey.FinishTrip,
                action = { driverRepository.completeTrip(tripId) },
                successNotice = {
                    ActionNotice(
                        kind = NoticeKind.SUCCESS,
                        title = "Viaje finalizado",
                        detail = "Hora de registro: ${now().toPeruTime()}",
                    )
                },
            )
            return
        }

        _uiState.update { it.copy(pending = TripActionKey.FinishTrip, notice = null) }
        scope.launch {
            val departure = driverRepository.markDeparture(stopToDepart.id)
            val departureError = departure.exceptionOrNull()
            if (departureError != null) {
                _uiState.update {
                    it.copy(
                        pending = null,
                        notice = ActionNotice(
                            kind = NoticeKind.ERROR,
                            title = "No se registró la salida de ${stopToDepart.stopName}",
                            detail = messageFor(departureError) + " El viaje sigue en curso.",
                        ),
                    )
                }
                return@launch
            }

            val completion = driverRepository.completeTrip(tripId)
            val completionError = completion.exceptionOrNull()
            if (completionError != null) {
                refresh()
                _uiState.update {
                    it.copy(
                        pending = null,
                        notice = ActionNotice(
                            kind = NoticeKind.ERROR,
                            title = "No se pudo finalizar el viaje",
                            detail = messageFor(completionError),
                        ),
                    )
                }
                return@launch
            }

            refresh()
            val arrivalTime = _uiState.value.model.stops
                .firstOrNull { it.id == stopToDepart.id }
                ?.departureLabel
            _uiState.update {
                it.copy(
                    pending = null,
                    notice = ActionNotice(
                        kind = NoticeKind.SUCCESS,
                        title = "Viaje finalizado",
                        detail = buildString {
                            append("Salida de ${stopToDepart.stopName}: ")
                            append(arrivalTime ?: "registrada")
                            append(" · Fin del viaje: ")
                            append(now().toPeruTime())
                        },
                    ),
                )
            }
        }
    }

    fun board(reservationId: Long) {
        val name = displayNameFor(reservationId)
        runAction(
            key = TripActionKey.Board(reservationId),
            action = { driverRepository.markBoarded(reservationId) },
            successNotice = {
                // `boarded_at` lo escribe el SP: es la hora real del servidor.
                val time = passengers
                    .firstOrNull { it.reservationId == reservationId }
                    ?.boardedAt
                    ?.toPeruTime()
                    ?: now().toPeruTime()
                ActionNotice(
                    kind = NoticeKind.SUCCESS,
                    title = "$name: subió",
                    detail = "Hora de registro: $time",
                )
            },
        )
    }

    fun noShow(reservationId: Long) {
        val passenger = passengerFor(reservationId)
        val name = passenger?.let(::displayNameForPassenger) ?: "El pasajero"
        runAction(
            key = TripActionKey.NoShow(reservationId),
            action = { driverRepository.markNoShow(reservationId) },
            localMarkState = PassengerMarkState.NO_SHOW,
            localMarkPassenger = passenger,
            successNotice = {
                ActionNotice(
                    kind = NoticeKind.SUCCESS,
                    title = "$name: no se presentó",
                    detail = "Hora de registro: ${now().toPeruTime()}. El asiento queda liberado.",
                )
            },
        )
    }

    fun alight(reservationId: Long) {
        val passenger = passengerFor(reservationId)
        val name = passenger?.let(::displayNameForPassenger) ?: "El pasajero"
        runAction(
            key = TripActionKey.Alight(reservationId),
            action = { driverRepository.markAlighted(reservationId) },
            localMarkState = PassengerMarkState.ALIGHTED,
            localMarkPassenger = passenger,
            successNotice = {
                ActionNotice(
                    kind = NoticeKind.SUCCESS,
                    title = "$name: bajó",
                    detail = "Hora de registro: ${now().toPeruTime()}",
                )
            },
        )
    }

    /** Refresco manual pedido por el conductor. */
    fun refreshNow() {
        if (_uiState.value.pending != null) return
        _uiState.update { it.copy(refreshing = true) }
        scope.launch {
            refresh(announceAutoResolved = true)
            _uiState.update { it.copy(refreshing = false) }
        }
    }

    fun dismissNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    fun dispose() {
        scope.cancel()
    }

    // ------------------------------------------------------------------ interno

    private fun runAction(
        key: TripActionKey,
        action: suspend () -> Result<Unit>,
        successNotice: (TripDetailUiState) -> ActionNotice,
        localMarkState: PassengerMarkState? = null,
        localMarkPassenger: Passenger? = null,
    ) {
        // Guarda dura contra duplicados: un segundo toque mientras la primera
        // peticion esta en vuelo no dispara otra peticion.
        if (_uiState.value.pending != null) return

        _uiState.update { it.copy(pending = key, notice = null) }
        scope.launch {
            val result = action()
            result.fold(
                onSuccess = {
                    if (localMarkState != null && localMarkPassenger != null) {
                        val stopId = _uiState.value.model.focusStop?.id
                        if (stopId != null) {
                            localMarks += LocalMark(
                                passenger = localMarkPassenger,
                                state = localMarkState,
                                stopId = stopId,
                                at = now(),
                            )
                        }
                    }
                    refresh()
                    _uiState.update { state ->
                        state.copy(pending = null, notice = successNotice(state))
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            pending = null,
                            notice = ActionNotice(
                                kind = NoticeKind.ERROR,
                                title = errorTitleFor(key),
                                detail = messageFor(error),
                            ),
                        )
                    }
                },
            )
        }
    }

    private fun errorTitleFor(key: TripActionKey): String = when (key) {
        TripActionKey.StartTrip -> "No se pudo iniciar el viaje"
        TripActionKey.FinishTrip -> "No se pudo finalizar el viaje"
        is TripActionKey.Arrival -> "No se registró la llegada"
        is TripActionKey.Departure -> "No se registró la salida"
        is TripActionKey.Board -> "No se registró el abordaje"
        is TripActionKey.NoShow -> "No se pudo marcar “No se presentó”"
        is TripActionKey.Alight -> "No se registró la bajada"
    }

    private fun passengerFor(reservationId: Long): Passenger? =
        passengers.firstOrNull { it.reservationId == reservationId }
            ?: localMarks.firstOrNull { it.passenger.reservationId == reservationId }?.passenger
            ?: arrivalSnapshot?.occupants?.firstOrNull { it.reservationId == reservationId }

    private fun displayNameFor(reservationId: Long): String =
        passengerFor(reservationId)?.let(::displayNameForPassenger) ?: "El pasajero"

    private fun displayNameForPassenger(passenger: Passenger): String =
        passenger.guestDisplayName?.takeIf { it.isNotBlank() } ?: passenger.workerFullName

    private fun messageFor(error: Throwable): String = when (error) {
        // El backend ya responde en español y con el motivo concreto
        // (por ejemplo: "Aún no terminó el tiempo de tolerancia de NO_SHOW"),
        // asi que se muestra tal cual en vez de reemplazarlo por un texto generico.
        is AppError.Conflict -> error.message
        is AppError.Validation -> error.message
        is AppError.NotFound -> "El recurso solicitado no existe."
        is AppError.Forbidden -> "No está asignado a este viaje."
        is AppError.Unauthorized -> "Sesión expirada. Vuelva a iniciar sesión."
        is AppError.Network -> "Sin conexión. Verifique los datos móviles e intente otra vez."
        else -> "Ocurrió un error inesperado. Intente nuevamente."
    }
}
