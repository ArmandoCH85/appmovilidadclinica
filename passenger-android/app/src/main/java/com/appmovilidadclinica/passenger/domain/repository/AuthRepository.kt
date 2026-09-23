package com.appmovilidadclinica.passenger.domain.repository

import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import com.appmovilidadclinica.passenger.shared.domain.model.User
import kotlinx.coroutines.flow.Flow

/**
 * `domain` define el contrato, `data.repository.AuthRepositoryImpl` lo
 * implementa contra Retrofit+DataStore ” el ViewModel/UseCase nunca conoce
 * esa implementacion (regla de dependencia de Clean Architecture, ver diseño).
 */
interface AuthRepository {
    /** POST /api/auth/login ” persiste token+user en exito. */
    suspend fun login(documentNumber: String, password: String): AppResult<User>

    /**
     * POST /api/auth/change-password — verifica la clave actual en el
     * servidor y la reemplaza. El userID sale del JWT, no hace falta pasarlo.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String): AppResult<Unit>

    /** Limpia la sesion local (DataStore). No llama al backend (no hay endpoint de logout). */
    suspend fun logout()

    /** Usuario actual, o null si no hay sesion / el JWT ya expiro localmente. */
    fun observeSession(): Flow<User?>

    /** Segundos restantes hasta que expire el JWT (decodificado localmente, `exp` del payload). */
    fun observeSecondsUntilExpiry(): Flow<Long?>

    /**
     * Emite un evento cada vez que una request cualquiera recibe 401 ”
     * la UI raiz lo observa para forzar logout (mismo patron que el
     * `sessionExpired` reactivo del panel admin, `useAuth.ts`).
     */
    fun observeSessionExpired(): Flow<Unit>
}
