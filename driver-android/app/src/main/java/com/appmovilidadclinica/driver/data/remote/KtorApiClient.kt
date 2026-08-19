package com.appmovilidadclinica.driver.data.remote

import io.ktor.client.HttpClient

class KtorApiClient(val client: HttpClient) {
    val authApi: KtorAuthApi = KtorAuthApi(client)
    val driverApi: KtorDriverApi = KtorDriverApi(client)
    val bookingApi: KtorBookingApi = KtorBookingApi(client)
}