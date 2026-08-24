package com.appmovilidadclinica.driver

import android.app.Application
import com.appmovilidadclinica.driver.location.TripLocationService
import com.appmovilidadclinica.driver.shared.di.androidDataModule
import com.appmovilidadclinica.driver.shared.di.platformModule
import com.appmovilidadclinica.driver.shared.di.storageModule
import com.appmovilidadclinica.driver.shared.trip.TripLocationController
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class DriverApp : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@DriverApp)
            modules(
                platformModule,    // Settings + ScannerQr/Location/Notification (androidMain actual)
                storageModule,     // SessionStore
                androidDataModule, // Repos + Ktor (Android-only impls)
                appTripTrackingModule, // TripLocationController Android-only
            )
        }
    }
}

/**
 * Modulo Android-only: provee el TripLocationController que arranca
 * el Foreground Service cuando el trip pasa a IN_PROGRESS.
 */
private val appTripTrackingModule = module {
    single<TripLocationController> {
        object : TripLocationController {
            override fun startTracking() {
                TripLocationService.start(get())
            }
            override fun stopTracking() {
                TripLocationService.stop(get())
            }
        }
    }
}
