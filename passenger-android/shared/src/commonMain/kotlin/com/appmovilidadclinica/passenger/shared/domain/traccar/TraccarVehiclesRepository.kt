package com.appmovilidadclinica.passenger.shared.domain.traccar

import com.appmovilidadclinica.passenger.shared.data.remote.traccar.TraccarApi
import com.appmovilidadclinica.passenger.shared.data.remote.traccar.TraccarDeviceDto
import com.appmovilidadclinica.passenger.shared.data.remote.traccar.TraccarPositionDto
import com.appmovilidadclinica.passenger.shared.domain.error.AppError
import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import com.appmovilidadclinica.passenger.shared.domain.model.VehicleLocation
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.minutes

/**
 * Placas que muestra la app → id de dispositivo en Traccar.
 *
 * **Verificado contra `GET /api/devices` (servidor real, oct-2026):**
 * - `CJI-579` → id **147** (name = "CJI-579")
 * - `C0C-965` → id **142** (name = "COC-965", con **letra O** en el servidor;
 *   la app y el backend usan `C0C-965` con **cero**: es el mismo vehículo)
 *
 * Se resuelve por **id** y no por nombre a propósito: el servidor no escribe
 * la placa igual que la app (O vs 0) y confundir vehículos sería grave.
 */
val TRACKED_VEHICLES: Map<String, Long> = linkedMapOf(
    "CJI-579" to 147L,
    "C0C-965" to 142L,
)

/** A partir de esta antigüedad la posición se considera "última conocida". */
private val STALE_AFTER = 5.minutes

/** Factor de conversión nudos → km/h (Traccar entrega `speed` en nudos). */
private const val KNOTS_TO_KMH = 1.852

/**
 * Lee las últimas posiciones de los dos vehículos desde Traccar.
 *
 * Solo lectura. No inventa datos: si un vehículo no tiene posición utilizable,
 * se devuelve con `latitude`/`longitude` en null.
 */
class TraccarVehiclesRepository(
    private val api: TraccarApi,
    private val now: () -> Instant = { Clock.System.now() },
    private val trackedVehicles: Map<String, Long> = TRACKED_VEHICLES,
) {

    suspend fun latestPositions(): AppResult<List<VehicleLocation>> {
        return try {
            val devicesResponse = api.devices()
            if (!devicesResponse.status.isSuccess()) {
                return AppResult.Failure(devicesResponse.toAppError())
            }
            val devices = devicesResponse.body<List<TraccarDeviceDto>>()

            val positionsResponse = api.positions()
            if (!positionsResponse.status.isSuccess()) {
                return AppResult.Failure(positionsResponse.toAppError())
            }
            val positions = positionsResponse.body<List<TraccarPositionDto>>()

            val devicesById = devices.associateBy { it.id }
            val current = now()

            AppResult.Success(
                trackedVehicles.map { (plate, deviceId) ->
                    val device = devicesById[deviceId]
                    val position = positions
                        .filter { it.deviceId == deviceId }
                        .maxByOrNull { it.fixTime ?: "" }

                    buildVehicleLocation(
                        plate = plate,
                        deviceId = deviceId,
                        device = device,
                        position = position,
                        now = current,
                    )
                }
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            AppResult.Failure(
                AppError.Network(error.message ?: "No se pudo conectar con el servidor GPS.")
            )
        }
    }

    private fun buildVehicleLocation(
        plate: String,
        deviceId: Long,
        device: TraccarDeviceDto?,
        position: TraccarPositionDto?,
        now: Instant,
    ): VehicleLocation {
        val online = device?.status?.let { it.equals("online", ignoreCase = true) }
        val fixTime = position?.fixTime?.let(::parseInstantOrNull)
        val serverTime = position?.serverTime?.let(::parseInstantOrNull)

        // Coordenadas solo si son válidas y no representan una posición vacía.
        val lat = position?.latitude?.takeIf { it.isFinite() && it in -90.0..90.0 }
        val lon = position?.longitude?.takeIf { it.isFinite() && it in -180.0..180.0 }
        val hasUsableCoordinates = lat != null && lon != null && !(lat == 0.0 && lon == 0.0)
        val validFix = position?.valid != false

        val stale = when {
            !hasUsableCoordinates || !validFix -> true
            fixTime == null -> true
            else -> now - fixTime > STALE_AFTER
        }

        return VehicleLocation(
            plate = plate,
            deviceId = deviceId,
            deviceName = device?.name,
            latitude = if (hasUsableCoordinates) lat else null,
            longitude = if (hasUsableCoordinates) lon else null,
            fixTime = fixTime,
            serverTime = serverTime,
            speedKmh = position?.speed?.takeIf { it.isFinite() }?.let { it * KNOTS_TO_KMH },
            courseDegrees = position?.course?.takeIf { it.isFinite() && it in 0.0..360.0 },
            online = online,
            isStale = stale,
        )
    }
}

private suspend fun HttpResponse.toAppError(): AppError {
    val message = runCatching {
        body<com.appmovilidadclinica.passenger.shared.data.remote.dto.ErrorResponseDto>().error.message
    }.getOrNull()
    return when (status.value) {
        401 -> AppError.Unauthorized(
            "Credenciales del servidor GPS inválidas o vencidas."
        )
        403 -> AppError.Forbidden(
            message ?: "La cuenta de GPS no tiene acceso a estos vehículos."
        )
        else -> AppError.Unknown(message ?: "El servidor GPS respondió ${status.value}.")
    }
}

/** Traccar entrega ISO-8601 en UTC; se acepta también el formato sin `Z`. */
private fun parseInstantOrNull(raw: String): Instant? {
    runCatching { Instant.parse(raw) }.getOrNull()?.let { return it }
    return runCatching {
        val normalizado = raw.replace(" ", "T").substringBefore(".")
        kotlinx.datetime.LocalDateTime.parse(normalizado).toInstant(TimeZone.UTC)
    }.getOrNull()
}
