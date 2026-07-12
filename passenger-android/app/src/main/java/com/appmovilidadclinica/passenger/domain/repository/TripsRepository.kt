package com.appmovilidadclinica.passenger.domain.repository

import com.appmovilidadclinica.passenger.domain.error.AppResult
import com.appmovilidadclinica.passenger.domain.model.TripDetail
import com.appmovilidadclinica.passenger.domain.model.TripDirection
import com.appmovilidadclinica.passenger.domain.model.TripSearchResult
import com.appmovilidadclinica.passenger.domain.model.TripSeat
import java.time.LocalDate

interface TripsRepository {
    /** GET /api/trips?date=&direction=&origin=&destination= (origin/destination = stop_id). */
    suspend fun search(
        date: LocalDate,
        direction: TripDirection,
        originStopId: Long,
        destinationStopId: Long,
    ): AppResult<List<TripSearchResult>>

    /** GET /api/trips/{id} — cronograma completo, resuelve trip_stop_time_id de cada parada. */
    suspend fun getDetail(tripId: Long): AppResult<TripDetail>

    /** GET /api/trips/{id}/seats?origin=&destination= (acá origin/destination = trip_stop_time_id). */
    suspend fun listSeats(
        tripId: Long,
        originTripStopTimeId: Long,
        destinationTripStopTimeId: Long,
    ): AppResult<List<TripSeat>>
}
