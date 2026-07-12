package com.appmovilidadclinica.driver.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.appmovilidadclinica.driver.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "session_prefs")

@Singleton
class SessionDataStore @Inject constructor(
    private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }
    
    companion object {
        private val TOKEN_KEY = stringPreferencesKey("token")
        private val USER_KEY = stringPreferencesKey("user")
        private val TOKEN_EXP_KEY = stringPreferencesKey("token_exp")
    }
    
    suspend fun saveSession(token: String, user: User) {
        context.dataStore.edit { prefs ->
            prefs[TOKEN_KEY] = token
            prefs[USER_KEY] = json.encodeToString(user)
            // Parse and store expiration
            val exp = parseTokenExpiration(token)
            if (exp != null) {
                prefs[TOKEN_EXP_KEY] = exp.toString()
            }
        }
    }
    
    fun getToken(): Flow<String?> = context.dataStore.data.map { it[TOKEN_KEY] }
    
    fun getUser(): Flow<User?> = context.dataStore.data.map { prefs ->
        prefs[USER_KEY]?.let { json.decodeFromString<User>(it) }
    }
    
    fun getTokenExpiration(): Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[TOKEN_EXP_KEY]?.toLongOrNull()
    }
    
    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }
    
    private fun parseTokenExpiration(token: String): Long? {
        return try {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE))
                val json = org.json.JSONObject(payload)
                json.optLong("exp", 0).takeIf { it > 0 }
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
