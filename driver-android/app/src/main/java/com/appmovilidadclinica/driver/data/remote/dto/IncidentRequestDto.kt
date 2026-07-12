package com.appmovilidadclinica.driver.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class IncidentRequestDto(
    val incident_type: String,
    val description: String
)
