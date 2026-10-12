package com.appmovilidadclinica.passenger.shared.data.mapper

import com.appmovilidadclinica.passenger.shared.data.remote.dto.StopDto
import com.appmovilidadclinica.passenger.shared.domain.model.Stop
import com.appmovilidadclinica.passenger.shared.domain.model.StopType

/**
 * DTO → dominio del catalogo de paradas (`GET /stops`).
 * Movido desde el modulo Android: al ser puro, iOS lo usa igual.
 */
fun StopDto.toDomain(): Stop = Stop(
    id = id,
    code = code,
    name = name,
    stopType = StopType.valueOf(stopType),
)
