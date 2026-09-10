package com.appmovilidadclinica.passenger.data.repository

import com.appmovilidadclinica.passenger.data.local.ReservationDao
import com.appmovilidadclinica.passenger.data.mapper.toDomain
import com.appmovilidadclinica.passenger.data.mapper.toEntity
import com.appmovilidadclinica.passenger.data.remote.ApiErrorMapper
import com.appmovilidadclinica.passenger.data.remote.KtorApiClient
import com.appmovilidadclinica.passenger.data.remote.safeApiCall
import com.appmovilidadclinica.passenger.data.remote.safeApiCallUnit
import com.appmovilidadclinica.passenger.shared.data.remote.dto.ReportIncidentRequestDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.ReportIncidentResponseDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.ReservationListItemDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.ReservationRequestDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.ReservationResponseDto
import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import com.appmovilidadclinica.passenger.shared.domain.error.map
import com.appmovilidadclinica.passenger.shared.domain.model.Reservation
import com.appmovilidadclinica.passenger.shared.domain.model.ReservationRequest
import com.appmovilidadclinica.passenger.domain.repository.ReservationTripContext
import com.appmovilidadclinica.passenger.domain.repository.ReservationsRepository
import io.ktor.client.call.body
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReservationsRepositoryImpl @Inject constructor(
    private val apiClient: KtorApiClient,
    private val reservationDao: ReservationDao,
    private val errorMapper: ApiErrorMapper,
) : ReservationsRepository {

    override suspend fun confirm(
        request: ReservationRequest,
        tripContext: ReservationTripContext,
    ): AppResult<Reservation> {
        val result = safeApiCall<ReservationResponseDto>(
            errorMapper = errorMapper,
            call = {
                apiClient.reservationsApi.confirm(
                    ReservationRequestDto(
                        tripId = request.tripId,
                        tripSeatId = request.tripSeatId,
                        originTripStopTimeId = request.originTripStopTimeId,
                        destinationTripStopTimeId = request.destinationTripStopTimeId,
                    )
                )
            },
            parseBody = { it.body() },
        )
        if (result is AppResult.Success) {
            val entity = result.data.toEntity(request, tripContext, confirmedAt = Instant.now())
            reservationDao.upsert(entity)
            return AppResult.Success(entity.toDomain())
        }
        @Suppress("UNCHECKED_CAST")
        return result as AppResult<Reservation>
    }

    override suspend fun cancel(reservationId: Long): AppResult<Unit> {
        val result = safeApiCallUnit(errorMapper) { apiClient.reservationsApi.cancel(reservationId) }
        if (result is AppResult.Success) {
            reservationDao.updateStatus(reservationId, "CANCELLED")
        }
        return result
    }

    override suspend fun selfCheckin(reservationId: Long): AppResult<Reservation> {
        val result = safeApiCall<com.appmovilidadclinica.passenger.shared.data.remote.dto.SelfCheckinResponseDto>(
            errorMapper = errorMapper,
            call = { apiClient.reservationsApi.selfCheckin(reservationId) },
            parseBody = { it.body() },
        )
        if (result is AppResult.Success) {
            reservationDao.updateStatus(reservationId, result.data.status)
            val updated = reservationDao.getById(reservationId)
            if (updated != null) return AppResult.Success(updated.toDomain())
        }
        @Suppress("UNCHECKED_CAST")
        return result as AppResult<Reservation>
    }

    override suspend fun reportIncident(
        reservationId: Long,
        incidentType: String,
        description: String,
    ): AppResult<Long> {
        val result = safeApiCall<ReportIncidentResponseDto>(
            errorMapper = errorMapper,
            call = {
                apiClient.reservationsApi.reportIncident(
                    reservationId,
                    ReportIncidentRequestDto(incidentType, description),
                )
            },
            parseBody = { it.body() },
        )
        return result.map { it.id }
    }

    override fun observeReservations(): Flow<List<Reservation>> =
        reservationDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeReservation(reservationId: Long): Flow<Reservation?> =
        reservationDao.observeById(reservationId).map { it?.toDomain() }

    override suspend fun syncFromBackend(): AppResult<Int> {
        val result = safeApiCall<List<ReservationListItemDto>>(
            errorMapper = errorMapper,
            call = { apiClient.reservationsApi.list() },
            parseBody = { it.body() },
        )
        if (result !is AppResult.Success) {
            @Suppress("UNCHECKED_CAST")
            return result as AppResult<Int>
        }

        val remoteList = result.data
        val remoteIds = remoteList.map { it.id }

        val newEntities = remoteList.map { it.toEntity(preservedQrToken = null) }
        reservationDao.insertAllIgnore(newEntities)

        val localIds = reservationDao.getAllIds().toSet()
        for (dto in remoteList) {
            if (dto.id in localIds) {
                reservationDao.updateStatus(dto.id, dto.status)
            }
        }

        if (remoteIds.isNotEmpty()) {
            reservationDao.deleteOrphans(remoteIds)
        }

        return AppResult.Success(remoteList.size)
    }
}