package com.appmovilidadclinica.passenger.shared.di

import com.appmovilidadclinica.passenger.data.remote.createHttpClient
import com.appmovilidadclinica.passenger.shared.data.local.SessionStore
import com.appmovilidadclinica.passenger.shared.data.local.StoredSession
import com.appmovilidadclinica.passenger.shared.data.mapper.toStoredSession
import com.appmovilidadclinica.passenger.shared.data.remote.AuthApi
import com.appmovilidadclinica.passenger.shared.data.remote.dto.ErrorResponseDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.LoginRequestDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.LoginResponseDto
import com.appmovilidadclinica.passenger.shared.domain.error.AppError
import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import com.appmovilidadclinica.passenger.shared.platform.createPlatformSettings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

/**
 * Grafo minimo de la app compartida (Etapa 1 iOS): sesion persistida, cliente
 * HTTP multiplataforma y login.
 *
 * Es lo que las plataformas necesitan para arrancar la UI comun:
 * - iOS: `MainViewController()` lo crea y monta [com.appmovilidadclinica.passenger.shared.ui.PassengerApp].
 * - Android: la app lo consumira cuando se migre la pantalla de login (hoy
 *   sigue con su propio Hilt/Retrofit sin cambios).
 */
class PassengerSharedGraph internal constructor(
    val sessionStore: SessionStore,
    val httpClient: HttpClient,
    private val authApi: AuthApi,
) {

    /**
     * Login contra `POST /auth/login`. En exito devuelve la sesion lista para
     * persistir; en error, un [AppError] ya mapeado (nunca lanza).
     */
    suspend fun signIn(documentNumber: String, password: String): AppResult<StoredSession> =
        try {
            val response = authApi.login(LoginRequestDto(documentNumber, password))
            if (response.status.isSuccess()) {
                val body: LoginResponseDto = response.body()
                AppResult.Success(body.toStoredSession())
            } else {
                AppResult.Failure(response.toAppError())
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            AppResult.Failure(
                AppError.Network(error.message ?: "Sin conexión a internet.")
            )
        }

    fun close() {
        httpClient.close()
    }

    companion object {
        /** Arma el grafo con el engine HTTP de la plataforma (OkHttp / Darwin). */
        fun create(): PassengerSharedGraph {
            val sessionStore = SessionStore(createPlatformSettings())
            val client = createHttpClient(tokenProvider = { sessionStore.currentToken() })
            return PassengerSharedGraph(
                sessionStore = sessionStore,
                httpClient = client,
                authApi = AuthApi(client),
            )
        }
    }
}

/** Traduce el error del backend (`{"error":{"code","message"}}`) a [AppError]. */
private suspend fun HttpResponse.toAppError(): AppError {
    val message = runCatching { body<ErrorResponseDto>().error.message }.getOrNull()
    return when (status.value) {
        401 -> AppError.Unauthorized(message ?: "Usuario o contraseña incorrectos.")
        403 -> AppError.Forbidden(message ?: "No tiene permiso para esta acción.")
        404 -> AppError.NotFound(message ?: "No se encontró el recurso.")
        409 -> AppError.Conflict(message ?: "La operación no se pudo completar.")
        422 -> AppError.Validation(null, message ?: "Datos inválidos.")
        else -> AppError.Unknown(message ?: "Error del servidor (${status.value}).")
    }
}
