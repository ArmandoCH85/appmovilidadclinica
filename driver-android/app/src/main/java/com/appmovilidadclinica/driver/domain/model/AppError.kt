package com.appmovilidadclinica.driver.domain.model

sealed class AppError {
    data class Unauthorized(val message: String) : AppError()
    data class Forbidden(val message: String) : AppError()
    data class NotFound(val message: String) : AppError()
    data class Conflict(val message: String) : AppError()
    data class Validation(val field: String?, val message: String) : AppError()
    data class Network(val message: String) : AppError()
    data class Unknown(val message: String) : AppError()
}
