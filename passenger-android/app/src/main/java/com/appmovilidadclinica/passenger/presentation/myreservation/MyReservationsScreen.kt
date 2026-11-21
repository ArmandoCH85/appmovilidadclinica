package com.appmovilidadclinica.passenger.presentation.myreservation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.appmovilidadclinica.passenger.shared.domain.model.Reservation
import com.appmovilidadclinica.passenger.shared.domain.model.ReservationStatus
import com.appmovilidadclinica.passenger.shared.domain.model.VehicleLocation
import com.appmovilidadclinica.passenger.presentation.common.canSelfCheckin
import com.appmovilidadclinica.passenger.presentation.common.toPeruDateTime
import com.appmovilidadclinica.passenger.presentation.vehicles.VehiclesLiveViewModel
import com.appmovilidadclinica.passenger.presentation.vehicles.ageLabel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyReservationsScreen(
    onReservationSelected: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: MyReservationsViewModel = hiltViewModel(),
    vehiclesViewModel: VehiclesLiveViewModel = hiltViewModel(),
) {
    val reservations by viewModel.reservations.collectAsStateWithLifecycle()
    val rowStates by viewModel.rowStates.collectAsStateWithLifecycle()
    val livePositions by vehiclesViewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                viewModel.sync()
                delay(20_000)
            }
        }
    }

    // Posiciones del bus: se consultan solo mientras esta lista esta visible
    // (el pasajero ve dónde viene su bus sin abrir la reserva).
    LaunchedEffect(Unit) { vehiclesViewModel.start() }
    DisposableEffect(Unit) { onDispose { vehiclesViewModel.stop() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis reservas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        if (reservations.isEmpty()) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Outlined.EventBusy,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Todavía no tiene reservas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Busque un viaje para reservar su asiento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(reservations, key = { it.reservationId }) { reservation ->
                ReservationCard(
                    reservation = reservation,
                    rowState = rowStates[reservation.reservationId] ?: ReservationRowState(),
                    vehicle = livePositions.positions.entries
                        .firstOrNull { it.key.equals(reservation.plate, ignoreCase = true) }
                        ?.value,
                    onClick = { onReservationSelected(reservation.reservationId) },
                    onSelfCheckin = { viewModel.selfCheckin(reservation.reservationId) },
                )
            }
        }
    }
}

@Composable
private fun ReservationCard(
    reservation: Reservation,
    rowState: ReservationRowState,
    vehicle: VehicleLocation?,
    onClick: () -> Unit,
    onSelfCheckin: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Origen â†’ Destino
            Text(
                "${reservation.originName} → ${reservation.destinationName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(8.dp))

            // Hora de salida con icono
            ReservaInfoRow(
                icon = Icons.Default.Schedule,
                text = "Sale ${reservation.originDepartureAt.toPeruDateTime()}",
            )

            Spacer(Modifier.height(4.dp))

            // Asiento con icono
            ReservaInfoRow(
                icon = Icons.Default.EventSeat,
                text = "Asiento ${reservation.seatLabel}",
            )

            Spacer(Modifier.height(4.dp))

            // Vehiculo y placa con icono
            if (reservation.vehicleCode.isNotEmpty()) {
                ReservaInfoRow(
                    icon = Icons.Default.DirectionsBus,
                    text = "Vehículo ${reservation.plate}",
                )
            }

            // Dónde viene el bus: visible en la lista, sin abrir la reserva.
            // Solo para reservas activas (confirmada o abordada) y si el
            // backend reporta el vehículo; si no hay posición, se dice, no se
            // inventan coordenadas.
            if (reservation.status == ReservationStatus.CONFIRMED ||
                reservation.status == ReservationStatus.BOARDED
            ) {
                val textoBus = when {
                    vehicle == null || !vehicle.hasPosition ->
                        if (vehicle == null) "Bus: sin datos del GPS" else "Bus: sin posición"

                    else -> {
                        val age = ageLabel(vehicle.fixTime)
                        val prefijo = if (vehicle.isStale) "última conocida" else "en vivo"
                        if (age != null) "Bus $prefijo · $age" else "Bus $prefijo"
                    }
                }
                ReservaInfoRow(
                    icon = Icons.Default.MyLocation,
                    text = textoBus,
                    emphasize = vehicle?.hasPosition == true && vehicle.isStale.not(),
                )
            }

            Spacer(Modifier.height(8.dp))

            // Status: icono + texto (nunca solo color, para accesibilidad)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    statusIcon(reservation.status),
                    contentDescription = null,
                    tint = statusColor(reservation.status),
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    statusLabel(reservation.status),
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor(reservation.status),
                    fontWeight = FontWeight.Medium,
                )
            }

            // El abordaje lo confirma unicamente el conductor desde su app: la
            // lista ya no ofrece "Confirmar abordaje" al pasajero.
        }
    }
}

@Composable
private fun ReservaInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    emphasize: Boolean = false,
) {
    val tint = if (emphasize) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = tint,
        )
        Text(
            "  $text",
            style = MaterialTheme.typography.bodySmall,
            color = tint,
            fontWeight = if (emphasize) FontWeight.SemiBold else null,
        )
    }
}

private fun statusLabel(status: ReservationStatus): String = when (status) {
    ReservationStatus.CONFIRMED -> "Confirmada"
    ReservationStatus.BOARDED -> "Abordada"
    ReservationStatus.COMPLETED -> "Completada"
    ReservationStatus.NO_SHOW -> "No se presentó"
    ReservationStatus.CANCELLED -> "Cancelada"
}

private fun statusIcon(status: ReservationStatus) = when (status) {
    ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW -> Icons.Filled.Cancel
    else -> Icons.Filled.CheckCircle
}

@Composable
private fun statusColor(status: ReservationStatus) = when (status) {
    ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW -> MaterialTheme.colorScheme.error
    ReservationStatus.COMPLETED, ReservationStatus.BOARDED -> MaterialTheme.colorScheme.primary
    ReservationStatus.CONFIRMED -> MaterialTheme.colorScheme.onSurface
}
