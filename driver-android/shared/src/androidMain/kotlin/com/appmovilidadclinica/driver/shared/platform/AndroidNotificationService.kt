package com.appmovilidadclinica.driver.shared.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger

/**
 * Android impl de [NotificationService] usando NotificationCompat.
 * Canal unico para eventos del conductor (trip started, incident,
 * passenger boarded). El `notificationId` debe ser estable para que
 * el sistema reemplace la notificacion anterior del mismo tipo.
 */
class AndroidNotificationService(private val context: Context) : NotificationService {
    private val logger = Logger.withTag("Notification")
    private val channelId = "driver_events"
    private val notificationIdBase = 1000

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Eventos del conductor",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notificaciones de viajes, incidencias y abordajes."
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun notifyTripStarted(tripId: String) {
        post(
            id = notificationIdBase,
            title = "Viaje iniciado",
            body = "Trip #$tripId en curso.",
        )
    }

    override fun notifyIncidentReported(incidentId: String) {
        post(
            id = notificationIdBase + 1,
            title = "Incidencia reportada",
            body = "Incidencia #$incidentId enviada.",
        )
    }

    override fun notifyBoardingPassenger(passengerName: String) {
        post(
            id = notificationIdBase + 2,
            title = "Pasajero abordado",
            body = passengerName,
        )
    }

    private fun post(id: Int, title: String, body: String) {
        // Permiso runtime en Android 13+; si no esta, intentamos igual (no-op silencioso).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                logger.w { "POST_NOTIFICATIONS no granted, skip" }
                return
            }
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            logger.w { "notify SecurityException: ${e.message}" }
        }
    }
}
