package com.appmovilidadclinica.passenger.shared.data.remote

import com.appmovilidadclinica.passenger.shared.data.remote.dto.StopDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse

/**
 * Reemplazo Ktor de la antigua `StopsApi` Retrofit.
 *
 * CONTRATO NUEVO propuesto — `GET /api/stops`, publico para cualquier JWT
 * valido (no bajo /admin), solo lectura. Backstop del catalogo chico de
 * paradas (paraderos + sedes por cliente), sin paginacion, a diferencia
 * de `/admin/stops` que si pagina para el panel admin.
 */
class StopsApi(private val client: HttpClient) {

    suspend fun list(): HttpResponse =
        client.get("stops")

    suspend fun listParsed(): List<StopDto> =
        list().body()
}