package com.appmovilidadclinica.passenger.shared.data.remote

import com.appmovilidadclinica.passenger.shared.data.remote.dto.*
import com.appmovilidadclinica.passenger.shared.domain.error.AppError
import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

/**
 * Traductor unico HTTP -> AppError, espejo Kotlin de
 * `admin/src/api/client.ts` (`extractMessage`) y del shape
 * `{"error":{"code","message"}}` que emite todo handler Go via
 * `apperror.WriteJSONError`. Ningun repository parsea un error a mano ”
 * todos pasan por `safeApiCall`.
 *
 * Variante Ktor (antes usaba Retrofit `Response.errorBody()`). Ahora
 * lee el body como texto via `bodyAsText()` y lo parsea con el mismo
 * `ErrorResponseDto`.
 */
class ApiErrorMapper(private val json: Json = defaultJson) {

    suspend fun map(httpResponse: HttpResponse): AppError {
        val raw = runCatching { httpResponse.bodyAsText() }.getOrNull()
        val message = parseMessage(raw) ?: fallbackMessage(httpResponse.status.value)
        return when (httpResponse.status.value) {
            401 -> AppError.Unauthorized(message)
            403 -> AppError.Forbidden(message)
            404 -> AppError.NotFound(message)
            409 -> AppError.Conflict(message)
            422 -> AppError.Validation(field = null, message = message)
            else -> AppError.Unknown(message)
        }
    }

    private fun parseMessage(raw: String?): String? {
        val text = raw?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            json.decodeFromString(ErrorResponseDto.serializer(), text).error.message
        }.getOrNull()
    }

    // Mismo texto que admin/src/messages.ts ” consistencia entre el
    // panel web y esta app.
    private fun fallbackMessage(code: Int): String = when (code) {
        401 -> "Sesion expirada. Inicie sesion nuevamente."
        403 -> "No tiene permisos para realizar esta accion."
        404 -> "El recurso solicitado no existe."
        409 -> "La operacion entra en conflicto con datos existentes."
        422 -> "Hay campos invalidos en el formulario."
        500 -> "Error interno del servidor."
        else -> "Ocurrio un error inesperado. Intente nuevamente."
    }

    companion object {
        private val defaultJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }
    }
}

/**
 * Envoltorio para requests con body de respuesta (200/201).
 * Equivalente Ktor del antiguo `safeApiCall` Retrofit.
 */
suspend inline fun <T> safeApiCall(
    errorMapper: ApiErrorMapper,
    crossinline call: suspend () -> HttpResponse,
    crossinline parseBody: suspend (HttpResponse) -> T,
): AppResult<T> = try {
    val response = call()
    if (response.status.value in 200..299) {
        AppResult.Success(parseBody(response))
    } else {
        AppResult.Failure(errorMapper.map(response))
    }
} catch (e: java.io.IOException) {
    AppResult.Failure(AppError.Network(e.message ?: "No se pudo conectar con el servidor."))
} catch (e: Exception) {
    AppResult.Failure(AppError.Unknown(e.message ?: "Ocurrio un error inesperado."))
}

/**
 * Envoltorio para requests sin body de respuesta (204, ej. cancel).
 */
suspend inline fun safeApiCallUnit(
    errorMapper: ApiErrorMapper,
    crossinline call: suspend () -> HttpResponse,
): AppResult<Unit> = try {
    val response = call()
    if (response.status.value in 200..299) {
        AppResult.Success(Unit)
    } else {
        AppResult.Failure(errorMapper.map(response))
    }
} catch (e: java.io.IOException) {
    AppResult.Failure(AppError.Network(e.message ?: "No se pudo conectar con el servidor."))
} catch (e: Exception) {
    AppResult.Failure(AppError.Unknown(e.message ?: "Ocurrio un error inesperado."))
}