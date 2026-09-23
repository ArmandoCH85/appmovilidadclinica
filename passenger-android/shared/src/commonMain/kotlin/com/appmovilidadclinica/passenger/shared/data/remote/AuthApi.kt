package com.appmovilidadclinica.passenger.shared.data.remote

import com.appmovilidadclinica.passenger.shared.data.remote.dto.LoginRequestDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.LoginResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Reemplazo Ktor de la antigua `AuthApi` Retrofit.
 * La interfaz es la misma (login con documento + password), pero sin
 * anotaciones de Retrofit — se usa la API directa de Ktor cliente.
 *
 * El path es relativo al baseUrl del HttpClient (ver [KtorClientFactory]).
 */
class AuthApi(private val client: HttpClient) {

    /** Publica — no pasa por AuthInterceptor (no hay token todavia). */
    suspend fun login(body: LoginRequestDto): HttpResponse =
        client.post("auth/login") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    suspend fun loginParsed(body: LoginRequestDto): LoginResponseDto =
        login(body).body()
}