package com.appmovilidadclinica.driver.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TripStopDto(
    val id: Long,
    @SerialName("stop_name") val stopName: String,
    @SerialName("stop_order") val stopOrder: Int,
    @SerialName("scheduled_arrival_at") val scheduledArrivalAt: String? = null,
    @SerialName("scheduled_departure_at") val scheduledDepartureAt: String? = null,
    @SerialName("actual_arrival_at") val actualArrivalAt: String? = null,
    @SerialName("actual_departure_at") val actualDepartureAt: String? = null,
    val status: String
)
