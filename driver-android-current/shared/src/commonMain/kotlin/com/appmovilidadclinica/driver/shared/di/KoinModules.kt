package com.appmovilidadclinica.driver.shared.di

import com.appmovilidadclinica.driver.shared.data.local.SessionStore
import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Storage multiplatform (Fase 2 — Reemplaza DataStore + wrapper Android).
 *
 * En androidMain se provee [Settings] via [platformModule] (SharedPreferencesSettings).
 * En iosMain se provera via [platformModule] (NSUserDefaultsSettings).
 */
val storageModule: Module = module {
    single { SessionStore(get()) }
}

/**
 * Modulo cargado en cada target. androidMain/iosMain proveen las
 * implementaciones platform-specific (Settings, ScannerQr, Location,
 * Notification, etc.) registradas con su interface como binding.
 */
expect val platformModule: Module
