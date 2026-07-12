package com.appmovilidadclinica.passenger.data.remote

import com.appmovilidadclinica.passenger.data.remote.dto.StopDto
import retrofit2.Response
import retrofit2.http.GET

/**
 * CONTRATO NUEVO propuesto — `GET /api/stops`, publico para cualquier JWT
 * valido (no bajo /admin), solo lectura. No existe en el backend hoy (ver
 * diseño técnico #2) — el catalogo de paradas es chico (un puñado de
 * paraderos + sedes por cliente), asi que se propone sin paginacion, a
 * diferencia de `/admin/stops` que si pagina para el panel admin.
 */
interface StopsApi {
    @GET("stops")
    suspend fun list(): Response<List<StopDto>>
}
