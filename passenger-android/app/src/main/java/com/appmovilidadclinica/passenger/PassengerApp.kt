package com.sitech.clinica.empleados

import android.app.Application
import com.appmovilidadclinica.passenger.shared.platform.SharedAndroidContext
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PassengerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // El DI (AppModule) vive en shared y es lazy; las actual functions
        // Android necesitan el Context para SharedPreferencesSettings y
        // AndroidSqliteDriver.
        SharedAndroidContext.appContext = this
    }
}