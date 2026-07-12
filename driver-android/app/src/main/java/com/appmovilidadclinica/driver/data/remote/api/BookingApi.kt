package com.appmovilidadclinica.driver.data.remote.api

import com.appmovilidadclinica.driver.data.remote.dto.ReservationDto
import com.appmovilidadclinica.driver.data.remote.dto.VerifyQrRequestDto
import retrofit2.http.Body
import retrofit2.http.POST

interface BookingApi {
    @POST("reservations/verify-qr")
    suspend fun verifyQr(@Body body: VerifyQrRequestDto): ReservationDto
}
