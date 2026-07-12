package com.appmovilidadclinica.passenger.domain.usecase

import com.appmovilidadclinica.passenger.domain.error.AppError
import com.appmovilidadclinica.passenger.domain.error.AppResult
import com.appmovilidadclinica.passenger.domain.model.User
import com.appmovilidadclinica.passenger.domain.model.UserRole
import com.appmovilidadclinica.passenger.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Ver Specs #1: el backend deja loguearse a ADMIN/DRIVER igual que a
 * WORKER — esta app es solo de pasajero, asi que acá se traduce un login
 * tecnicamente exitoso pero de rol incorrecto en un AppError.Forbidden
 * legible, en vez de dejar que la UI navegue a pantallas que no aplican.
 */
class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(documentNumber: String, password: String): AppResult<User> {
        val result = authRepository.login(documentNumber, password)
        if (result is AppResult.Success && result.data.role != UserRole.WORKER) {
            authRepository.logout()
            return AppResult.Failure(
                AppError.Forbidden("Esta app es para trabajadores. Usá el panel admin o la app de conductor.")
            )
        }
        return result
    }
}
