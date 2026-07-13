package com.appmovilidadclinica.passenger.presentation.myreservation

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.appmovilidadclinica.passenger.domain.error.AppResult
import com.appmovilidadclinica.passenger.domain.model.Reservation
import com.appmovilidadclinica.passenger.domain.model.ReservationStatus
import com.appmovilidadclinica.passenger.domain.repository.ReservationsRepository
import com.appmovilidadclinica.passenger.domain.usecase.GenerateQrUseCase
import com.appmovilidadclinica.passenger.presentation.common.toBitmap
import com.appmovilidadclinica.passenger.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

data class MyReservationDetailUiState(
    val reservation: Reservation? = null,
    val qrBitmap: Bitmap? = null,
    val cancelling: Boolean = false,
    val cancelled: Boolean = false,
    val checkingIn: Boolean = false,
    val errorMessage: String? = null,
    val showCancelConfirm: Boolean = false,
)

/**
 * Ver Specs #5: el boton de auto-confirmacion solo se habilita en una
 * ventana razonable alrededor del horario de salida — evita "auto-abordarse"
 * desde cualquier lado en cualquier momento. Ventana sugerida: +-30min.
 */
private val SELF_CHECKIN_WINDOW = Duration.ofMinutes(30)

/**
 * `ObserveReservationUseCase`/`CancelReservationUseCase`/`SelfCheckinUseCase`
 * se eliminaron (solo delegaban) — este ViewModel inyecta
 * `ReservationsRepository` directo. `GenerateQrUseCase` SÍ se conserva (tiene
 * lógica real: encode ZXing). Ver memoria "android-passenger-module/ponytail-audit".
 */
@HiltViewModel
class MyReservationDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reservationsRepository: ReservationsRepository,
    private val generateQrUseCase: GenerateQrUseCase,
) : ViewModel() {

    private val route: Screen.MyReservationDetail = savedStateHandle.toRoute<Screen.MyReservationDetail>()

    private val _uiState = MutableStateFlow(MyReservationDetailUiState())
    val uiState: StateFlow<MyReservationDetailUiState> = _uiState.combineReservation(
        reservationsRepository.observeReservation(route.reservationId)
    )

    private fun MutableStateFlow<MyReservationDetailUiState>.combineReservation(
        reservationFlow: Flow<Reservation?>,
    ): StateFlow<MyReservationDetailUiState> =
        combine(this, reservationFlow) { state, reservation ->
            // Si el flow de Room emite la reserva con status CANCELLED
            // (porque el repo.updateStatus() se ejecuto despues del
            // cancel exitoso), marcamos cancelled=true para que el
            // LaunchedEffect navegue atras. Sin esto, el cambio de
            // status viene del flow de Room pero el flag cancelled
            // esta en _uiState — nunca se sincronizan.
            val cancelledFromFlow = reservation?.status == ReservationStatus.CANCELLED
            if (reservation != null && reservation != state.reservation) {
                val qr = reservation.qrToken
                val bitmap = qr?.let { generateQrUseCase(it).toBitmap() }
                state.copy(
                    reservation = reservation,
                    qrBitmap = bitmap,
                    cancelled = state.cancelled || cancelledFromFlow,
                )
            } else {
                state.copy(
                    reservation = reservation,
                    cancelled = state.cancelled || cancelledFromFlow,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, MyReservationDetailUiState())

    val canSelfCheckin: Boolean
        get() {
            val reservation = _uiState.value.reservation ?: return false
            if (reservation.status != ReservationStatus.CONFIRMED) return false
            val now = Instant.now()
            val windowStart = reservation.originDepartureAt.minus(SELF_CHECKIN_WINDOW)
            val windowEnd = reservation.originDepartureAt.plus(SELF_CHECKIN_WINDOW)
            return !now.isBefore(windowStart) && !now.isAfter(windowEnd)
        }

    fun askCancel() {
        android.util.Log.d("MyResDetail", "askCancel: showing dialog for ${route.reservationId}")
        _uiState.update { it.copy(showCancelConfirm = true) }
    }
    fun dismissCancel() = _uiState.update { it.copy(showCancelConfirm = false) }

    fun confirmCancel() {
        android.util.Log.d("MyResDetail", "confirmCancel: starting for ${route.reservationId}")
        val reservationId = route.reservationId
        _uiState.update { it.copy(cancelling = true, showCancelConfirm = false, errorMessage = null) }
        viewModelScope.launch {
            android.util.Log.d("MyResDetail", "confirmCancel: calling API")
            when (val result = reservationsRepository.cancel(reservationId)) {
                is AppResult.Success -> {
                    android.util.Log.d("MyResDetail", "confirmCancel: success")
                    _uiState.update { it.copy(cancelling = false, cancelled = true) }
                }
                is AppResult.Failure -> {
                    android.util.Log.e("MyResDetail", "confirmCancel: failure: $result")
                    _uiState.update {
                        it.copy(cancelling = false, errorMessage = errorMessageFor(result))
                    }
                }
            }
        }
    }

    fun selfCheckin() {
        val reservationId = _uiState.value.reservation?.reservationId ?: return
        _uiState.update { it.copy(checkingIn = true) }
        viewModelScope.launch {
            when (val result = reservationsRepository.selfCheckin(reservationId)) {
                is AppResult.Success -> _uiState.update { it.copy(checkingIn = false) }
                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        checkingIn = false,
                        errorMessage = if (result.error is com.appmovilidadclinica.passenger.domain.error.AppError.NotFound) {
                            "Esta función todavía no está disponible en el servidor."
                        } else {
                            errorMessageFor(result)
                        },
                    )
                }
            }
        }
    }

    private fun errorMessageFor(failure: AppResult.Failure): String =
        (failure.error as? com.appmovilidadclinica.passenger.domain.error.AppError.Conflict)?.message
            ?: "Ocurrió un error inesperado. Intente nuevamente."
}
