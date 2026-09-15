package com.appmovilidadclinica.passenger.shared.data.remote

import com.appmovilidadclinica.passenger.shared.data.remote.dto.SeatResultDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.TripDetailResponseDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.TripSearchResultDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse

/**
 * Reemplazo Ktor de la antigua `TripsApi` Retrofit.
 */
class TripsApi(private val client: HttpClient) {

    /** origin/destination = transport_stops.id (ver reporte de exploracion #3 backend). */
    suspend fun search(
        date: String,
        direction: String,
        originStopId: Long,
        destinationStopId: Long,
    ): HttpResponse =
        client.get("trips") {
            url {
                parameters.append("date", date)
                parameters.append("origin", originStopId.toString())
                parameters.append("destination", destinationStopId.toString())
            }
        }

    suspend fun searchParsed(
        date: String,
        direction: String,
        originStopId: Long,
        destinationStopId: Long,
    ): List<TripSearchResultDto> =
        search(date, direction, originStopId, destinationStopId).body()

    suspend fun getDetail(tripId: Long): HttpResponse =
        client.get("trips/$tripId")

    suspend fun getDetailParsed(tripId: Long): TripDetailResponseDto =
        getDetail(tripId).body()

    /** origin/destination = trip_stop_time_id (distinto del /trips de arriba). */
    suspend fun listSeats(
        tripId: Long,
        originTripStopTimeId: Long,
        destinationTripStopTimeId: Long,
    ): HttpResponse =
        client.get("trips/$tripId/seats") {
            url {
                parameters.append("origin", originTripStopTimeId.toString())
                parameters.append("destination", destinationTripStopTimeId.toString())
            }
        }

    suspend fun listSeatsParsed(
        tripId: Long,
        originTripStopTimeId: Long,
        destinationTripStopTimeId: Long,
    ): List<SeatResultDto> =
        listSeats(tripId, originTripStopTimeId, destinationTripStopTimeId).body()
}