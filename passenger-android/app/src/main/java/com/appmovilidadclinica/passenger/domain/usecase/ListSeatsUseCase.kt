package com.appmovilidadclinica.passenger.domain.usecase

import com.appmovilidadclinica.passenger.domain.error.AppError
import com.appmovilidadclinica.passenger.domain.error.AppResult
import com.appmovilidadclinica.passenger.domain.model.TripSeat
import com.appmovilidadclinica.passenger.domain.model.TripStop
import com.appmovilidadclinica.passenger.domain.repository.TripsRepository
import javax.inject.Inject

/**
 * Ver Specs #3: origen debe preceder a destino en `stop_order` — se valida
 * ACA, del lado del cliente, antes de pegarle al backend (que devolveria
 * 409 vía `sp_list_trip_seats` si se viola, pero es mejor no gastar la
 * llamada de red en un request que ya sabemos invalido).
 */
class ListSeatsUseCase @Inject constructor(
    private val tripsRepository: TripsRepository,
) {
    suspend operator fun invoke(
        tripId: Long,
        origin: TripStop,
        destination: TripStop,
    ): AppResult<List<TripSeat>> {
        if (origin.stopOrder >= destination.stopOrder) {
            return AppResult.Failure(
                AppError.Validation(field = null, message = "El origen debe ir antes que el destino en la ruta.")
            )
        }
        return tripsRepository.listSeats(tripId, origin.tripStopTimeId, destination.tripStopTimeId)
    }
}
