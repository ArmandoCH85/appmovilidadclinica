package com.appmovilidadclinica.passenger.data.repository

import com.appmovilidadclinica.passenger.data.local.ReservationDao
import com.appmovilidadclinica.passenger.data.mapper.toDomain
import com.appmovilidadclinica.passenger.data.mapper.toEntity
import com.appmovilidadclinica.passenger.data.remote.ApiErrorMapper
import com.appmovilidadclinica.passenger.data.remote.ReservationsApi
import com.appmovilidadclinica.passenger.data.remote.dto.ReservationRequestDto
import com.appmovilidadclinica.passenger.data.remote.safeApiCall
import com.appmovilidadclinica.passenger.data.remote.safeApiCallUnit
import com.appmovilidadclinica.passenger.domain.error.AppResult
import com.appmovilidadclinica.passenger.domain.model.Reservation
import com.appmovilidadclinica.passenger.domain.model.ReservationRequest
import com.appmovilidadclinica.passenger.domain.repository.ReservationTripContext
import com.appmovilidadclinica.passenger.domain.repository.ReservationsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReservationsRepositoryImpl @Inject constructor(
    private val reservationsApi: ReservationsApi,
    private val reservationDao: ReservationDao,
    private val errorMapper: ApiErrorMapper,
) : ReservationsRepository {

    override suspend fun confirm(
        request: ReservationRequest,
        tripContext: ReservationTripContext,
    ): AppResult<Reservation> {
        val result = safeApiCall(errorMapper) {
            reservationsApi.confirm(
                ReservationRequestDto(
                    tripId = request.tripId,
                    tripSeatId = request.tripSeatId,
                    originTripStopTimeId = request.originTripStopTimeId,
                    destinationTripStopTimeId = request.destinationTripStopTimeId,
                )
            )
        }
        if (result is AppResult.Success) {
            // CRITICO (ver Specs #3 y diseño técnico): persistir el qr_token
            // es la PRIMERA accion tras la respuesta 201, antes de cualquier
            // otra cosa — es la unica vez que el backend lo entrega en claro.
            val entity = result.data.toEntity(request, tripContext, confirmedAt = Instant.now())
            reservationDao.upsert(entity)
            return AppResult.Success(entity.toDomain())
        }
        @Suppress("UNCHECKED_CAST")
        return result as AppResult<Reservation>
    }

    override suspend fun cancel(reservationId: Long): AppResult<Unit> {
        val result = safeApiCallUnit(errorMapper) { reservationsApi.cancel(reservationId) }
        if (result is AppResult.Success) {
            reservationDao.updateStatus(reservationId, "CANCELLED")
        }
        return result
    }

    override suspend fun selfCheckin(reservationId: Long): AppResult<Reservation> {
        val result = safeApiCall(errorMapper) { reservationsApi.selfCheckin(reservationId) }
        if (result is AppResult.Success) {
            reservationDao.updateStatus(reservationId, result.data.status)
            val updated = reservationDao.getById(reservationId)
            if (updated != null) return AppResult.Success(updated.toDomain())
        }
        @Suppress("UNCHECKED_CAST")
        return result as AppResult<Reservation>
    }

    override fun observeReservations(): Flow<List<Reservation>> =
        reservationDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeReservation(reservationId: Long): Flow<Reservation?> =
        reservationDao.observeById(reservationId).map { it?.toDomain() }
}
