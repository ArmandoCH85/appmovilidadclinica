package com.appmovilidadclinica.passenger.data.remote

import com.appmovilidadclinica.passenger.shared.data.remote.dto.SeatResultDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.TripDetailResponseDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.TripSearchResultDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse

class KtorTripsApi(private val client: HttpClient) {
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
    ): List<TripSearchResultDto> = search(date, direction, originStopId, destinationStopId).body()

    suspend fun getDetail(tripId: Long): HttpResponse =
        client.get("trips/$tripId")

    suspend fun getDetailParsed(tripId: Long): TripDetailResponseDto =
        getDetail(tripId).body()

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
    ): List<SeatResultDto> = listSeats(tripId, originTripStopTimeId, destinationTripStopTimeId).body()
}