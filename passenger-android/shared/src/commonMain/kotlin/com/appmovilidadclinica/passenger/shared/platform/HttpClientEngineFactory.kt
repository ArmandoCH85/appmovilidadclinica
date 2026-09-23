package com.appmovilidadclinica.passenger.shared.platform

import io.ktor.client.engine.HttpClientEngineFactory

/**
 * `expect/actual` para el engine HTTP multiplatform — Android usa OkHttp,
 * iOS (cuando llegue) usa Darwin. El cliente llama `httpClientEngineFactory()`
 * y obtiene el engine concreto de cada plataforma sin acoplarse.
 *
 * Ver `KtorClientFactory.kt` (commonMain) donde se invoca dentro de
 * `HttpClient(...)`.
 */
expect fun httpClientEngineFactory(): HttpClientEngineFactory<*>
