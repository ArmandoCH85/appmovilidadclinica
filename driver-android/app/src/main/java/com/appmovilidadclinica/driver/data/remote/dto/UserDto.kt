package com.appmovilidadclinica.driver.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: Long,
    @SerialName("employee_code") val employeeCode: String,
    @SerialName("document_number") val documentNumber: String,
    @SerialName("full_name") val fullName: String,
    val role: String,
    val department: String? = null,
    val phone: String? = null,
    @SerialName("driver_license_number") val driverLicenseNumber: String? = null,
    @SerialName("driver_license_category") val driverLicenseCategory: String? = null,
    @SerialName("driver_license_expires_on") val driverLicenseExpiresOn: String? = null,
    val active: Boolean
)
