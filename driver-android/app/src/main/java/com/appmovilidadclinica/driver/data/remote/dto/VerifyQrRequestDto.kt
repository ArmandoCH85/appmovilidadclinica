package com.appmovilidadclinica.driver.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class VerifyQrRequestDto(
    val token: String
)
