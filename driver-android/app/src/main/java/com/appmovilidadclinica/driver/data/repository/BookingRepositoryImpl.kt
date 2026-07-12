package com.appmovilidadclinica.driver.data.repository

import com.appmovilidadclinica.driver.data.mapper.toDomain
import com.appmovilidadclinica.driver.data.remote.api.BookingApi
import com.appmovilidadclinica.driver.data.remote.dto.VerifyQrRequestDto
import com.appmovilidadclinica.driver.domain.model.AppError
import com.appmovilidadclinica.driver.domain.model.Reservation
import com.appmovilidadclinica.driver.domain.repository.BookingRepository
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookingRepositoryImpl @Inject constructor(
    private val bookingApi: BookingApi
) : BookingRepository {

    override suspend fun verifyQr(token: String): Result<Reservation> {
        return try {
            val reservation = bookingApi.verifyQr(VerifyQrRequestDto(token)).toDomain()
            Result.success(reservation)
        } catch (e: HttpException) {
            val error = when (e.code()) {
                404 -> AppError.NotFound("QR inválido")
                401 -> AppError.Unauthorized("No autorizado")
                else -> AppError.Unknown("Error del servidor")
            }
            Result.failure(error)
        } catch (e: IOException) {
            Result.failure(AppError.Network("Sin conexión a internet"))
        } catch (e: Exception) {
            Result.failure(AppError.Unknown(e.message ?: "Error desconocido"))
        }
    }
}
