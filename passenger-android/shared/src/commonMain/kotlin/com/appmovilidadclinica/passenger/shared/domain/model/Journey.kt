package com.appmovilidadclinica.passenger.shared.domain.model

/** Estado de polling del pasajero (espejo de GET /reservations/{id}/journey). */
data class JourneyState(
    val reservationId: Long,
    val reservationStatus: ReservationStatus,
    val tripStatus: TripStatus,
    val destinationStopOrder: Int,
    val stops: List<TripStop>,
    val canExtend: Boolean,
    val extension: ExtensionOffer?,
)

data class ExtensionOffer(
    val currentSeatFree: Boolean,
    val remainingStops: List<ExtensionStop>,
)

data class ExtensionStop(
    val tripStopTimeId: Long,
    val stopName: String,
    val stopOrder: Int,
)

/** Resultado de POST /reservations/{id}/extend. */
data class ExtendResult(
    val reservationId: Long,
    val destinationStopOrder: Int,
    val tripSeatId: Long,
    val seatLabel: String,
    val status: ReservationStatus,
)
