package com.appmovilidadclinica.driver

import android.app.Application
import com.appmovilidadclinica.driver.shared.di.androidDataModule
import com.appmovilidadclinica.driver.shared.di.platformModule
import com.appmovilidadclinica.driver.shared.di.storageModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class DriverApp : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@DriverApp)
            modules(
                platformModule,    // Settings + ScannerQr/Location/Notification (androidMain actual)
                storageModule,     // SessionStore
                androidDataModule, // Repos + Ktor (Android-only impls)
            )
        }
    }
}
