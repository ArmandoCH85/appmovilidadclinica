package com.appmovilidadclinica.driver.domain.repository

import com.appmovilidadclinica.driver.domain.model.Reservation

interface BookingRepository {
    suspend fun verifyQr(token: String): Result<Reservation>
}
