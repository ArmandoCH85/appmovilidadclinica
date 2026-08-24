package com.appmovilidadclinica.driver.shared.data.repository

import com.appmovilidadclinica.driver.shared.data.mapper.toDomain
import com.appmovilidadclinica.driver.shared.data.remote.ApiErrorMapper
import com.appmovilidadclinica.driver.shared.data.remote.KtorApiClient
import com.appmovilidadclinica.driver.shared.data.remote.dto.VerifyQrRequestDto
import com.appmovilidadclinica.driver.shared.domain.model.AppError
import com.appmovilidadclinica.driver.shared.domain.model.Reservation
import com.appmovilidadclinica.driver.shared.domain.repository.BookingRepository
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookingRepositoryImpl @Inject constructor(
    private val apiClient: KtorApiClient,
    private val apiErrorMapper: ApiErrorMapper,
) : BookingRepository {

    override suspend fun verifyQr(token: String): Result<Reservation> {
        return try {
            val response = apiClient.bookingApi.verifyQr(VerifyQrRequestDto(token = token))
            if (response.status.value == 200) {
                val body = response.body<com.appmovilidadclinica.driver.shared.data.remote.dto.ReservationDto>()
                Result.success(body.toDomain())
            } else {
                Result.failure(apiErrorMapper.map(response))
            }
        } catch (e: IOException) {
            Result.failure(AppError.Network("Sin conexion a internet"))
        } catch (e: Exception) {
            Result.failure(AppError.Unknown(e.message ?: "Error desconocido"))
        }
    }
}
