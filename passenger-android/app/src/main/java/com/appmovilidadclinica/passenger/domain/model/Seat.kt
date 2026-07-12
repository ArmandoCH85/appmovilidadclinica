package com.appmovilidadclinica.passenger.domain.model

/**
 * Disponibilidad de un asiento para un tramo [origen,destino) concreto —
 * espejo de `sp_list_trip_seats`. `OCCUPIED_IN_REQUESTED_RANGE` y `BLOCKED`
 * son ambos "no elegible" mismo si en la UI se distinguen visualmente
 * (bloqueado permanente vs. ocupado solo en este tramo).
 */
enum class SeatAvailability { AVAILABLE, OCCUPIED_IN_REQUESTED_RANGE, BLOCKED }

data class TripSeat(
    /** `trip_seat_id` — el id que se manda en `POST /reservations`. */
    val tripSeatId: Long,
    val seatNumber: Int,
    val seatLabel: String,
    val availability: SeatAvailability,
) {
    val isSelectable: Boolean get() = availability == SeatAvailability.AVAILABLE
}
