package com.appmovilidadclinica.passenger.shared.data.remote.vehicles

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse

/**
 * API de posiciones de vehículos de NUESTRO backend.
 * Autenticación: el JWT del pasajero lo inyecta el cliente HTTP compartido.
 */
class VehiclesApi(private val client: HttpClient) {

    /** `GET /vehicles/positions` — posiciones de los vehículos de la flota. */
    suspend fun positions(): HttpResponse = client.get("vehicles/positions")
}
