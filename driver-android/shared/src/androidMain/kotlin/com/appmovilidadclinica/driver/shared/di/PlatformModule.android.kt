package com.appmovilidadclinica.driver.shared.di

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
}
