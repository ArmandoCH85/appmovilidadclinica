package com.appmovilidadclinica.driver

import android.app.Application
import com.appmovilidadclinica.driver.di.AppModule

class DriverApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppModule.initialize(this)
    }
}
