package com.appmovilidadclinica.passenger.data.repository

import com.appmovilidadclinica.passenger.data.mapper.toDomain
import com.appmovilidadclinica.passenger.data.remote.ApiErrorMapper
import com.appmovilidadclinica.passenger.data.remote.KtorApiClient
import com.appmovilidadclinica.passenger.data.remote.safeApiCall
import com.appmovilidadclinica.passenger.shared.data.remote.dto.SeatResultDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.TripDetailResponseDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.TripSearchResultDto
import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import com.appmovilidadclinica.passenger.shared.domain.error.map
import com.appmovilidadclinica.passenger.shared.domain.model.TripDetail
import com.appmovilidadclinica.passenger.shared.domain.model.TripDirection
import com.appmovilidadclinica.passenger.shared.domain.model.TripSearchResult
import com.appmovilidadclinica.passenger.shared.domain.model.TripSeat
import com.appmovilidadclinica.passenger.domain.repository.TripsRepository
import io.ktor.client.call.body
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripsRepositoryImpl @Inject constructor(
    private val apiClient: KtorApiClient,
    private val errorMapper: ApiErrorMapper,
) : TripsRepository {

    override suspend fun search(
        date: LocalDate,
        direction: TripDirection,
        originStopId: Long,
        destinationStopId: Long,
    ): AppResult<List<TripSearchResult>> =
        safeApiCall<List<TripSearchResultDto>>(
            errorMapper = errorMapper,
            call = { apiClient.tripsApi.search(date.toString(), direction.name, originStopId, destinationStopId) },
            parseBody = { it.body() },
        ).map { list -> list.map { it.toDomain() } }

    override suspend fun getDetail(tripId: Long): AppResult<TripDetail> =
        safeApiCall<TripDetailResponseDto>(
            errorMapper = errorMapper,
            call = { apiClient.tripsApi.getDetail(tripId) },
            parseBody = { it.body() },
        ).map { it.toDomain() }

    override suspend fun listSeats(
        tripId: Long,
        originTripStopTimeId: Long,
        destinationTripStopTimeId: Long,
    ): AppResult<List<TripSeat>> =
        safeApiCall<List<SeatResultDto>>(
            errorMapper = errorMapper,
            call = { apiClient.tripsApi.listSeats(tripId, originTripStopTimeId, destinationTripStopTimeId) },
            parseBody = { it.body() },
        ).map { list -> list.map { it.toDomain() } }
}