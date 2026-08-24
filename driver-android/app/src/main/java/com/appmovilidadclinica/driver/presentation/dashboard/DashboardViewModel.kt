package com.appmovilidadclinica.driver.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appmovilidadclinica.driver.shared.domain.model.AppError
import com.appmovilidadclinica.driver.shared.domain.model.DriverTrip
import com.appmovilidadclinica.driver.shared.domain.repository.DriverRepository
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
        is AppError.Network -> "Sin conexiÃ³n a internet."
        is AppError.Unauthorized -> "SesiÃ³n expirada."
        else -> "No se pudieron cargar los viajes."
    }
}
