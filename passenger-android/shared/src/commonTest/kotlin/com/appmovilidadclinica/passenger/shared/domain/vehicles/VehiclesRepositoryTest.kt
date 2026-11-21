package com.appmovilidadclinica.passenger.shared.domain.vehicles

import com.appmovilidadclinica.passenger.data.remote.createHttpClient
import com.appmovilidadclinica.passenger.shared.data.remote.vehicles.VehiclesApi
import com.appmovilidadclinica.passenger.shared.domain.error.AppError
import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La app consume NUESTRO endpoint (`/api/vehicles/positions`), no Traccar:
 * las credenciales del GPS viven en el backend y el pasajero nunca las ve.
 */
class VehiclesRepositoryTest {

    private val jsonHeaders = headersOf("Content-Type", ContentType.Application.Json.toString())

    private fun repository(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): VehiclesRepository {
        val engine = MockEngine {
            respond(content = body, status = status, headers = jsonHeaders)
        }
        val client = createHttpClient(
            baseUrl = "https://test.local/api/",
            enableLogging = false,
            engine = engine,
        )
        return VehiclesRepository(VehiclesApi(client))
    }

    @Test
    fun mapeaLaPosicionDelBackend() = runTest {
        val repo = repository(
            """
            [
              {"plate":"C0C-965","latitude":-12.073196,"longitude":-77.034911,
               "fix_time":"2026-11-20T20:58:00Z","speed_kmh":29.6,"course":350.0,
               "online":true,"has_position":true,"is_stale":false}
            ]
            """.trimIndent(),
        )

        val result = repo.latestPositions()

        assertTrue(result is AppResult.Success)
        val vehicle = (result as AppResult.Success).data.single()
        assertEquals("C0C-965", vehicle.plate)
        assertEquals(-12.073196, vehicle.latitude)
        assertEquals(-77.034911, vehicle.longitude)
        assertEquals(29.6, vehicle.speedKmh)
        assertEquals(350.0, vehicle.courseDegrees)
        assertEquals(true, vehicle.online)
        assertTrue(vehicle.hasPosition)
        assertFalse(vehicle.isStale)
        assertEquals(Instant.parse("2026-11-20T20:58:00Z"), vehicle.fixTime)
    }

    @Test
    fun sinPosicionNoSeInventanCoordenadas() = runTest {
        val repo = repository(
            """
            [{"plate":"CJI-579","has_position":false,"is_stale":true,"online":false}]
            """.trimIndent(),
        )

        val vehicle = (repo.latestPositions() as AppResult.Success).data.single()
        assertFalse(vehicle.hasPosition)
        assertNull(vehicle.latitude)
        assertNull(vehicle.longitude)
        assertTrue(vehicle.isStale)
    }

    @Test
    fun sesionVencidaDevuelveUnauthorized() = runTest {
        val repo = repository(
            body = """{"error":{"code":401,"message":"token JWT requerido o invalido"}}""",
            status = HttpStatusCode.Unauthorized,
        )

        val result = repo.latestPositions()
        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Unauthorized)
    }

    @Test
    fun errorDelServidorNoRompeLaPantalla() = runTest {
        val repo = repository(
            body = """{"error":{"code":500,"message":"error interno"}}""",
            status = HttpStatusCode.InternalServerError,
        )

        val result = repo.latestPositions()
        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Unknown)
    }
}
