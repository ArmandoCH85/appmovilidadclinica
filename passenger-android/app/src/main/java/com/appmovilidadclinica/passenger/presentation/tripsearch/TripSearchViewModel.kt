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

        val direction = deriveDirection(origin, destination)
        if (direction == null) {
            _uiState.update {
                it.copy(errorMessage = "El origen debe ser una sede y el destino un paradero, o viceversa.")
            }
            return
        }

        _uiState.update { it.copy(searching = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = tripsRepository.search(state.date, direction, originId, destinationId)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(searching = false, results = result.data, hasSearched = true)
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(searching = false, hasSearched = true, errorMessage = "No se pudo buscar. Intente nuevamente.")
                }
            }
        }
    }

    /**
     * Regla de negocio (ver `desarrollo_pasajero.md` §2.1):
     *   PARADERO -> SEDE     = IDA
     *   SEDE     -> PARADERO = VUELTA
     * Cualquier otra combinacion (paradero->paradero, sede->sede) es
     * invalida: el backend no tiene viajes con esa configuracion.
     */
    private fun deriveDirection(origin: Stop, destination: Stop): TripDirection? = when {
        origin.stopType == StopType.PARADERO && destination.stopType == StopType.SEDE -> TripDirection.IDA
        origin.stopType == StopType.SEDE && destination.stopType == StopType.PARADERO -> TripDirection.VUELTA
        else -> null
    }

    private fun errorMessageFor(error: AppError): String = when (error) {
        is AppError.Forbidden -> "El backend todavía no expone un catálogo de paradas para pasajeros (ver diseño técnico)."
        else -> error.toString()
    }
}