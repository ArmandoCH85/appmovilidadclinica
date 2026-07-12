package com.appmovilidadclinica.passenger.data.repository

import com.appmovilidadclinica.passenger.data.mapper.toDomain
import com.appmovilidadclinica.passenger.data.remote.ApiErrorMapper
import com.appmovilidadclinica.passenger.data.remote.TripsApi
import com.appmovilidadclinica.passenger.data.remote.safeApiCall
import com.appmovilidadclinica.passenger.domain.error.AppResult
import com.appmovilidadclinica.passenger.domain.error.map
import com.appmovilidadclinica.passenger.domain.model.TripDetail
import com.appmovilidadclinica.passenger.domain.model.TripDirection
import com.appmovilidadclinica.passenger.domain.model.TripSearchResult
import com.appmovilidadclinica.passenger.domain.model.TripSeat
import com.appmovilidadclinica.passenger.domain.repository.TripsRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripsRepositoryImpl @Inject constructor(
    private val tripsApi: TripsApi,
    private val errorMapper: ApiErrorMapper,
) : TripsRepository {

    override suspend fun search(
        date: LocalDate,
        direction: TripDirection,
        originStopId: Long,
        destinationStopId: Long,
    ): AppResult<List<TripSearchResult>> =
        safeApiCall(errorMapper) {
            tripsApi.search(date.toString(), direction.name, originStopId, destinationStopId)
        }.map { list -> list.map { it.toDomain() } }

    override suspend fun getDetail(tripId: Long): AppResult<TripDetail> =
        safeApiCall(errorMapper) { tripsApi.getDetail(tripId) }.map { it.toDomain() }

    override suspend fun listSeats(
        tripId: Long,
        originTripStopTimeId: Long,
        destinationTripStopTimeId: Long,
    ): AppResult<List<TripSeat>> =
        safeApiCall(errorMapper) {
            tripsApi.listSeats(tripId, originTripStopTimeId, destinationTripStopTimeId)
        }.map { list -> list.map { it.toDomain() } }
}
