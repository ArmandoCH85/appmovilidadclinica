package com.appmovilidadclinica.driver.data.remote

import com.appmovilidadclinica.driver.shared.data.remote.dto.ErrorResponseDto
import com.appmovilidadclinica.driver.shared.domain.model.AppError
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

class ApiErrorMapper {

    suspend fun map(httpResponse: HttpResponse): AppError {
        val raw = runCatching { httpResponse.bodyAsText() }.getOrNull()
        val message = parseMessage(raw) ?: fallbackMessage(httpResponse.status.value)
        return when (httpResponse.status.value) {
            401 -> AppError.Unauthorized(message)
            403 -> AppError.Forbidden(message)
            404 -> AppError.NotFound(message)
            409 -> AppError.Conflict(message)
            422 -> AppError.Validation(null, message)
            else -> AppError.Unknown(message)
        }
    }

    private fun parseMessage(raw: String?): String? {
        val text = raw?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            json.decodeFromString(ErrorResponseDto.serializer(), text).error?.message
        }.getOrNull()
    }

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
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }
    }
}