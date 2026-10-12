package com.appmovilidadclinica.passenger.shared.di

import com.appmovilidadclinica.passenger.data.remote.createHttpClient
import com.appmovilidadclinica.passenger.shared.data.local.SessionStore
import com.appmovilidadclinica.passenger.shared.data.local.StoredSession
import com.appmovilidadclinica.passenger.shared.data.mapper.toDomain
import com.appmovilidadclinica.passenger.shared.data.mapper.toStoredSession
import com.appmovilidadclinica.passenger.shared.data.remote.AuthApi
import com.appmovilidadclinica.passenger.shared.data.remote.ReservationsApi
import com.appmovilidadclinica.passenger.shared.data.remote.StopsApi
import com.appmovilidadclinica.passenger.shared.data.remote.TripsApi
import com.appmovilidadclinica.passenger.shared.data.remote.dto.ErrorResponseDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.LoginRequestDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.LoginResponseDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.ReservationRequestDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.ReservationResponseDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.SeatResultDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.StopDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.TripDetailResponseDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.TripSearchResultDto
import com.appmovilidadclinica.passenger.shared.domain.error.AppError
import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import com.appmovilidadclinica.passenger.shared.domain.model.ReservationCreated
import com.appmovilidadclinica.passenger.shared.domain.model.ReservationRequest
import com.appmovilidadclinica.passenger.shared.domain.model.Stop
import com.appmovilidadclinica.passenger.shared.domain.model.TripDetail
import com.appmovilidadclinica.passenger.shared.domain.model.TripDirection
import com.appmovilidadclinica.passenger.shared.domain.model.TripSearchResult
import com.appmovilidadclinica.passenger.shared.domain.model.TripSeat
import com.appmovilidadclinica.passenger.shared.platform.createPlatformSettings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate

/**
 * Grafo de la app compartida: sesion persistida, cliente HTTP multiplataforma
 * y las operaciones de cada flujo (login, busqueda de viajes, ...).
 *
 * Es lo unico que las plataformas necesitan para levantar la UI comun:
 * - iOS: [com.appmovilidadclinica.passenger.shared.MainViewController].
 * - Android (debug): la activity de preview.
 * - Android (app real): cuando cada pantalla se migre, consume estas mismas
 *   operaciones (hoy sigue con su Hilt sin cambios).
 */
class PassengerSharedGraph internal constructor(
    val sessionStore: SessionStore,
    val httpClient: HttpClient,
    private val authApi: AuthApi,
    private val stopsApi: StopsApi,
    private val tripsApi: TripsApi,
    private val reservationsApi: ReservationsApi,
) {

    // ------------------------------------------------------------------ login

    /**
     * Login contra `POST /auth/login`. En exito devuelve la sesion lista para
     * persistir; en error, un [AppError] ya mapeado (nunca lanza).
     */
    suspend fun signIn(documentNumber: String, password: String): AppResult<StoredSession> =
        request {
            val response = authApi.login(LoginRequestDto(documentNumber, password))
            if (response.status.isSuccess()) {
                val body: LoginResponseDto = response.body()
                AppResult.Success(body.toStoredSession())
            } else {
                AppResult.Failure(response.toAppError())
            }
        }

    // -------------------------------------------------------------- paradas

    /** Catalogo de paradas (`GET /stops`). */
    suspend fun loadStops(): AppResult<List<Stop>> = request {
        val response = stopsApi.list()
        if (response.status.isSuccess()) {
            AppResult.Success(response.body<List<StopDto>>().map { it.toDomain() })
        } else {
            AppResult.Failure(response.toAppError())
        }
    }

    // ---------------------------------------------------------- busqueda

    /** Viajes de una fecha y direccion (`GET /trips`). */
    suspend fun searchTrips(
        date: LocalDate,
        direction: TripDirection,
        originStopId: Long,
        destinationStopId: Long,
    ): AppResult<List<TripSearchResult>> = request {
        val response = tripsApi.search(
            date = date.toString(),
            direction = direction.name,
            originStopId = originStopId,
            destinationStopId = destinationStopId,
        )
        if (response.status.isSuccess()) {
            AppResult.Success(response.body<List<TripSearchResultDto>>().map { it.toDomain() })
        } else {
            AppResult.Failure(response.toAppError())
        }
    }

    // ------------------------------------------------------- seleccion

    /** Detalle del viaje con su cronograma (`GET /trips/{id}`). */
    suspend fun loadTripDetail(tripId: Long): AppResult<TripDetail> = request {
        val response = tripsApi.getDetail(tripId)
        if (response.status.isSuccess()) {
            AppResult.Success(response.body<TripDetailResponseDto>().toDomain())
        } else {
            AppResult.Failure(response.toAppError())
        }
    }

    /**
     * Asientos del tramo (`GET /trips/{id}/seats`). Aca origin/destination son
     * `trip_stop_time_id` (distinto de la busqueda de viajes).
     */
    suspend fun listSeats(
        tripId: Long,
        originTripStopTimeId: Long,
        destinationTripStopTimeId: Long,
    ): AppResult<List<TripSeat>> = request {
        val response = tripsApi.listSeats(tripId, originTripStopTimeId, destinationTripStopTimeId)
        if (response.status.isSuccess()) {
            AppResult.Success(response.body<List<SeatResultDto>>().map { it.toDomain() })
        } else {
            AppResult.Failure(response.toAppError())
        }
    }

    /** Confirma la reserva del asiento elegido (`POST /reservations`). */
    suspend fun confirmReservation(request: ReservationRequest): AppResult<ReservationCreated> = request {
        val response = reservationsApi.confirm(
            ReservationRequestDto(
                tripId = request.tripId,
                tripSeatId = request.tripSeatId,
                originTripStopTimeId = request.originTripStopTimeId,
                destinationTripStopTimeId = request.destinationTripStopTimeId,
            )
        )
        if (response.status.isSuccess()) {
            val body: ReservationResponseDto = response.body()
            AppResult.Success(
                ReservationCreated(
                    reservationId = body.reservationId,
                    reservationCode = body.reservationCode,
                    qrToken = body.qrToken,
                )
            )
        } else {
            AppResult.Failure(response.toAppError())
        }
    }

    fun close() {
        httpClient.close()
    }

    companion object {
        /**
         * Arma el grafo con el engine HTTP de la plataforma (OkHttp / Darwin).
         * @param engine permite inyectar MockEngine en los tests.
         */
        fun create(engine: HttpClientEngine? = null): PassengerSharedGraph {
            val sessionStore = SessionStore(createPlatformSettings())
            val client = createHttpClient(
                engine = engine,
                tokenProvider = { sessionStore.currentToken() },
            )
            return PassengerSharedGraph(
                sessionStore = sessionStore,
                httpClient = client,
                authApi = AuthApi(client),
                stopsApi = StopsApi(client),
                tripsApi = TripsApi(client),
                reservationsApi = ReservationsApi(client),
            )
        }
    }
}

/** Envuelve la llamada: los errores de red se convierten en [AppError.Network]. */
private inline fun <T> request(block: () -> AppResult<T>): AppResult<T> =
    try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        AppResult.Failure(AppError.Network(error.message ?: "Sin conexión a internet."))
    }

/** Traduce el error del backend (`{"error":{"code","message"}}`) a [AppError]. */
private suspend fun HttpResponse.toAppError(): AppError {
    val message = runCatching { body<ErrorResponseDto>().error.message }.getOrNull()
    return when (status.value) {
        401 -> AppError.Unauthorized(message ?: "Usuario o contraseña incorrectos.")
        403 -> AppError.Forbidden(message ?: "No tiene permiso para esta acción.")
        404 -> AppError.NotFound(message ?: "No se encontró el recurso.")
        409 -> AppError.Conflict(message ?: "La operación no se pudo completar.")
        422 -> AppError.Validation(null, message ?: "Datos inválidos.")
        else -> AppError.Unknown(message ?: "Error del servidor (${status.value}).")
    }
}
