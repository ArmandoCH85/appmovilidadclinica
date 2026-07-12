package com.appmovilidadclinica.passenger.data.mapper

import com.appmovilidadclinica.passenger.data.local.StoredSession
import com.appmovilidadclinica.passenger.data.remote.dto.UserDto
import com.appmovilidadclinica.passenger.domain.model.User
import com.appmovilidadclinica.passenger.domain.model.UserRole

fun UserDto.toDomain(): User = User(
    id = id,
    employeeCode = employeeCode,
    fullName = fullName,
    role = UserRole.fromRaw(role),
    department = department,
    phone = phone,
)

fun StoredSession.toDomain(): User = User(
    id = userId,
    employeeCode = employeeCode,
    fullName = fullName,
    role = UserRole.fromRaw(role),
    department = department,
    phone = phone,
)
