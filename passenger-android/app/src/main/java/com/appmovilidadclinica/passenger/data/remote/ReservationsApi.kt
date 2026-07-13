package com.appmovilidadclinica.passenger.data.remote

import com.appmovilidadclinica.passenger.data.remote.dto.ReservationListItemDto
import com.appmovilidadclinica.passenger.data.remote.dto.ReservationRequestDto
import com.appmovilidadclinica.passenger.data.remote.dto.ReservationResponseDto
import com.appmovilidadclinica.passenger.data.remote.dto.SelfCheckinResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ReservationsApi {
    /**
     * Lista las reservas del WORKER autenticado. La app llama este endpoint
     * en "Mis reservas" para sincronizar la cache local con el backend:
     * sin sync, una reserva creada en otro dispositivo o sesion no aparece
     * porque Room es la unica fuente de verdad local. El backend NO envia
     * el qr_token (nunca lo devuelve despues del confirm inicial) — las
     * reservas sincronizadas vienen con `qrToken = null` y la UI lo indica.
     */
    @GET("reservations")
    suspend fun list(): Response<List<ReservationListItemDto>>

    @POST("reservations")
    suspend fun confirm(@Body body: ReservationRequestDto): Response<ReservationResponseDto>

    /** Sin body — 204 No Content. */
    @POST("reservations/{id}/cancel")
    suspend fun cancel(@Path("id") reservationId: Long): Response<Unit>

    /**
     * CONTRATO NUEVO propuesto, no existe en el backend actual — ver
     * diseño técnico. Contra el servidor de hoy, esta llamada devuelve 404.
     */
    @POST("reservations/{id}/self-checkin")
    suspend fun selfCheckin(@Path("id") reservationId: Long): Response<SelfCheckinResponseDto>
}
