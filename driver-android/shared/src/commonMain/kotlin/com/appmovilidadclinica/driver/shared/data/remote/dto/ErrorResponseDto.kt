package com.appmovilidadclinica.driver.shared.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponseDto(
    val error: ErrorDetailDto? = null
)

@Serializable
data class ErrorDetailDto(
    val code: String? = null,
    val message: String? = null
)
