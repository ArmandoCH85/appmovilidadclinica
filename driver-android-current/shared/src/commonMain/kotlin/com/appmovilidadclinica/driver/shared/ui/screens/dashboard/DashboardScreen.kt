package com.appmovilidadclinica.driver.shared.ui.screens.dashboard

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.appmovilidadclinica.driver.shared.domain.model.DriverTrip
import com.appmovilidadclinica.driver.shared.domain.model.TripStatus
import com.appmovilidadclinica.driver.shared.domain.repository.DriverRepository
import com.appmovilidadclinica.driver.shared.ui.common.color
import com.appmovilidadclinica.driver.shared.ui.common.label
import com.appmovilidadclinica.driver.shared.ui.common.toPeruTime
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.koin.compose.koinInject

private val SPANISH_WEEKDAYS = listOf(
    "lun" to DayOfWeek.MONDAY,
    "mar" to DayOfWeek.TUESDAY,
    "mié" to DayOfWeek.WEDNESDAY,
    "jue" to DayOfWeek.THURSDAY,
    "vie" to DayOfWeek.FRIDAY,
    "sáb" to DayOfWeek.SATURDAY,
    "dom" to DayOfWeek.SUNDAY,
)

private val SPANISH_MONTHS = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

private fun formatShortDateEs(date: LocalDate): String {
    val weekday = SPANISH_WEEKDAYS.first { it.second == date.dayOfWeek }.first
    val month = SPANISH_MONTHS[date.monthNumber - 1]
    return "$weekday ${date.dayOfMonth} de $month"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    driverRepository: DriverRepository = koinInject(),
    onTripSelected: (DriverTrip) -> Unit = {},
    onOpenProfile: () -> Unit = {},
) {
    val viewModel = remember(driverRepository) { DashboardViewModel(driverRepository) }
    DisposableEffect(viewModel) { onDispose { viewModel.dispose() } }

    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Viajes del día") },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                    }
                    IconButton(onClick = onOpenProfile) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Perfil")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            DateSelector(
                date = state.date,
                onPreviousDay = viewModel::onPreviousDay,
                onNextDay = viewModel::onNextDay,
            )

            when {
                state.loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(48.dp))
                        CircularProgressIndicator()
                    }
                }

                state.errorMessage != null -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            state.errorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                state.trips.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "No tiene viajes asignados para hoy.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        items(state.trips, key = { it.id }) { trip ->
                            TripCard(
                                trip = trip,
                                onClick = {
                                    viewModel.onTripSelected(trip)
                                    onTripSelected(trip)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateSelector(
    date: LocalDate,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = onPreviousDay) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Día anterior")
        }
        Text(
            formatShortDateEs(date).replaceFirstChar { it.titlecaseChar() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        IconButton(onClick = onNextDay) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Día siguiente")
        }
    }
}

@Composable
private fun TripCard(trip: DriverTrip, onClick: () -> Unit) {
    val enabled = trip.status != TripStatus.CANCELLED
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Hora de salida prominente: es lo que el conductor usa para
            // reconocer de un vistazo cuál viaje le toca.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        trip.scheduledStartAt.toPeruTime(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "→ ${trip.scheduledEndAt.toPeruTime()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    trip.status.label(),
                    style = MaterialTheme.typography.labelMedium,
                    color = trip.status.color(),
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                trip.routeName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(10.dp))

            InfoRow(
                icon = Icons.Default.DirectionsBus,
                text = "Vehículo ${trip.plate}",
            )

            Spacer(Modifier.height(4.dp))

            InfoRow(
                icon = Icons.Default.EventSeat,
                text = "Capacidad ${trip.seatCapacity} asientos",
            )

            if (!enabled) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Viaje cancelado",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "  $text",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
