package com.appmovilidadclinica.passenger.data.remote

import io.ktor.client.HttpClient

/**
 * Container de las 4 APIs Ktor del pasajero. Vive en el app (no en
 * shared) para que Hilt/KSP pueda procesar la inyeccion sin drama.
 * Las 4 APIs vienen de shared/ y usan DTOs compartidos.
 */
class KtorApiClient(val client: HttpClient) {
    val authApi: KtorAuthApi = KtorAuthApi(client)
    val tripsApi: KtorTripsApi = KtorTripsApi(client)
    val stopsApi: KtorStopsApi = KtorStopsApi(client)
    val reservationsApi: KtorReservationsApi = KtorReservationsApi(client)
}