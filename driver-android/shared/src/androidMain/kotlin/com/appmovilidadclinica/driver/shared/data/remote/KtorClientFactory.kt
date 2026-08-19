package com.appmovilidadclinica.driver.shared.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

const val DEFAULT_BASE_URL: String = "https://movilidad.sitech.site/api"

private val ktorToKermit: io.ktor.client.plugins.logging.Logger =
    object : io.ktor.client.plugins.logging.Logger {
        override fun log(message: String) {
            co.touchlab.kermit.Logger.i("KtorHttp", null) { message }
        }
    }

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