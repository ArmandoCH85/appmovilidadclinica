package com.appmovilidadclinica.passenger.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Fuente de verdad LOCAL de las reservas del usuario — en particular del
 * `qrToken`, que el backend nunca vuelve a entregar en claro (ver dominio
 * `Reservation`, y diseño técnico). Si esta fila se pierde, el QR no se
 * puede regenerar contra el servidor.
 *
 * `qrToken` es nullable: las reservas sincronizadas desde el backend
 * (endpoint `GET /api/reservations`) vienen sin qrToken porque el server
 * NUNCA lo devuelve despues del confirm inicial. La UI distingue el caso
 * (mostrando "QR no disponible" en la pantalla de detalle) para que el
 * usuario sepa que tiene que reconfirmar si quiere ver el QR.
 */
@Entity(tableName = "reservations")
data class ReservationEntity(
    @PrimaryKey val reservationId: Long,
    val reservationCode: String,
    val qrToken: String?,
    val tripId: Long,
    val tripSeatId: Long,
    val originTripStopTimeId: Long,
    val destinationTripStopTimeId: Long,
    val status: String,
    val confirmedAtEpochMillis: Long,
    val routeName: String,
    val originName: String,
    val destinationName: String,
    val originDepartureAtEpochMillis: Long,
    val seatLabel: String,
    val vehicleCode: String = "",
    val plate: String = "",
)
