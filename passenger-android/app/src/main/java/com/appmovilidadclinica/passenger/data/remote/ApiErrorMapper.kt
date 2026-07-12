package com.appmovilidadclinica.passenger.data.remote

import com.appmovilidadclinica.passenger.data.remote.dto.ErrorResponseDto
import com.appmovilidadclinica.passenger.domain.error.AppError
import com.appmovilidadclinica.passenger.domain.error.AppResult
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Traductor unico HTTP -> AppError, reflejo Kotlin de
 * `admin/src/api/client.ts` (`extractMessage`) y del shape
 * `{"error":{"code","message"}}` que emite todo handler Go via
 * `apperror.WriteJSONError`. Ningun repository parsea un error a mano —
 * todos pasan por `safeApiCall`.
 */
@Singleton
class ApiErrorMapper @Inject constructor(private val json: Json) {

    fun map(code: Int, errorBody: ResponseBody?): AppError {
        val message = parseMessage(errorBody) ?: fallbackMessage(code)
        return when (code) {
            401 -> AppError.Unauthorized(message)
            403 -> AppError.Forbidden(message)
            404 -> AppError.NotFound(message)
            409 -> AppError.Conflict(message)
            422 -> AppError.Validation(field = null, message = message)
            else -> AppError.Unknown(message)
        }
    }

    private fun parseMessage(errorBody: ResponseBody?): String? {
        val raw = errorBody?.string()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { json.decodeFromString(ErrorResponseDto.serializer(), raw).error.message }
            .getOrNull()
    }

    // Mismo texto que ERROR_BY_STATUS/ERROR_FALLBACK_DEFAULT en
    // admin/src/messages.ts — consistencia entre el panel web y esta app.
    private fun fallbackMessage(code: Int): String = when (code) {
        401 -> "Sesión expirada. Inicie sesión nuevamente."
        403 -> "No tiene permisos para realizar esta acción."
        404 -> "El recurso solicitado no existe."
        409 -> "La operación entra en conflicto con datos existentes."
        422 -> "Hay campos inválidos en el formulario."
        500 -> "Error interno del servidor."
        else -> "Ocurrió un error inesperado. Intente nuevamente."
    }
}

/** Envoltorio para requests con body de respuesta (200/201). */
suspend fun <T> safeApiCall(errorMapper: ApiErrorMapper, call: suspend () -> Response<T>): AppResult<T> =
    try {
        val response = call()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            AppResult.Success(body)
        } else {
            AppResult.Failure(errorMapper.map(response.code(), response.errorBody()))
        }
    } catch (e: IOException) {
        AppResult.Failure(AppError.Network(e.message ?: "No se pudo conectar con el servidor."))
    } catch (e: Exception) {
        AppResult.Failure(AppError.Unknown(e.message ?: "Ocurrió un error inesperado."))
    }

/** Envoltorio para requests sin body de respuesta (204, ej. cancel). */
suspend fun safeApiCallUnit(errorMapper: ApiErrorMapper, call: suspend () -> Response<Unit>): AppResult<Unit> =
    try {
        val response = call()
        if (response.isSuccessful) {
            AppResult.Success(Unit)
        } else {
            AppResult.Failure(errorMapper.map(response.code(), response.errorBody()))
        }
    } catch (e: IOException) {
        AppResult.Failure(AppError.Network(e.message ?: "No se pudo conectar con el servidor."))
    } catch (e: Exception) {
        AppResult.Failure(AppError.Unknown(e.message ?: "Ocurrió un error inesperado."))
    }
