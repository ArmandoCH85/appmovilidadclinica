package com.appmovilidadclinica.driver.shared.domain.repository

import com.appmovilidadclinica.driver.shared.domain.model.DriverTrip
import com.appmovilidadclinica.driver.shared.domain.model.Incident
import com.appmovilidadclinica.driver.shared.domain.model.Passenger
import com.appmovilidadclinica.driver.shared.domain.model.TripStop
import kotlinx.datetime.LocalDate

interface DriverRepository {
    suspend fun getTrips(date: LocalDate): Result<List<DriverTrip>>
    suspend fun getPassengers(tripId: Long): Result<List<Passenger>>
    suspend fun getTripStops(tripId: Long): Result<List<TripStop>>
    suspend fun startTrip(tripId: Long): Result<Unit>
    suspend fun completeTrip(tripId: Long): Result<Unit>
    suspend fun markArrival(tripStopTimeId: Long): Result<Unit>
    suspend fun markDeparture(tripStopTimeId: Long): Result<Unit>
    suspend fun markBoarded(reservationId: Long): Result<Unit>
    suspend fun markNoShow(reservationId: Long): Result<Unit>
    suspend fun markAlighted(reservationId: Long): Result<Unit>
    suspend fun reportIncident(tripId: Long, type: String, description: String): Result<Incident>
}
