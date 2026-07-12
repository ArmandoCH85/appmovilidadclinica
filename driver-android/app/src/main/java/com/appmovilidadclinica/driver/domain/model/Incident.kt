package com.appmovilidadclinica.driver.domain.model

data class Incident(
    val id: Long,
    val tripId: Long,
    val incidentType: IncidentType,
    val description: String
)
