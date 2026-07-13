package com.appmovilidadclinica.driver.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appmovilidadclinica.driver.domain.model.AppError
import com.appmovilidadclinica.driver.domain.model.DriverTrip
import com.appmovilidadclinica.driver.domain.repository.DriverRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DashboardUiState(
    val date: LocalDate = LocalDate.now(),
    val trips: List<DriverTrip> = emptyList(),
    val loading: Boolean = true,
    val errorMessage: String? = null,
)

class DashboardViewModel(
    private val driverRepository: DriverRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState

    init {
        loadTrips()
    }

    fun onPreviousDay() {
        _uiState.update { it.copy(date = it.date.minusDays(1)) }
        loadTrips()
    }

    fun onNextDay() {
        _uiState.update { it.copy(date = it.date.plusDays(1)) }
        loadTrips()
    }

    fun refresh() {
        loadTrips()
    }

    private fun loadTrips() {
        val date = _uiState.value.date
        _uiState.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
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

    private fun messageFor(error: Throwable): String = when (error) {
        is AppError.Network -> "Sin conexión a internet."
        is AppError.Unauthorized -> "Sesión expirada."
        else -> "No se pudieron cargar los viajes."
    }
}
