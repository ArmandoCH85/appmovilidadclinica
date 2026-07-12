package com.appmovilidadclinica.passenger.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequestDto(
    @SerialName("document_number") val documentNumber: String,
    val password: String,
)

@Serializable
data class LoginResponseDto(
    val token: String,
    val user: UserDto,
)

@Serializable
data class UserDto(
    val id: Long,
    @SerialName("employee_code") val employeeCode: String,
    @SerialName("document_number") val documentNumber: String,
    @SerialName("full_name") val fullName: String,
    val role: String,
    val department: String? = null,
    val phone: String? = null,
    val active: Boolean = true,
)
