package com.appmovilidadclinica.passenger.presentation.tripsearch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appmovilidadclinica.passenger.domain.model.BookingState
import com.appmovilidadclinica.passenger.domain.model.Stop
import com.appmovilidadclinica.passenger.domain.model.TripDirection
import com.appmovilidadclinica.passenger.domain.model.TripSearchResult
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.direction == TripDirection.IDA,
                    onClick = { viewModel.onDirectionChange(TripDirection.IDA) },
                    label = { Text("Ida") },
                )
                FilterChip(
                    selected = state.direction == TripDirection.VUELTA,
                    onClick = { viewModel.onDirectionChange(TripDirection.VUELTA) },
                    label = { Text("Vuelta") },
                )
            }

            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 12.dp))

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

    OutlinedTextField(
        value = date.toString(),
        onValueChange = {},
        readOnly = true,
        label = { Text("Fecha") },
        trailingIcon = {
            Icon(Icons.Default.DateRange, contentDescription = "Seleccionar fecha")
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true },
    )

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
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("${trip.routeName} (${trip.routeCode})", style = MaterialTheme.typography.titleMedium)
            Text("${trip.originName} → ${trip.destinationName}", style = MaterialTheme.typography.bodyMedium)
            Text("Sale ${trip.originDepartureAt} · Llega ${trip.destinationArrivalAt}")
            Text("Vehículo ${trip.vehicleCode} · ${trip.plate}")
            Text("${trip.availableSeats} asientos disponibles")
            if (!enabled) {
                Text(
                    text = when (trip.bookingState) {
                        BookingState.NOT_OPEN -> "Reserva aún no abierta"
                        BookingState.CLOSED -> "Reserva cerrada"
                        else -> ""
                    },
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
