package com.appmovilidadclinica.driver.shared.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Android impl de [LocationService] usando FusedLocationProviderClient
 * (Google Play Services Location). Requiere permiso ACCESS_FINE_LOCATION
 * o ACCESS_COARSE_LOCATION en runtime.
 *
 * No-op si el permiso no esta concedido. La UI debe llamar
 * [requestPermissions] primero.
 */
class AndroidLocationService(private val context: Context) : LocationService {
    private val logger = Logger.withTag("Location")
    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    override fun requestPermissions(): Flow<PermissionStatus> = flow {
        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            emit(PermissionStatus.GRANTED)
        } else {
            // La UI debe disparar un permission launcher via Activity.
            // Aqui solo devolvemos el estado actual; para pedirlo en runtime
            // hay que usar rememberLauncherForActivityResult en la composable.
            emit(PermissionStatus.DENIED)
        }
    }

    override suspend fun currentLocation(): Coordinates? {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return null
        return suspendCancellableCoroutine { cont ->
            try {
                @SuppressLint("MissingPermission")
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { location ->
                        cont.resume(
                            location?.let {
                                Coordinates(it.latitude, it.longitude, it.accuracy)
                            },
                        )
                    }
                    .addOnFailureListener { e ->
                        logger.w { "currentLocation failed: ${e.message}" }
                        cont.resume(null)
                    }
            } catch (e: SecurityException) {
                logger.w { "currentLocation SecurityException: ${e.message}" }
                cont.resume(null)
            }
        }
    }

    override fun observeLocation(): Flow<Coordinates> = callbackFlow {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            close(); return@callbackFlow
        }
        val callback = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                result.lastLocation?.let { location ->
                    trySend(
                        Coordinates(location.latitude, location.longitude, location.accuracy),
                    )
                }
            }
        }
        try {
            @SuppressLint("MissingPermission")
            client.requestLocationUpdates(
                com.google.android.gms.location.LocationRequest.Builder(
                    com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                    5_000L,
                ).build(),
                callback,
                context.mainLooper,
            )
        } catch (e: SecurityException) {
            logger.w { "observeLocation SecurityException: ${e.message}" }
            close(e); return@callbackFlow
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }

    override fun isAvailable(): Boolean = true

    private fun hasPermission(perm: String): Boolean =
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
}
