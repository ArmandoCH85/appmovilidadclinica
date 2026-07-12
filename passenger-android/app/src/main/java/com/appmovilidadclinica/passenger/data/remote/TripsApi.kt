package com.appmovilidadclinica.passenger.data.remote

import com.appmovilidadclinica.passenger.data.remote.dto.SeatResultDto
import com.appmovilidadclinica.passenger.data.remote.dto.TripDetailResponseDto
import com.appmovilidadclinica.passenger.data.remote.dto.TripSearchResultDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TripsApi {
    /** origin/destination = transport_stops.id (ver reporte de exploracion #3). */
    @GET("trips")
    suspend fun search(
        @Query("date") date: String,
        @Query("direction") direction: String,
        @Query("origin") originStopId: Long,
        @Query("destination") destinationStopId: Long,
    ): Response<List<TripSearchResultDto>>

    @GET("trips/{id}")
    suspend fun getDetail(@Path("id") tripId: Long): Response<TripDetailResponseDto>

    /** origin/destination = trip_stop_time_id (distinto del /trips de arriba — ver reporte #3). */
    @GET("trips/{id}/seats")
    suspend fun listSeats(
        @Path("id") tripId: Long,
        @Query("origin") originTripStopTimeId: Long,
        @Query("destination") destinationTripStopTimeId: Long,
    ): Response<List<SeatResultDto>>
}
