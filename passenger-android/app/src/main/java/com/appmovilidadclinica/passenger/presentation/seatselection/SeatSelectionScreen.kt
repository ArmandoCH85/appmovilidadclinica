package com.appmovilidadclinica.passenger.presentation.seatselection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appmovilidadclinica.passenger.presentation.common.SeatCell

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeatSelectionScreen(
    onReservationConfirmed: (reservationId: Long) -> Unit,
    onBack: () -> Unit,
    viewModel: SeatSelectionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.confirmedReservationId) {
        state.confirmedReservationId?.let(onReservationConfirmed)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Selección de asiento") },
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

            state.origin?.let { origin ->
                state.destination?.let { destination ->
                    Text(
                        "${origin.stopName} → ${destination.stopName}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                items(state.seats, key = { it.tripSeatId }) { seat ->
                    SeatCell(
                        seat = seat,
                        selected = seat.tripSeatId == state.selectedSeatId,
                        onClick = { if (seat.isSelectable) viewModel.selectSeat(seat.tripSeatId) },
                    )
                }
            }

            if (state.errorMessage != null) {
                Text(
                    state.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Button(
                onClick = viewModel::confirm,
                enabled = state.selectedSeatId != null && !state.confirming,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Text(if (state.confirming) "Confirmando…" else "Confirmar reserva")
            }
        }
    }
}
