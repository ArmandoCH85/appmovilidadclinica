package com.appmovilidadclinica.driver.shared.ui.screens.seatmap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.appmovilidadclinica.driver.shared.domain.model.SeatAvailability
import com.appmovilidadclinica.driver.shared.domain.repository.DriverRepository
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeatMapScreen(
    tripId: Long,
    driverRepository: DriverRepository = koinInject(),
    onBack: () -> Unit = {},
    onRegistered: () -> Unit = {},
) {
    val viewModel = remember(tripId, driverRepository) {
        SeatMapViewModel(tripId, driverRepository)
    }
    DisposableEffect(viewModel) { onDispose { viewModel.dispose() } }

    val state by viewModel.uiState.collectAsState()
    var stopPickerFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(state.registered) {
        if (state.registered) onRegistered()
    }

    val originStop = state.stops.firstOrNull { it.id == state.originStopId }
    val destinationStop = state.stops.firstOrNull { it.id == state.destinationStopId }
    val validLeg = originStop != null && destinationStop != null &&
        originStop.stopOrder < destinationStop.stopOrder

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ocupar asiento") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loadingStops) {
            Column(
                modifier = Modifier.padding(padding).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(48.dp))
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(),
        ) {
            Text(
                "Tramo",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { stopPickerFor = "origin" },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Origen: ${originStop?.stopName ?: "—"}")
                }
                OutlinedButton(
                    onClick = { stopPickerFor = "destination" },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Destino: ${destinationStop?.stopName ?: "—"}")
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Asientos",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))

            when {
                state.loadingSeats -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                !validLeg -> {
                    Text(
                        "Seleccione un tramo válido (el destino debe ser posterior al origen).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
                state.seats.isEmpty() -> {
                    Text(
                        "No hay asientos para este tramo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.seats, key = { it.tripSeatId }) { seat ->
                            SeatCell(
                                seat = seat,
                                selected = state.selectedSeatId == seat.tripSeatId,
                                onClick = { viewModel.onSeatSelected(seat) },
                            )
                        }
                    }
                }
            }

            if (state.errorMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    state.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    if (stopPickerFor != null) {
        AlertDialog(
            onDismissRequest = { stopPickerFor = null },
            title = { Text(if (stopPickerFor == "origin") "Parada de subida" else "Parada de bajada") },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    items(state.stops, key = { it.id }) { stop ->
                        TextButton(
                            onClick = {
                                if (stopPickerFor == "origin") {
                                    viewModel.onOriginSelected(stop.id)
                                } else {
                                    viewModel.onDestinationSelected(stop.id)
                                }
                                stopPickerFor = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "${stop.stopOrder}. ${stop.stopName}",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { stopPickerFor = null }) { Text("Cancelar") }
            },
        )
    }

    if (state.showGuestDialog) {
        val selectedSeat = state.seats.firstOrNull { it.tripSeatId == state.selectedSeatId }
        AlertDialog(
            onDismissRequest = { if (!state.submitting) viewModel.dismissGuestDialog() },
            title = { Text("Pasajero sin app") },
            text = {
                Column {
                    if (selectedSeat != null) {
                        Text(
                            "Asiento ${selectedSeat.seatLabel} · " +
                                "${originStop?.stopName.orEmpty()} → " +
                                destinationStop?.stopName.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = state.firstName,
                        onValueChange = viewModel::onFirstNameChange,
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.lastName,
                        onValueChange = viewModel::onLastNameChange,
                        label = { Text("Apellido") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.fieldError != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.fieldError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (state.errorMessage != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.errorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::submit, enabled = !state.submitting) {
                    if (state.submitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Confirmar")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::dismissGuestDialog,
                    enabled = !state.submitting,
                ) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun SeatCell(seat: SeatAvailability, selected: Boolean, onClick: () -> Unit) {
    when {
        selected -> {
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(seat.seatLabel, style = MaterialTheme.typography.bodyMedium)
            }
        }
        seat.isAvailable -> {
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(seat.seatLabel, style = MaterialTheme.typography.bodyMedium)
            }
        }
        else -> {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        seat.seatLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
