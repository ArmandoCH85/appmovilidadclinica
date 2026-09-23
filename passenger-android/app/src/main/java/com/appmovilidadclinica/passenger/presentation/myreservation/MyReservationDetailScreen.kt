package com.appmovilidadclinica.passenger.presentation.myreservation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.appmovilidadclinica.passenger.shared.domain.model.ExtensionStop
import com.appmovilidadclinica.passenger.shared.domain.model.ReservationStatus
import com.appmovilidadclinica.passenger.shared.domain.model.TripSeat
import com.appmovilidadclinica.passenger.shared.domain.model.TripStop
import com.appmovilidadclinica.passenger.shared.domain.model.TripStopStatus
import com.appmovilidadclinica.passenger.presentation.common.SeatCell
import com.appmovilidadclinica.passenger.presentation.common.toPeruDateTime
import com.appmovilidadclinica.passenger.presentation.common.toPeruTime
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyReservationDetailScreen(
    onBack: () -> Unit,
    viewModel: MyReservationDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val reservation = state.reservation
    var showExtensionSheet by remember { mutableStateOf(false) }

    // Polling del estado del viaje mientras la reserva está abordada.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(state.reservation?.status) {
        val status = state.reservation?.status
        if (status == ReservationStatus.CONFIRMED || status == ReservationStatus.BOARDED) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    viewModel.refreshJourney()
                    delay(20_000)
                }
            }
        }
    }

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

            // Oferta de extensión de viaje (un paradero antes del destino).
            if (state.canExtend) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            if (state.extension?.currentSeatFree == true) {
                                "Estás por llegar a tu destino. ¿Querés extender tu viaje?"
                            } else {
                                "Tu asiento ya se asignó a otra persona. Podés elegir otro para seguir viaje."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { showExtensionSheet = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Extender viaje")
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }

            // Botones de accion (solo si esta CONFIRMED).
            // El abordaje lo confirma unicamente el conductor desde su app, asi
            // que aqui ya no hay boton de self-checkin: solo cancelar.
            if (reservation.status == ReservationStatus.CONFIRMED) {
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

    val offer = state.extension
    if (showExtensionSheet && offer != null) {
        ExtensionBottomSheet(
            remainingStops = offer.remainingStops,
            seats = state.extensionSeats,
            loadingSeats = state.loadingExtensionSeats,
            extending = state.extending,
            errorMessage = state.extensionError,
            onStopSelected = viewModel::loadExtensionSeats,
            onConfirm = { tripStopTimeId, tripSeatId ->
                viewModel.extend(tripStopTimeId, tripSeatId)
                showExtensionSheet = false
            },
            onDismiss = {
                showExtensionSheet = false
                viewModel.dismissExtensionError()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtensionBottomSheet(
    remainingStops: List<ExtensionStop>,
    seats: List<TripSeat>,
    loadingSeats: Boolean,
    extending: Boolean,
    errorMessage: String?,
    onStopSelected: (Long) -> Unit,
    onConfirm: (Long, Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedStopId by remember { mutableStateOf(remainingStops.firstOrNull()?.tripStopTimeId) }
    var selectedSeatId by remember { mutableStateOf<Long?>(null) }
    var expanded by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "Extender viaje",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = remainingStops.find { it.tripStopTimeId == selectedStopId }?.stopName.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Nuevo destino") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    remainingStops.forEach { stop ->
                        DropdownMenuItem(
                            text = { Text(stop.stopName) },
                            onClick = {
                                selectedStopId = stop.tripStopTimeId
                                selectedSeatId = null
                                expanded = false
                                onStopSelected(stop.tripStopTimeId)
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (loadingSeats) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                ) {
                    items(seats, key = { it.tripSeatId }) { seat ->
                        SeatCell(
                            seat = seat,
                            selected = selectedSeatId == seat.tripSeatId,
                            onClick = { selectedSeatId = seat.tripSeatId },
                            modifier = Modifier.size(64.dp),
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val stopId = selectedStopId
                    if (stopId != null) onConfirm(stopId, selectedSeatId)
                },
                enabled = selectedStopId != null && !extending,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(if (extending) "Extendiendo…" else "Confirmar extensión")
            }
            Spacer(Modifier.height(16.dp))
        }
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
departed -> "Salió ${stop.actualDepartureAt!!.toPeruTime()}"
        arrived -> "Llegó ${stop.actualArrivalAt!!.toPeruTime()}"
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



