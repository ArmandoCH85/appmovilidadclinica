package com.appmovilidadclinica.passenger.data.mapper

import com.appmovilidadclinica.passenger.shared.data.remote.dto.StopDto
import com.appmovilidadclinica.passenger.shared.domain.model.Stop
import com.appmovilidadclinica.passenger.shared.domain.model.StopType

fun StopDto.toDomain(): Stop = Stop(
    id = id,
    code = code,
    name = name,
    stopType = StopType.valueOf(stopType),
)
