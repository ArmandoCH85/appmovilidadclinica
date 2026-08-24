package com.appmovilidadclinica.driver.shared.data.repository

import com.appmovilidadclinica.driver.shared.data.mapper.toDomain
import com.appmovilidadclinica.driver.shared.data.remote.ApiErrorMapper
import com.appmovilidadclinica.driver.shared.data.remote.KtorApiClient
import com.appmovilidadclinica.driver.shared.data.remote.dto.IncidentRequestDto
import com.appmovilidadclinica.driver.shared.domain.model.AppError
import com.appmovilidadclinica.driver.shared.domain.model.DriverTrip
import com.appmovilidadclinica.driver.shared.domain.model.Incident
import com.appmovilidadclinica.driver.shared.domain.model.IncidentType
import com.appmovilidadclinica.driver.shared.domain.model.Passenger
import com.appmovilidadclinica.driver.shared.domain.model.TripStop
import com.appmovilidadclinica.driver.shared.domain.repository.DriverRepository
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import java.io.IOException
import kotlinx.datetime.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriverRepositoryImpl @Inject constructor(
    private val apiClient: KtorApiClient,
    private val apiErrorMapper: ApiErrorMapper,
) : DriverRepository {

    private suspend fun <T> safeCall(
        call: suspend () -> HttpResponse,
        parseBody: suspend (HttpResponse) -> T,
    ): Result<T> = try {
        val response = call()
        if (response.status.value in 200..299) {
            Result.success(parseBody(response))
        } else {
            Result.failure(apiErrorMapper.map(response))
        }
    } catch (e: IOException) {
        Result.failure(AppError.Network("Sin conexion a internet"))
    } catch (e: Exception) {
        Result.failure(AppError.Unknown(e.message ?: "Error desconocido"))
    }

    private suspend fun noBodyCall(call: suspend () -> HttpResponse): Result<Unit> = try {
        val response = call()
        if (response.status.value in 200..299) {
            Result.success(Unit)
        } else {
            Result.failure(apiErrorMapper.map(response))
        }
    } catch (e: IOException) {
        Result.failure(AppError.Network("Sin conexion a internet"))
    } catch (e: Exception) {
        Result.failure(AppError.Unknown(e.message ?: "Error desconocido"))
    }

    override suspend fun getTrips(date: LocalDate): Result<List<DriverTrip>> {
        val dateStr = date.toString()
        return safeCall(
            call = { apiClient.driverApi.getTrips(dateStr) },
            parseBody = { response -> response.body<List<com.appmovilidadclinica.driver.shared.data.remote.dto.DriverTripDto>>().map { it.toDomain() } },
        )
    }

    override suspend fun getPassengers(tripId: Long): Result<List<Passenger>> =
        safeCall(
            call = { apiClient.driverApi.getPassengers(tripId) },
            parseBody = { response -> response.body<List<com.appmovilidadclinica.driver.shared.data.remote.dto.PassengerDto>>().map { it.toDomain() } },
        )

    override suspend fun getTripStops(tripId: Long): Result<List<TripStop>> =
        safeCall(
            call = { apiClient.driverApi.getTripStops(tripId) },
            parseBody = { response -> response.body<List<com.appmovilidadclinica.driver.shared.data.remote.dto.TripStopDto>>().map { it.toDomain() } },
        )

    override suspend fun startTrip(tripId: Long): Result<Unit> =
        noBodyCall { apiClient.driverApi.startTrip(tripId) }

    override suspend fun completeTrip(tripId: Long): Result<Unit> =
        noBodyCall { apiClient.driverApi.completeTrip(tripId) }

    override suspend fun markArrival(tripStopTimeId: Long): Result<Unit> =
        noBodyCall { apiClient.driverApi.markArrival(tripStopTimeId) }

    override suspend fun markBoarded(reservationId: Long): Result<Unit> =
        noBodyCall { apiClient.driverApi.boardPassenger(reservationId) }

    override suspend fun markNoShow(reservationId: Long): Result<Unit> =
        noBodyCall { apiClient.driverApi.markNoShow(reservationId) }

    override suspend fun markAlighted(reservationId: Long): Result<Unit> =
        noBodyCall { apiClient.driverApi.alightPassenger(reservationId) }

    override suspend fun reportIncident(
        tripId: Long,
        type: String,
        description: String,
    ): Result<Incident> = safeCall(
        call = {
            apiClient.driverApi.reportIncident(
                tripId,
                IncidentRequestDto(
                    incident_type = type,
                    description = description,
                )
            )
        },
        parseBody = { response ->
            val body = response.body<Map<String, Long>>()
            val incidentId = body["id"] ?: throw Exception("No incident ID returned")
            Incident(
                id = incidentId,
                tripId = tripId,
                incidentType = IncidentType.valueOf(type),
                description = description,
            )
        },
    )
}