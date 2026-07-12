package com.appmovilidadclinica.passenger.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sesion persistida (JWT + datos basicos del user). DataStore Preferences
 * (no SharedPreferences, deprecado — ver diseño técnico).
 *
 * OJO: esto NO cifra el token en reposo. Para produccion real conviene
 * envolver este DataStore con `androidx.security.crypto` (EncryptedFile) o
 * usar Keystore — se dejo fuera del alcance de este scaffolding inicial
 * (ver limite explicito del pedido: no se generaron dependencias mas alla
 * de las declaradas en el diseño técnico), pero es el primer hardening a
 * sumar antes de producción.
 *
 * ────────────────────────────────────────────────────────────────────────
 * Evaluación de cifrado (ver §12 del doc de desarrollo, 2026-07-12)
 * ────────────────────────────────────────────────────────────────────────
 * Opciones consideradas:
 *
 * A) `androidx.security.crypto.EncryptedSharedPreferences` — DESCARTADO.
 *    Marcado `@Deprecated("Use android.content.SharedPreferences instead")`
 *    en androidx-main (la propia guia dice "use SharedPreferences normal
 *    y cifra vos con Keystore"). No encaja con DataStore<Preferences> de
 *    todas formas: solo cubre SharedPreferences.
 *
 * B) `androidx.datastore:datastore-tink` (AeadSerializer) — VIABLE,
 *    camino "moderno" oficial. Requiere migrar de DataStore<Preferences>
 *    a DataStore<Tipado> con un Serializer<T> propio, lo cual implica
 *    un nuevo archivo (no puede leer las preferences actuales) +
 *    migración manual de usuarios existentes. Es la opción correcta si
 *    se decide hacer el cambio de arquitectura de storage.
 *
 * C) Cifrado manual con Android Keystore directo (AES/GCM/NoPadding) —
 *    RECOMENDADO para este alcance. Cero dependencias nuevas, mínimo
 *    cambio de API (sigue siendo DataStore<Preferences>; solo cambia
 *    que `token` se guarda como Base64(IV || ciphertext) en vez de
 *    plano). Un solo campo de alto valor (el JWT). El resto (employeeCode,
 *    fullName, role, department, phone) son datos no sensibles.
 *    Pasos de implementación cuando se decida hacerlo:
 *      1. Crear `SecretKey` AES-256 en Keystore con KeyGenParameterSpec
 *         (setUserAuthenticationRequired = false; el JWT ya es la
 *         segunda factor — usuario + contraseña).
 *      2. En `save()`: cifrar `session.token` con `Cipher` modo
 *         AES/GCM/NoPadding (IV aleatorio de 12 bytes por escritura,
 *         prepended al ciphertext), Base64-encode, guardar en
 *         `Keys.TOKEN`.
 *      3. En `tokenFlow`/`currentToken()`: Base64-decode, separar IV,
 *         `Cipher.init(DECRYPT_MODE)`, decifrar.
 *      4. Manejar `KeyPermanentlyInvalidatedException` (huella/biometría
 *         enrolada cambió) → `clear()` + forzar re-login.
 *    Tradeoffs: ~50 líneas de código crypto a mano (IV, padding, error
 *    handling). Tink debajo ofrece la misma garantía con menos código,
 *    pero a costa de la migración de Opción B.
 *
 * D) No cifrar (estado actual) — ACEPTABLE TEMPORALMENTE dado que:
 *    - `allowBackup="false"` en el manifest ya descarta extracción por
 *      ADB backup / Google Auto Backup.
 *    - JWT expira a las 24h, así que el blast radius es acotado.
 *    - Riesgo residual: dispositivo rooteado, otro app con root, forensic
 *      dump de la particion /data. Para la amenaza asumida (compañero
 *      que toma el telefono desbloqueado y abre archivos), DataStore no
 *      es legible sin root ni `run-as` en producción.
 *
 * Decisión: implementar Opción C cuando se haga el primer hardening
 * pre-producción. Mantener este comentario sincronizado con el doc
 * `desarrollo_pasajero.md` §12.
 */
@Singleton
class SessionDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val USER_ID = longPreferencesKey("user_id")
        val EMPLOYEE_CODE = stringPreferencesKey("employee_code")
        val FULL_NAME = stringPreferencesKey("full_name")
        val ROLE = stringPreferencesKey("role")
        val DEPARTMENT = stringPreferencesKey("department")
        val PHONE = stringPreferencesKey("phone")
    }

    val tokenFlow: Flow<String?> = dataStore.data.map { it[Keys.TOKEN] }

    val sessionFlow: Flow<StoredSession?> = dataStore.data.map { prefs ->
        val token = prefs[Keys.TOKEN] ?: return@map null
        val userId = prefs[Keys.USER_ID] ?: return@map null
        StoredSession(
            token = token,
            userId = userId,
            employeeCode = prefs[Keys.EMPLOYEE_CODE].orEmpty(),
            fullName = prefs[Keys.FULL_NAME].orEmpty(),
            role = prefs[Keys.ROLE].orEmpty(),
            department = prefs[Keys.DEPARTMENT],
            phone = prefs[Keys.PHONE],
        )
    }

    suspend fun currentToken(): String? = tokenFlow.first()

    suspend fun save(session: StoredSession) {
        dataStore.edit { prefs ->
            prefs[Keys.TOKEN] = session.token
            prefs[Keys.USER_ID] = session.userId
            prefs[Keys.EMPLOYEE_CODE] = session.employeeCode
            prefs[Keys.FULL_NAME] = session.fullName
            prefs[Keys.ROLE] = session.role
            session.department?.let { prefs[Keys.DEPARTMENT] = it }
            session.phone?.let { prefs[Keys.PHONE] = it }
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}

data class StoredSession(
    val token: String,
    val userId: Long,
    val employeeCode: String,
    val fullName: String,
    val role: String,
    val department: String?,
    val phone: String?,
)
