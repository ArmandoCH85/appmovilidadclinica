package com.appmovilidadclinica.passenger.presentation.myreservation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appmovilidadclinica.passenger.domain.model.Reservation
import com.appmovilidadclinica.passenger.domain.model.ReservationStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyReservationsScreen(
    onReservationSelected: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: MyReservationsViewModel = hiltViewModel(),
) {
    val reservations by viewModel.reservations.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Mis reservas") }) }) { padding ->
        if (reservations.isEmpty()) {
            Column(modifier = Modifier.padding(padding).padding(16.dp)) {
                Text("Todavía no tiene reservas.")
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(reservations, key = { it.reservationId }) { reservation ->
                ReservationCard(reservation, onClick = { onReservationSelected(reservation.reservationId) })
            }
        }
    }
}

@Composable
private fun ReservationCard(reservation: Reservation, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(reservation.routeName, style = MaterialTheme.typography.titleMedium)
            Text("${reservation.originName} → ${reservation.destinationName}")
            Text("Asiento ${reservation.seatLabel} · ${reservation.originDepartureAt}")
            Text(statusLabel(reservation.status), color = statusColor(reservation.status))
        }
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
