package com.appmovilidadclinica.passenger.presentation.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appmovilidadclinica.passenger.shared.domain.error.AppError
import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import com.appmovilidadclinica.passenger.shared.domain.model.VehicleLocation
import com.appmovilidadclinica.passenger.shared.domain.vehicles.VehiclesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import javax.inject.Inject

data class VehiclesLiveUiState(
    val loading: Boolean = true,
    val positions: Map<String, VehicleLocation> = emptyMap(),
    val errorMessage: String? = null,
    val lastUpdated: Instant? = null,
)

/**
 * Posiciones en vivo de los vehículos de las reservas del pasajero.
 *
 * Una sola instancia por pantalla: "Mis reservas" la usa para mostrar, fila
 * por fila, dónde está el bus de cada viaje (sin tener que abrir la reserva);
 * el detalle la usa para su tarjeta.
 *
 * Consulta `GET /api/vehicles/positions` (nuestro backend, que es quien habla
 * con el GPS): el pasajero nunca ve ni ingresa credenciales.
 */
@HiltViewModel
class VehiclesLiveViewModel @Inject constructor(
    private val vehiclesRepository: VehiclesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VehiclesLiveUiState())
    val uiState: StateFlow<VehiclesLiveUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var refreshing = false

    fun start() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            var backoff = 0L
            while (isActive) {
                val failed = refreshOnce()
                backoff = if (failed) {
                    (if (backoff == 0L) REFRESH_INTERVAL_MS else backoff * 2)
                        .coerceAtMost(MAX_BACKOFF_MS)
                } else {
                    0L
                }
                delay(REFRESH_INTERVAL_MS + backoff)
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun refresh() {
        viewModelScope.launch { refreshOnce() }
    }

    /** Posición conocida de una placa (null si el backend no la reporta). */
    fun vehicleFor(plate: String?): VehicleLocation? {
        if (plate.isNullOrBlank()) return null
        return _uiState.value.positions.entries
            .firstOrNull { it.key.equals(plate, ignoreCase = true) }
            ?.value
    }

    private suspend fun refreshOnce(): Boolean {
        if (refreshing) return false
        refreshing = true
        _uiState.update { it.copy(loading = it.positions.isEmpty()) }

        val result = vehiclesRepository.latestPositions()
        refreshing = false

        return when (result) {
            is AppResult.Success -> {
                _uiState.update {
                    it.copy(
                        loading = false,
                        positions = result.data.associateBy { vehicle -> vehicle.plate },
                        errorMessage = null,
                        lastUpdated = Clock.System.now(),
                    )
                }
                false
            }

            is AppResult.Failure -> {
                _uiState.update {
                    it.copy(loading = false, errorMessage = messageFor(result.error))
                }
                true
            }
        }
    }

    private fun messageFor(error: AppError): String = when (error) {
        is AppError.Unauthorized -> "Sesión expirada. Inicie sesión nuevamente."
        is AppError.Network -> "Sin conexión. Se muestra la última ubicación conocida."
        else -> "No se pudo obtener la ubicación del bus en este momento."
    }

    companion object {
        /** Cada cuánto se refresca la posición mientras la pantalla está visible. */
        const val REFRESH_INTERVAL_MS: Long = 30_000L
        const val MAX_BACKOFF_MS: Long = 90_000L
    }
}

/**
 * "hace 3 min" / "hace 2 h": antigüedad legible de la última posición GPS.
 * Devuelve null si no hay hora.
 */
fun ageLabel(fixTime: Instant?, now: Instant = Clock.System.now()): String? {
    val fix = fixTime ?: return null
    val minutes = (now - fix).inWholeMinutes
    return when {
        minutes < 1 -> "hace instantes"
        minutes < 60 -> "hace $minutes min"
        minutes < 1440 -> "hace ${minutes / 60} h"
        else -> "hace ${minutes / 1440} d"
    }
}
