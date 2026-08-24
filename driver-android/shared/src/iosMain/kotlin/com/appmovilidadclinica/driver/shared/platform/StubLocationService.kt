package com.appmovilidadclinica.driver.shared.platform

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * iOS stub del LocationService. La impl nativa (CoreLocation CLLocationManager)
 * llega en Fase 7.
 */
class StubLocationService : LocationService {
    private val logger = Logger.withTag("Location")

    override fun requestPermissions(): Flow<PermissionStatus> = flowOf(PermissionStatus.NEVER_ASK_AGAIN)

    override suspend fun currentLocation(): Coordinates? {
        logger.w { "LocationService stub: iOS no implementado" }
        return null
    }

    override fun observeLocation(): Flow<Coordinates> = flowOf()

    override fun isAvailable(): Boolean = false
}
