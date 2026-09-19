package com.appmovilidadclinica.driver.shared.platform

import co.touchlab.kermit.Logger

/**
 * iOS stub del NotificationService. La impl nativa (UNUserNotificationCenter)
 * llega en Fase 7.
 */
class StubNotificationService : NotificationService {
    private val logger = Logger.withTag("Notification")

    override fun notifyTripStarted(tripId: String) {
        logger.i { "[iOS stub] trip started: $tripId" }
    }

    override fun notifyIncidentReported(incidentId: String) {
        logger.i { "[iOS stub] incident reported: $incidentId" }
    }

    override fun notifyBoardingPassenger(passengerName: String) {
        logger.i { "[iOS stub] boarded: $passengerName" }
    }
}
