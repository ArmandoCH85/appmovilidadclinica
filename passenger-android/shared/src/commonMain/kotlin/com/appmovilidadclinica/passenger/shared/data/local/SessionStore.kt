package com.appmovilidadclinica.passenger.shared.data.local

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Snapshot inmutable de la sesion. Vive en `commonMain` para que las
 * 2 apps lo compartan.
 */
data class StoredSession(
    val token: String,
    val userId: Long,
    val employeeCode: String,
    val fullName: String,
    val role: String,
    val department: String?,
    val phone: String?,
)

/**
 * Sesion persistida (JWT + datos basicos del user). Compatible Android +
 * iOS via `com.russhwolf:multiplatform-settings`.
 *
 * El constructor toma `Settings` (interfaz multiplatform). La factory
 * concreta (SharedPreferences en Android, NSUserDefaults en iOS) la
 * provee cada app via su `createPlatformSettings`.
 *
 * OJO: el JWT NO esta cifrado en reposo. El hardening (cifrado con
 * Android Keystore / iOS Keychain) queda fuera de alcance de este MVP.
 */
class SessionStore(private val settings: Settings) {

    private object Keys {
        const val TOKEN = "token"
        const val USER_ID = "user_id"
        const val EMPLOYEE_CODE = "employee_code"
        const val FULL_NAME = "full_name"
        const val ROLE = "role"
        const val DEPARTMENT = "department"
        const val PHONE = "phone"
    }

    private val state = MutableStateFlow(read())

    val sessionState: StateFlow<StoredSession?> = state.asStateFlow()

    val tokenFlow: Flow<String?> = state.map { it?.token }

    val sessionFlow: Flow<StoredSession?> = state

    suspend fun currentToken(): String? = state.value?.token

    fun save(session: StoredSession) {
        settings.putString(Keys.TOKEN, session.token)
        settings.putLong(Keys.USER_ID, session.userId)
        settings.putString(Keys.EMPLOYEE_CODE, session.employeeCode)
        settings.putString(Keys.FULL_NAME, session.fullName)
        settings.putString(Keys.ROLE, session.role)
        if (session.department != null) {
            settings.putString(Keys.DEPARTMENT, session.department)
        } else {
            settings.remove(Keys.DEPARTMENT)
        }
        if (session.phone != null) {
            settings.putString(Keys.PHONE, session.phone)
        } else {
            settings.remove(Keys.PHONE)
        }
        state.value = read()
    }

    fun clear() {
        settings.remove(Keys.TOKEN)
        settings.remove(Keys.USER_ID)
        settings.remove(Keys.EMPLOYEE_CODE)
        settings.remove(Keys.FULL_NAME)
        settings.remove(Keys.ROLE)
        settings.remove(Keys.DEPARTMENT)
        settings.remove(Keys.PHONE)
        state.value = null
    }

    private fun read(): StoredSession? {
        val token = settings.getStringOrNull(Keys.TOKEN) ?: return null
        val userId = settings.getLongOrNull(Keys.USER_ID) ?: return null
        return StoredSession(
            token = token,
            userId = userId,
            employeeCode = settings.getStringOrNull(Keys.EMPLOYEE_CODE).orEmpty(),
            fullName = settings.getStringOrNull(Keys.FULL_NAME).orEmpty(),
            role = settings.getStringOrNull(Keys.ROLE).orEmpty(),
            department = settings.getStringOrNull(Keys.DEPARTMENT),
            phone = settings.getStringOrNull(Keys.PHONE),
        )
    }

    private fun Settings.getStringOrNull(key: String): String? =
        if (this.hasKey(key)) this.getString(key, "") else null

    private fun Settings.getLongOrNull(key: String): Long? =
        if (this.hasKey(key)) this.getLong(key, 0L) else null

    private fun Settings.hasKey(key: String): Boolean =
        this.getStringOrNull(key) != null || this.getLongOrNull(key) != null
}