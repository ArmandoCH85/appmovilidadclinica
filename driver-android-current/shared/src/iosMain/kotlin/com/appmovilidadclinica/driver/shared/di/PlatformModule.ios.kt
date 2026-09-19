package com.appmovilidadclinica.driver.shared.di

import com.appmovilidadclinica.driver.shared.platform.LocationService
import com.appmovilidadclinica.driver.shared.platform.NotificationService
import com.appmovilidadclinica.driver.shared.platform.ScannerQrService
import com.appmovilidadclinica.driver.shared.platform.StubLocationService
import com.appmovilidadclinica.driver.shared.platform.StubNotificationService
import com.appmovilidadclinica.driver.shared.platform.StubScannerQrService
import com.appmovilidadclinica.driver.shared.trip.TripLocationController
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    // Fase 4: servicios de plataforma (stubs en iOS hasta Fase 7).
    single<ScannerQrService> { StubScannerQrService() }
    single<LocationService> { StubLocationService() }
    single<NotificationService> { StubNotificationService() }

    // Tracking GPS en background — no-op en iOS hasta Fase 7.
    single<TripLocationController> {
        object : TripLocationController {
            override fun startTracking() { /* no-op */ }
            override fun stopTracking() { /* no-op */ }
        }
    }
}
