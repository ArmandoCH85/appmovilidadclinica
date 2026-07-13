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
     * Sincroniza la cache local con la lista del backend.
     *
     * Estrategia que NO pisa el qrToken de reservas ya existentes:
     * 1. INSERT OR IGNORE: inserta solo las reservas NUEVAS (que no
     *    existen en Room). Las que ya existen se ignoran — su qrToken
     *    y todos sus datos locales se preservan intactos.
     * 2. UPDATE status: para las reservas que YA existen localmente,
     *    actualiza SOLO el status (por si cambi en el backend: fue
     *    cancelada, abordada, completada, etc.). No toca qrToken ni
     *    ningun otro campo.
     * 3. DELETE orphans: elimina de Room las reservas que ya no
     *    existen en el backend (por ejemplo, si un admin las borr).
     *
     * Las reservas nuevas del sync vienen SIN qrToken (el backend
     * nunca lo expone despus del confirm inicial). La UI muestra
     * "QR no disponible" para esas.
     */
    override suspend fun syncFromBackend(): AppResult<Int> {
        val result = safeApiCall(errorMapper) { reservationsApi.list() }
        if (result !is AppResult.Success) {
            @Suppress("UNCHECKED_CAST")
            return result as AppResult<Int>
        }

        val remoteList = result.data
        val remoteIds = remoteList.map { it.id }

        // 1. Insertar solo las nuevas (IGNORE las que ya existen)
        val newEntities = remoteList.map { it.toEntity(preservedQrToken = null) }
        reservationDao.insertAllIgnore(newEntities)

        // 2. Actualizar SOLO el status de las que ya existan localmente
        val localIds = reservationDao.getAllIds().toSet()
        for (dto in remoteList) {
            if (dto.id in localIds) {
                reservationDao.updateStatus(dto.id, dto.status)
            }
        }

        // 3. Eliminar las que ya no existen en el backend
        if (remoteIds.isNotEmpty()) {
            reservationDao.deleteOrphans(remoteIds)
        }

        return AppResult.Success(remoteList.size)
    }
}
