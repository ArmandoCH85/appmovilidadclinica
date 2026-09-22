package com.appmovilidadclinica.driver.shared.data.remote

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Emisor global de "sesion expirada". El cliente Ktor lo dispara cuando una
 * request autenticada recibe 401 (token expirado o usuario suspendido en la
 * BD); la UI raiz lo observa para forzar logout + navegacion a Login.
 * Mismo patron que la app del pasajero.
 */
class SessionExpiredNotifier {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun notifySessionExpired() {
        _events.tryEmit(Unit)
    }
}
