package com.appmovilidadclinica.passenger.data.mapper

import com.appmovilidadclinica.passenger.shared.data.remote.dto.ExtendResponseDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.ExtensionOfferDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.ExtensionStopDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.JourneyStateDto
import com.appmovilidadclinica.passenger.shared.data.remote.dto.JourneyStopDto
import com.appmovilidadclinica.passenger.shared.domain.model.ReservationStatus
import com.appmovilidadclinica.passenger.shared.domain.model.TripStatus
import com.appmovilidadclinica.passenger.shared.domain.model.TripStopStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyMapperTest {

    @Test
    fun `mapea journey con paradas y oferta de extension`() {
        val dto = JourneyStateDto(
            reservationId = 1,
            reservationStatus = "BOARDED",
            tripId = 5,
            tripStatus = "IN_PROGRESS",
            lastDepartedStopOrder = 3,
            destinationStopOrder = 4,
            stops = listOf(
                JourneyStopDto(
                    tripStopTimeId = 44,
                    stopId = 9,
                    stopName = "SEDE_A",
                    stopOrder = 5,
                    scheduledArrivalAt = "2026-09-15T08:30:00-05:00",
                    scheduledDepartureAt = "2026-09-15T08:35:00-05:00",
                    status = "PENDING",
                ),
            ),
            canExtend = true,
            extension = ExtensionOfferDto(
                currentSeatFree = true,
                remainingStops = listOf(ExtensionStopDto(44, "SEDE_A", 5)),
            ),
        )

        val state = dto.toDomain()

        assertEquals(ReservationStatus.BOARDED, state.reservationStatus)
        assertEquals(TripStatus.IN_PROGRESS, state.tripStatus)
        assertEquals(4, state.destinationStopOrder)
        assertTrue(state.canExtend)
        assertEquals(1, state.stops.size)
        assertEquals(TripStopStatus.PENDING, state.stops.first().status)
        assertTrue(state.extension?.currentSeatFree == true)
        assertEquals("SEDE_A", state.extension?.remainingStops?.first()?.stopName)
    }

    @Test
    fun `mapea respuesta de extend`() {
        val dto = ExtendResponseDto(
            reservationId = 1,
            destinationStopOrder = 6,
            tripSeatId = 33,
            seatLabel = "12",
            status = "BOARDED",
        )

        val result = dto.toDomain()

        assertEquals(1, result.reservationId)
        assertEquals(6, result.destinationStopOrder)
        assertEquals(33, result.tripSeatId)
        assertEquals(ReservationStatus.BOARDED, result.status)
    }
}
