package com.appmovilidadclinica.driver.shared.data.local

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Sesion persistida (JWT + datos basicos del user). Compatible Android +
 * iOS via `com.russhwolf:multiplatform-settings`.
 *
 * En iOS (Fase 5) agregaremos iosMain con NSUserDefaultsSettings.
 */
class SessionStore(private val settings: Settings) {

    private object Keys {
        const val TOKEN = "token"
        const val USER = "user"
        const val TOKEN_EXP = "token_exp"
    }

    private val state = MutableStateFlow(read())

    val sessionState: StateFlow<StoredSession?> = state.asStateFlow()

    fun getToken(): Flow<String?> = state.map { it?.token }

    fun getUser(): Flow<String?> = state.map { it?.user }

    fun getTokenExpiration(): Flow<Long?> = state.map { it?.tokenExpiration }

    suspend fun currentToken(): String? = state.value?.token

    fun saveSession(token: String, userJson: String, tokenExpiration: Long) {
        settings.putString(Keys.TOKEN, token)
        settings.putString(Keys.USER, userJson)
        settings.putLong(Keys.TOKEN_EXP, tokenExpiration)
        state.value = read()
    }

    fun clearSession() {
        settings.remove(Keys.TOKEN)
        settings.remove(Keys.USER)
        settings.remove(Keys.TOKEN_EXP)
        state.value = null
    }

    private fun read(): StoredSession? {
        val token = settings.getStringOrNull(Keys.TOKEN) ?: return null
        val user = settings.getStringOrNull(Keys.USER) ?: return null
        val expiration = settings.getLongOrNull(Keys.TOKEN_EXP) ?: return null
        return StoredSession(token, user, expiration)
    }

    private fun Settings.getStringOrNull(key: String): String? =
        if (this.hasKey(key)) this.getString(key, "") else null

    private fun Settings.getLongOrNull(key: String): Long? =
        if (this.hasKey(key)) this.getLong(key, 0L) else null

    private fun Settings.hasKey(key: String): Boolean =
        this.getStringOrNull(key) != null || this.getLongOrNull(key) != null
}

data class StoredSession(
    val token: String,
    val user: String,
    val tokenExpiration: Long,
)