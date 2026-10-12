package com.appmovilidadclinica.passenger.shared.ui.seatselection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.appmovilidadclinica.passenger.shared.domain.model.SeatAvailability
import com.appmovilidadclinica.passenger.shared.domain.model.TripSeat
import com.appmovilidadclinica.passenger.shared.ui.common.toSharedPeruDateTime

/**
 * Seleccion de asiento, comun a Android e iOS. Accesible: cada asiento tiene
 * descripcion propia (no depende del color) y los no disponibles se avisan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeatSelectionScreen(
    controller: SeatSelectionController,
    onBack: () -> Unit,
    onConfirmed: (reservationId: Long) -> Unit,
) {
    val state by controller.uiState.collectAsState()

    LaunchedEffect(controller) { controller.load() }
    DisposableEffect(controller) { onDispose { controller.dispose() } }

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
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            val confirmation = state.confirmed
            if (confirmation != null) {
                ConfirmationCard(confirmation)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        controller.consumeConfirmation()
                        onConfirmed(confirmation.reservationId)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text("Ver mi reserva")
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text("Volver a buscar viajes")
                }
                return@Column
            }

            if (state.loading) {
                CircularProgressIndicator()
                return@Column
            }

            val origin = state.origin
            val destination = state.destination
            if (origin != null && destination != null) {
                Text(
                    "${origin.stopName} → ${destination.stopName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Sale ${origin.scheduledDepartureAt.toSharedPeruDateTime()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().height(
                    // Altura fija calculada: la grilla va dentro de una columna
                    // con scroll, asi que no puede pedir "todo el alto".
                    ((((state.seats.size / 4) + 1).coerceAtLeast(1)) * 84).dp
                ),
            ) {
                items(state.seats, key = { it.tripSeatId }) { seat ->
                    SeatCell(
                        seat = seat,
                        selected = seat.tripSeatId == state.selectedSeatId,
                        onClick = { controller.selectSeat(seat.tripSeatId) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            SeatLegend()

            if (state.errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    state.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = controller::confirm,
                enabled = !state.confirming && state.selectedSeatId != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (state.confirming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Confirmar reserva", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ConfirmationCard(confirmation: SeatSelectionConfirmation) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Reserva confirmada",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text("Código: ${confirmation.reservationCode}", style = MaterialTheme.typography.bodyLarge)
            Text("Asiento: ${confirmation.seatLabel}", style = MaterialTheme.typography.bodyLarge)
            Text(
                "${confirmation.originName} → ${confirmation.destinationName}",
                style = MaterialTheme.typography.bodyLarge,
            )
            confirmation.departureAt?.let {
                Text("Sale ${it.toSharedPeruDateTime()}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Asiento cuadrado de 72dp. El estado NO se comunica solo con color: va con
 * icono y descripcion, para que se entienda tambien en escala de grises.
 */
@Composable
private fun SeatCell(
    seat: TripSeat,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val selectable = seat.isSelectable
    val background = when {
        selected -> MaterialTheme.colorScheme.primary
        selectable -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        selectable -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val description = when {
        selected -> "Asiento ${seat.seatLabel} seleccionado"
        seat.availability == SeatAvailability.AVAILABLE -> "Asiento ${seat.seatLabel} disponible"
        seat.availability == SeatAvailability.BLOCKED -> "Asiento ${seat.seatLabel} bloqueado"
        else -> "Asiento ${seat.seatLabel} ocupado en este tramo"
    }

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selectable || selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(12.dp),
            )
            .let { if (selectable) it.clickable(onClick = onClick) else it }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp), tint = contentColor)
            } else if (seat.availability == SeatAvailability.BLOCKED) {
                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = contentColor)
            }
            Text(
                seat.seatLabel,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SeatLegend() {
    Column {
        LegendRow(MaterialTheme.colorScheme.surface, "Disponible")
        LegendRow(MaterialTheme.colorScheme.primary, "Elegido")
        LegendRow(MaterialTheme.colorScheme.surfaceVariant, "Ocupado o bloqueado")
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(4.dp))
}
