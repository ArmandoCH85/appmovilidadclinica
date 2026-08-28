package com.appmovilidadclinica.passenger.presentation.myreservation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appmovilidadclinica.passenger.shared.domain.model.ReservationStatus
import com.appmovilidadclinica.passenger.shared.domain.model.TripStop
import com.appmovilidadclinica.passenger.shared.domain.model.TripStopStatus
import com.appmovilidadclinica.passenger.presentation.common.toPeruDateTime
import com.appmovilidadclinica.passenger.presentation.common.toPeruTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyReservationDetailScreen(
    onBack: () -> Unit,
    viewModel: MyReservationDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val reservation = state.reservation

    // Navegar atras automaticamente cuando el cancel es exitoso
    LaunchedEffect(state.cancelled) {
        if (state.cancelled) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi reserva") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loadingStops,
            onRefresh = viewModel::refreshStops,
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (reservation == null) {
                    Text("Cargando…")
                    return@Column
                }

            // Codigo de reserva
            Text(
                reservation.reservationCode,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(6.dp))

            // Estado: icono + texto (nunca solo color, para accesibilidad)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    statusIcon(reservation.status),
                    contentDescription = null,
                    tint = statusColor(reservation.status),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    statusLabel(reservation.status),
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor(reservation.status),
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(20.dp))

            // QR o placeholder
            if (state.qrBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = state.qrBitmap!!.asImageBitmap(),
                    contentDescription = "CÃ³digo QR de la reserva",
                    modifier = Modifier.size(220.dp),
                )
            } else {
                Text(
                    "QR no disponible. Cancele y reconfirme para regenerar el cÃ³digo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(20.dp))

            // Card con info del viaje
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Ruta
                    Text(
                        "${reservation.originName} â†’ ${reservation.destinationName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Spacer(Modifier.height(12.dp))

                    // Salida con icono
                    DetailRow(
                        icon = Icons.Default.Schedule,
                        text = "Salida: ${reservation.originDepartureAt.toPeruDateTime()}",
                    )

                    Spacer(Modifier.height(6.dp))

                    // Asiento con icono
                    DetailRow(
                        icon = Icons.Default.EventSeat,
                        text = "Asiento: ${reservation.seatLabel}",
                    )

                    if (reservation.vehicleCode.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        DetailRow(
                            icon = Icons.Default.DirectionsBus,
                            text = "VehÃ­culo: ${reservation.plate}",
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Card con el recorrido: hora aproximada/real de llegada del bus a cada parada
            if (state.loadingStops || state.stops.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Recorrido",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(12.dp))

                        if (state.loadingStops) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            TripStopsTimeline(state.stops)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }

            // Botones de accion (solo si esta CONFIRMED)
            if (reservation.status == ReservationStatus.CONFIRMED) {
                Button(
                    onClick = viewModel::selfCheckin,
                    enabled = viewModel.canSelfCheckin && !state.checkingIn,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Text(if (state.checkingIn) "Confirmandoâ€¦" else "Confirmar abordaje")
                }
                if (!viewModel.canSelfCheckin) {
                    Text(
                        "Disponible solo cerca del horario de salida.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = viewModel::askCancel,
                    enabled = !state.cancelling,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Text(if (state.cancelling) "Cancelandoâ€¦" else "Cancelar reserva")
                }
            }

            if (state.errorMessage != null) {
                Text(
                    state.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            }
        }
    }

    // Dialog de confirmacion de cancel
    if (state.showCancelConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCancel,
            title = { Text("Cancelar reserva") },
            text = { Text("Â¿Confirma que desea cancelar esta reserva? Esta acciÃ³n no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmCancel) { Text("SÃ­, cancelar") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCancel) { Text("Volver") }
            },
        )
    }
}

/** Timeline de paradas: icono de estado + hora real (si el chofer ya la marco) o estimada. */
@Composable
private fun TripStopsTimeline(stops: List<TripStop>) {
    Column {
        stops.forEachIndexed { index, stop ->
            TripStopRow(stop = stop, isLast = index == stops.lastIndex)
        }
    }
}

@Composable
private fun TripStopRow(stop: TripStop, isLast: Boolean) {
    val skipped = stop.status == TripStopStatus.SKIPPED
    val arrived = stop.actualArrivalAt != null
    val departed = stop.actualDepartureAt != null

    // Semáforo de colores: rojo PENDIENTE, amarillo ARRIVED, verde DEPARTED,
    // gris SKIPPED.
    val semaphoreColor = when (stop.status) {
        TripStopStatus.PENDING -> Color(0xFFD32F2F)
        TripStopStatus.ARRIVED -> Color(0xFFF9A825)
        TripStopStatus.DEPARTED -> Color(0xFF388E3C)
        TripStopStatus.SKIPPED -> Color(0xFF9E9E9E)
    }

    val timeText = when {
        skipped -> "Parada omitida"
        departed -> "Salio ${stop.actualDepartureAt!!.toPeruTime()}"
        arrived -> "Llego ${stop.actualArrivalAt!!.toPeruTime()}"
        else -> "Hora aprox. ${stop.scheduledArrivalAt.toPeruTime()}"
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(24.dp)) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(semaphoreColor),
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.padding(bottom = if (isLast) 0.dp else 14.dp)) {
            Text(
                stop.stopName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (arrived || skipped) FontWeight.Medium else FontWeight.Normal,
                color = if (skipped) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (skipped) TextDecoration.LineThrough else null,
            )
            Text(
                timeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "  $text",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun statusLabel(status: ReservationStatus): String = when (status) {
    ReservationStatus.CONFIRMED -> "Confirmada"
    ReservationStatus.BOARDED -> "Abordada"
    ReservationStatus.COMPLETED -> "Completada"
    ReservationStatus.NO_SHOW -> "No se presentÃ³"
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



