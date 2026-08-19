package com.appmovilidadclinica.passenger.data.repository

import android.util.Base64
import com.appmovilidadclinica.passenger.data.local.SessionDataStore
import com.appmovilidadclinica.passenger.data.local.StoredSession
import com.appmovilidadclinica.passenger.data.mapper.toDomain
import com.appmovilidadclinica.passenger.data.remote.ApiErrorMapper
import com.appmovilidadclinica.passenger.data.remote.KtorApiClient
import com.appmovilidadclinica.passenger.data.remote.SessionExpiredNotifier
import com.appmovilidadclinica.passenger.data.remote.safeApiCall
import com.appmovilidadclinica.passenger.shared.data.remote.dto.LoginRequestDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.LoginResponseDto
import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import com.appmovilidadclinica.passenger.shared.domain.model.User
import com.appmovilidadclinica.passenger.domain.repository.AuthRepository
import io.ktor.client.call.body
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val apiClient: KtorApiClient,
    private val sessionDataStore: SessionDataStore,
    private val errorMapper: ApiErrorMapper,
    private val sessionExpiredNotifier: SessionExpiredNotifier,
    private val json: Json,
) : AuthRepository {

    override suspend fun login(documentNumber: String, password: String): AppResult<User> {
        val apiResult = safeApiCall<LoginResponseDto>(
            errorMapper = errorMapper,
            call = { apiClient.authApi.login(LoginRequestDto(documentNumber, password)) },
            parseBody = { it.body() },
        )
        @Suppress("UNCHECKED_CAST")
        return when (apiResult) {
            is AppResult.Success -> {
                val body = apiResult.data
                sessionDataStore.save(
                    StoredSession(
                        token = body.token,
                        userId = body.user.id,
                        employeeCode = body.user.employeeCode,
                        fullName = body.user.fullName,
                        role = body.user.role,
                        department = body.user.department,
                        phone = body.user.phone,
                    )
                )
                AppResult.Success(body.user.toDomain())
            }
            is AppResult.Failure -> apiResult as AppResult<User>
        }
    }

    override suspend fun logout() {
        sessionDataStore.clear()
    }

    override fun observeSession(): Flow<User?> =
        sessionDataStore.sessionFlow.map { it?.toDomain() }

    override fun observeSecondsUntilExpiry(): Flow<Long?> {
        val ticker = flow {
            while (true) {
                emit(Unit)
                delay(1_000)
            }
        }
        return combine(sessionDataStore.tokenFlow, ticker) { token, _ -> token }
            .map { token ->
                if (token == null) return@map null
                val expiresAt = expiresAtEpochSeconds(token) ?: return@map null
                val nowSeconds = System.currentTimeMillis() / 1000
                (expiresAt - nowSeconds).coerceAtLeast(0)
            }
    }

    override fun observeSessionExpired(): Flow<Unit> = sessionExpiredNotifier.events

    private fun expiresAtEpochSeconds(token: String): Long? {
        val parts = token.split(".")
        if (parts.size != 3) return null
        return runCatching {
            val payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val payload = json.parseToJsonElement(String(payloadBytes, Charsets.UTF_8)) as JsonObject
            payload["exp"]?.jsonPrimitive?.longOrNull
        }.getOrNull()
    }
}