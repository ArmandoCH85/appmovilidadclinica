package com.appmovilidadclinica.passenger.presentation.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appmovilidadclinica.passenger.data.local.TraccarCredentialsStore
import com.appmovilidadclinica.passenger.shared.data.remote.traccar.TraccarApi
import com.appmovilidadclinica.passenger.shared.data.remote.traccar.TraccarCredentials
import com.appmovilidadclinica.passenger.shared.data.remote.traccar.createTraccarHttpClient
import com.appmovilidadclinica.passenger.shared.domain.error.AppError
import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import com.appmovilidadclinica.passenger.shared.domain.model.VehicleLocation
import com.appmovilidadclinica.passenger.shared.domain.traccar.TraccarVehiclesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import javax.inject.Inject

data class VehicleTrackingUiState(
    val credentialsConfigured: Boolean = false,
    val loading: Boolean = true,
    val vehicles: List<VehicleLocation> = emptyList(),
    val errorMessage: String? = null,
    val lastUpdated: Instant? = null,
)

/**
 * Posiciones en vivo de los dos vehículos (Traccar).
 *
 * Estrategia (REST periódico, la opción A del documento):
 *  - consulta al entrar a la pantalla y cada [REFRESH_INTERVAL_MS] (20 s);
 *  - con error, espera más (backoff hasta [MAX_BACKOFF_MS]) y conserva lo
 *    último bueno;
 *  - no superpone llamadas;
 *  - el sondeo se detiene cuando la pantalla deja de estar visible.
 */
@HiltViewModel
class VehicleTrackingViewModel @Inject constructor(
    private val credentialsStore: TraccarCredentialsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VehicleTrackingUiState())
    val uiState: StateFlow<VehicleTrackingUiState> = _uiState.asStateFlow()

    private var repository: TraccarVehiclesRepository? = null
    private var pollingJob: Job? = null
    private var refreshing = false

    init {
        _uiState.update { it.copy(credentialsConfigured = credentialsStore.load()?.isComplete == true) }
    }

    /** Arranca el sondeo (idempotente). */
    fun startPolling() {
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

    /** Detiene el sondeo (al salir de la pantalla). */
    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /** Refresco manual (botón "Actualizar"). */
    fun refresh() {
        viewModelScope.launch { refreshOnce() }
    }

    fun saveCredentials(user: String, password: String) {
        val credentials = TraccarCredentials(user.trim(), password)
        if (!credentials.isComplete) {
            _uiState.update { it.copy(errorMessage = "Complete usuario y contraseña del GPS.") }
            return
        }
        credentialsStore.save(credentials)
        // Credenciales nuevas: se rehace el cliente con ellas.
        repository = null
        _uiState.update { it.copy(credentialsConfigured = true, errorMessage = null, loading = true) }
        viewModelScope.launch { refreshOnce() }
    }

    fun forgetCredentials() {
        credentialsStore.clear()
        repository = null
        _uiState.update {
            it.copy(
                credentialsConfigured = false,
                vehicles = emptyList(),
                errorMessage = null,
                loading = false,
            )
        }
    }

    private fun repository(): TraccarVehiclesRepository? {
        repository?.let { return it }
        val credentials = credentialsStore.load() ?: return null
        val client = createTraccarHttpClient(credentials)
        return TraccarVehiclesRepository(TraccarApi(client)).also { repository = it }
    }

    /** @return true si hubo error (para aplicar backoff). */
    private suspend fun refreshOnce(): Boolean {
        if (refreshing) return false
        val repo = repository()
        if (repo == null) {
            _uiState.update {
                it.copy(loading = false, credentialsConfigured = false, errorMessage = null)
            }
            return false
        }

        refreshing = true
        _uiState.update { it.copy(loading = it.vehicles.isEmpty()) }
        val result = repo.latestPositions()
        refreshing = false

        return when (result) {
            is AppResult.Success -> {
                _uiState.update {
                    it.copy(
                        loading = false,
                        vehicles = result.data,
                        errorMessage = null,
                        lastUpdated = kotlinx.datetime.Clock.System.now(),
                    )
                }
                false
            }

            is AppResult.Failure -> {
                _uiState.update {
                    // Se conservan las últimas posiciones buenas y se avisa.
                    it.copy(loading = false, errorMessage = messageFor(result.error))
                }
                true
            }
        }
    }

    private fun messageFor(error: AppError): String = when (error) {
        is AppError.Unauthorized -> error.message.ifBlank { "Credenciales del GPS inválidas." }
        is AppError.Forbidden -> error.message.ifBlank { "La cuenta de GPS no tiene acceso a estos vehículos." }
        is AppError.Network -> "Sin conexión con el servidor GPS. Se muestra la última ubicación conocida."
        else -> error.messageOrEmpty().ifBlank { "No se pudieron obtener las posiciones." }
    }

    private fun AppError.messageOrEmpty(): String = when (this) {
        is AppError.Unauthorized -> message
        is AppError.Forbidden -> message
        is AppError.NotFound -> message
        is AppError.Conflict -> message
        is AppError.Validation -> message
        is AppError.Network -> message
        is AppError.Unknown -> message
    }

    companion object {
        /** Intervalo de actualización mientras la pantalla está visible. */
        const val REFRESH_INTERVAL_MS: Long = 20_000L
        const val MAX_BACKOFF_MS: Long = 60_000L
    }
}
