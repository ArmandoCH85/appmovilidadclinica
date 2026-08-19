package com.appmovilidadclinica.driver.data.remote

import com.appmovilidadclinica.driver.shared.data.remote.dto.ReservationDto
import com.appmovilidadclinica.driver.shared.data.remote.dto.VerifyQrRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType

class KtorBookingApi(private val client: HttpClient) {
    suspend fun verifyQr(body: VerifyQrRequestDto): HttpResponse =
        client.post("reservations/verify-qr") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    suspend fun verifyQrParsed(body: VerifyQrRequestDto): ReservationDto =
        verifyQr(body).body()
}