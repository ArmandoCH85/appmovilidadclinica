package com.appmovilidadclinica.passenger.shared.ui.reservations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.appmovilidadclinica.passenger.shared.domain.model.Reservation
import com.appmovilidadclinica.passenger.shared.domain.model.ReservationStatus
import com.appmovilidadclinica.passenger.shared.ui.common.toSharedPeruDateTime

/**
 * Mis reservas (lista), comun a Android e iOS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationsScreen(
    controller: ReservationsController,
    onOpenReservation: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val state by controller.uiState.collectAsState()

    LaunchedEffect(controller) { controller.load() }
    DisposableEffect(controller) { onDispose { controller.dispose() } }

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
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            if (state.loading) {
                CircularProgressIndicator()
                return@Column
            }

            if (state.errorMessage != null) {
                Text(
                    state.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(12.dp))
            }

            if (state.reservations.isEmpty()) {
                Text(
                    "Todavía no tiene reservas.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.reservations, key = { it.reservationId }) { reservation ->
                    ReservationCard(
                        reservation = reservation,
                        onClick = { onOpenReservation(reservation.reservationId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReservationCard(reservation: Reservation, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${reservation.originName} → ${reservation.destinationName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                StatusChip(reservation.status)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                reservation.originDepartureAt.toSharedPeruDateTime(),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Asiento ${reservation.seatLabel} · Código ${reservation.reservationCode}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Estado de la reserva: texto + color (nunca solo color). */
@Composable
fun StatusChip(status: ReservationStatus) {
    val (label, container, content) = when (status) {
        ReservationStatus.CONFIRMED -> Triple(
            "Confirmada",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )

        ReservationStatus.BOARDED -> Triple(
            "Abordada",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )

        ReservationStatus.COMPLETED -> Triple(
            "Completada",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ReservationStatus.NO_SHOW -> Triple(
            "No se presentó",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )

        ReservationStatus.CANCELLED -> Triple(
            "Cancelada",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Surface(color = container, shape = MaterialTheme.shapes.small) {
        Text(
            label,
            color = content,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
