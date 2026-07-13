package com.appmovilidadclinica.driver.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Long,
    val employeeCode: String,
    val documentNumber: String,
    val fullName: String,
    val role: String,
    val department: String?,
    val phone: String?,
    val driverLicenseNumber: String?,
    val driverLicenseCategory: String?,
    val driverLicenseExpiresOn: String?,
    val active: Boolean
)
