package com.appmovilidadclinica.passenger.shared.platform

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import platform.Foundation.NSUserDefaults

/**
 * iOS actual: `NSUserDefaults` (equivalente al `SharedPreferences` de Android).
 * El `SessionStore` comun no cambia: solo cambia de donde lee y escribe.
 */
actual fun createPlatformSettings(): Settings =
    NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
