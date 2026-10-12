package com.appmovilidadclinica.passenger.shared.platform

import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

/**
 * Android actual: `SharedPreferences` (mismo archivo `session_prefs` que ya usa
 * la app, para que la sesion sea la misma). Necesita que el host haya
 * inicializado [SharedAndroidContext] en `Application.onCreate`.
 */
actual fun createPlatformSettings(): Settings =
    SharedPreferencesSettings(
        SharedAndroidContext.appContext.getSharedPreferences(
            SETTINGS_NAME,
            android.content.Context.MODE_PRIVATE,
        )
    )

private const val SETTINGS_NAME = "session_prefs"
