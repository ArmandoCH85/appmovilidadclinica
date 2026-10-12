package com.appmovilidadclinica.passenger.shared.ui.tripsearch

import com.appmovilidadclinica.passenger.shared.domain.error.AppError
import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import com.appmovilidadclinica.passenger.shared.domain.model.Stop
import com.appmovilidadclinica.passenger.shared.domain.model.StopType
import com.appmovilidadclinica.passenger.shared.domain.model.TripDirection
import com.appmovilidadclinica.passenger.shared.domain.model.TripSearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class TripSearchUiState(
    val stops: List<Stop> = emptyList(),
    val loadingStops: Boolean = true,
    val date: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.of("America/Lima")).date,
    val originStopId: Long? = null,
    val destinationStopId: Long? = null,
    val searching: Boolean = false,
    val results: List<TripSearchResult> = emptyList(),
    val hasSearched: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Logica de la busqueda de viajes, comun a Android e iOS (antes vivia en el
 * `TripSearchViewModel` del modulo Android con Hilt).
 *
 * Las reglas de negocio son las mismas que ya estaban (ver
 * `desarrollo_pasajero.md` §2.1):
 *   - PARADERO -> SEDE    = IDA
 *   - SEDE -> PARADERO    = VUELTA
 *   - SEDE -> SEDE        = IDA y VUELTA (se buscan las dos)
 *   - PARADERO -> PARADERO = invalida, se rechaza sin llamar al backend
 *
 * @param loadStops operacion de red del catalogo de paradas.
 * @param searchTrips busqueda para una direccion (el grafo la provee).
 */
class TripSearchController(
    private val loadStops: suspend () -> AppResult<List<Stop>>,
    private val searchTrips: suspend (LocalDate, TripDirection, Long, Long) -> AppResult<List<TripSearchResult>>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val _uiState = MutableStateFlow(TripSearchUiState())
    val uiState: StateFlow<TripSearchUiState> = _uiState

    private var stopsLoaded = false

    fun load() {
        if (stopsLoaded) return
        stopsLoaded = true
        _uiState.update { it.copy(loadingStops = true) }
        scope.launch {
            when (val result = loadStops()) {
                is AppResult.Success -> _uiState.update {
                    it.copy(stops = result.data, loadingStops = false, errorMessage = null)
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        loadingStops = false,
                        errorMessage = "No se pudieron cargar las paradas. ${messageFor(result.error)}",
                    )
                }
            }
        }
    }

    fun onDateChange(date: LocalDate) = _uiState.update { it.copy(date = date) }

    /**
     * Al cambiar el origen, si el destino elegido dejo de ser valido para el
     * nuevo origen (ver [destinationStopTypesFor]) se limpia.
     */
    fun onOriginChange(stopId: Long) = _uiState.update { state ->
        val origin = state.stops.find { it.id == stopId }
        val destination = state.stops.find { it.id == state.destinationStopId }
        val destinationStillValid = destination == null ||
            destination.stopType in destinationStopTypesFor(origin?.stopType)
        state.copy(
            originStopId = stopId,
            destinationStopId = if (destinationStillValid) state.destinationStopId else null,
            errorMessage = null,
        )
    }

    fun onDestinationChange(stopId: Long) = _uiState.update {
        it.copy(destinationStopId = stopId, errorMessage = null)
    }

    /**
     * Tipos de parada validos como destino segun el origen:
     * paradero de origen (IDA) → el destino solo puede ser sede; en el resto,
     * sede o paradero.
     */
    fun destinationStopTypesFor(originStopType: StopType?): Set<StopType> =
        if (originStopType == StopType.PARADERO) {
            setOf(StopType.SEDE)
        } else {
            setOf(StopType.SEDE, StopType.PARADERO)
        }

    /** Paradas que pueden elegirse como destino con el origen actual. */
    fun destinationOptions(): List<Stop> {
        val state = _uiState.value
        val originType = state.stops.find { it.id == state.originStopId }?.stopType
        val validTypes = destinationStopTypesFor(originType)
        return state.stops.filter { it.stopType in validTypes }
    }

    fun search() {
        val state = _uiState.value
        if (state.searching) return

        val originId = state.originStopId
        val destinationId = state.destinationStopId
        if (originId == null || destinationId == null) {
            _uiState.update { it.copy(errorMessage = "Seleccione el origen y el destino.") }
            return
        }
        if (originId == destinationId) {
            _uiState.update { it.copy(errorMessage = "Origen y destino no pueden ser la misma parada.") }
            return
        }

        val origin = state.stops.find { it.id == originId }
        val destination = state.stops.find { it.id == destinationId }
        if (origin == null || destination == null) {
            _uiState.update { it.copy(errorMessage = "Las paradas seleccionadas no son válidas.") }
            return
        }

        val directions = deriveDirections(origin, destination)
        if (directions.isEmpty()) {
            _uiState.update {
                it.copy(errorMessage = "No hay viajes configurados para esa combinación de paradas.")
            }
            return
        }

        _uiState.update { it.copy(searching = true, errorMessage = null) }
        scope.launch {
            // Sede→sede es ambigua: se buscan las dos direcciones en paralelo y
            // se unen. El backend (SP) es la fuente de verdad.
            val results = directions
                .map { direction -> async { searchTrips(state.date, direction, originId, destinationId) } }
                .awaitAll()

            val merged = results.flatMap { result ->
                when (result) {
                    is AppResult.Success -> result.data
                    is AppResult.Failure -> emptyList()
                }
            }

            _uiState.update {
                it.copy(
                    searching = false,
                    results = merged.distinctBy { trip -> trip.tripId },
                    hasSearched = true,
                )
            }
        }
    }

    fun dispose() {
        scope.cancel()
    }

    private fun deriveDirections(origin: Stop, destination: Stop): List<TripDirection> = when {
        origin.stopType == StopType.PARADERO && destination.stopType == StopType.SEDE ->
            listOf(TripDirection.IDA)

        origin.stopType == StopType.SEDE && destination.stopType == StopType.PARADERO ->
            listOf(TripDirection.VUELTA)

        origin.stopType == StopType.SEDE && destination.stopType == StopType.SEDE ->
            listOf(TripDirection.IDA, TripDirection.VUELTA)

        else -> emptyList()
    }

    private fun messageFor(error: AppError): String = when (error) {
        is AppError.Network -> "Sin conexión a internet."
        is AppError.Forbidden -> "El backend todavía no expone un catálogo de paradas para pasajeros."
        else -> error.messageOrNull() ?: "Intente nuevamente."
    }
}

private fun AppError.messageOrNull(): String? = when (this) {
    is AppError.Unauthorized -> message
    is AppError.Forbidden -> message
    is AppError.NotFound -> message
    is AppError.Conflict -> message
    is AppError.Validation -> message
    is AppError.Network -> message
    is AppError.Unknown -> message
}
