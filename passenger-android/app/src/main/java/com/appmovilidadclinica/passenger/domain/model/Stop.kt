package com.appmovilidadclinica.passenger.domain.model

enum class StopType { SEDE, PARADERO }

/** Catálogo de paradas — consume `GET /api/stops` (endpoint nuevo, ver diseño técnico #2). */
data class Stop(
    val id: Long,
    val code: String,
    val name: String,
    val stopType: StopType,
)
