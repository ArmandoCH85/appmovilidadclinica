package com.appmovilidadclinica.driver.shared.ui.screens.profile

import com.appmovilidadclinica.driver.shared.domain.model.User
import com.appmovilidadclinica.driver.shared.domain.repository.AuthRepository
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class ProfileUiState(
    val user: User? = null,
    val todayTripCount: Int? = null,
    val showLogoutConfirm: Boolean = false,
    val loggedOut: Boolean = false,
)

class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val driverRepository: DriverRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState

    init {
        loadUser()
        loadTodayTripCount()
    }

    private fun loadUser() {
        scope.launch {
            authRepository.getCurrentUser().collect { user ->
                _uiState.update { it.copy(user = user) }
            }
        }
    }

    private fun loadTodayTripCount() {
        scope.launch {
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val result = driverRepository.getTrips(today)
            result.onSuccess { trips ->
                _uiState.update { it.copy(todayTripCount = trips.size) }
            }
        }
    }

    fun askLogout() {
        _uiState.update { it.copy(showLogoutConfirm = true) }
    }

    fun dismissLogout() {
        _uiState.update { it.copy(showLogoutConfirm = false) }
    }

    fun confirmLogout() {
        scope.launch {
            authRepository.logout()
            _uiState.update { it.copy(showLogoutConfirm = false, loggedOut = true) }
        }
    }

    fun dispose() {
        scope.cancel()
    }
}
