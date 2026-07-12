package com.appmovilidadclinica.driver.data.remote

import com.appmovilidadclinica.driver.data.remote.dto.ErrorResponseDto
import com.appmovilidadclinica.driver.domain.model.AppError
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiErrorMapper @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true }

    fun map(throwable: Throwable): AppError {
        return when (throwable) {
            is HttpException -> mapHttpException(throwable)
            is IOException -> AppError.Network("Sin conexión a internet")
            else -> AppError.Unknown(throwable.message ?: "Error desconocido")
        }
    }

    private fun mapHttpException(exception: HttpException): AppError {
        val code = exception.code()
        val message = try {
            val errorBody = exception.response()?.errorBody()?.string()
            if (!errorBody.isNullOrBlank()) {
                val errorResponse = json.decodeFromString<ErrorResponseDto>(errorBody)
                errorResponse.error?.message ?: "Error del servidor"
            } else {
                "Error del servidor"
            }
        } catch (e: Exception) {
            "Error del servidor"
        }

        return when (code) {
            401 -> AppError.Unauthorized(message)
            403 -> AppError.Forbidden(message)
            404 -> AppError.NotFound(message)
            409 -> AppError.Conflict(message)
            422 -> AppError.Validation(null, message)
            else -> AppError.Unknown(message)
        }
    }
}
