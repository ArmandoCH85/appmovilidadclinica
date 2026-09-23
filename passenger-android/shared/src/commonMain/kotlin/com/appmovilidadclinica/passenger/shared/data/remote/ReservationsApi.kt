package com.appmovilidadclinica.passenger.shared.data.remote

import com.appmovilidadclinica.passenger.shared.data.remote.dto.ReservationListItemDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.ReservationRequestDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.ReservationResponseDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.SelfCheckinResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Reemplazo Ktor de la antigua `ReservationsApi` Retrofit.
 */
class ReservationsApi(private val client: HttpClient) {

    /**
     * Lista las reservas del WORKER autenticado. La app llama este endpoint
     * en "Mis reservas" para sincronizar la cache local con el backend:
     * sin sync, una reserva creada en otro dispositivo o sesion no aparece
     * porque Room es la unica fuente de verdad local. El backend NO envia
     * el qr_token (nunca lo devuelve despues del confirm inicial) — las
     * reservas sincronizadas vienen con `qrToken = null` y la UI lo indica.
     */
    suspend fun list(): HttpResponse =
        client.get("reservations")

    suspend fun listParsed(): List<ReservationListItemDto> =
        list().body()

    suspend fun confirm(body: ReservationRequestDto): HttpResponse =
        client.post("reservations") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    suspend fun confirmParsed(body: ReservationRequestDto): ReservationResponseDto =
        confirm(body).body()

    /** Sin body — 204 No Content. */
    suspend fun cancel(reservationId: Long): HttpResponse =
        client.post("reservations/$reservationId/cancel")

    /**
     * CONTRATO NUEVO propuesto, no existe en el backend actual — ver
     * diseño tecnico. Contra el servidor de hoy, esta llamada devuelve 404.
     */
    suspend fun selfCheckin(reservationId: Long): HttpResponse =
        client.post("reservations/$reservationId/self-checkin")

    suspend fun selfCheckinParsed(reservationId: Long): SelfCheckinResponseDto =
        selfCheckin(reservationId).body()
}