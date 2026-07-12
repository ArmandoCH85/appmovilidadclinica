package com.appmovilidadclinica.passenger.data.remote

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bus minimo (un solo evento, sin payload) para el caso "cualquier request
 * recibio 401" — `AuthInterceptor` emite, `AuthRepositoryImpl` lo expone
 * como `observeSessionExpired()` del dominio, la UI raiz lo observa para
 * forzar logout + navegacion a Login. Mismo patron que el modal de sesion
 * expirada del panel admin (`sessionExpired` reactivo en `useAuth.ts`).
 */
@Singleton
class SessionExpiredNotifier @Inject constructor() {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun notifySessionExpired() {
        _events.tryEmit(Unit)
    }
}
