package com.appmovilidadclinica.passenger.presentation.vehicles

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appmovilidadclinica.passenger.presentation.common.toPeruDateTime
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

/**
 * Mapa del bus en vivo con OpenStreetMap (osmdroid, sin API key).
 *
 * Muestra el marcador del vehículo de la reserva y lo **mueve** cada vez que
 * llega una posición nueva (el ViewModel refresca cada 30 s). La cámara solo
 * se centra al entrar o cuando el pasajero toca "Centrar": no salta sola en
 * cada actualización.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleMapScreen(
    plate: String,
    onBack: () -> Unit,
    viewModel: VehiclesLiveViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val vehicle = state.positions.entries
        .firstOrNull { it.key.equals(plate, ignoreCase = true) }
        ?.value

    LaunchedEffect(plate) { viewModel.start() }
    DisposableEffect(plate) { onDispose { viewModel.stop() } }

    val mapView = remember { createMapView(context) }
    val marker = remember(mapView) {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = plate
            isEnabled = true
        }
    }
    // Línea azul con el camino que hizo el bus en las últimas horas.
    val polyline = remember(mapView) {
        Polyline().apply {
            outlinePaint.color = Color.parseColor("#1E88E5")
            outlinePaint.strokeWidth = 10f
            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
        }
    }
    var centredOnVehicle by remember { mutableStateOf(false) }

    DisposableEffect(mapView, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bus $plate en el mapa") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize(),
            )

            // El marcador y el recorrido se actualizan por estado, no por el
            // update() de AndroidView: así se mueven solos en cada refresco.
            LaunchedEffect(vehicle?.latitude, vehicle?.longitude, vehicle?.fixTime) {
                val lat = vehicle?.latitude ?: return@LaunchedEffect
                val lon = vehicle?.longitude ?: return@LaunchedEffect
                val point = GeoPoint(lat, lon)
                marker.position = point
                marker.snippet = vehicle.fixTime?.let { "Posición: ${it.toPeruDateTime()}" }
                if (!mapView.overlays.contains(marker)) {
                    mapView.overlays.add(marker)
                }
                if (!centredOnVehicle) {
                    mapView.controller.setZoom(16.0)
                    mapView.controller.setCenter(point)
                    centredOnVehicle = true
                }
                mapView.invalidate()
            }

            // Línea del camino recorrido (2 h de histórico que manda el backend).
            LaunchedEffect(vehicle?.trail) {
                val points = vehicle?.trail.orEmpty()
                    .map { GeoPoint(it.latitude, it.longitude) }
                if (points.size >= 2) {
                    polyline.setPoints(points)
                    if (!mapView.overlays.contains(polyline)) {
                        // Al fondo: el marcador del bus queda encima de la línea.
                        mapView.overlays.add(0, polyline)
                    }
                    mapView.invalidate()
                }
            }

            // Estado del bus arriba del mapa
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp)
                    .fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.loading && vehicle == null) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            when {
                                vehicle == null -> "Buscando el bus…"
                                !vehicle.hasPosition -> "El bus no reporta posición"
                                vehicle.isStale -> "Última ubicación conocida"
                                else -> "Bus en vivo"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (vehicle?.isStale == true || vehicle == null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                    vehicle?.let { v ->
                        Spacer(Modifier.height(4.dp))
                        val age = ageLabel(v.fixTime)
                        Text(
                            listOfNotNull(
                                age?.let { "hace $it" },
                                v.speedKmh?.let { "%.0f km/h".format(it) },
                                if (v.isStale) "desactualizada" else null,
                            ).joinToString(" · ").ifBlank { "sin datos de hora" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.errorMessage?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            // Acciones abajo
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                horizontalAlignment = Alignment.End,
            ) {
                vehicle?.let { v ->
                    val lat = v.latitude
                    val lon = v.longitude
                    if (lat != null && lon != null) {
                        ExtendedFloatingActionButton(
                            onClick = { openGoogleMaps(context, lat, lon, plate) },
                            icon = { Icon(Icons.Default.MyLocation, contentDescription = null) },
                            text = { Text("Abrir en Google Maps") },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
                ExtendedFloatingActionButton(
                    onClick = {
                        vehicle?.latitude?.let { lat ->
                            vehicle.longitude?.let { lon ->
                                mapView.controller.animateTo(GeoPoint(lat, lon))
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.MyLocation, contentDescription = null) },
                    text = { Text("Centrar") },
                )
            }
        }
    }
}

/** MapView configurado: tiles OSM, caché en la carpeta de la app, sin permisos extra. */
private fun createMapView(context: Context): MapView {
    Configuration.getInstance().apply {
        // osmdroid exige un User-Agent identificable para usar los tiles de OSM.
        userAgentValue = context.packageName
        osmdroidBasePath = File(context.cacheDir, "osmdroid")
        osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
    }
    return MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        setBuiltInZoomControls(false)
        controller.setZoom(12.0)
        // Centro inicial: Lima, hasta que llegue la posición del vehículo.
        controller.setCenter(GeoPoint(-12.0464, -77.0428))
    }
}

private fun openGoogleMaps(
    context: Context,
    latitude: Double,
    longitude: Double,
    plate: String,
) {
    val geo = Uri.parse("geo:%.6f,%.6f?q=%.6f,%.6f(%s)".format(latitude, longitude, latitude, longitude, plate))
    val web = Uri.parse("https://www.google.com/maps/search/?api=1&query=%.6f,%.6f".format(latitude, longitude))
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, geo)) }
        .onFailure { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, web)) } }
}
