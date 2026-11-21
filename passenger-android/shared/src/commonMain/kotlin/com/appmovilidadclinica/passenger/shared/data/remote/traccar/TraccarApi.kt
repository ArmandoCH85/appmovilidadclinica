package com.appmovilidadclinica.passenger.shared.data.remote.traccar

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse

/**
 * API de lectura de Traccar (MS Global GPS).
 * Las rutas son relativas al baseUrl del cliente (`.../api/`).
 */
class TraccarApi(private val client: HttpClient) {

    suspend fun devices(): HttpResponse = client.get("devices")

    /**
     * Últimas posiciones conocidas de todos los dispositivos accesibles.
     * Se consulta sin parámetros y se filtra por `deviceId` (ver documento:
     * filtrar por `deviceId` en la URL exige `from`/`to`).
     */
    suspend fun positions(): HttpResponse = client.get("positions")
}
