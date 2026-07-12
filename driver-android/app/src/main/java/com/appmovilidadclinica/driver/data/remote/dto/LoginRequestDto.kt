package com.appmovilidadclinica.driver.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequestDto(
    val document_number: String,
    val password: String
)
