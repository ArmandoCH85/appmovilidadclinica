package com.appmovilidadclinica.passenger.presentation.vehicles

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appmovilidadclinica.passenger.shared.domain.model.VehicleLocation
import com.appmovilidadclinica.passenger.presentation.common.toPeruDateTime
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Vehículos en vivo (Traccar): posición real de CJI-579 y C0C-965, con su
 * hora de fix y estado. Se actualiza cada 20 s mientras la pantalla está
 * visible y se detiene al salir.
 *
 * No hay mapa embebido a propósito (la app no tenía uno y el documento pide no
 * agregar librerías de más): se muestran las coordenadas y un botón para
 * abrirlas en Google Maps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleTrackingScreen(
    onBack: () -> Unit,
    viewModel: VehicleTrackingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCredentialsDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.startPolling()
        if (!state.credentialsConfigured) showCredentialsDialog = true
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.stopPolling() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vehículos en vivo") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                    }
                    IconButton(onClick = { showCredentialsDialog = true }) {
                        Icon(Icons.Default.Place, contentDescription = "Configurar acceso GPS")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Posiciones del servidor GPS (MS Global). Se actualizan cada " +
                    "${VehicleTrackingViewModel.REFRESH_INTERVAL_MS / 1000} segundos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.errorMessage?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            if (!state.credentialsConfigured) {
                Button(
                    onClick = { showCredentialsDialog = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text("Configurar acceso GPS")
                }
            }

            if (state.loading && state.vehicles.isEmpty()) {
                CircularProgressIndicator()
            }

            state.vehicles.forEach { vehicle ->
                VehicleCard(
                    vehicle = vehicle,
                    onOpenMap = {
                        val lat = vehicle.latitude
                        val lon = vehicle.longitude
                        if (lat != null && lon != null) {
                            openInMaps(context, lat, lon, vehicle.plate)
                        }
                    },
                )
            }

            state.lastUpdated?.let { updated ->
                Spacer(Modifier.height(4.dp))
                Text(
                    "Última consulta: ${updated.toPeruDateTime()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showCredentialsDialog) {
        CredentialsDialog(
            onDismiss = { showCredentialsDialog = false },
            onSave = { user, password ->
                viewModel.saveCredentials(user, password)
                showCredentialsDialog = false
            },
            onForget = if (state.credentialsConfigured) {
                {
                    viewModel.forgetCredentials()
                    showCredentialsDialog = false
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun VehicleCard(vehicle: VehicleLocation, onOpenMap: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    vehicle.plate,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                StateChip(
                    label = when {
                        vehicle.online == true -> "GPS en línea"
                        vehicle.online == false -> "GPS sin conexión"
                        else -> "Estado desconocido"
                    },
                    positive = vehicle.online == true,
                )
            }

            Spacer(Modifier.height(8.dp))

            if (vehicle.hasPosition) {
                Text(
                    "%.6f, %.6f".format(vehicle.latitude, vehicle.longitude),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                vehicle.fixTime?.let { fix ->
                    Text(
                        "Posición GPS: ${fix.toPeruDateTime()}" +
                            if (vehicle.isStale) "  ·  última ubicación conocida" else "  ·  reciente",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (vehicle.isStale) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                vehicle.speedKmh?.let { kmh ->
                    Text(
                        "Velocidad: %.0f km/h".format(kmh),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                vehicle.courseDegrees?.let { course ->
                    Text(
                        "Rumbo: %.0f°".format(course),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onOpenMap,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text("Ver en el mapa")
                }
            } else {
                Text(
                    "Sin posición disponible en este momento.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "No se muestran coordenadas inventadas: el vehículo no reportó\n" +
                        "una posición válida todavía.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (vehicle.deviceName != null && vehicle.deviceName != vehicle.plate) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Nombre en el servidor GPS: ${vehicle.deviceName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StateChip(label: String, positive: Boolean) {
    Surface(
        color = if (positive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (positive) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun CredentialsDialog(
    onDismiss: () -> Unit,
    onSave: (user: String, password: String) -> Unit,
    onForget: (() -> Unit)?,
) {
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Acceso al servidor GPS") },
        text = {
            Column {
                Text(
                    "Usuario y contraseña de MS Global GPS. Se guardan solo en este " +
                        "dispositivo y no se envían a ningún otro lado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("Usuario") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onForget != null) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onForget) { Text("Olvidar credenciales guardadas") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(user, password) },
                enabled = user.isNotBlank() && password.isNotBlank(),
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

/** Abre las coordenadas en la app de mapas; si no hay, en el navegador. */
private fun openInMaps(
    context: android.content.Context,
    latitude: Double,
    longitude: Double,
    label: String,
) {
    val geo = Uri.parse("geo:%.6f,%.6f?q=%.6f,%.6f(%s)".format(latitude, longitude, latitude, longitude, label))
    val web = Uri.parse("https://www.google.com/maps/search/?api=1&query=%.6f,%.6f".format(latitude, longitude))
    val intent = Intent(Intent.ACTION_VIEW, geo)
    runCatching { context.startActivity(intent) }
        .onFailure { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, web)) } }
}

/** "hace N min" para mostrar antigüedad sin obligar a calcularla. */
@Suppress("unused")
private fun ageLabel(fixTime: Instant?): String? {
    val fix = fixTime ?: return null
    val minutes = (Clock.System.now() - fix).inWholeMinutes
    return when {
        minutes < 1 -> "hace instantes"
        minutes < 60 -> "hace $minutes min"
        minutes < 1440 -> "hace ${minutes / 60} h"
        else -> "hace ${minutes / 1440} días"
    }
}
