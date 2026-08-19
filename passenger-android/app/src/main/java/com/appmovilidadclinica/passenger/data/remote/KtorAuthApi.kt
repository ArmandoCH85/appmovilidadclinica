package com.appmovilidadclinica.passenger.data.remote

import com.appmovilidadclinica.passenger.shared.data.remote.dto.LoginRequestDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.LoginResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType

class KtorAuthApi(private val client: HttpClient) {
    suspend fun login(body: LoginRequestDto): HttpResponse =
        client.post("auth/login") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    suspend fun loginParsed(body: LoginRequestDto): LoginResponseDto =
        login(body).body()
}