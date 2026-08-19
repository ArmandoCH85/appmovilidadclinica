package com.appmovilidadclinica.driver.shared.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequestDto(
    val document_number: String,
    val password: String
)
