package com.appmovilidadclinica.driver.data.mapper

import com.appmovilidadclinica.driver.data.remote.dto.UserDto
import com.appmovilidadclinica.driver.domain.model.User

fun UserDto.toDomain(): User = User(
    id = id,
    employeeCode = employeeCode,
    documentNumber = documentNumber,
    fullName = fullName,
    role = role,
    department = department,
    phone = phone,
    driverLicenseNumber = driverLicenseNumber,
    driverLicenseCategory = driverLicenseCategory,
    driverLicenseExpiresOn = driverLicenseExpiresOn,
    active = active
)
