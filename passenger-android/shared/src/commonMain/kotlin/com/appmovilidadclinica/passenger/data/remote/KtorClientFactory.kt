package com.appmovilidadclinica.passenger.data.remote

import com.appmovilidadclinica.passenger.shared.platform.httpClientEngineFactory
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.KtorDsl
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

const val NETWORK_BASE_URL: String = "https://movilidad.sitech.site/api/"

fun interface KtorTokenProvider {
    suspend fun currentToken(): String?
}

private val ktorToKermit: io.ktor.client.plugins.logging.Logger =
    object : io.ktor.client.plugins.logging.Logger {
        override fun log(message: String) {
            co.touchlab.kermit.Logger.i("KtorHttp", null) { message }
        }
    }

/**
 * Plugin custom que inyecta `Authorization: Bearer <jwt>` en CADA request,
 * releyendo del `tokenProvider` en cada llamada (sin cache). Esto es
 * necesario porque el plugin `Auth { bearer { loadTokens { ... } } }` de
 * Ktor cachea el resultado de `loadTokens` y no lo vuelve a invocar cuando
 * el token pasa de null a no-null despues del login, lo que provoca que
 * requests inmediatamente posteriores al login salgan sin Bearer y el
 * backend responda 401 "token JWT requerido o invalido".
 */
@KtorDsl
private fun bearerFromTokenProvider(provider: KtorTokenProvider) =
    createClientPlugin("BearerFromTokenProvider") {
        onRequest { request, _ ->
            val token = runBlocking { provider.currentToken() }
            if (!token.isNullOrBlank()) {
                request.headers.append(HttpHeaders.Authorization, "Bearer $token")
            }
        }
    }

/**
 * Factory del HttpClient multiplataforma (antes KtorClientFactory, OkHttp-only).
 * El engine (OkHttp/Darwin) llega via expect/actual; el token Bearer se inyecta
 * con un plugin custom que relee el SessionStore en cada request.
 */
fun createHttpClient(
    baseUrl: String = NETWORK_BASE_URL,
    tokenProvider: KtorTokenProvider? = null,
    enableLogging: Boolean = true,
): HttpClient = HttpClient(httpClientEngineFactory()) {
    expectSuccess = false

    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        })
    }

    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 15_000
        socketTimeoutMillis = 15_000
    }

    install(io.ktor.client.plugins.DefaultRequest) {
        url.takeFrom(baseUrl)
    }

    if (tokenProvider != null) {
        install(bearerFromTokenProvider(tokenProvider))
    }

    if (enableLogging) {
        install(Logging) {
            logger = ktorToKermit
            level = LogLevel.INFO
        }
    }
}