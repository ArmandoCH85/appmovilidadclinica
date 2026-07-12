package com.appmovilidadclinica.driver.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PassengerDto(
    @SerialName("reservation_id") val reservationId: Long,
    @SerialName("reservation_code") val reservationCode: String,
    @SerialName("worker_id") val workerId: Long,
    @SerialName("worker_full_name") val workerFullName: String,
    @SerialName("seat_number") val seatNumber: Int,
    @SerialName("seat_label") val seatLabel: String,
    @SerialName("origin_stop_order") val originStopOrder: Int,
    @SerialName("origin_stop_name") val originStopName: String,
    @SerialName("destination_stop_order") val destinationStopOrder: Int,
    @SerialName("destination_stop_name") val destinationStopName: String,
    val status: String,
    @SerialName("confirmed_at") val confirmedAt: String? = null,
    @SerialName("boarded_at") val boardedAt: String? = null
)
