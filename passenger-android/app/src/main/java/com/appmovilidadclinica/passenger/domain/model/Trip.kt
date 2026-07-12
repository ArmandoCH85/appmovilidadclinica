package com.appmovilidadclinica.passenger.domain.model

import java.time.Instant
import java.time.LocalDate

/** Direccion de una ruta — espejo de `transport_routes.direction`. */
enum class TripDirection { IDA, VUELTA }

/**
 * Estado de apertura de reserva de un viaje, calculado por `sp_search_trips`
 * comparando `CURRENT_TIMESTAMP` contra `booking_opens_at`/`booking_closes_at`.
 * La app NO recalcula esto localmente — confia en el valor que manda el
 * backend en cada busqueda (evita desincronizacion de reloj cliente/servidor).
 */
enum class BookingState { NOT_OPEN, OPEN, CLOSED }

/** Un resultado de `GET /api/trips` — espejo de domain de `TripSearchResult`. */
data class TripSearchResult(
    val tripId: Long,
    val tripCode: String,
    val routeCode: String,
    val routeName: String,
    val direction: TripDirection,
    val originName: String,
    val originDepartureAt: Instant,
    val destinationName: String,
    val destinationArrivalAt: Instant,
    val vehicleCode: String,
    val plate: String,
    val bookingOpensAt: Instant,
    val bookingClosesAt: Instant,
    val bookingState: BookingState,
    val availableSeats: Int,
)

/** Estado de una parada dentro del cronograma de un viaje concreto. */
enum class TripStopStatus { PENDING, ARRIVED, DEPARTED, SKIPPED }

/** Una parada del cronograma completo de `GET /api/trips/{id}`. */
data class TripStop(
    /** `trip_stop_time_id` — clave que despues se usa en /seats y /reservations. */
    val tripStopTimeId: Long,
    val stopId: Long,
    val stopOrder: Int,
    val stopName: String,
    val scheduledArrivalAt: Instant,
    val scheduledDepartureAt: Instant,
    val status: TripStopStatus,
)

enum class TripStatus { DRAFT, PUBLISHED, BOARDING, IN_PROGRESS, COMPLETED, CANCELLED }

/** Cabecera + cronograma de `GET /api/trips/{id}`. */
data class TripDetail(
    val tripId: Long,
    val tripCode: String,
    val routeId: Long,
    val serviceDate: LocalDate,
    val scheduledStartAt: Instant,
    val scheduledEndAt: Instant,
    val bookingOpensAt: Instant,
    val bookingClosesAt: Instant,
    val status: TripStatus,
    val stops: List<TripStop>,
)
