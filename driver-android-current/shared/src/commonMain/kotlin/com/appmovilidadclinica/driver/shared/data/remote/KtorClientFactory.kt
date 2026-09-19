package com.appmovilidadclinica.driver.shared.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.encodedPath
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

const val NETWORK_BASE_URL: String = "https://movilidad.sitech.site/api/"

/**
 * Proveedor del token JWT actual. Multiplatform: lo implementa la capa
 * Android-only (lee de SessionStore) o iOS (NSUserDefaults en Fase 7).
 */
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
 * Multiplatform. Usa [httpEngineFactory] (expect/actual OkHttp/Darwin)
 * y el plugin Auth de Ktor para inyectar Bearer token en cada request,
 * compatible con ambos backends.
 */
object KtorClientFactory {
    fun create(
        baseUrl: String = NETWORK_BASE_URL,
        tokenProvider: KtorTokenProvider? = null,
        enableLogging: Boolean = true,
    ): HttpClient = HttpClient(httpEngineFactory()) {
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
            install(Auth) {
                bearer {
                    loadTokens {
                        tokenProvider.currentToken()?.let { BearerTokens(it, "") }
                    }
                    sendWithoutRequest { request ->
                        // Solo mandar token en endpoints protegidos (no en /auth/login).
                        !request.url.encodedPath.contains("/auth/")
                    }
                }
            }
        }

        if (enableLogging) {
            install(Logging) {
                logger = ktorToKermit
                level = LogLevel.INFO
            }
        }
    }
}
