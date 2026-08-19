package com.appmovilidadclinica.driver.data.repository

import com.appmovilidadclinica.driver.data.local.SessionDataStore
import com.appmovilidadclinica.driver.data.mapper.toDomain
import com.appmovilidadclinica.driver.data.remote.ApiErrorMapper
import com.appmovilidadclinica.driver.data.remote.KtorApiClient
import com.appmovilidadclinica.driver.shared.data.remote.dto.LoginRequestDto
import com.appmovilidadclinica.driver.shared.domain.model.AppError
import com.appmovilidadclinica.driver.shared.domain.model.AuthResult
import com.appmovilidadclinica.driver.shared.domain.model.User
import com.appmovilidadclinica.driver.domain.repository.AuthRepository
import io.ktor.client.call.body
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val apiClient: KtorApiClient,
    private val sessionDataStore: SessionDataStore,
    private val apiErrorMapper: ApiErrorMapper,
) : AuthRepository {

    override suspend fun login(documentNumber: String, password: String): Result<AuthResult> {
        return try {
            val response = apiClient.authApi.login(
                LoginRequestDto(
                    document_number = documentNumber,
                    password = password
                )
            )
            if (response.status.value == 200) {
                val body = response.body<com.appmovilidadclinica.driver.shared.data.remote.dto.LoginResponseDto>()
                val user = body.user.toDomain()
                val authResult = AuthResult(token = body.token, user = user)
                sessionDataStore.saveSession(body.token, user)
                Result.success(authResult)
            } else {
                val error = apiErrorMapper.map(response)
                Result.failure(error)
            }
        } catch (e: java.io.IOException) {
            Result.failure(AppError.Network("Sin conexion a internet"))
        } catch (e: Exception) {
            Result.failure(AppError.Unknown(e.message ?: "Error desconocido"))
        }
    }

    override suspend fun logout() {
        sessionDataStore.clearSession()
    }

    override fun isLoggedIn(): Flow<Boolean> {
        return sessionDataStore.getToken().map { !it.isNullOrBlank() }
    }

    override fun getCurrentUser(): Flow<User?> {
        return sessionDataStore.getUser()
    }

    override fun getToken(): Flow<String?> {
        return sessionDataStore.getToken()
    }

    override suspend fun clearSession() {
        sessionDataStore.clearSession()
    }
}