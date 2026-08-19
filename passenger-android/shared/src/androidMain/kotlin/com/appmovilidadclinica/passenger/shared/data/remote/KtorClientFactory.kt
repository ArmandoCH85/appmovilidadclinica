package com.appmovilidadclinica.passenger.shared.data.remote

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Default base URL de la API. Centralizada aca para que el dia que se
 * arme build variants (dev/staging/prod) haya un solo lugar donde
 * cambiarla.
 */
const val DEFAULT_BASE_URL: String = "https://movilidad.sitech.site/api"

/**
 * Logger de Ktor que delega a Kermit (multiplatform). Asi el output
 * aparece en logcat en Android y en NSLog/os_log en iOS sin tocar este
 * codigo.
 */
private val ktorToKermit: io.ktor.client.plugins.logging.Logger =
    object : io.ktor.client.plugins.logging.Logger {
        override fun log(message: String) {
            Logger.i("KtorHttp", null) { message }
        }
    }

/**
 * Factory del cliente HTTP para la capa compartida.
 *
 * Fase 1: usa OkHttp engine (Android). En Fase 5, cuando se componga el
 * Xcode project para iOS, este factory se mueve a `actual`/`expect` en
 * commonMain/androidMain/iosMain para usar el engine nativo de cada
 * plataforma (Darwin en iOS).
 */
object KtorClientFactory {
    fun create(
        baseUrl: String = DEFAULT_BASE_URL,
        enableLogging: Boolean = true,
    ): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false

        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    explicitNulls = false
                },
            )
        }

        if (enableLogging) {
            install(Logging) {
                logger = ktorToKermit
                level = LogLevel.INFO
            }
        }
    }
}