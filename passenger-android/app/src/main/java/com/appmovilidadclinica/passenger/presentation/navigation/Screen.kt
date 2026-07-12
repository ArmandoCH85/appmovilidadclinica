package com.appmovilidadclinica.passenger.presentation.navigation

import kotlinx.serialization.Serializable

/**
 * Rutas tipadas (Navigation Compose 2.8+, sin strings de ruta a mano ni
 * `Bundle` manual) — cada pantalla declara exactamente los argumentos que
 * necesita, verificados en tiempo de compilacion.
 */
sealed interface Screen {
    @Serializable
    data object Login : Screen

    @Serializable
    data object TripSearch : Screen

    @Serializable
    data object MyReservations : Screen

    @Serializable
    data class SeatSelection(
        val tripId: Long,
        val originStopId: Long,
        val destinationStopId: Long,
    ) : Screen

    @Serializable
    data class MyReservationDetail(val reservationId: Long) : Screen
}
