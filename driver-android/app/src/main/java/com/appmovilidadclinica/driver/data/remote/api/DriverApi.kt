package com.appmovilidadclinica.driver.data.remote.api

import com.appmovilidadclinica.driver.data.remote.dto.DriverTripDto
import com.appmovilidadclinica.driver.data.remote.dto.IncidentRequestDto
import com.appmovilidadclinica.driver.data.remote.dto.PassengerDto
import com.appmovilidadclinica.driver.data.remote.dto.TripStopDto
import retrofit2.Response
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

    // Response<Unit>, no un suspend fun de retorno no-nulo desnudo: estos
    // endpoints responden 204 sin cuerpo. Retrofit tira una excepcion interna
    // ("response body was null") si el tipo de retorno de una suspend fun es
    // no-nulo y el cuerpo es null — Response<Unit> evita ese problema porque
    // no intenta deserializar/desempaquetar el cuerpo. El error real (4xx/5xx)
    // se mapea a mano en el repositorio via ApiErrorMapper leyendo el
    // errorBody(), no se pierde.
    @POST("driver/trips/{id}/start")
    suspend fun startTrip(@Path("id") tripId: Long): Response<Unit>

    @POST("driver/trips/{id}/complete")
    suspend fun completeTrip(@Path("id") tripId: Long): Response<Unit>

    @POST("driver/trip-stops/{id}/arrival")
    suspend fun markArrival(@Path("id") tripStopTimeId: Long): Response<Unit>

    @POST("driver/reservations/{id}/board")
    suspend fun boardPassenger(@Path("id") reservationId: Long): Response<Unit>

    @POST("driver/reservations/{id}/no-show")
    suspend fun markNoShow(@Path("id") reservationId: Long): Response<Unit>

    @POST("driver/reservations/{id}/alight")
    suspend fun alightPassenger(@Path("id") reservationId: Long): Response<Unit>

    @POST("driver/trips/{id}/incidents")
    suspend fun reportIncident(
        @Path("id") tripId: Long,
        @Body body: IncidentRequestDto
    ): Map<String, Long>
}
