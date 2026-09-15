package com.appmovilidadclinica.passenger.shared.platform

import android.content.Context

/**
 * Holder del [Context] Android para las `actual fun` en :shared/androidMain
 * que lo necesitan (SharedPreferencesSettings, AndroidSqliteDriver, etc.).
 *
 * Inicializado UNA vez desde `PassengerApp.onCreate` antes de cualquier
 * llamada al DI del :shared. NUNCA se guarda una referencia a un Activity
 * Context — solo el Application Context (memory-safe).
 *
 * Ver `PassengerApp.kt:12` donde se setea.
 */
object SharedAndroidContext {
    lateinit var appContext: Context
}
