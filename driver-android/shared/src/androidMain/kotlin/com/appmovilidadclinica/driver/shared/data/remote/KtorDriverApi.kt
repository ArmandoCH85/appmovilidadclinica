package com.appmovilidadclinica.driver.shared.data.remote

import com.appmovilidadclinica.driver.shared.data.remote.dto.DriverTripDto
import com.appmovilidadclinica.driver.shared.data.remote.dto.GuestOccupantRequestDto
import com.appmovilidadclinica.driver.shared.data.remote.dto.IncidentRequestDto
import com.appmovilidadclinica.driver.shared.data.remote.dto.PassengerDto
import com.appmovilidadclinica.driver.shared.data.remote.dto.SeatAvailabilityDto
import com.appmovilidadclinica.driver.shared.data.remote.dto.TripStopDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType

class KtorDriverApi(private val client: HttpClient) {

    suspend fun getTrips(date: String): HttpResponse =
        client.get("driver/trips") {
            url { parameters.append("date", date) }
        }

    suspend fun getTripsParsed(date: String): List<DriverTripDto> = getTrips(date).body()

    suspend fun getPassengers(tripId: Long): HttpResponse =
        client.get("driver/trips/$tripId/passengers")

    suspend fun getPassengersParsed(tripId: Long): List<PassengerDto> = getPassengers(tripId).body()

    suspend fun getTripStops(tripId: Long): HttpResponse =
        client.get("driver/trips/$tripId/stops")
suspend fun getTripStopsParsed(tripId: Long): List<TripStopDto> =
        getTripStops(tripId).body()

    suspend fun getTrip(tripId: Long): HttpResponse =
        client.get("driver/trips/$tripId")

    suspend fun getTripParsed(tripId: Long): DriverTripDto =
        getTrip(tripId).body()

    suspend fun startTrip(tripId: Long): HttpResponse =
        client.post("driver/trips/$tripId/start")

    suspend fun completeTrip(tripId: Long): HttpResponse =
        client.post("driver/trips/$tripId/complete")

    suspend fun markArrival(tripStopTimeId: Long): HttpResponse =
        client.post("driver/trip-stops/$tripStopTimeId/arrival")

    suspend fun markDeparture(tripStopTimeId: Long): HttpResponse =
        client.post("driver/trip-stops/$tripStopTimeId/departure")

    suspend fun boardPassenger(reservationId: Long): HttpResponse =
        client.post("driver/reservations/$reservationId/board")

    suspend fun markNoShow(reservationId: Long): HttpResponse =
        client.post("driver/reservations/$reservationId/no-show")

    suspend fun alightPassenger(reservationId: Long): HttpResponse =
        client.post("driver/reservations/$reservationId/alight")

    suspend fun reportIncident(tripId: Long, body: IncidentRequestDto): HttpResponse =
        client.post("driver/trips/$tripId/incidents") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    suspend fun reportIncidentParsed(tripId: Long, body: IncidentRequestDto): Map<String, Long> =
        reportIncident(tripId, body).body()

    suspend fun getSeats(tripId: Long, originTripStopTimeId: Long, destinationTripStopTimeId: Long): HttpResponse =
        client.get("trips/$tripId/seats") {
            url {
                parameters.append("origin", originTripStopTimeId.toString())
                parameters.append("destination", destinationTripStopTimeId.toString())
            }
        }

    suspend fun getSeatsParsed(tripId: Long, originTripStopTimeId: Long, destinationTripStopTimeId: Long): List<SeatAvailabilityDto> =
        getSeats(tripId, originTripStopTimeId, destinationTripStopTimeId).body()

    suspend fun registerGuest(tripId: Long, body: GuestOccupantRequestDto): HttpResponse =
        client.post("driver/trips/$tripId/guest-occupants") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
}