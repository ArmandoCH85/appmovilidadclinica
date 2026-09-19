package com.appmovilidadclinica.driver.shared.data.remote

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

actual fun httpEngineFactory(): HttpClientEngineFactory<*> = OkHttp
