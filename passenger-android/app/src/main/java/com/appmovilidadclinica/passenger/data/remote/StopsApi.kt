package com.appmovilidadclinica.passenger.data.remote

import com.appmovilidadclinica.passenger.shared.data.remote.dto.StopDto
import retrofit2.Response
import retrofit2.http.GET

/**
 * CONTRATO NUEVO propuesto â€” `GET /api/stops`, publico para cualquier JWT
 * valido (no bajo /admin), solo lectura. No existe en el backend hoy (ver
 * diseÃ±o tÃ©cnico #2) â€” el catalogo de paradas es chico (un puÃ±ado de
 * paraderos + sedes por cliente), asi que se propone sin paginacion, a
 * diferencia de `/admin/stops` que si pagina para el panel admin.
 */
interface StopsApi {
    @GET("stops")
    suspend fun list(): Response<List<StopDto>>
}
