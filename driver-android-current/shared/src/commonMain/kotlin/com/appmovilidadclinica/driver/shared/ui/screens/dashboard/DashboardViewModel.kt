package com.appmovilidadclinica.driver.shared.ui.screens.dashboard

import com.appmovilidadclinica.driver.shared.domain.model.AppError
import com.appmovilidadclinica.driver.shared.domain.model.DriverTrip
import com.appmovilidadclinica.driver.shared.domain.repository.DriverRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

private fun today(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

data class DashboardUiState(
    val date: LocalDate = today(),
    val trips: List<DriverTrip> = emptyList(),
    val lastSelectedTrip: DriverTrip? = null,
    val loading: Boolean = true,
    val errorMessage: String? = null,
)

/**
 * Dashboard ViewModel multiplatform. No extiende androidx.lifecycle.ViewModel
 * (no es KMP-friendly); usa su propio CoroutineScope. Inyectable via Koin
 * `koinInject<DriverRepository>()` en la Screen.
 */
class DashboardViewModel(
    private val driverRepository: DriverRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState

    init {
        loadTrips()
    }

    fun onPreviousDay() {
        _uiState.update { it.copy(date = it.date.minus(DatePeriod(days = 1))) }
        loadTrips()
    }

    fun onNextDay() {
        _uiState.update { it.copy(date = it.date.plus(DatePeriod(days = 1))) }
        loadTrips()
    }

    fun refresh() {
        loadTrips()
    }

    fun onTripSelected(trip: DriverTrip) {
        _uiState.update { it.copy(lastSelectedTrip = trip) }
    }

    private fun loadTrips() {
        val date = _uiState.value.date
        _uiState.update { it.copy(loading = true, errorMessage = null) }
        scope.launch {
            val result = driverRepository.getTrips(date)
            result.fold(
                onSuccess = { trips ->
                    val sorted = trips.sortedWith(
                        compareBy<DriverTrip> { it.status.name == "CANCELLED" }
                            .thenBy { it.scheduledStartAt },
                    )
                    _uiState.update { it.copy(loading = false, trips = sorted) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(loading = false, errorMessage = messageFor(error)) }
                },
            )
        }
    }

    fun dispose() {
        scope.cancel()
    }

    private fun messageFor(error: Throwable): String = when (error) {
        is AppError.Network -> "Sin conexión a internet."
        is AppError.Unauthorized -> "Sesión expirada."
        else -> "No se pudieron cargar los viajes."
    }
}
