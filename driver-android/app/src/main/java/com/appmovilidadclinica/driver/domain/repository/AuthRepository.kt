package com.appmovilidadclinica.driver.domain.repository

import com.appmovilidadclinica.driver.domain.model.AppError
import com.appmovilidadclinica.driver.domain.model.AuthResult
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun login(documentNumber: String, password: String): Result<AuthResult>
    suspend fun logout()
    fun isLoggedIn(): Flow<Boolean>
    fun getCurrentUser(): Flow<com.appmovilidadclinica.driver.domain.model.User?>
    fun getToken(): Flow<String?>
    suspend fun clearSession()
}
