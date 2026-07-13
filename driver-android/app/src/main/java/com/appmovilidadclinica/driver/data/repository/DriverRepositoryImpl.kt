package com.appmovilidadclinica.driver.data.repository

import com.appmovilidadclinica.driver.data.mapper.toDomain
import com.appmovilidadclinica.driver.data.remote.ApiErrorMapper
import com.appmovilidadclinica.driver.data.remote.api.DriverApi
import com.appmovilidadclinica.driver.data.remote.dto.IncidentRequestDto
import com.appmovilidadclinica.driver.domain.model.AppError
import com.appmovilidadclinica.driver.domain.model.DriverTrip
import com.appmovilidadclinica.driver.domain.model.Incident
import com.appmovilidadclinica.driver.domain.model.IncidentType
import com.appmovilidadclinica.driver.domain.model.Passenger
import com.appmovilidadclinica.driver.domain.model.TripStop
import com.appmovilidadclinica.driver.domain.repository.DriverRepository
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriverRepositoryImpl @Inject constructor(
    private val driverApi: DriverApi,
    private val apiErrorMapper: ApiErrorMapper,
) : DriverRepository {

    override suspend fun getTrips(date: LocalDate): Result<List<DriverTrip>> {
        return try {
            val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val trips = driverApi.getTrips(dateStr).map { it.toDomain() }
            Result.success(trips)
        } catch (e: HttpException) {
            Result.failure(apiErrorMapper.map(e))
        } catch (e: IOException) {
            Result.failure(AppError.Network("Sin conexión a internet"))
        } catch (e: Exception) {
            Result.failure(AppError.Unknown(e.message ?: "Error desconocido"))
        }
    }

    override suspend fun getPassengers(tripId: Long): Result<List<Passenger>> {
        return try {
            val passengers = driverApi.getPassengers(tripId).map { it.toDomain() }
            Result.success(passengers)
        } catch (e: HttpException) {
            Result.failure(apiErrorMapper.map(e))
        } catch (e: IOException) {
            Result.failure(AppError.Network("Sin conexión a internet"))
        } catch (e: Exception) {
            Result.failure(AppError.Unknown(e.message ?: "Error desconocido"))
        }
    }

    override suspend fun getTripStops(tripId: Long): Result<List<TripStop>> {
        return try {
            val stops = driverApi.getTripStops(tripId).map { it.toDomain() }
            Result.success(stops)
        } catch (e: HttpException) {
            Result.failure(apiErrorMapper.map(e))
        } catch (e: IOException) {
            Result.failure(AppError.Network("Sin conexión a internet"))
        } catch (e: Exception) {
            Result.failure(AppError.Unknown(e.message ?: "Error desconocido"))
        }
    }

    override suspend fun startTrip(tripId: Long): Result<Unit> {
        return try {
            driverApi.startTrip(tripId)
            Result.success(Unit)
        } catch (e: HttpException) {
            Result.failure(apiErrorMapper.map(e))
        } catch (e: IOException) {
            Result.failure(AppError.Network("Sin conexión a internet"))
        } catch (e: Exception) {
            Result.failure(AppError.Unknown(e.message ?: "Error desconocido"))
        }
    }

    override suspend fun completeTrip(tripId: Long): Result<Unit> {
        return try {
            driverApi.completeTrip(tripId)
            Result.success(Unit)
        } catch (e: HttpException) {
            Result.failure(apiErrorMapper.map(e))
        } catch (e: IOException) {
            Result.failure(AppError.Network("Sin conexión a internet"))
        } catch (e: Exception) {
            Result.failure(AppError.Unknown(e.message ?: "Error desconocido"))
        }
    }

    override suspend fun markArrival(tripStopTimeId: Long): Result<Unit> {
        return try {
            driverApi.markArrival(tripStopTimeId)
            Result.success(Unit)
        } catch (e: HttpException) {
            Result.failure(apiErrorMapper.map(e))
        } catch (e: IOException) {
            Result.failure(AppError.Network("Sin conexión a internet"))
        } catch (e: Exception) {
            Result.failure(AppError.Unknown(e.message ?: "Error desconocido"))
        }
    }

    override suspend fun markBoarded(reservationId: Long): Result<Unit> {
        return try {
            driverApi.boardPassenger(reservationId)
            Result.success(Unit)
        } catch (e: HttpException) {
            Result.failure(apiErrorMapper.map(e))
        } catch (e: IOException) {
            Result.failure(AppError.Network("Sin conexión a internet"))
        } catch (e: Exception) {
            Result.failure(AppError.Unknown(e.message ?: "Error desconocido"))
        }
    }

    override suspend fun markNoShow(reservationId: Long): Result<Unit> {
        return try {
            driverApi.markNoShow(reservationId)
            Result.success(Unit)
        } catch (e: HttpException) {
            Result.failure(apiErrorMapper.map(e))
        } catch (e: IOException) {
            Result.failure(AppError.Network("Sin conexión a internet"))
        } catch (e: Exception) {
            Result.failure(AppError.Unknown(e.message ?: "Error desconocido"))
        }
    }

    override suspend fun markAlighted(reservationId: Long): Result<Unit> {
        return try {
            driverApi.alightPassenger(reservationId)
            Result.success(Unit)
        } catch (e: HttpException) {
            Result.failure(apiErrorMapper.map(e))
        } catch (e: IOException) {
            Result.failure(AppError.Network("Sin conexión a internet"))
        } catch (e: Exception) {
            Result.failure(AppError.Unknown(e.message ?: "Error desconocido"))
        }
    }

    override suspend fun reportIncident(
        tripId: Long,
        type: String,
        description: String
    ): Result<Incident> {
        return try {
            val response = driverApi.reportIncident(
                tripId,
                IncidentRequestDto(
                    incident_type = type,
                    description = description
                )
            )
            
            val incidentId = response["id"] ?: throw Exception("No incident ID returned")
            
            val incident = Incident(
                id = incidentId,
                tripId = tripId,
                incidentType = IncidentType.valueOf(type),
                description = description
            )
            
            Result.success(incident)
        } catch (e: HttpException) {
            Result.failure(apiErrorMapper.map(e))
        } catch (e: IOException) {
            Result.failure(AppError.Network("Sin conexión a internet"))
        } catch (e: Exception) {
            Result.failure(AppError.Unknown(e.message ?: "Error desconocido"))
        }
    }
}
