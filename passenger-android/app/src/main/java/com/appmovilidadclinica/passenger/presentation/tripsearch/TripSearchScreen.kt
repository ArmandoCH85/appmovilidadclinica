package com.appmovilidadclinica.passenger.presentation.tripsearch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appmovilidadclinica.passenger.domain.model.BookingState
import com.appmovilidadclinica.passenger.domain.model.Stop
import com.appmovilidadclinica.passenger.domain.model.TripSearchResult
import com.appmovilidadclinica.passenger.presentation.common.toPeruTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripSearchScreen(
    onTripSelected: (tripId: Long, originStopId: Long, destinationStopId: Long) -> Unit,
    onOpenReservations: () -> Unit,
    onLogout: () -> Unit,
    viewModel: TripSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Buscar viaje") },
                actions = {
                    TextButton(onClick = onOpenReservations) { Text("Mis reservas") }
                    TextButton(onClick = onLogout) { Text("Salir") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            if (state.loadingStops) {
                CircularProgressIndicator()
            } else {
                StopDropdown(
                    label = "Origen",
                    stops = state.stops,
                    selectedId = state.originStopId,
                    onSelected = viewModel::onOriginChange,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))
                StopDropdown(
                    label = "Destino",
                    stops = state.stops,
                    selectedId = state.destinationStopId,
                    onSelected = viewModel::onDestinationChange,
                )
            }

            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 12.dp))

            DatePickerField(
                date = state.date,
                onDateChange = viewModel::onDateChange,
            )

            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 12.dp))

            Button(onClick = viewModel::search, enabled = !state.searching, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.searching) "Buscando…" else "Buscar viajes")
            }

            if (state.errorMessage != null) {
                Text(
                    state.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 16.dp))

            if (state.hasSearched && state.results.isEmpty() && !state.searching) {
                Text("No hay viajes para esa búsqueda.", style = MaterialTheme.typography.bodyMedium)
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(state.results, key = { it.tripId }) { trip ->
                    TripResultCard(
                        trip = trip,
                        onClick = {
                            if (trip.bookingState == BookingState.OPEN) {
                                onTripSelected(trip.tripId, state.originStopId!!, state.destinationStopId!!)
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier
        .fillMaxWidth()
        .clickable { showDialog = true }
    ) {
        OutlinedTextField(
            value = date.toString(),
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text("Fecha") },
            trailingIcon = {
                Icon(Icons.Default.DateRange, contentDescription = "Seleccionar fecha")
            },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = LocalContentColor.current,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }

    if (showDialog) {
        // El DatePicker trabaja con milisegundos UTC a medianoche. Convertimos
        // LocalDate <-> Long siempre via ZoneOffset.UTC para evitar off-by-one
        // cuando el dispositivo esta en una zona horaria alejada de UTC.
        val initialMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onDateChange(picked)
                    }
                    showDialog = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancelar") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StopDropdown(
    label: String,
    stops: List<Stop>,
    selectedId: Long?,
    onSelected: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = stops.find { it.id == selectedId }?.name ?: ""

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            stops.forEach { stop ->
                DropdownMenuItem(
                    text = { Text(stop.name) },
                    onClick = {
                        onSelected(stop.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun TripResultCard(trip: TripSearchResult, onClick: () -> Unit) {
    val enabled = trip.bookingState == BookingState.OPEN
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Origen → Destino como titulo principal
            Text(
                "${trip.originName} → ${trip.destinationName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(12.dp))

            // Hora de salida
            TripInfoRow(
                icon = Icons.Default.Schedule,
                label = "Sale",
                value = trip.originDepartureAt.toPeruTime(),
            )

            Spacer(Modifier.height(6.dp))

            // Hora de llegada
            TripInfoRow(
                icon = Icons.Default.Flag,
                label = "Llega",
                value = trip.destinationArrivalAt.toPeruTime(),
            )

            Spacer(Modifier.height(10.dp))

            // Asientos disponibles
            TripInfoRow(
                icon = Icons.Default.EventSeat,
                label = "",
                value = "${trip.availableSeats} asientos disponibles",
                valueColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(6.dp))

            // Vehiculo y placa
            TripInfoRow(
                icon = Icons.Default.DirectionsBus,
                label = "Vehículo",
                value = "${trip.vehicleCode} · ${trip.plate}",
            )

            if (!enabled) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when (trip.bookingState) {
                        BookingState.NOT_OPEN -> "Reserva aún no abierta"
                        BookingState.CLOSED -> "Reserva cerrada"
                        else -> ""
                    },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun TripInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (label.isNotEmpty()) {
            Text(
                "$label  ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
        )
    }
}
