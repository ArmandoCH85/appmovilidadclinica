package com.appmovilidadclinica.driver.shared.data.repository

import com.appmovilidadclinica.driver.shared.data.mapper.toDomain
import com.appmovilidadclinica.driver.shared.data.remote.ApiErrorMapper
import com.appmovilidadclinica.driver.shared.data.remote.KtorApiClient
import com.appmovilidadclinica.driver.shared.data.local.SessionStore
import com.appmovilidadclinica.driver.shared.data.remote.dto.LoginRequestDto
import com.appmovilidadclinica.driver.shared.data.remote.dto.LoginResponseDto
import com.appmovilidadclinica.driver.shared.domain.model.AppError
import com.appmovilidadclinica.driver.shared.domain.model.AuthResult
import com.appmovilidadclinica.driver.shared.domain.model.User
import com.appmovilidadclinica.driver.shared.domain.repository.AuthRepository
import io.ktor.client.call.body
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val apiClient: KtorApiClient,
    private val sessionStore: SessionStore,
    private val apiErrorMapper: ApiErrorMapper,
) : AuthRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun login(documentNumber: String, password: String): Result<AuthResult> {
        return try {
            val response = apiClient.authApi.login(
                LoginRequestDto(
                    document_number = documentNumber,
                    password = password
                )
            )
            if (response.status.value == 200) {
                val body = response.body<LoginResponseDto>()
                val user = body.user.toDomain()
                val userJson = json.encodeToString(User.serializer(), user)
                val exp = parseTokenExpiration(body.token)
                sessionStore.saveSession(body.token, userJson, exp)
                Result.success(AuthResult(token = body.token, user = user))
            } else {
                val error = apiErrorMapper.map(response)
                Result.failure(error)
            }
        } catch (e: java.io.IOException) {
            Result.failure(AppError.Network("Sin conexión a internet"))
        } catch (e: Exception) {
            Result.failure(AppError.Unknown(e.message ?: "Error desconocido"))
        }
    }

    override suspend fun logout() {
        sessionStore.clearSession()
    }

    override fun isLoggedIn(): Flow<Boolean> {
        return sessionStore.getToken().map { !it.isNullOrBlank() }
    }

    override fun getCurrentUser(): Flow<User?> {
        return sessionStore.getUser().map { jsonStr ->
            jsonStr?.let { runCatching { json.decodeFromString(User.serializer(), it) }.getOrNull() }
        }
    }

    override fun getToken(): Flow<String?> {
        return sessionStore.getToken()
    }

    override suspend fun clearSession() {
        sessionStore.clearSession()
    }

    private fun parseTokenExpiration(token: String): Long {
        // Stub: expira en 24h. En realidad debería decodificar el JWT
        // y leer el `exp` field. Para Fase 4 alcanza.
        return System.currentTimeMillis() / 1000 + 24 * 60 * 60
    }
}
