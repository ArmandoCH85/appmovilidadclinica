package com.appmovilidadclinica.passenger.data.local

import android.content.Context
import com.appmovilidadclinica.passenger.shared.data.remote.traccar.TraccarCredentials
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Guarda las credenciales del GPS (Traccar) que configura el usuario.
 *
 * **Nunca** se embeben en el código, en `BuildConfig`, en el repo ni en logs:
 * se ingresan desde la pantalla de vehículos y quedan en el almacenamiento
 * privado de la app (SharedPreferences del sandbox).
 *
 * Nota de seguridad (ver `INTEGRACION_TRACCAR_KOTLIN_INKAIKO.md`): si la app se
 * distribuye a usuarios finales, lo ideal es un backend propio que guarde
 * estas credenciales y exponga solo las posiciones. Para pruebas y uso interno
 * este almacenamiento local es suficiente.
 */
@Singleton
class TraccarCredentialsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): TraccarCredentials? {
        val user = prefs.getString(KEY_USER, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        val credentials = TraccarCredentials(user, password)
        return credentials.takeIf { it.isComplete }
    }

    fun save(credentials: TraccarCredentials) {
        prefs.edit()
            .putString(KEY_USER, credentials.user)
            .putString(KEY_PASSWORD, credentials.password)
            .apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_USER).remove(KEY_PASSWORD).apply()
    }

    private companion object {
        const val PREFS_NAME = "traccar_credentials"
        const val KEY_USER = "user"
        const val KEY_PASSWORD = "password"
    }
}
