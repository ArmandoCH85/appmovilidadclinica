package com.appmovilidadclinica.driver.shared.ui.screens.tripdetail

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import co.touchlab.kermit.Logger
import com.appmovilidadclinica.driver.shared.domain.model.DriverTrip
import com.appmovilidadclinica.driver.shared.domain.model.Passenger
import com.appmovilidadclinica.driver.shared.domain.model.ReservationStatus
import com.appmovilidadclinica.driver.shared.domain.model.TripStatus
import com.appmovilidadclinica.driver.shared.domain.model.TripStop
import com.appmovilidadclinica.driver.shared.domain.model.TripStopStatus
import com.appmovilidadclinica.driver.shared.domain.repository.DriverRepository
import com.appmovilidadclinica.driver.shared.platform.LocationService
import com.appmovilidadclinica.driver.shared.platform.NotificationService
import com.appmovilidadclinica.driver.shared.trip.TripLocationController
import com.appmovilidadclinica.driver.shared.ui.common.color
import com.appmovilidadclinica.driver.shared.ui.common.label
import com.appmovilidadclinica.driver.shared.ui.common.toPeruTime
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    tripId: Long,
    initialTrip: DriverTrip? = null,
    driverRepository: DriverRepository = koinInject(),
    notificationService: NotificationService = koinInject(),
    locationService: LocationService = koinInject(),
    tripLocationController: TripLocationController = koinInject(),
    onBack: () -> Unit = {},
    onScanQr: (Long) -> Unit = {},
    onReportIncident: (Long) -> Unit = {},
) {
    val viewModel = remember(tripId, initialTrip, driverRepository) {
        TripDetailViewModel(tripId, initialTrip, driverRepository)
    }
    DisposableEffect(viewModel) { onDispose { viewModel.dispose() } }

    val state by viewModel.uiState.collectAsState()
    var arrivalConfirmId by remember { mutableStateOf<Long?>(null) }
    var passengersExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }

    // GPS tracking en primer plano (Flow) cuando IN_PROGRESS.
    LaunchedEffect(state.trip?.status, locationService.isAvailable()) {
        val shouldTrack = state.trip?.status == TripStatus.IN_PROGRESS &&
            locationService.isAvailable()
        if (shouldTrack) {
            Logger.withTag("GpsTracking").i { "start observing location" }
            locationService.observeLocation().collect { coords ->
                Logger.withTag("GpsTracking").i {
                    "(${coords.latitude}, ${coords.longitude}) ±${coords.accuracyMeters}m"
                }
            }
        }
    }

    // GPS tracking en background via Foreground Service (Android) — el
    // service sigue emitiendo aunque la app este minimizada. iOS no-op.
    LaunchedEffect(state.trip?.status) {
        when (state.trip?.status) {
            TripStatus.IN_PROGRESS -> tripLocationController.startTracking()
            TripStatus.COMPLETED, TripStatus.CANCELLED -> tripLocationController.stopTracking()
            else -> Unit
        }
    }

    // Side-effect multiplatform: notificar cuando el trip cambia a IN_PROGRESS
    // o COMPLETED. En Android dispara NotificationCompat; en iOS el stub loguea.
    LaunchedEffect(state.trip?.status) {
        when (state.trip?.status) {
            TripStatus.IN_PROGRESS -> notificationService.notifyTripStarted(tripId.toString())
            TripStatus.COMPLETED -> notificationService.notifyTripStarted(tripId.toString())
            else -> Unit
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
                title = { Text("Detalle del viaje") },
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    trip.routeName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(50),
                                ) {
                                    Text(
                                        trip.direction.label(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                            }
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
                                    "  Vehículo ${trip.plate}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }

                if (trip.status == TripStatus.PUBLISHED || trip.status == TripStatus.BOARDING) {
                    item {
                        Button(
                            onClick = viewModel::startTrip,
                            enabled = state.pendingActionId != tripId,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.pendingActionId == tripId) "Iniciando…" else "Iniciar viaje")
                        }
                    }
                } else if (trip.status == TripStatus.IN_PROGRESS) {
                    item {
                        Button(
                            onClick = viewModel::completeTrip,
                            enabled = state.pendingActionId != tripId,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.pendingActionId == tripId) "Finalizando…" else "Finalizar viaje")
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clickable { passengersExpanded = !passengersExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Pasajeros (${state.passengers.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Icon(
                        if (passengersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (passengersExpanded) "Contraer pasajeros" else "Expandir pasajeros",
                    )
                }
            }

            if (passengersExpanded) {
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
                        tripInProgress = state.trip?.status == TripStatus.IN_PROGRESS,
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
private fun StopRow(
    stop: TripStop,
    pending: Boolean,
    tripInProgress: Boolean,
    onMarkArrival: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                    Button(onClick = onMarkArrival, enabled = !pending && tripInProgress) {
                        Text(if (pending) "…" else "Marcar llegada")
                    }
                }
            }
            if (stop.status == TripStopStatus.PENDING && !tripInProgress) {
                Text(
                    "Iniciá el viaje para marcar llegadas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
