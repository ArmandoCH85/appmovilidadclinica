package com.appmovilidadclinica.driver.shared.di

import com.appmovilidadclinica.driver.shared.platform.AndroidLocationService
import com.appmovilidadclinica.driver.shared.platform.AndroidNotificationService
import com.appmovilidadclinica.driver.shared.platform.AndroidScannerQrService
import com.appmovilidadclinica.driver.shared.platform.LocationService
import com.appmovilidadclinica.driver.shared.platform.NotificationService
import com.appmovilidadclinica.driver.shared.platform.ScannerQrService
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<Settings> {
        SharedPreferencesSettings(
            androidContext().getSharedPreferences(
                "session_prefs",
                android.content.Context.MODE_PRIVATE,
            ),
        )
    }

    // Fase 4: servicios de plataforma
    single<ScannerQrService> { AndroidScannerQrService(androidContext()) }
    single<LocationService> { AndroidLocationService(androidContext()) }
    single<NotificationService> { AndroidNotificationService(androidContext()) }
}
