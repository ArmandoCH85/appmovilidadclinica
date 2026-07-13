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

    /**
     * Sincroniza la cache local con la lista del backend. Trae TODAS las
     * reservas del WORKER y las inserta con REPLACE: las filas locales
     * existentes (creadas por `confirm()` en este device) se actualizan
     * con el status fresco del backend (importante si otra sesion
     * cancela la reserva); las que no existen localmente aparecen.
     *
     * Preservacion de qrToken: REPLACE escribe TODA la fila, lo que
     * borraria el qrToken de las reservas que creamos localmente. Para
     * evitar perder ese dato irrecuperable, antes de cada upsert
     * consultamos la fila local y copiamos su qrToken al DTO convertido
     * a entidad. Si la fila no existia localmente, qrToken queda null
     * (el backend no lo manda).
     */
    override suspend fun syncFromBackend(): AppResult<Int> {
        val result = safeApiCall(errorMapper) { reservationsApi.list() }
        if (result !is AppResult.Success) {
            @Suppress("UNCHECKED_CAST")
            return result as AppResult<Int>
        }
        val entities = result.data.map { dto ->
            val existingQr = reservationDao.getById(dto.id)?.qrToken
            dto.toEntity(preservedQrToken = existingQr)
        }
        reservationDao.upsertAll(entities)
        return AppResult.Success(entities.size)
    }
}
