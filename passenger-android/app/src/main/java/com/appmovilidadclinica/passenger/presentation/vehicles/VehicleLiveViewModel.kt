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

data class VehicleLiveUiState(
    val loading: Boolean = true,
    val vehicle: VehicleLocation? = null,
    val errorMessage: String? = null,
    val lastUpdated: Instant? = null,
)

/**
 * Posición en vivo del vehículo de UNA reserva.
 *
 * Consulta `GET /api/vehicles/positions` (nuestro backend) cada 30 s mientras
 * la tarjeta está visible; el backend es quien habla con el GPS, así que el
 * pasajero nunca ve credenciales del servidor de monitoreo.
 */
@HiltViewModel
class VehicleLiveViewModel @Inject constructor(
    private val vehiclesRepository: VehiclesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VehicleLiveUiState())
    val uiState: StateFlow<VehicleLiveUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var plate: String? = null
    private var refreshing = false

    /** Arranca el sondeo para la placa de la reserva (idempotente). */
    fun start(plate: String) {
        if (this.plate == plate && pollingJob?.isActive == true) return
        this.plate = plate
        pollingJob?.cancel()
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

    private suspend fun refreshOnce(): Boolean {
        if (refreshing) return false
        refreshing = true
        _uiState.update { it.copy(loading = it.vehicle == null) }

        val result = vehiclesRepository.latestPositions()
        refreshing = false

        return when (result) {
            is AppResult.Success -> {
                val wanted = plate
                val match = result.data.firstOrNull { it.plate.equals(wanted, ignoreCase = true) }
                _uiState.update {
                    it.copy(
                        loading = false,
                        vehicle = match,
                        // Si el vehículo no viene en la respuesta, no se inventa nada.
                        errorMessage = if (match == null) {
                            "Este vehículo no está reportando posición."
                        } else {
                            null
                        },
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
        else -> "No se pudo obtener la ubicación en este momento."
    }

    companion object {
        /** Cada cuánto se refresca la posición mientras la tarjeta está visible. */
        const val REFRESH_INTERVAL_MS: Long = 30_000L
        const val MAX_BACKOFF_MS: Long = 90_000L
    }
}
