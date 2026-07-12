package com.appmovilidadclinica.passenger.data.remote

import com.appmovilidadclinica.passenger.data.remote.dto.ReservationRequestDto
import com.appmovilidadclinica.passenger.data.remote.dto.ReservationResponseDto
import com.appmovilidadclinica.passenger.data.remote.dto.SelfCheckinResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface ReservationsApi {
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
