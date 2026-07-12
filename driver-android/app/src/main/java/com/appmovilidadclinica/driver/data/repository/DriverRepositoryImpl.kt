package com.appmovilidadclinica.driver.data.repository

import com.appmovilidadclinica.driver.data.mapper.toDomain
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
    private val driverApi: DriverApi
) : DriverRepository {

    override suspend fun getTrips(date: LocalDate): Result<List<DriverTrip>> {
        return try {
            val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val trips = driverApi.getTrips(dateStr).map { it.toDomain() }
            Result.success(trips)
        } catch (e: HttpException) {
            Result.failure(mapHttpException(e))
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
            Result.failure(mapHttpException(e))
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
            Result.failure(mapHttpException(e))
        } catch (e: IOException) {
            Result.failure(AppError.Network("Sin conexión a internet"))
        } catch (e: Exception) {
            Result.failure(AppError.Unknown(e.message ?: "Error desconocido"))
        }
    }

    override suspend fun markArrival(tripStopTimeId: Long): Result<Unit> {
        return try {
            val response = driverApi.markArrival(tripStopTimeId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(AppError.Unknown("Error al marcar llegada"))
            }
        } catch (e: HttpException) {
            Result.failure(mapHttpException(e))
        } catch (e: IOException) {
            Result.failure(AppError.Network("Sin conexión a internet"))
        } catch (e: Exception) {
            Result.failure(AppError.Unknown(e.message ?: "Error desconocido"))
        }
    }

    override suspend fun markBoarded(reservationId: Long): Result<Unit> {
        return try {
            val response = driverApi.boardPassenger(reservationId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(AppError.Unknown("Error al marcar abordaje"))
            }
        } catch (e: HttpException) {
            Result.failure(mapHttpException(e))
        } catch (e: IOException) {
            Result.failure(AppError.Network("Sin conexión a internet"))
        } catch (e: Exception) {
            Result.failure(AppError.Unknown(e.message ?: "Error desconocido"))
        }
    }

    override suspend fun markNoShow(reservationId: Long): Result<Unit> {
        return try {
            val response = driverApi.markNoShow(reservationId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(AppError.Unknown("Error al marcar no-show"))
            }
        } catch (e: HttpException) {
            Result.failure(mapHttpException(e))
        } catch (e: IOException) {
            Result.failure(AppError.Network("Sin conexión a internet"))
        } catch (e: Exception) {
            Result.failure(AppError.Unknown(e.message ?: "Error desconocido"))
        }
    }

    override suspend fun markAlighted(reservationId: Long): Result<Unit> {
        return try {
            val response = driverApi.alightPassenger(reservationId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(AppError.Unknown("Error al marcar bajada"))
            }
        } catch (e: HttpException) {
            Result.failure(mapHttpException(e))
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
            Result.failure(mapHttpException(e))
        } catch (e: IOException) {
            Result.failure(AppError.Network("Sin conexión a internet"))
        } catch (e: Exception) {
            Result.failure(AppError.Unknown(e.message ?: "Error desconocido"))
        }
    }

    private fun mapHttpException(e: HttpException): AppError {
        return when (e.code()) {
            401 -> AppError.Unauthorized("No autorizado")
            403 -> AppError.Forbidden("Acceso denegado")
            404 -> AppError.NotFound("No encontrado")
            409 -> AppError.Conflict("Conflicto")
            422 -> AppError.Validation(null, "Error de validación")
            else -> AppError.Unknown("Error del servidor")
        }
    }
}
