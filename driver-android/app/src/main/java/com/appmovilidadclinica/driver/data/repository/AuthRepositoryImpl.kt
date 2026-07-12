package com.appmovilidadclinica.driver.data.repository

import com.appmovilidadclinica.driver.data.local.SessionDataStore
import com.appmovilidadclinica.driver.data.mapper.toDomain
import com.appmovilidadclinica.driver.data.remote.api.AuthApi
import com.appmovilidadclinica.driver.data.remote.dto.LoginRequestDto
import com.appmovilidadclinica.driver.domain.model.AppError
import com.appmovilidadclinica.driver.domain.model.AuthResult
import com.appmovilidadclinica.driver.domain.model.User
import com.appmovilidadclinica.driver.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val sessionDataStore: SessionDataStore
) : AuthRepository {

    override suspend fun login(documentNumber: String, password: String): Result<AuthResult> {
        return try {
            val response = authApi.login(
                LoginRequestDto(
                    document_number = documentNumber,
                    password = password
                )
            )
            
            val authResult = AuthResult(
                token = response.token,
                user = response.user.toDomain()
            )
            
            // Save session
            sessionDataStore.saveSession(response.token, response.user.toDomain())
            
            Result.success(authResult)
        } catch (e: HttpException) {
            val error = when (e.code()) {
                401 -> AppError.Unauthorized("Documento o contraseña incorrectos")
                else -> AppError.Unknown("Error del servidor")
            }
            Result.failure(error)
        } catch (e: IOException) {
            Result.failure(AppError.Network("Sin conexión a internet"))
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
