package com.appmovilidadclinica.passenger.shared.domain.traccar

import com.appmovilidadclinica.passenger.shared.data.remote.traccar.TraccarApi
import com.appmovilidadclinica.passenger.shared.data.remote.traccar.createTraccarHttpClient
import com.appmovilidadclinica.passenger.shared.data.remote.traccar.TraccarCredentials
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
 * Mapeo Traccar → dominio. Usa MockEngine con el JSON real del servidor,
 * incluida la trampa de la placa: Traccar escribe "COC-965" (letra O) y la app
 * muestra "C0C-965" (cero). El vehículo se identifica por id, no por nombre.
 */
class TraccarVehiclesRepositoryTest {

    private val jsonHeaders = headersOf("Content-Type", ContentType.Application.Json.toString())

    private val devicesJson = """
        [
          {"id":147,"name":"CJI-579","uniqueId":"867144060257076","status":"online","positionId":33326723},
          {"id":142,"name":"COC-965","uniqueId":"867144060260534","status":"online","positionId":33326742},
          {"id":126,"name":"ALJ-627","uniqueId":"865190071329968","status":"online","positionId":33325966}
        ]
    """.trimIndent()

    private fun repository(
        positionsJson: String,
        devicesStatus: HttpStatusCode = HttpStatusCode.OK,
        positionsStatus: HttpStatusCode = HttpStatusCode.OK,
        now: Instant = Instant.parse("2026-10-12T22:00:00Z"),
    ): TraccarVehiclesRepository {
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/devices") -> respond(
                    content = devicesJson,
                    status = devicesStatus,
                    headers = jsonHeaders,
                )

                request.url.encodedPath.endsWith("/positions") -> respond(
                    content = positionsJson,
                    status = positionsStatus,
                    headers = jsonHeaders,
                )

                else -> respond(content = "[]", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }
        val client = createTraccarHttpClient(
            credentials = TraccarCredentials("usuario@prueba.com", "clave"),
            engine = engine,
        )
        return TraccarVehiclesRepository(api = TraccarApi(client), now = { now })
    }

    @Test
    fun mapeaLasDosPlacasPorIdYNoPorNombre() = runTest {
        val repo = repository(
            positionsJson = """
                [
                  {"id":2,"deviceId":142,"latitude":-12.1191,"longitude":-77.0281,"fixTime":"2026-10-12T21:58:00Z","serverTime":"2026-10-12T21:58:10Z","valid":true,"speed":21.6,"course":85.0},
                  {"id":1,"deviceId":147,"latitude":-12.0464,"longitude":-77.0428,"fixTime":"2026-10-12T21:57:00Z","serverTime":"2026-10-12T21:57:10Z","valid":true,"speed":0.0,"course":0.0}
                ]
            """.trimIndent(),
        )

        val result = repo.latestPositions()

        assertTrue(result is AppResult.Success)
        val vehicles = (result as AppResult.Success).data
        assertEquals(2, vehicles.size)

        val cji = vehicles.first { it.plate == "CJI-579" }
        assertEquals(147L, cji.deviceId)
        assertEquals(-12.0464, cji.latitude)
        assertEquals(-77.0428, cji.longitude)
        assertTrue(cji.hasPosition)
        assertFalse(cji.isStale)

        // La app usa C0C-965 (cero); Traccar lo llama COC-965 (letra O).
        val c0c = vehicles.first { it.plate == "C0C-965" }
        assertEquals(142L, c0c.deviceId)
        assertEquals("COC-965", c0c.deviceName)
        assertEquals(-12.1191, c0c.latitude)
    }

    @Test
    fun convierteVelocidadDeNudosAKmH() = runTest {
        val repo = repository(
            positionsJson = """
                [{"id":1,"deviceId":147,"latitude":-12.0,"longitude":-77.0,
                  "fixTime":"2026-10-12T21:59:00Z","valid":true,"speed":10.0,"course":90.0}]
            """.trimIndent(),
        )

        val vehicles = (repo.latestPositions() as AppResult.Success).data
        val cji = vehicles.first { it.plate == "CJI-579" }
        // 10 nudos = 18.52 km/h
        assertEquals(18.52, cji.speedKmh!!, 0.01)
        assertEquals(90.0, cji.courseDegrees)
    }

    @Test
    fun vehiculoSinPosicionNoInventaCoordenadas() = runTest {
        // CJI-579 no aparece en /positions.
        val repo = repository(
            positionsJson = """
                [{"id":2,"deviceId":142,"latitude":-12.1,"longitude":-77.0,
                  "fixTime":"2026-10-12T21:59:00Z","valid":true}]
            """.trimIndent(),
        )

        val vehicles = (repo.latestPositions() as AppResult.Success).data
        val cji = vehicles.first { it.plate == "CJI-579" }

        assertFalse(cji.hasPosition)
        assertNull(cji.latitude)
        assertNull(cji.longitude)
        assertTrue(cji.isStale)
        assertNull(cji.fixTime)
    }

    @Test
    fun posicionViejaSeMarcaComoUltimaUbicacionConocida() = runTest {
        // ahora = 22:00Z; el fix es de hace 2 horas.
        val repo = repository(
            positionsJson = """
                [{"id":1,"deviceId":147,"latitude":-12.0,"longitude":-77.0,
                  "fixTime":"2026-10-12T20:00:00Z","valid":true}]
            """.trimIndent(),
        )

        val cji = (repo.latestPositions() as AppResult.Success).data.first { it.plate == "CJI-579" }
        assertTrue(cji.isStale)
        assertTrue(cji.hasPosition) // hay coordenadas, pero son viejas
    }

    @Test
    fun posicionEnCeroCeroSeDescarta() = runTest {
        val repo = repository(
            positionsJson = """
                [{"id":1,"deviceId":147,"latitude":0.0,"longitude":0.0,
                  "fixTime":"2026-10-12T21:59:00Z","valid":true}]
            """.trimIndent(),
        )

        val cji = (repo.latestPositions() as AppResult.Success).data.first { it.plate == "CJI-579" }
        assertFalse(cji.hasPosition)
        assertNull(cji.latitude)
    }

    @Test
    fun coordenadasFueraDeRangoSeDescartan() = runTest {
        val repo = repository(
            positionsJson = """
                [{"id":1,"deviceId":147,"latitude":-999.0,"longitude":-77.0,
                  "fixTime":"2026-10-12T21:59:00Z","valid":true}]
            """.trimIndent(),
        )

        val cji = (repo.latestPositions() as AppResult.Success).data.first { it.plate == "CJI-579" }
        assertFalse(cji.hasPosition)
    }

    @Test
    fun credencialesInvalidasDevuelvenUnauthorized() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":"Unauthorized"}""",
                status = HttpStatusCode.Unauthorized,
                headers = jsonHeaders,
            )
        }
        val client = createTraccarHttpClient(
            credentials = TraccarCredentials("mal", "mal"),
            engine = engine,
        )
        val result = TraccarVehiclesRepository(TraccarApi(client)).latestPositions()

        assertTrue(result is AppResult.Failure)
        val error = (result as AppResult.Failure).error
        assertTrue(error is AppError.Unauthorized)
        assertTrue(error.message.contains("Credenciales"))
    }

    @Test
    fun elDispositivoOfflineSeReflejaEnElEstado() = runTest {
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/devices") -> respond(
                    content = """[{"id":147,"name":"CJI-579","status":"offline"}]""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )

                else -> respond(
                    content = """[{"id":1,"deviceId":147,"latitude":-12.0,"longitude":-77.0,
                        "fixTime":"2026-10-12T21:59:00Z","valid":true}]""".trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            }
        }
        val repo = TraccarVehiclesRepository(
            api = TraccarApi(createTraccarHttpClient(TraccarCredentials("u", "p"), engine)),
            now = { Instant.parse("2026-10-12T22:00:00Z") },
        )

        val cji = (repo.latestPositions() as AppResult.Success).data.first { it.plate == "CJI-579" }
        assertEquals(false, cji.online)
    }
}
