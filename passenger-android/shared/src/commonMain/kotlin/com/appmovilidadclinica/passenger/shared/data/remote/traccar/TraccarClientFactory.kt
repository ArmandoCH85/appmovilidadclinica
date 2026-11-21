package com.appmovilidadclinica.passenger.shared.data.remote.traccar

import com.appmovilidadclinica.passenger.shared.platform.httpClientEngineFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Base de la API de Traccar de MS Global GPS (con barra final). */
const val TRACCAR_BASE_URL: String = "https://gps.msglobalgps.com/api/"

/**
 * Credenciales de Traccar ingresadas por el usuario en la app.
 *
 * NO se hardcodean: se guardan en el almacenamiento privado de la app
 * (`TraccarCredentialsStore`) y nunca se registran en logs.
 */
data class TraccarCredentials(
    val user: String,
    val password: String,
) {
    val isComplete: Boolean get() = user.isNotBlank() && password.isNotBlank()
}

/**
 * Cliente HTTP para Traccar.
 *
 * Autenticación **Basic por request** (suficiente para lectura y sin manejar
 * cookies de sesión). El logging de Ktor queda **deshabilitado a propósito**:
 * así nunca se imprimen los headers de autorización.
 *
 * @param engine permite inyectar `MockEngine` en los tests.
 */
fun createTraccarHttpClient(
    credentials: TraccarCredentials,
    engine: HttpClientEngine? = null,
): HttpClient {
    val configure: io.ktor.client.HttpClientConfig<*>.() -> Unit = {
        expectSuccess = false

        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    explicitNulls = false
                }
            )
        }

        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 20_000
            socketTimeoutMillis = 20_000
        }

        install(io.ktor.client.plugins.DefaultRequest) {
            url.takeFrom(TRACCAR_BASE_URL)
            header(HttpHeaders.Authorization, basicAuthHeader(credentials))
        }
    }
    return if (engine != null) {
        HttpClient(engine, configure)
    } else {
        HttpClient(httpClientEngineFactory(), configure)
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun basicAuthHeader(credentials: TraccarCredentials): String =
    "Basic " + Base64.encode("${credentials.user}:${credentials.password}".encodeToByteArray())
