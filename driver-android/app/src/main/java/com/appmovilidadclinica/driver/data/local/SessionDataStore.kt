package com.appmovilidadclinica.driver.data.local

import android.content.Context
import com.appmovilidadclinica.driver.shared.data.local.SessionStore
import com.russhwolf.settings.SharedPreferencesSettings

/**
 * Wrapper Android del [SessionStore] multiplataforma. El driver usa
 * DI manual con object AppModule (no Hilt), asi que no hay @Inject.
 */
class SessionDataStore(context: Context) {

    private val delegate: SessionStore = SessionStore(
        SharedPreferencesSettings(
            context.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
        )
    )

    fun getToken() = delegate.getToken()
    fun getUser() = delegate.getUser()
    fun getTokenExpiration() = delegate.getTokenExpiration()

    suspend fun currentToken(): String? = delegate.currentToken()

    fun saveSession(token: String, userJson: String, tokenExpiration: Long) =
        delegate.saveSession(token, userJson, tokenExpiration)

    fun clearSession() = delegate.clearSession()

    companion object {
        private const val SETTINGS_NAME = "session_prefs"
    }
}