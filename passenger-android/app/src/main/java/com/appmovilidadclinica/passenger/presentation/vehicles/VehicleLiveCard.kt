package com.appmovilidadclinica.passenger.presentation.vehicles

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appmovilidadclinica.passenger.presentation.common.toPeruDateTime
import com.appmovilidadclinica.passenger.shared.domain.model.VehicleLocation

/**
 * Tarjeta "vehículo en vivo" para el detalle de una reserva: el pasajero ve
 * dónde está el bus de SU viaje, sin saber nada del servidor de monitoreo ni
 * de credenciales.
 */
@Composable
fun VehicleLiveCard(
    plate: String,
    modifier: Modifier = Modifier,
    onOpenMap: (String) -> Unit = {},
    viewModel: VehiclesLiveViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(plate) { viewModel.start() }
    DisposableEffect(plate) { onDispose { viewModel.stop() } }

    val vehicle = state.positions.entries
        .firstOrNull { it.key.equals(plate, ignoreCase = true) }
        ?.value

    // Nombre de la ubicación (geocodificación inversa del propio Android).
    var placeName by remember(vehicle?.latitude, vehicle?.longitude) {
        mutableStateOf<String?>(null)
    }
    LaunchedEffect(vehicle?.latitude, vehicle?.longitude) {
        val lat = vehicle?.latitude
        val lon = vehicle?.longitude
        placeName = if (lat != null && lon != null) {
            resolvePlaceName(context, lat, lon)
        } else {
            null
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.DirectionsBus,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Vehículo $plate",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                IconButton(onClick = viewModel::refresh) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Actualizar ubicación",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            val vehicle = state.positions.entries
                .firstOrNull { it.key.equals(plate, ignoreCase = true) }
                ?.value
            if (state.loading && vehicle == null) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                return@Column
            }

            state.errorMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (vehicle != null && vehicle.hasPosition) {
                Spacer(Modifier.height(6.dp))
                // Nombre de la ubicación en vez de coordenadas. Si no se puede
                // resolver (sin red o sin geocodificador), no se muestra nada.
                if (placeName != null) {
                    Text(
                        placeName!!,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                vehicle.fixTime?.let { fix ->
                    Text(
                        "Última posición: ${fix.toPeruDateTime()}" +
                            if (vehicle.isStale) "  ·  última conocida" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (vehicle.isStale) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { onOpenMap(plate) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Ver en el mapa")
                }
            } else if (vehicle != null && !vehicle.hasPosition) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Sin posición del bus en este momento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (!state.loading && state.errorMessage == null) {
                // Seguridad: el backend no devuelve ubicación hasta que el
                // conductor inicie el recorrido desde su app.
                Spacer(Modifier.height(6.dp))
                Text(
                    "La ubicación del bus estará disponible cuando el conductor inicie el recorrido.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.lastUpdated?.let { updated ->
                Spacer(Modifier.height(6.dp))
                Text(
                    "Actualizado: ${updated.toPeruDateTime()} (cada ${VehiclesLiveViewModel.REFRESH_INTERVAL_MS / 1000} s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun openInMaps(context: android.content.Context, vehicle: VehicleLocation) {
    val lat = vehicle.latitude ?: return
    val lon = vehicle.longitude ?: return
    val geo = Uri.parse("geo:%.6f,%.6f?q=%.6f,%.6f(%s)".format(lat, lon, lat, lon, vehicle.plate))
    val web = Uri.parse("https://www.google.com/maps/search/?api=1&query=%.6f,%.6f".format(lat, lon))
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, geo)) }
        .onFailure { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, web)) } }
}
