package com.appmovilidadclinica.driver.presentation.tripdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.appmovilidadclinica.driver.di.AppModule
import com.appmovilidadclinica.driver.domain.model.Passenger
import com.appmovilidadclinica.driver.domain.model.ReservationStatus
import com.appmovilidadclinica.driver.domain.model.TripStop
import com.appmovilidadclinica.driver.domain.model.TripStopStatus
import com.appmovilidadclinica.driver.presentation.common.color
import com.appmovilidadclinica.driver.presentation.common.label
import com.appmovilidadclinica.driver.presentation.common.toPeruTime
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    tripId: Long,
    onBack: () -> Unit,
    onScanQr: (Long) -> Unit,
    onReportIncident: (Long) -> Unit,
    viewModel: TripDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer { TripDetailViewModel(tripId, AppModule.provideDriverRepository()) }
        },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var arrivalConfirmId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.load()
        }
    }

    LaunchedEffect(state.toastMessage) {
        if (state.toastMessage != null) {
            delay(2500)
            viewModel.dismissToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.trip?.tripCode ?: "Detalle del viaje") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading && state.passengers.isEmpty() && state.stops.isEmpty()) {
            Column(
                modifier = Modifier.padding(padding).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(48.dp))
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.trip?.let { trip ->
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                trip.routeName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "  ${trip.scheduledStartAt.toPeruTime()} – ${trip.scheduledEndAt.toPeruTime()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.DirectionsBus,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "  ${trip.vehicleCode} · ${trip.plate}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }

            if (state.toastMessage != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            state.toastMessage.orEmpty(),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            if (state.errorMessage != null) {
                item {
                    Text(
                        state.errorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { onScanQr(tripId) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Escanear QR")
                    }
                    OutlinedButton(
                        onClick = { onReportIncident(tripId) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Report, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Incidencia")
                    }
                }
            }

            item {
                Text(
                    "Pasajeros",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (state.passengers.isEmpty()) {
                item {
                    Text(
                        "No hay pasajeros en este viaje.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.passengers, key = { "p_${it.reservationId}" }) { passenger ->
                    PassengerCard(
                        passenger = passenger,
                        pending = state.pendingActionId == passenger.reservationId,
                        onBoard = { viewModel.board(passenger.reservationId) },
                        onNoShow = { viewModel.noShow(passenger.reservationId) },
                        onAlight = { viewModel.alight(passenger.reservationId) },
                    )
                }
            }

            item {
                Text(
                    "Paradas",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (state.stopsErrorMessage != null) {
                item {
                    Text(
                        "No se pudo cargar el cronograma de paradas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (state.stops.isEmpty()) {
                item {
                    Text(
                        "Este viaje no tiene paradas configuradas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.stops, key = { "s_${it.id}" }) { stop ->
                    StopRow(
                        stop = stop,
                        pending = state.pendingActionId == stop.id,
                        onMarkArrival = { arrivalConfirmId = stop.id },
                    )
                }
            }
        }
    }

    if (arrivalConfirmId != null) {
        AlertDialog(
            onDismissRequest = { arrivalConfirmId = null },
            title = { Text("Marcar llegada") },
            text = { Text("¿Confirma la llegada a esta parada?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.markArrival(arrivalConfirmId!!)
                    arrivalConfirmId = null
                }) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { arrivalConfirmId = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun PassengerCard(
    passenger: Passenger,
    pending: Boolean,
    onBoard: () -> Unit,
    onNoShow: () -> Unit,
    onAlight: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    passenger.workerFullName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    passenger.status.label(),
                    style = MaterialTheme.typography.labelMedium,
                    color = passenger.status.color(),
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                "Asiento ${passenger.seatLabel} · ${passenger.originStopName} → ${passenger.destinationStopName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (passenger.status == ReservationStatus.CONFIRMED || passenger.status == ReservationStatus.BOARDED) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (passenger.status) {
                        ReservationStatus.CONFIRMED -> {
                            Button(onClick = onBoard, enabled = !pending) {
                                Text(if (pending) "…" else "Abordar")
                            }
                            OutlinedButton(onClick = onNoShow, enabled = !pending) {
                                Text("No presentado")
                            }
                        }
                        ReservationStatus.BOARDED -> {
                            OutlinedButton(onClick = onAlight, enabled = !pending) {
                                Text(if (pending) "…" else "Bajar")
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun StopRow(stop: TripStop, pending: Boolean, onMarkArrival: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${stop.stopOrder}. ${stop.stopName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                val timeText = stop.actualArrivalAt?.toPeruTime()
                    ?: stop.scheduledArrivalAt?.toPeruTime()
                    ?: "—"
                Text(
                    "${stop.status.label()} · $timeText",
                    style = MaterialTheme.typography.bodySmall,
                    color = stop.status.color(),
                )
            }
            if (stop.status == TripStopStatus.PENDING) {
                Button(onClick = onMarkArrival, enabled = !pending) {
                    Text(if (pending) "…" else "Marcar llegada")
                }
            }
        }
    }
}
