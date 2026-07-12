package com.appmovilidadclinica.driver.domain.repository

import com.appmovilidadclinica.driver.domain.model.DriverTrip
import com.appmovilidadclinica.driver.domain.model.Incident
import com.appmovilidadclinica.driver.domain.model.Passenger
import com.appmovilidadclinica.driver.domain.model.TripStop
import java.time.LocalDate

interface DriverRepository {
    suspend fun getTrips(date: LocalDate): Result<List<DriverTrip>>
    suspend fun getPassengers(tripId: Long): Result<List<Passenger>>
    suspend fun getTripStops(tripId: Long): Result<List<TripStop>>
    suspend fun markArrival(tripStopTimeId: Long): Result<Unit>
    suspend fun markBoarded(reservationId: Long): Result<Unit>
    suspend fun markNoShow(reservationId: Long): Result<Unit>
    suspend fun markAlighted(reservationId: Long): Result<Unit>
    suspend fun reportIncident(tripId: Long, type: String, description: String): Result<Incident>
}
