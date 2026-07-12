package com.appmovilidadclinica.passenger.data.remote

import com.appmovilidadclinica.passenger.data.remote.dto.LoginRequestDto
import com.appmovilidadclinica.passenger.data.remote.dto.LoginResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    /** Publica — no pasa por AuthInterceptor con token (no hay token todavia). */
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): Response<LoginResponseDto>
}
