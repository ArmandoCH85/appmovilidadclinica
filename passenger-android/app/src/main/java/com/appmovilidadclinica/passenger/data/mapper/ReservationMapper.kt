package com.appmovilidadclinica.passenger.data.mapper

import com.appmovilidadclinica.passenger.data.local.ReservationEntity
import com.appmovilidadclinica.passenger.data.remote.dto.ReservationListItemDto
import com.appmovilidadclinica.passenger.data.remote.dto.ReservationResponseDto
import com.appmovilidadclinica.passenger.domain.model.Reservation
import com.appmovilidadclinica.passenger.domain.model.ReservationStatus
import com.appmovilidadclinica.passenger.domain.repository.ReservationTripContext
import java.time.Instant
import java.time.OffsetDateTime

fun ReservationResponseDto.toEntity(
    request: com.appmovilidadclinica.passenger.domain.model.ReservationRequest,
    context: ReservationTripContext,
    confirmedAt: Instant,
): ReservationEntity = ReservationEntity(
    reservationId = reservationId,
    reservationCode = reservationCode,
    qrToken = qrToken,
    tripId = request.tripId,
    tripSeatId = request.tripSeatId,
    originTripStopTimeId = request.originTripStopTimeId,
    destinationTripStopTimeId = request.destinationTripStopTimeId,
    status = status,
    confirmedAtEpochMillis = confirmedAt.toEpochMilli(),
    routeName = context.routeName,
    originName = context.originName,
    destinationName = context.destinationName,
    originDepartureAtEpochMillis = context.originDepartureAt.toEpochMilli(),
    seatLabel = context.seatLabel,
)

/**
 * DTO -> entity para reservas sincronizadas del backend. NO trae qr_token
 * (el server NUNCA lo expone despues del confirm inicial), asi que el
 * campo queda null salvo que la entidad local ya tuviera un qrToken
 * guardado y se pase via `preservedQrToken` (mecanismo de preservacion
 * de qrToken al re-sincronizar para no perder el unico registro del QR).
 */
fun ReservationListItemDto.toEntity(
    preservedQrToken: String? = null,
): ReservationEntity = ReservationEntity(
    reservationId = id,
    reservationCode = reservationCode,
    qrToken = preservedQrToken,
    tripId = tripId,
    tripSeatId = tripSeatId,
    originTripStopTimeId = originTripStopTimeId,
    destinationTripStopTimeId = destinationTripStopTimeId,
    status = status,
    confirmedAtEpochMillis = OffsetDateTime.parse(confirmedAt).toInstant().toEpochMilli(),
    routeName = tripCode,
    originName = originName,
    destinationName = destinationName,
    originDepartureAtEpochMillis = OffsetDateTime.parse(scheduledStartAt).toInstant().toEpochMilli(),
    seatLabel = seatLabel,
)

fun ReservationEntity.toDomain(): Reservation = Reservation(
    reservationId = reservationId,
    reservationCode = reservationCode,
    qrToken = qrToken,
    tripId = tripId,
    tripSeatId = tripSeatId,
    originTripStopTimeId = originTripStopTimeId,
    destinationTripStopTimeId = destinationTripStopTimeId,
    status = ReservationStatus.valueOf(status),
    confirmedAt = Instant.ofEpochMilli(confirmedAtEpochMillis),
    routeName = routeName,
    originName = originName,
    destinationName = destinationName,
    originDepartureAt = Instant.ofEpochMilli(originDepartureAtEpochMillis),
    seatLabel = seatLabel,
)
