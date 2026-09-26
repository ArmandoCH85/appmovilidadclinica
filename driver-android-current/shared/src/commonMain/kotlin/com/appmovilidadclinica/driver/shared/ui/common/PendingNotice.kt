package com.appmovilidadclinica.driver.shared.ui.common

/**
 * Aviso de una sola vez entre pantallas.
 *
 * La pantalla que termina una accion lo publica y la pantalla que se muestra
 * al volver lo consume. Se usa para que el conductor vea la confirmacion
 * despues de una accion que cierra su pantalla; por ejemplo, al enviar una
 * incidencia se vuelve al detalle del viaje y ahi aparece "Incidencia
 * reportada".
 *
 * Vive en memoria (se pierde con la muerte del proceso), igual que
 * SelectedTripHolder: suficiente para este MVP.
 */
object PendingNotice {
    private var message: String? = null

    fun post(message: String) {
        this.message = message
    }

    /** Devuelve el aviso pendiente (una sola vez) y lo borra. */
    fun consume(): String? {
        val current = message
        message = null
        return current
    }
}
