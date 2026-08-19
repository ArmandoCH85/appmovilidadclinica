package com.appmovilidadclinica.passenger.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response

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

internal class BearerTokenInterceptor(
    private val tokenProvider: KtorTokenProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokenProvider.currentToken() }
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}

object KtorClientFactory {
    fun create(
        baseUrl: String = NETWORK_BASE_URL,
        tokenProvider: KtorTokenProvider? = null,
        enableLogging: Boolean = true,
    ): HttpClient = HttpClient(OkHttp) {
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

        engine {
            if (tokenProvider != null) {
                addInterceptor(BearerTokenInterceptor(tokenProvider))
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