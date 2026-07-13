package com.appmovilidadclinica.driver.presentation.common

import com.appmovilidadclinica.driver.domain.model.DriverTrip

/**
 * Cache en memoria del viaje seleccionado en el Dashboard, para evitar un
 * round-trip extra al abrir el detalle (el backend no expone GET /driver/trips/{id}).
 * Se pierde en muerte de proceso — aceptable para este MVP (sin estrategia offline).
 */
object SelectedTripHolder {
    var trip: DriverTrip? = null
}
