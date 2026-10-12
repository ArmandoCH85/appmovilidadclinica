package com.appmovilidadclinica.passenger.shared.ui.reservations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.appmovilidadclinica.passenger.shared.domain.model.Reservation
import com.appmovilidadclinica.passenger.shared.domain.model.ReservationStatus
import com.appmovilidadclinica.passenger.shared.ui.common.toSharedPeruDateTime

/**
 * Detalle de una reserva, comun a Android e iOS. Permite cancelar (solo si
 * sigue activa) con una confirmacion previa.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationDetailScreen(
    controller: ReservationsController,
    reservationId: Long,
    onBack: () -> Unit,
) {
    val state by controller.uiState.collectAsState()
    var askCancel by remember { mutableStateOf(false) }

    LaunchedEffect(controller, reservationId) { controller.load() }

    val reservation = state.reservations.find { it.reservationId == reservationId }

    if (askCancel && reservation != null) {
        AlertDialog(
            onDismissRequest = { askCancel = false },
            title = { Text("Cancelar reserva") },
            text = {
                Text(
                    "¿Seguro que quiere cancelar la reserva del asiento " +
                        "${reservation.seatLabel} (${reservation.originName} → " +
                        "${reservation.destinationName})?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askCancel = false
                    controller.cancel(reservation.reservationId)
                }) { Text("Sí, cancelar") }
            },
            dismissButton = {
                TextButton(onClick = { askCancel = false }) { Text("No, volver") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle de la reserva") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
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
            if (reservation == null) {
                if (state.loading) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        "No se encontró la reserva.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${reservation.originName} → ${reservation.destinationName}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(reservation.status)
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DetailRow("Código", reservation.reservationCode)
                    DetailRow("Asiento", reservation.seatLabel)
                    DetailRow("Sale", reservation.originDepartureAt.toSharedPeruDateTime())
                    DetailRow("Reservada el", reservation.confirmedAt.toSharedPeruDateTime())
                    if (reservation.plate.isNotBlank()) {
                        DetailRow("Vehículo", reservation.plate)
                    }
                    if (reservation.qrToken == null) {
                        DetailRow("QR", "No disponible (reconfirme para verlo)")
                    }
                }
            }

            state.notice?.let { notice ->
                Text(
                    notice,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (notice.startsWith("Reserva cancelada")) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }

            Spacer(Modifier.height(4.dp))

            val activa = reservation.status == ReservationStatus.CONFIRMED ||
                reservation.status == ReservationStatus.BOARDED
            if (activa) {
                OutlinedButton(
                    onClick = { askCancel = true },
                    enabled = !state.cancelling,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text("Cancelar reserva")
                }
            }

            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text("Volver")
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
    Spacer(Modifier.height(8.dp))
}

/** Reserva de ejemplo para previews/tests de UI (no se usa en produccion). */
internal fun sampleReservation(): Reservation = Reservation(
    reservationId = 1L,
    reservationCode = "R-0001",
    qrToken = null,
    tripId = 55L,
    tripSeatId = 1L,
    originTripStopTimeId = 100L,
    destinationTripStopTimeId = 200L,
    status = ReservationStatus.CONFIRMED,
    confirmedAt = kotlinx.datetime.Instant.parse("2026-07-14T12:00:00Z"),
    routeName = "T-55",
    originName = "Sede Lima",
    destinationName = "Sede Surco",
    originDepartureAt = kotlinx.datetime.Instant.parse("2026-07-14T18:20:00Z"),
    seatLabel = "2",
    vehicleCode = "V-1",
    plate = "C0C-965",
)
