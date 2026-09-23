package com.appmovilidadclinica.driver.shared.testfakes

import com.appmovilidadclinica.driver.shared.domain.model.AppError
import com.appmovilidadclinica.driver.shared.domain.model.DriverTrip
import com.appmovilidadclinica.driver.shared.domain.model.Incident
import com.appmovilidadclinica.driver.shared.domain.model.Passenger
import com.appmovilidadclinica.driver.shared.domain.model.ReservationStatus
import com.appmovilidadclinica.driver.shared.domain.model.SeatAvailability
import com.appmovilidadclinica.driver.shared.domain.model.TripStatus
import com.appmovilidadclinica.driver.shared.domain.model.TripStop
import com.appmovilidadclinica.driver.shared.domain.model.TripStopStatus
import com.appmovilidadclinica.driver.shared.domain.repository.DriverRepository
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/** Instante fijo de referencia para los tests: 25/09/2026 06:00 AM en Lima. */
val FIXED_NOW: Instant = Instant.parse("2026-09-25T11:00:00Z")

/**
 * Repositorio falso que replica las reglas de los stored procedures del
 * backend (no solo la forma de los datos), para que los tests del "Detalle
 * del viaje" validen el flujo real:
 *
 *  - iniciar viaje: solo desde PUBLISHED/BOARDING.
 *  - llegada a parada: exige viaje iniciado y cierra automáticamente las
 *    reservas e invitados cuyo destino es esa parada (`sp_mark_trip_stop_arrival`).
 *  - salida de parada: exige llegada previa y no admite repetirse
 *    (`sp_mark_trip_stop_departure`).
 *  - abordaje: exige reserva CONFIRMED y llegada registrada en su parada de subida.
 *  - no show: exige CONFIRMED, llegada en la parada de subida y tolerancia vencida.
 *  - bajada: exige reserva BOARDED y llegada registrada en la parada de destino.
 *  - finalizar: exige viaje IN_PROGRESS; cierra las reservas que quedaron BOARDED.
 *  - `GET /driver/trips/{id}/passengers` solo devuelve CONFIRMED y BOARDED.
 */
class FakeDriverRepository(
    trip: DriverTrip,
    stops: List<TripStop>,
    passengers: List<Passenger> = emptyList(),
    /** Minutos de tolerancia para marcar NO_SHOW (backend: 5 por defecto). */
    var noShowToleranceMinutes: Int = 0,
) : DriverRepository {

    var trip: DriverTrip = trip
        private set

    private val stops: MutableList<TripStop> = stops.toMutableList()
    private val passengers: MutableList<Passenger> = passengers.toMutableList()

    /** Operaciones recibidas, en orden. Permite detectar llamadas duplicadas. */
    val recordedCalls: MutableList<String> = mutableListOf()

    /** Error forzado para la operación indicada (por nombre de método). */
    var forcedError: AppError? = null
    var forcedErrorOperation: String? = null

    /** Latencia artificial para probar el guard anti-duplicado. */
    var latencyMillis: Long = 0

    /** Minutos que se suman al reloj del backend en cada operación. */
    var clockOffsetMinutes: Long = 0

    private var backendNow: Instant = FIXED_NOW

    private suspend fun gate(operation: String): AppError? {
        recordedCalls += operation
        if (latencyMillis > 0) delay(latencyMillis)
        val forced = forcedError
        return if (forced != null && forcedErrorOperation == operation) forced else null
    }

    /** Avanza el reloj del backend (para probar la tolerancia de NO_SHOW). */
    fun advanceBackendClock(minutes: Long) {
        clockOffsetMinutes += minutes
    }

    override suspend fun getTrips(date: LocalDate): Result<List<DriverTrip>> {
        gate("getTrips")?.let { return Result.failure(it) }
        return Result.success(listOf(trip))
    }

    override suspend fun getTrip(tripId: Long): Result<DriverTrip> {
        gate("getTrip")?.let { return Result.failure(it) }
        return Result.success(trip)
    }

    override suspend fun getPassengers(tripId: Long): Result<List<Passenger>> {
        gate("getPassengers")?.let { return Result.failure(it) }
        return Result.success(
            passengers
                .filter { it.status == ReservationStatus.CONFIRMED || it.status == ReservationStatus.BOARDED }
                .sortedBy { it.originStopOrder },
        )
    }

    override suspend fun getTripStops(tripId: Long): Result<List<TripStop>> {
        gate("getTripStops")?.let { return Result.failure(it) }
        return Result.success(stops.sortedBy { it.stopOrder })
    }

    override suspend fun startTrip(tripId: Long): Result<Unit> {
        gate("startTrip")?.let { return Result.failure(it) }
        if (trip.status != TripStatus.PUBLISHED && trip.status != TripStatus.BOARDING) {
            return Result.failure(
                AppError.Conflict("el viaje esta en estado ${trip.status}, no se puede iniciar"),
            )
        }
        trip = trip.copy(status = TripStatus.IN_PROGRESS)
        return Result.success(Unit)
    }

    override suspend fun completeTrip(tripId: Long): Result<Unit> {
        gate("completeTrip")?.let { return Result.failure(it) }
        if (trip.status != TripStatus.IN_PROGRESS) {
            return Result.failure(AppError.Conflict("El viaje no esta en curso"))
        }
        trip = trip.copy(status = TripStatus.COMPLETED)
        passengers.replaceAll {
            if (it.status == ReservationStatus.BOARDED) it.copy(status = ReservationStatus.COMPLETED) else it
        }
        return Result.success(Unit)
    }

    override suspend fun markArrival(tripStopTimeId: Long): Result<Unit> {
        gate("markArrival")?.let { return Result.failure(it) }
        if (trip.status != TripStatus.IN_PROGRESS && trip.status != TripStatus.BOARDING) {
            return Result.failure(AppError.Conflict("El viaje debe estar iniciado para marcar la llegada"))
        }
        val index = stops.indexOfFirst { it.id == tripStopTimeId }
        if (index < 0) return Result.failure(AppError.NotFound("parada de viaje"))
        val stop = stops[index]
        val arrivalAt = backendNow.plusMinutes(clockOffsetMinutes)
        stops[index] = stop.copy(
            actualArrivalAt = stop.actualArrivalAt ?: arrivalAt,
            status = if (stop.status == TripStopStatus.PENDING) TripStopStatus.ARRIVED else stop.status,
        )
        // sp_mark_trip_stop_arrival: cierra reservas e invitados cuyo destino es esta parada.
        passengers.replaceAll {
            if (it.status == ReservationStatus.BOARDED && it.destinationStopOrder == stop.stopOrder) {
                it.copy(status = ReservationStatus.COMPLETED)
            } else {
                it
            }
        }
        return Result.success(Unit)
    }

    override suspend fun markDeparture(tripStopTimeId: Long): Result<Unit> {
        gate("markDeparture")?.let { return Result.failure(it) }
        val index = stops.indexOfFirst { it.id == tripStopTimeId }
        if (index < 0) return Result.failure(AppError.NotFound("parada de viaje"))
        val stop = stops[index]
        if (stop.status == TripStopStatus.PENDING) {
            return Result.failure(AppError.Conflict("Primero debe registrarse la llegada al paradero"))
        }
        if (stop.status == TripStopStatus.DEPARTED) {
            return Result.failure(AppError.Conflict("El paradero ya fue marcado como salido"))
        }
        stops[index] = stop.copy(
            actualDepartureAt = backendNow.plusMinutes(clockOffsetMinutes + 2),
            status = TripStopStatus.DEPARTED,
        )
        return Result.success(Unit)
    }

    override suspend fun markBoarded(reservationId: Long): Result<Unit> {
        gate("markBoarded")?.let { return Result.failure(it) }
        val index = passengers.indexOfFirst { it.reservationId == reservationId }
        if (index < 0) return Result.failure(AppError.NotFound("reserva"))
        val passenger = passengers[index]
        if (passenger.status != ReservationStatus.CONFIRMED) {
            return Result.failure(AppError.Conflict("La reserva no está CONFIRMED"))
        }
        val originStop = stops.firstOrNull { it.stopOrder == passenger.originStopOrder }
        if (originStop?.actualArrivalAt == null) {
            return Result.failure(AppError.Conflict("Primero debe registrarse la llegada física al punto de subida"))
        }
        passengers[index] = passenger.copy(
            status = ReservationStatus.BOARDED,
            boardedAt = backendNow.plusMinutes(clockOffsetMinutes + 3),
        )
        return Result.success(Unit)
    }

    override suspend fun markNoShow(reservationId: Long): Result<Unit> {
        gate("markNoShow")?.let { return Result.failure(it) }
        val index = passengers.indexOfFirst { it.reservationId == reservationId }
        if (index < 0) return Result.failure(AppError.NotFound("reserva"))
        val passenger = passengers[index]
        if (passenger.status != ReservationStatus.CONFIRMED) {
            return Result.failure(AppError.Conflict("Sólo una reserva CONFIRMED puede pasar a NO_SHOW"))
        }
        val originStop = stops.firstOrNull { it.stopOrder == passenger.originStopOrder }
        val arrivalAt = originStop?.actualArrivalAt
            ?: return Result.failure(AppError.Conflict("Debe registrarse la llegada física al punto de subida"))
        val now = backendNow.plusMinutes(clockOffsetMinutes)
        if (now < arrivalAt.plusMinutes(noShowToleranceMinutes.toLong())) {
            return Result.failure(AppError.Conflict("Aún no terminó el tiempo de tolerancia de NO_SHOW"))
        }
        passengers[index] = passenger.copy(status = ReservationStatus.NO_SHOW)
        return Result.success(Unit)
    }

    override suspend fun markAlighted(reservationId: Long): Result<Unit> {
        gate("markAlighted")?.let { return Result.failure(it) }
        val index = passengers.indexOfFirst { it.reservationId == reservationId }
        if (index < 0) return Result.failure(AppError.NotFound("reserva"))
        val passenger = passengers[index]
        if (passenger.status != ReservationStatus.BOARDED) {
            return Result.failure(AppError.Conflict("Sólo una reserva BOARDED puede finalizar"))
        }
        val destinationStop = stops.firstOrNull { it.stopOrder == passenger.destinationStopOrder }
        if (destinationStop?.actualArrivalAt == null) {
            return Result.failure(AppError.Conflict("Primero debe registrarse la llegada al destino"))
        }
        passengers[index] = passenger.copy(status = ReservationStatus.COMPLETED)
        return Result.success(Unit)
    }

    override suspend fun reportIncident(
        tripId: Long,
        type: String,
        description: String,
    ): Result<Incident> {
        gate("reportIncident")?.let { return Result.failure(it) }
        return Result.success(
            Incident(
                id = 1L,
                tripId = tripId,
                incidentType = com.appmovilidadclinica.driver.shared.domain.model.IncidentType.OTHER,
                description = description,
            ),
        )
    }

    override suspend fun getSeats(
        tripId: Long,
        originTripStopTimeId: Long,
        destinationTripStopTimeId: Long,
    ): Result<List<SeatAvailability>> {
        gate("getSeats")?.let { return Result.failure(it) }
        return Result.success(emptyList())
    }

    override suspend fun registerGuest(
        tripId: Long,
        tripSeatId: Long,
        originTripStopTimeId: Long,
        destinationTripStopTimeId: Long,
        firstName: String,
        lastName: String,
    ): Result<Long> {
        gate("registerGuest")?.let { return Result.failure(it) }
        return Result.success(99L)
    }

    /** Snapshot actual del manifiesto, para asserts de los tests. */
    fun passengerState(reservationId: Long): ReservationStatus? =
        passengers.firstOrNull { it.reservationId == reservationId }?.status

    fun stopState(stopOrder: Int): TripStopStatus? =
        stops.firstOrNull { it.stopOrder == stopOrder }?.status

    fun countCalls(operation: String): Int = recordedCalls.count { it == operation }
}

private fun Instant.plusMinutes(minutes: Long): Instant =
    kotlinx.datetime.Instant.fromEpochMilliseconds(toEpochMilliseconds() + minutes * 60_000)
