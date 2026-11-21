package com.appmovilidadclinica.passenger.presentation.vehicles

import android.content.Context
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Nombre legible de una coordenada ("Av. Javier Prado, Surco").
 *
 * Usa el geocodificador del propio Android: sin API key, sin librerías y sin
 * permiso de ubicación. Es una consulta de red, así que corre en IO.
 *
 * Devuelve null si no se pudo resolver: en ese caso la UI **oculta** el dato en
 * lugar de mostrar coordenadas crudas.
 */
suspend fun resolvePlaceName(
    context: Context,
    latitude: Double,
    longitude: Double,
): String? = withContext(Dispatchers.IO) {
    if (!Geocoder.isPresent()) return@withContext null
    runCatching {
        val geocoder = Geocoder(context, Locale("es", "PE"))
        val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val latch = java.util.concurrent.CountDownLatch(1)
            var found: List<android.location.Address> = emptyList()
            geocoder.getFromLocation(
                latitude,
                longitude,
                1,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<android.location.Address>) {
                        found = addresses
                        latch.countDown()
                    }

                    override fun onError(errorMessage: String?) {
                        latch.countDown()
                    }
                },
            )
            latch.await(8, java.util.concurrent.TimeUnit.SECONDS)
            found
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(latitude, longitude, 1).orEmpty()
        }

        addresses.firstOrNull()?.toShortName()
    }.getOrNull()
}

/** Arma un nombre corto y útil: calle + distrito, o distrito + ciudad. */
private fun android.location.Address.toShortName(): String? {
    val parts = listOfNotNull(
        thoroughfare ?: subThoroughfare,
        subLocality ?: locality,
        if (subLocality != null) locality else null,
    ).map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

    return parts.take(2).joinToString(", ").ifBlank {
        listOfNotNull(locality, adminArea, countryName)
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
    }
}
