package com.appmovilidadclinica.driver.shared.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class VerifyQrRequestDto(
    val token: String
)
