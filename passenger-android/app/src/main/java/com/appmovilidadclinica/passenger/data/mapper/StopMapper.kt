package com.appmovilidadclinica.passenger.data.mapper

import com.appmovilidadclinica.passenger.data.remote.dto.StopDto
import com.appmovilidadclinica.passenger.domain.model.Stop
import com.appmovilidadclinica.passenger.domain.model.StopType

fun StopDto.toDomain(): Stop = Stop(
    id = id,
    code = code,
    name = name,
    stopType = StopType.valueOf(stopType),
)
