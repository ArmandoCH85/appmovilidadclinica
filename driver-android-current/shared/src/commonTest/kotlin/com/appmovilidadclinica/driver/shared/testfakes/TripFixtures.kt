package com.appmovilidadclinica.driver.shared.testfakes

import com.appmovilidadclinica.driver.shared.domain.model.Direction
import com.appmovilidadclinica.driver.shared.domain.model.DriverTrip
import com.appmovilidadclinica.driver.shared.domain.model.Passenger
import com.appmovilidadclinica.driver.shared.domain.model.ReservationStatus
import com.appmovilidadclinica.driver.shared.domain.model.TripStatus
import com.appmovilidadclinica.driver.shared.domain.model.TripStop
import com.appmovilidadclinica.driver.shared.domain.model.TripStopStatus
import kotlinx.datetime.Instant

/**
 * Fixtures del "Detalle del viaje". Los instantes estan en UTC y se muestran
 * en America/Lima (UTC-5): 11:05Z = 06:05 AM.
 */
object TripFixtures {

    const val TRIP_ID = 55L

    fun trip(
        status: TripStatus = TripStatus.PUBLISHED,
        scheduledStartAt: Instant = Instant.parse("2026-09-25T11:05:00Z"),
        scheduledEndAt: Instant = Instant.parse("2026-09-25T12:20:00Z"),
    ): DriverTrip = DriverTrip(
        id = TRIP_ID,
        tripCode = "T-BUS25-20260925",
        routeName = "BUS-25 · Mañana · IDA",
        direction = Direction.IDA,
        scheduledStartAt = scheduledStartAt,
        scheduledEndAt = scheduledEndAt,
        vehicleCode = "BUS-25",
        plate = "ABC-123",
        seatCapacity = 20,
        status = status,
    )

    /**
     * Tres paradas:
     *  1 Sede Surco (11:05Z / 06:05 AM)
     *  2 Paradero Cultura (11:30Z / 06:30 AM)
     *  3 Sede Lima (11:55Z / 06:55 AM)
     */
    fun stops(
        firstStatus: TripStopStatus = TripStopStatus.PENDING,
        secondStatus: TripStopStatus = TripStopStatus.PENDING,
        thirdStatus: TripStopStatus = TripStopStatus.PENDING,
        firstArrival: Instant? = null,
        firstDeparture: Instant? = null,
    ): List<TripStop> = listOf(
        TripStop(
            id = 101L,
            stopName = "Sede Surco",
            stopOrder = 1,
            scheduledArrivalAt = Instant.parse("2026-09-25T11:05:00Z"),
            scheduledDepartureAt = Instant.parse("2026-09-25T11:10:00Z"),
            actualArrivalAt = firstArrival,
            actualDepartureAt = firstDeparture,
            status = firstStatus,
        ),
        TripStop(
            id = 102L,
            stopName = "Paradero Cultura",
            stopOrder = 2,
            scheduledArrivalAt = Instant.parse("2026-09-25T11:30:00Z"),
            scheduledDepartureAt = Instant.parse("2026-09-25T11:32:00Z"),
            actualArrivalAt = null,
            actualDepartureAt = null,
            status = secondStatus,
        ),
        TripStop(
            id = 103L,
            stopName = "Sede Lima",
            stopOrder = 3,
            scheduledArrivalAt = Instant.parse("2026-09-25T11:55:00Z"),
            scheduledDepartureAt = Instant.parse("2026-09-25T11:57:00Z"),
            actualArrivalAt = null,
            actualDepartureAt = null,
            status = thirdStatus,
        ),
    )

    fun passenger(
        reservationId: Long,
        name: String = "Pasajero $reservationId",
        seatLabel: String = "3A",
        originOrder: Int = 1,
        originName: String = "Sede Surco",
        destinationOrder: Int = 3,
        destinationName: String = "Sede Lima",
        status: ReservationStatus = ReservationStatus.CONFIRMED,
        boardedAt: Instant? = null,
    ): Passenger = Passenger(
        reservationId = reservationId,
        reservationCode = "R-$reservationId",
        workerId = 900L + reservationId,
        workerFullName = name,
        seatNumber = reservationId.toInt(),
        seatLabel = seatLabel,
        originStopOrder = originOrder,
        originStopName = originName,
        destinationStopOrder = destinationOrder,
        destinationStopName = destinationName,
        status = status,
        confirmedAt = Instant.parse("2026-09-24T15:00:00Z"),
        boardedAt = boardedAt,
    )

    fun guest(
        name: String = "Invitado Uno",
        seatLabel: String = "9C",
        originOrder: Int = 2,
        destinationOrder: Int = 3,
    ): Passenger = Passenger(
        reservationId = 0L,
        reservationCode = "",
        workerId = 0L,
        workerFullName = name,
        seatNumber = 9,
        seatLabel = seatLabel,
        originStopOrder = originOrder,
        originStopName = "Paradero Cultura",
        destinationStopOrder = destinationOrder,
        destinationStopName = "Sede Lima",
        status = ReservationStatus.BOARDED,
        confirmedAt = Instant.parse("2026-09-25T11:00:00Z"),
        boardedAt = Instant.parse("2026-09-25T11:00:00Z"),
        isGuest = true,
        guestDisplayName = name,
    )
}
