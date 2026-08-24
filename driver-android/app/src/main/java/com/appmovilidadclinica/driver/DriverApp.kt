package com.appmovilidadclinica.driver

import android.app.Application
import com.appmovilidadclinica.driver.di.AppModule
import com.appmovilidadclinica.driver.shared.data.local.SessionStore
import com.appmovilidadclinica.driver.shared.di.platformModule
import com.appmovilidadclinica.driver.shared.di.storageModule
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class DriverApp : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@DriverApp)
            modules(platformModule, storageModule)
        }

        val sessionStore: SessionStore by inject()
        AppModule.initialize(this, sessionStore)
    }
}
