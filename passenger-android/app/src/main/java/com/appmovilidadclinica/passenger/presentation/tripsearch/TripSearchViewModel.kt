package com.appmovilidadclinica.passenger.presentation.tripsearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appmovilidadclinica.passenger.domain.error.AppError
import com.appmovilidadclinica.passenger.domain.error.AppResult
import com.appmovilidadclinica.passenger.domain.model.Stop
import com.appmovilidadclinica.passenger.domain.model.StopType
import com.appmovilidadclinica.passenger.domain.model.TripDirection
import com.appmovilidadclinica.passenger.domain.model.TripSearchResult
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
 * Inyecta repositories directo — ver memoria "android-passenger-module/ponytail-audit".
 *
 * `direction` ya NO es input del usuario: se deriva automáticamente desde
 * el `stopType` del origen y destino elegidos. La regla del negocio es
 * estricta (ver `desarrollo_pasajero.md` §2.1 y el doc de arquitectura):
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
    fun onOriginChange(stopId: Long) = _uiState.update { it.copy(originStopId = stopId) }
    fun onDestinationChange(stopId: Long) = _uiState.update { it.copy(destinationStopId = stopId) }

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
            // Para sede→sede (ambas direcciones posibles), lanzamos las
            // dos búsquedas en paralelo y mergearos. El backend SP es la
            // fuente de verdad: devuelve lo que exista según la
            // configuración de rutas del admin. Para combos unívocos
            // (paradero→sede = solo IDA, sede→paradero = solo VUELTA)
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
            // ambas direcciones (no debería, pero defensivo).
            val unique = merged.distinctBy { it.tripId }

            _uiState.update {
                it.copy(searching = false, results = unique, hasSearched = true)
            }
        }
    }

    /**
     * Devuelve las direcciones a buscar para la combinación de paradas
     * elegida, según las reglas del negocio (ver `desarrollo_pasajero.md`
     * §2.1):
     *   - PARADERO → SEDE = [IDA]
     *   - SEDE → PARADERO = [VUELTA]
     *   - SEDE → SEDE = [IDA, VUELTA] — ambigua: el destino es sede (IDA)
     *     y el origen también es sede (VUELTA). El admin pudo haber
     *     configurado la ruta como cualquiera de las dos, así que
     *     buscamos ambas y el SP decide.
     *   - PARADERO → PARADERO = [] — no válida según las reglas
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