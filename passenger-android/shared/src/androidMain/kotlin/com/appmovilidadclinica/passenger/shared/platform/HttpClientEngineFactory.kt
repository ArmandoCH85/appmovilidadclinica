package com.appmovilidadclinica.passenger.shared.platform

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Android actual: engine OkHttp (unica dep Ktor en `androidMain`, ver
 * `shared/build.gradle.kts` linea 42). iOS en Fase 5 usara Darwin.
 */
actual fun httpClientEngineFactory(): HttpClientEngineFactory<*> = OkHttp
