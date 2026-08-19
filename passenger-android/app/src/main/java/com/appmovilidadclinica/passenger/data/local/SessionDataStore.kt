package com.appmovilidadclinica.passenger.data.local

import android.content.Context
import com.appmovilidadclinica.passenger.shared.data.local.SessionStore
import com.appmovilidadclinica.passenger.shared.data.local.StoredSession
import com.russhwolf.settings.SharedPreferencesSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper Android del [SessionStore] multiplataforma. Vive en el app
 * porque Hilt no procesa tipos del shared/androidMain (limitación de
 * KSP+KMP, ver diseno tecnico).
 *
 * Usa `SharedPreferencesSettings` de multiplatform-settings — mismo
 * formato de datos que el SessionStore del shared, asi que cuando
 * llegue iOS (Fase 5) un NSUserDefaultsSettings reemplaza a este y el
 * SessionStore del shared no cambia.
 */
@Singleton
class SessionDataStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val delegate: SessionStore = SessionStore(
        SharedPreferencesSettings(
            context.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
        )
    )

    val tokenFlow get() = delegate.tokenFlow
    val sessionFlow get() = delegate.sessionFlow

    suspend fun currentToken(): String? = delegate.currentToken()

    suspend fun save(session: StoredSession) = delegate.save(session)

    suspend fun clear() = delegate.clear()

    companion object {
        private const val SETTINGS_NAME = "session_prefs"
    }
}