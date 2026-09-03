package com.appmovilidadclinica.passenger.data.remote

import com.appmovilidadclinica.passenger.shared.data.remote.dto.ErrorResponseDto
import com.appmovilidadclinica.passenger.shared.domain.error.AppError
import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

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

    private fun fallbackMessage(code: Int): String = when (code) {
        401 -> "Sesión expirada. Inicie sesión nuevamente."
        403 -> "No tiene permisos para realizar esta acción."
        404 -> "El recurso solicitado no existe."
        409 -> "La operación entra en conflicto con datos existentes."
        422 -> "Hay campos inválidos en el formulario."
        500 -> "Error interno del servidor."
        else -> "Ocurrió un error inesperado. Intente nuevamente."
    }

    companion object {
        private val defaultJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }
    }
}

suspend fun <T> safeApiCall(
    errorMapper: ApiErrorMapper,
    call: suspend () -> HttpResponse,
    parseBody: suspend (HttpResponse) -> T,
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
    AppResult.Failure(AppError.Unknown(e.message ?: "Ocurrió un error inesperado."))
}

suspend fun safeApiCallUnit(
    errorMapper: ApiErrorMapper,
    call: suspend () -> HttpResponse,
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
    AppResult.Failure(AppError.Unknown(e.message ?: "Ocurrió un error inesperado."))
}