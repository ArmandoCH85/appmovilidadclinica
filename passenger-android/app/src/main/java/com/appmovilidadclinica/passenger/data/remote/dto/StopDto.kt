package com.appmovilidadclinica.passenger.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mismo shape que `admin.Stop` (backend) — ver diseño técnico #2. */
@Serializable
data class StopDto(
    val id: Long,
    val code: String,
    val name: String,
    @SerialName("stop_type") val stopType: String,
)
