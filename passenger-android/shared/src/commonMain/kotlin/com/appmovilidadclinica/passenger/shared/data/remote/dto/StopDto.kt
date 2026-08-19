package com.appmovilidadclinica.passenger.shared.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mismo shape que `admin.Stop` (backend) â€” ver diseÃ±o tÃ©cnico #2. */
@Serializable
data class StopDto(
    val id: Long,
    val code: String,
    val name: String,
    @SerialName("stop_type") val stopType: String,
)
