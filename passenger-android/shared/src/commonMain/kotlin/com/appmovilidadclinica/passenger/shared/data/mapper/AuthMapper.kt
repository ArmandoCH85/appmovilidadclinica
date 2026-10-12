package com.appmovilidadclinica.passenger.shared.data.mapper

import com.appmovilidadclinica.passenger.shared.data.local.StoredSession
import com.appmovilidadclinica.passenger.shared.data.remote.dto.LoginResponseDto

/**
 * Respuesta del login (`auth/login`) → sesion persistible.
 * Misma conversion que ya hacia el modulo Android (`AuthRepositoryImpl`),
 * ahora en `shared` para que tambien la use iOS.
 */
fun LoginResponseDto.toStoredSession(): StoredSession = StoredSession(
    token = token,
    userId = user.id,
    employeeCode = user.employeeCode,
    fullName = user.fullName,
    role = user.role,
    department = user.department,
    phone = user.phone,
)
