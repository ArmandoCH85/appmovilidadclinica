package com.appmovilidadclinica.driver.shared.data.remote

import io.ktor.client.engine.HttpClientEngineFactory

/**
 * Proveedor multiplatform del HttpClientEngine.
 * - androidMain: OkHttp engine.
 * - iosMain: Darwin engine.
 *
 * Mantenido como expect/actual function en lugar de la clase engine
 * concreta para que el commonMain solo vea la interfaz publica.
 */
expect fun httpEngineFactory(): HttpClientEngineFactory<*>
