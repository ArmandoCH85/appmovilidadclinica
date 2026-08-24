package com.appmovilidadclinica.driver.shared.domain.repository

import com.appmovilidadclinica.driver.shared.domain.model.Reservation

interface BookingRepository {
    suspend fun verifyQr(token: String): Result<Reservation>
}
