package com.appmovilidadclinica.driver.data.remote.api

import com.appmovilidadclinica.driver.data.remote.dto.DriverTripDto
import com.appmovilidadclinica.driver.data.remote.dto.IncidentRequestDto
import com.appmovilidadclinica.driver.data.remote.dto.PassengerDto
import com.appmovilidadclinica.driver.data.remote.dto.TripStopDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface DriverApi {
    @GET("driver/trips")
    suspend fun getTrips(@Query("date") date: String): List<DriverTripDto>

    @GET("driver/trips/{id}/passengers")
    suspend fun getPassengers(@Path("id") tripId: Long): List<PassengerDto>

    @GET("driver/trips/{id}/stops")
    suspend fun getTripStops(@Path("id") tripId: Long): List<TripStopDto>

    // Sin envoltorio Response<Unit>: asi Retrofit lanza HttpException en codigos
    // no-2xx (204 en exito) y el repositorio puede mapear el error real del
    // backend via ApiErrorMapper en vez de perderlo.
    @POST("driver/trips/{id}/start")
    suspend fun startTrip(@Path("id") tripId: Long)

    @POST("driver/trips/{id}/complete")
    suspend fun completeTrip(@Path("id") tripId: Long)

    @POST("driver/trip-stops/{id}/arrival")
    suspend fun markArrival(@Path("id") tripStopTimeId: Long)

    @POST("driver/reservations/{id}/board")
    suspend fun boardPassenger(@Path("id") reservationId: Long)

    @POST("driver/reservations/{id}/no-show")
    suspend fun markNoShow(@Path("id") reservationId: Long)

    @POST("driver/reservations/{id}/alight")
    suspend fun alightPassenger(@Path("id") reservationId: Long)

    @POST("driver/trips/{id}/incidents")
    suspend fun reportIncident(
        @Path("id") tripId: Long,
        @Body body: IncidentRequestDto
    ): Map<String, Long>
}
