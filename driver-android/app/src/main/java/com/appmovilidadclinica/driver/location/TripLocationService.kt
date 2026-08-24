package com.appmovilidadclinica.driver.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import com.appmovilidadclinica.driver.MainActivity
import android.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Foreground service que trackea ubicación del conductor en background.
 *
 * No es KMP-friendly (es Service Android-only). Se inicia via Intent
 * desde el ViewModel cuando el trip pasa a IN_PROGRESS, y se detiene
 * cuando pasa a COMPLETED.
 *
 * TODO Fase 5+: cuando vayamos a iOS, este service es reemplazado por
 * `CLLocationManager.startUpdating...` con background mode en Info.plist.
 */
class TripLocationService : Service() {

    private val logger = Logger.withTag("TripLocationService")
    private lateinit var client: FusedLocationProviderClient
    private lateinit var callback: LocationCallback

    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    logger.i {
                        "track: (${location.latitude}, ${location.longitude}) " +
                            "±${location.accuracy}m"
                    }
                }
            }
        }
        requestUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        client.removeLocationUpdates(callback)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun requestUpdates() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                logger.w { "ACCESS_FINE_LOCATION no granted, stop service" }
                stopSelf()
                return
            }
        }
        try {
            client.requestLocationUpdates(
                LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    10_000L,
                ).build(),
                callback,
                Looper.getMainLooper(),
            )
        } catch (e: SecurityException) {
            logger.w { "requestLocationUpdates SecurityException: ${e.message}" }
            stopSelf()
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tracking del viaje",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Tracking GPS activo durante el viaje."
                setShowBadge(false)
            }
            val mgr = getSystemService(NotificationManager::class.java)
            mgr?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tracking del viaje")
            .setContentText("Registrando ubicacion del recorrido")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "trip_tracking"
        private const val NOTIFICATION_ID = 2000

        fun start(context: Context) {
            val intent = Intent(context, TripLocationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TripLocationService::class.java))
        }
    }
}
