package com.appmovilidadclinica.passenger.data.remote

import com.appmovilidadclinica.passenger.shared.data.remote.dto.StopDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse

class KtorStopsApi(private val client: HttpClient) {
    suspend fun list(): HttpResponse = client.get("stops")
    suspend fun listParsed(): List<StopDto> = list().body()
}