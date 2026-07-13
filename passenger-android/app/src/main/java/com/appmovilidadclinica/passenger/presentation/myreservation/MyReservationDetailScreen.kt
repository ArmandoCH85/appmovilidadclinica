package com.appmovilidadclinica.passenger.presentation.myreservation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appmovilidadclinica.passenger.domain.model.ReservationStatus
import com.appmovilidadclinica.passenger.presentation.common.toPeruDateTime

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
        Column(
            modifier = Modifier.padding(padding).padding(16.dp),
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
            Text(
                statusLabel(reservation.status),
                style = MaterialTheme.typography.labelMedium,
                color = statusColor(reservation.status),
            )

            Spacer(Modifier.height(20.dp))

            // QR o placeholder
            if (state.qrBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = state.qrBitmap!!.asImageBitmap(),
                    contentDescription = "Código QR de la reserva",
                    modifier = Modifier.size(220.dp),
                )
            } else {
                Text(
                    "QR no disponible. Cancele y reconfirme para regenerar el código.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(20.dp))

            // Card con info del viaje
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Ruta
                    Text(
                        "${reservation.originName} → ${reservation.destinationName}",
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
                            text = "Vehículo: ${reservation.plate}",
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Botones de accion (solo si esta CONFIRMED)
            if (reservation.status == ReservationStatus.CONFIRMED) {
                Button(
                    onClick = viewModel::selfCheckin,
                    enabled = viewModel.canSelfCheckin && !state.checkingIn,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Text(if (state.checkingIn) "Confirmando…" else "Confirmar abordaje")
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
                    Text(if (state.cancelling) "Cancelando…" else "Cancelar reserva")
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

    // Dialog de confirmacion de cancel
    if (state.showCancelConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCancel,
            title = { Text("Cancelar reserva") },
            text = { Text("¿Confirma que desea cancelar esta reserva? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmCancel) { Text("Sí, cancelar") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCancel) { Text("Volver") }
            },
        )
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
    ReservationStatus.NO_SHOW -> "No se presentó"
    ReservationStatus.CANCELLED -> "Cancelada"
}

@Composable
private fun statusColor(status: ReservationStatus) = when (status) {
    ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW -> MaterialTheme.colorScheme.error
    ReservationStatus.COMPLETED, ReservationStatus.BOARDED -> MaterialTheme.colorScheme.primary
    ReservationStatus.CONFIRMED -> MaterialTheme.colorScheme.onSurface
}