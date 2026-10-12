package com.appmovilidadclinica.passenger.shared.ui.tripsearch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.appmovilidadclinica.passenger.shared.domain.model.BookingState
import com.appmovilidadclinica.passenger.shared.domain.model.Stop
import com.appmovilidadclinica.passenger.shared.domain.model.TripSearchResult
import com.appmovilidadclinica.passenger.shared.ui.common.toSharedPeruTime
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * Busqueda de viajes, comun a Android e iOS. Es el mismo diseño y los mismos
 * textos que la pantalla que ya existia en el modulo Android.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripSearchScreen(
    controller: TripSearchController,
    onTripSelected: (tripId: Long, originStopId: Long, destinationStopId: Long) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val state by controller.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }

    // Carga el catalogo de paradas la primera vez que se entra.
    LaunchedEffect(controller) { controller.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Buscar viaje") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    VerticalDivider(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .height(24.dp),
                    )
                    TextButton(onClick = onLogout) { Text("Salir") }
                    Spacer(Modifier.size(4.dp))
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.loadingStops) {
                CircularProgressIndicator()
            } else {
                StopDropdown(
                    label = "Origen",
                    stops = state.stops,
                    selectedId = state.originStopId,
                    onSelected = controller::onOriginChange,
                )

                Spacer(Modifier.height(8.dp))

                StopDropdown(
                    label = "Destino",
                    stops = controller.destinationOptions(),
                    selectedId = state.destinationStopId,
                    onSelected = controller::onDestinationChange,
                )
            }

            Spacer(Modifier.height(12.dp))

            DateField(
                date = state.date,
                onClick = { showDatePicker = true },
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = controller::search,
                enabled = !state.searching,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.searching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Buscar viajes", style = MaterialTheme.typography.labelLarge)
                }
            }

            if (state.errorMessage != null) {
                Text(
                    state.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            if (state.hasSearched && state.results.isEmpty() && !state.searching) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Outlined.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No hay viajes para esa búsqueda",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Pruebe con otra fecha u otro origen/destino.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.results.forEach { trip ->
                TripResultCard(
                    trip = trip,
                    onClick = {
                        val originId = state.originStopId
                        val destinationId = state.destinationStopId
                        if (trip.bookingState == BookingState.OPEN && originId != null && destinationId != null) {
                            onTripSelected(trip.tripId, originId, destinationId)
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showDatePicker) {
        DatePickerSheet(
            date = state.date,
            onDismiss = { showDatePicker = false },
            onDateSelected = {
                controller.onDateChange(it)
                showDatePicker = false
            },
        )
    }
}

/**
 * Campo de fecha (solo lectura) que abre el selector. El `DatePicker` trabaja
 * con milisegundos UTC a medianoche: se convierte siempre por UTC para evitar
 * el corrimiento de un dia.
 */
@Composable
private fun DateField(date: LocalDate, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(
    date: LocalDate,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
) {
    val initialMillis = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { millis ->
                    onDateSelected(
                        Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
                    )
                }
            }) { Text("Aceptar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    ) {
        DatePicker(state = pickerState)
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
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "${trip.originName} → ${trip.destinationName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(12.dp))

            InfoRow(Icons.Default.Schedule, "Sale", trip.originDepartureAt.toSharedPeruTime())

            Spacer(Modifier.height(6.dp))

            InfoRow(Icons.Default.Flag, "Llega", trip.destinationArrivalAt.toSharedPeruTime())

            Spacer(Modifier.height(10.dp))

            InfoRow(
                icon = Icons.Default.EventSeat,
                label = "",
                value = "${trip.availableSeats} asientos disponibles",
                valueColor = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            Spacer(Modifier.height(6.dp))

            InfoRow(Icons.Default.DirectionsBus, "Vehículo", trip.plate)

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
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
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
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}
