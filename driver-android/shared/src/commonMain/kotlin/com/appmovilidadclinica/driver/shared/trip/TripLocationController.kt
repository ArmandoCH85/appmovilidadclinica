package com.appmovilidadclinica.driver.shared.trip

/**
 * Controlador multiplatform del tracking GPS en background del viaje.
 *
 * En Android arranca [com.appmovilidadclinica.driver.location.TripLocationService]
 * cuando el trip pasa a IN_PROGRESS y lo para en COMPLETED. En iOS es
 * no-op hasta Fase 7.
 *
 * Se inyecta via Koin (binding en :shared/androidMain/.../di/).
 */
interface TripLocationController {
    fun startTracking()
    fun stopTracking()
}
