package com.appmovilidadclinica.passenger.data.remote

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

class KtorReservationsApi(private val client: HttpClient) {
    suspend fun list(): HttpResponse = client.get("reservations")
    suspend fun listParsed(): List<ReservationListItemDto> = list().body()

    suspend fun confirm(body: ReservationRequestDto): HttpResponse =
        client.post("reservations") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    suspend fun confirmParsed(body: ReservationRequestDto): ReservationResponseDto =
        confirm(body).body()

    suspend fun cancel(reservationId: Long): HttpResponse =
        client.post("reservations/$reservationId/cancel")

    suspend fun selfCheckin(reservationId: Long): HttpResponse =
        client.post("reservations/$reservationId/self-checkin")

    suspend fun selfCheckinParsed(reservationId: Long): SelfCheckinResponseDto =
        selfCheckin(reservationId).body()
}