package com.appmovilidadclinica.driver.domain.model

sealed class AppError(msg: String) : Exception(msg) {
    data class Unauthorized(override val message: String) : AppError(message)
    data class Forbidden(override val message: String) : AppError(message)
    data class NotFound(override val message: String) : AppError(message)
    data class Conflict(override val message: String) : AppError(message)
    data class Validation(val field: String?, override val message: String) : AppError(message)
    data class Network(override val message: String) : AppError(message)
    data class Unknown(override val message: String) : AppError(message)
}
