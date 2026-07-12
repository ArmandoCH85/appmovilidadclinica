package com.appmovilidadclinica.driver.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginResponseDto(
    val token: String,
    val user: UserDto
)
