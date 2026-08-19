package com.appmovilidadclinica.passenger.shared.domain.model

enum class StopType { SEDE, PARADERO }

/** CatÃ¡logo de paradas â€” consume `GET /api/stops` (endpoint nuevo, ver diseÃ±o tÃ©cnico #2). */
data class Stop(
    val id: Long,
    val code: String,
    val name: String,
    val stopType: StopType,
)
