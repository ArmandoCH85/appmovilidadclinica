package com.appmovilidadclinica.driver.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DriverTripDto(
    val id: Long,
    @SerialName("trip_code") val tripCode: String,
    @SerialName("route_id") val routeId: Long,
    @SerialName("route_code") val routeCode: String,
    @SerialName("route_name") val routeName: String,
    val direction: String,
    @SerialName("service_date") val serviceDate: String,
    @SerialName("scheduled_start_at") val scheduledStartAt: String,
    @SerialName("scheduled_end_at") val scheduledEndAt: String,
    @SerialName("vehicle_id") val vehicleId: Long,
    @SerialName("vehicle_code") val vehicleCode: String,
    val plate: String,
    @SerialName("seat_capacity_snapshot") val seatCapacity: Int,
    val status: String
)
