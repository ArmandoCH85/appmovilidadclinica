package com.appmovilidadclinica.passenger.presentation.tripsearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appmovilidadclinica.passenger.shared.domain.error.AppError
import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import com.appmovilidadclinica.passenger.shared.domain.model.Stop
import com.appmovilidadclinica.passenger.shared.domain.model.StopType
import com.appmovilidadclinica.passenger.shared.domain.model.TripDirection
import com.appmovilidadclinica.passenger.shared.domain.model.TripSearchResult
import com.appmovilidadclinica.passenger.domain.repository.StopsRepository
import com.appmovilidadclinica.passenger.domain.repository.TripsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class TripSearchUiState(
    val stops: List<Stop> = emptyList(),
    val loadingStops: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val originStopId: Long? = null,
    val destinationStopId: Long? = null,
    val searching: Boolean = false,
    val results: List<TripSearchResult> = emptyList(),
    val hasSearched: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Inyecta repositories directo â€” ver memoria "android-passenger-module/ponytail-audit".
 *
 * `direction` ya NO es input del usuario: se deriva automÃ¡ticamente desde
 * el `stopType` del origen y destino elegidos. La regla del negocio es
 * estricta (ver `desarrollo_pasajero.md` Â§2.1 y el doc de arquitectura):
 *   - PARADERO -> SEDE  = IDA
 *   - SEDE    -> PARADERO = VUELTA
 *   - Cualquier otra combinacion = invalida, la app la rechaza antes de
 *     llamar al backend (mejor UX y evita gastar una llamada de red en
 *     un request que el SP ya sabemos que devuelve 0 resultados).
 */
@HiltViewModel
class TripSearchViewModel @Inject constructor(
    private val stopsRepository: StopsRepository,
    private val tripsRepository: TripsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TripSearchUiState())
    val uiState: StateFlow<TripSearchUiState> = _uiState

    init {
        loadStops()
    }

    private fun loadStops() {
        viewModelScope.launch {
            when (val result = stopsRepository.list()) {
                is AppResult.Success -> _uiState.update { it.copy(stops = result.data, loadingStops = false) }
                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        loadingStops = false,
                        errorMessage = "No se pudieron cargar las paradas. ${errorMessageFor(result.error)}",
                    )
                }
            }
        }
    }

    fun onDateChange(date: LocalDate) = _uiState.update { it.copy(date = date) }

    /**
     * Al cambiar el origen, si el destino ya elegido dejo de ser valido para
     * el nuevo origen (ver `destinationOptionsFor`) lo limpiamos â€” evita que
     * quede seleccionado un paradero como destino cuando el origen paso a
     * ser un paradero (IDA solo permite destino sede).
     */
    fun onOriginChange(stopId: Long) = _uiState.update { state ->
        val origin = state.stops.find { it.id == stopId }
        val destination = state.stops.find { it.id == state.destinationStopId }
        val destinationStillValid = destination == null ||
            destination.stopType in destinationStopTypesFor(origin?.stopType)
        state.copy(
            originStopId = stopId,
            destinationStopId = if (destinationStillValid) state.destinationStopId else null,
        )
    }

    fun onDestinationChange(stopId: Long) = _uiState.update { it.copy(destinationStopId = stopId) }

    /**
     * Tipos de parada validos como destino segun el origen elegido:
     *   - origen PARADERO (IDA) -> el destino SOLO puede ser SEDE (el combo
     *     de destino no debe mostrar paraderos).
     *   - origen SEDE o sin elegir -> el destino puede ser SEDE o PARADERO
     *     (cubre VUELTA sede->paradero y el caso ambiguo sede->sede).
     */
    fun destinationStopTypesFor(originStopType: StopType?): Set<StopType> =
        if (originStopType == StopType.PARADERO) setOf(StopType.SEDE) else setOf(StopType.SEDE, StopType.PARADERO)

    fun search() {
        val state = _uiState.value
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
        viewModelScope.launch {
            // Para sedeâ†’sede (ambas direcciones posibles), lanzamos las
            // dos bÃºsquedas en paralelo y mergearos. El backend SP es la
            // fuente de verdad: devuelve lo que exista segÃºn la
            // configuraciÃ³n de rutas del admin. Para combos unÃ­vocos
            // (paraderoâ†’sede = solo IDA, sedeâ†’paradero = solo VUELTA)
            // se hace una sola llamada.
            val results = directions.map { dir ->
                async { tripsRepository.search(state.date, dir, originId, destinationId) }
            }.awaitAll()

            val merged = results.flatMap { result ->
                when (result) {
                    is AppResult.Success -> result.data
                    is AppResult.Failure -> emptyList()
                }
            }
            // Deduplicar por tripId por si el mismo viaje apareciera en
            // ambas direcciones (no deberÃ­a, pero defensivo).
            val unique = merged.distinctBy { it.tripId }

            _uiState.update {
                it.copy(searching = false, results = unique, hasSearched = true)
            }
        }
    }

    /**
     * Devuelve las direcciones a buscar para la combinaciÃ³n de paradas
     * elegida, segÃºn las reglas del negocio (ver `desarrollo_pasajero.md`
     * Â§2.1):
     *   - PARADERO â†’ SEDE = [IDA]
     *   - SEDE â†’ PARADERO = [VUELTA]
     *   - SEDE â†’ SEDE = [IDA, VUELTA] â€” ambigua: el destino es sede (IDA)
     *     y el origen tambiÃ©n es sede (VUELTA). El admin pudo haber
     *     configurado la ruta como cualquiera de las dos, asÃ­ que
     *     buscamos ambas y el SP decide.
     *   - PARADERO â†’ PARADERO = [] â€” no vÃ¡lida segÃºn las reglas
     *     estrictas del negocio (subida en paradero solo en IDA, y en
     *     IDA el destino debe ser sede).
     */
    private fun deriveDirections(origin: Stop, destination: Stop): List<TripDirection> = when {
        origin.stopType == StopType.PARADERO && destination.stopType == StopType.SEDE ->
            listOf(TripDirection.IDA)
        origin.stopType == StopType.SEDE && destination.stopType == StopType.PARADERO ->
            listOf(TripDirection.VUELTA)
        origin.stopType == StopType.SEDE && destination.stopType == StopType.SEDE ->
            listOf(TripDirection.IDA, TripDirection.VUELTA)
        else -> emptyList()
    }

    private fun errorMessageFor(error: AppError): String = when (error) {
        is AppError.Forbidden -> "El backend todavía no expone un catálogo de paradas para pasajeros (ver diseño técnico)."
        else -> error.toString()
    }
}