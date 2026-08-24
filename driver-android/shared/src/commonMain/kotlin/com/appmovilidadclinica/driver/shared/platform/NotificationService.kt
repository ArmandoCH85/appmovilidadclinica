package com.appmovilidadclinica.driver.shared.platform

/**
 * Servicio multiplatform de notificaciones locales.
 *
 * Android: NotificationCompat + canales.
 * iOS: stub no-op hasta Fase 7 (UNUserNotificationCenter).
 */
interface NotificationService {
    fun notifyTripStarted(tripId: String)
    fun notifyIncidentReported(incidentId: String)
    fun notifyBoardingPassenger(passengerName: String)
}
