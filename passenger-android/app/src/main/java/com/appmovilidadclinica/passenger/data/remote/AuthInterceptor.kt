package com.appmovilidadclinica.passenger.data.remote

import com.appmovilidadclinica.passenger.data.local.SessionDataStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Chokepoint unico que agrega `Authorization: Bearer <token>` — ningun Api
 * arma ese header a mano (mismo principio que `admin/src/api/client.ts`,
 * "Ningun componente llama fetch() directo").
 *
 * `runBlocking` sobre una lectura de DataStore (IO local, rapida) es
 * aceptable en un interceptor OkHttp (que es sincrono por contrato) — no se
 * agrega un `Authenticator` custom aparte porque el caso de uso es simple
 * (leer un string), no justifica esa complejidad extra (ver ladder ponytail).
 *
 * Si detecta 401 en la respuesta, notifica el bus global — la UI raiz
 * decide que hacer (forzar logout), este interceptor no navega ni limpia
 * sesion (eso vive en `AuthRepositoryImpl`, capa mas arriba).
 */
class AuthInterceptor @Inject constructor(
    private val sessionDataStore: SessionDataStore,
    private val sessionExpiredNotifier: SessionExpiredNotifier,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { sessionDataStore.currentToken() }
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }

        val response = chain.proceed(request)
        if (response.code == 401) {
            sessionExpiredNotifier.notifySessionExpired()
        }
        return response
    }
}
