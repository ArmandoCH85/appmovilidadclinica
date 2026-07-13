package com.appmovilidadclinica.driver.presentation.qrscan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appmovilidadclinica.driver.domain.model.AppError
import com.appmovilidadclinica.driver.domain.model.Passenger
import com.appmovilidadclinica.driver.domain.model.Reservation
import com.appmovilidadclinica.driver.domain.repository.BookingRepository
import com.appmovilidadclinica.driver.domain.repository.DriverRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QrScanUiState(
    val verifying: Boolean = false,
    val reservation: Reservation? = null,
    val passenger: Passenger? = null,
    val actionInProgress: Boolean = false,
    val errorMessage: String? = null,
    val toastMessage: String? = null,
)

class QrScanViewModel(
    private val tripId: Long,
    private val bookingRepository: BookingRepository,
    private val driverRepository: DriverRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QrScanUiState())
    val uiState: StateFlow<QrScanUiState> = _uiState

    private var scanning = false

    fun onQrDetected(token: String) {
        if (scanning || _uiState.value.reservation != null) return
        scanning = true
        _uiState.update { it.copy(verifying = true, errorMessage = null) }
        viewModelScope.launch {
            val result = bookingRepository.verifyQr(token)
            result.fold(
                onSuccess = { reservation ->
                    val passengers = driverRepository.getPassengers(tripId).getOrDefault(emptyList())
                    val passenger = passengers.find { it.reservationId == reservation.id }
                    _uiState.update {
                        it.copy(verifying = false, reservation = reservation, passenger = passenger)
                    }
                },
                onFailure = { error ->
                    scanning = false
                    _uiState.update { it.copy(verifying = false, errorMessage = messageFor(error)) }
                },
            )
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
        scanning = false
    }

    fun board() = runAction("Pasajero abordado") { driverRepository.markBoarded(it) }
    fun noShow() = runAction("No presentado registrado") { driverRepository.markNoShow(it) }
    fun alight() = runAction("Bajada registrada") { driverRepository.markAlighted(it) }

    fun dismissToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun scanNext() {
        scanning = false
        _uiState.update { it.copy(reservation = null, passenger = null) }
    }

    private fun runAction(successMessage: String, action: suspend (Long) -> Result<Unit>) {
        val reservationId = _uiState.value.reservation?.id ?: return
        _uiState.update { it.copy(actionInProgress = true) }
        viewModelScope.launch {
            val result = action(reservationId)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            actionInProgress = false,
                            toastMessage = successMessage,
                            reservation = null,
                            passenger = null,
                        )
                    }
                    scanning = false
                },
                onFailure = { error ->
                    _uiState.update { it.copy(actionInProgress = false, errorMessage = messageFor(error)) }
                },
            )
        }
    }

    private fun messageFor(error: Throwable): String = when (error) {
        is AppError.NotFound -> "QR inválido."
        is AppError.Conflict -> error.message
        is AppError.Network -> "Sin conexión a internet."
        else -> "Ocurrió un error inesperado."
    }
}
