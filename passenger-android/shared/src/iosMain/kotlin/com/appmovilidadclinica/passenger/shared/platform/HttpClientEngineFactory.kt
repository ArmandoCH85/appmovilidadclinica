package com.appmovilidadclinica.passenger.shared.platform

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

/**
 * iOS actual: engine Darwin (la dependencia vive en `iosMain`, ver
 * `shared/build.gradle.kts`). En Android el actual usa OkHttp.
 */
actual fun httpClientEngineFactory(): HttpClientEngineFactory<*> = Darwin
