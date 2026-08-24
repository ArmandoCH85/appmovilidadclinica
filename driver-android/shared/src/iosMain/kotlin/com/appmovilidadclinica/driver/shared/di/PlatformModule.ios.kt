package com.appmovilidadclinica.driver.shared.di

import com.appmovilidadclinica.driver.shared.platform.StubLocationService
import com.appmovilidadclinica.driver.shared.platform.StubNotificationService
import com.appmovilidadclinica.driver.shared.platform.StubScannerQrService
import com.appmovilidadclinica.driver.shared.platform.LocationService
import com.appmovilidadclinica.driver.shared.platform.NotificationService
import com.appmovilidadclinica.driver.shared.platform.ScannerQrService
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    // iOS no provee Settings todavia (Fase 7); las screens que lo usen
    // daran error hasta que se implemente NSUserDefaultsSettings.
    single<ScannerQrService> { StubScannerQrService() }
    single<LocationService> { StubLocationService() }
    single<NotificationService> { StubNotificationService() }
}
