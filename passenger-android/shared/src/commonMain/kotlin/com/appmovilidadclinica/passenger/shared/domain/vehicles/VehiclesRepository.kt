package com.appmovilidadclinica.passenger.shared.domain.vehicles

import com.appmovilidadclinica.passenger.shared.data.remote.vehicles.VehiclePositionDto
import com.appmovilidadclinica.passenger.shared.data.remote.vehicles.VehiclesApi
import com.appmovilidadclinica.passenger.shared.domain.error.AppError
import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import com.appmovilidadclinica.passenger.shared.domain.model.VehicleLocation
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Instant

/**
 * Posiciones de vehículos que expone NUESTRO backend.
 *
 * Antes el cliente hablaba directo con Traccar y necesitaba las credenciales
 * del GPS en el teléfono: eso obligaba al pasajero a tenerlas, cosa que no
 * debe pasar. Ahora el backend hace esa consulta y acá solo llegan los datos.
 */
class VehiclesRepository(private val api: VehiclesApi) {

    suspend fun latestPositions(): AppResult<List<VehicleLocation>> {
        return try {
            val response = api.positions()
            if (!response.status.isSuccess()) {
                return AppResult.Failure(response.toAppError())
            }
            val body: List<VehiclePositionDto> = response.body()
            AppResult.Success(body.map { it.toDomain() })
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            AppResult.Failure(
                AppError.Network(error.message ?: "No se pudo conectar con el servidor.")
            )
        }
    }
}

/** DTO del backend → modelo de dominio. */
fun VehiclePositionDto.toDomain(): VehicleLocation = VehicleLocation(
    plate = plate,
    deviceId = 0L, // el backend no expone ids internos del GPS
    deviceName = null,
    latitude = if (hasPosition) latitude else null,
    longitude = if (hasPosition) longitude else null,
    fixTime = fixTime?.let(::parseInstantOrNull),
    serverTime = serverTime?.let(::parseInstantOrNull),
    speedKmh = speedKmh,
    courseDegrees = course,
    online = online,
    isStale = isStale,
)

private fun parseInstantOrNull(raw: String): Instant? =
    runCatching { Instant.parse(raw) }.getOrNull()

private suspend fun HttpResponse.toAppError(): AppError {
    val message = runCatching {
        body<com.appmovilidadclinica.passenger.shared.data.remote.dto.ErrorResponseDto>().error.message
    }.getOrNull()
    return when (status.value) {
        401 -> AppError.Unauthorized(message ?: "Sesión expirada. Inicie sesión nuevamente.")
        403 -> AppError.Forbidden(message ?: "No tiene permiso para ver las posiciones.")
        else -> AppError.Unknown(message ?: "No se pudo obtener la ubicación (${status.value}).")
    }
}
