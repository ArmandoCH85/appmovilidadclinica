package com.appmovilidadclinica.passenger.shared.platform

import com.russhwolf.settings.Settings

/**
 * Factory del almacenamiento clave-valor de cada plataforma:
 * - Android: `SharedPreferences` (ver `PlatformSettings.android.kt`)
 * - iOS: `NSUserDefaults` (ver `PlatformSettings.ios.kt`)
 *
 * Lo usa [com.appmovilidadclinica.passenger.shared.data.local.SessionStore],
 * que es comun a las dos apps.
 */
expect fun createPlatformSettings(): Settings
