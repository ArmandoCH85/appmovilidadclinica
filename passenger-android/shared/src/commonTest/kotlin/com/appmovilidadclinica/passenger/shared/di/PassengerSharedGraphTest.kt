package com.appmovilidadclinica.passenger.shared.di

import com.appmovilidadclinica.passenger.data.remote.KtorTokenProvider
import com.appmovilidadclinica.passenger.data.remote.createHttpClient
import com.appmovilidadclinica.passenger.shared.data.local.SessionStore
import com.appmovilidadclinica.passenger.shared.data.remote.AuthApi
import com.appmovilidadclinica.passenger.shared.data.remote.StopsApi
import com.appmovilidadclinica.passenger.shared.data.remote.TripsApi
import com.appmovilidadclinica.passenger.shared.domain.error.AppError
import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import com.appmovilidadclinica.passenger.shared.domain.model.BookingState
import com.appmovilidadclinica.passenger.shared.domain.model.StopType
import com.appmovilidadclinica.passenger.shared.domain.model.TripDirection
import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Prueba el grafo compartido contra un servidor simulado (MockEngine):
 * valida el JSON real del backend, el mapeo a dominio y —lo mas importante
 * para iOS— el parseo multiplataforma de fechas con offset (`-05:00`).
 */
class PassengerSharedGraphTest {

    private val jsonHeaders = headersOf("Content-Type", ContentType.Application.Json.toString())

    private fun graphWith(handler: io.ktor.client.engine.mock.MockRequestHandler): PassengerSharedGraph {
        val settings = MapSettings()
        val store = SessionStore(settings)
        val client = createHttpClient(
            baseUrl = "https://test.local/api/",
            tokenProvider = KtorTokenProvider { store.currentToken() },
            enableLogging = false,
            engine = MockEngine(handler),
        )
        return PassengerSharedGraph(
            sessionStore = store,
            httpClient = client,
            authApi = AuthApi(client),
            stopsApi = StopsApi(client),
            tripsApi = TripsApi(client),
        )
    }

    @Test
    fun loginExitosoDevuelveLaSesionDelUsuario() = runTest {
        val graph = graphWith {
            respond(
                content = """
                    {
                      "token": "jwt-de-prueba",
                      "user": {
                        "id": 7,
                        "employee_code": "E-0007",
                        "document_number": "12345678",
                        "full_name": "Ana Quispe",
                        "role": "WORKER",
                        "department": "Enfermería",
                        "phone": "999111222",
                        "active": true
                      }
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        val result = graph.signIn("12345678", "secreta")

        assertTrue(result is AppResult.Success)
        val session = (result as AppResult.Success).data
        assertEquals("jwt-de-prueba", session.token)
        assertEquals(7L, session.userId)
        assertEquals("Ana Quispe", session.fullName)
    }

    @Test
    fun loginConCredencialesInvalidasDevuelveElMensajeDelBackend() = runTest {
        val graph = graphWith {
            respond(
                content = """{"error":{"code":401,"message":"Credenciales inválidas"}}""",
                status = HttpStatusCode.Unauthorized,
                headers = jsonHeaders,
            )
        }

        val result = graph.signIn("12345678", "mala")

        assertTrue(result is AppResult.Failure)
        val error = (result as AppResult.Failure).error
        assertTrue(error is AppError.Unauthorized)
        assertEquals("Credenciales inválidas", error.message)
    }

    @Test
    fun cargaDeParadasMapeaElCatalogo() = runTest {
        val graph = graphWith {
            respond(
                content = """
                    [
                      {"id":1,"code":"SEDE-LIMA","name":"Sede Lima","stop_type":"SEDE"},
                      {"id":2,"code":"PAR-01","name":"Paradero Surco","stop_type":"PARADERO"}
                    ]
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        val result = graph.loadStops()

        assertTrue(result is AppResult.Success)
        val stops = (result as AppResult.Success).data
        assertEquals(2, stops.size)
        assertEquals(StopType.SEDE, stops[0].stopType)
        assertEquals(StopType.PARADERO, stops[1].stopType)
    }

    @Test
    fun busquedaDeViajesParseaFechasConOffsetDeLima() = runTest {
        val graph = graphWith {
            respond(
                content = """
                    [
                      {
                        "trip_id": 55,
                        "trip_code": "T-55",
                        "route_code": "R-1",
                        "route_name": "Lima → Surco",
                        "direction": "IDA",
                        "origin_order": 1,
                        "origin_name": "Sede Lima",
                        "origin_departure_at": "2026-07-14T13:00:00-05:00",
                        "destination_order": 2,
                        "destination_name": "Paradero Surco",
                        "destination_arrival_at": "2026-07-14T14:00:00-05:00",
                        "vehicle_code": "V-1",
                        "plate": "CJI-579",
                        "booking_opens_at": "2026-07-13T13:00:00-05:00",
                        "booking_closes_at": "2026-07-14T12:30:00-05:00",
                        "booking_state": "OPEN",
                        "available_seats": 12
                      }
                    ]
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        val result = graph.searchTrips(
            date = LocalDate.parse("2026-07-14"),
            direction = TripDirection.IDA,
            originStopId = 1,
            destinationStopId = 2,
        )

        assertTrue(result is AppResult.Success)
        val trip = (result as AppResult.Success).data.single()
        assertEquals(55L, trip.tripId)
        assertEquals(BookingState.OPEN, trip.bookingState)
        assertEquals(12, trip.availableSeats)
        // 13:00 en Lima (UTC-5) == 18:00 UTC
        assertEquals(Instant.parse("2026-07-14T18:00:00Z"), trip.originDepartureAt)
    }

    @Test
    fun errorDelServidorEnLaBusquedaSeDevuelveComoFailure() = runTest {
        val graph = graphWith {
            respond(
                content = """{"error":{"code":500,"message":"Error interno"}}""",
                status = HttpStatusCode.InternalServerError,
                headers = jsonHeaders,
            )
        }

        val result = graph.searchTrips(
            date = LocalDate.parse("2026-07-14"),
            direction = TripDirection.IDA,
            originStopId = 1,
            destinationStopId = 2,
        )

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Unknown)
    }
}
