package com.appmovilidadclinica.driver.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appmovilidadclinica.driver.domain.model.User
import com.appmovilidadclinica.driver.domain.repository.AuthRepository
import com.appmovilidadclinica.driver.domain.repository.DriverRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ProfileUiState(
    val user: User? = null,
    val todayTripCount: Int? = null,
    val showLogoutConfirm: Boolean = false,
    val loggedOut: Boolean = false,
)

class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val driverRepository: DriverRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState

    init {
        loadUser()
        loadTodayTripCount()
    }

    private fun loadUser() {
        viewModelScope.launch {
            authRepository.getCurrentUser().collect { user ->
                _uiState.update { it.copy(user = user) }
            }
        }
    }

    private fun loadTodayTripCount() {
        viewModelScope.launch {
            val result = driverRepository.getTrips(LocalDate.now())
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
        viewModelScope.launch {
            authRepository.logout()
            _uiState.update { it.copy(showLogoutConfirm = false, loggedOut = true) }
        }
    }
}
