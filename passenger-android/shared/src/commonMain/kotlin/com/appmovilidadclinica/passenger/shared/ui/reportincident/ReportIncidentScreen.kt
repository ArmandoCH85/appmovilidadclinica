package com.appmovilidadclinica.passenger.shared.ui.reportincident

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.appmovilidadclinica.passenger.shared.ui.common.toSharedPeruDateTime

/**
 * Reportar incidente sobre una reserva activa, comun a Android e iOS.
 * Se elige el viaje (si hay mas de uno), el tipo y se describe lo ocurrido
 * (minimo 10 caracteres).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportIncidentScreen(
    controller: ReportIncidentController,
    onBack: () -> Unit,
    onSent: () -> Unit,
) {
    val state by controller.uiState.collectAsState()
    var tripDropdownOpen by remember { mutableStateOf(false) }

    LaunchedEffect(controller) { controller.load() }
    DisposableEffect(controller) { onDispose { controller.dispose() } }

    LaunchedEffect(state.sent) {
        if (state.sent) {
            kotlinx.coroutines.delay(1_500)
            onSent()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reportar incidente") },
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.loadingReservations) {
                CircularProgressIndicator()
                return@Column
            }

            if (state.activeReservations.isEmpty()) {
                Text(
                    "No tienes viajes activos para reportar.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            // Viaje: si hay uno solo, ya viene elegido.
            val selected = state.activeReservations.find { it.reservationId == state.selectedReservationId }
            ExposedDropdownMenuBox(
                expanded = tripDropdownOpen,
                onExpandedChange = { tripDropdownOpen = it },
            ) {
                OutlinedTextField(
                    value = selected?.let { "${it.originName} → ${it.destinationName}" }.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Viaje") },
                    isError = state.reservationError != null,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tripDropdownOpen) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                )
                ExposedDropdownMenu(expanded = tripDropdownOpen, onDismissRequest = { tripDropdownOpen = false }) {
                    state.activeReservations.forEach { reservation ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "${reservation.originName} → ${reservation.destinationName} · " +
                                        reservation.originDepartureAt.toSharedPeruDateTime()
                                )
                            },
                            onClick = {
                                controller.onReservationSelected(reservation.reservationId)
                                tripDropdownOpen = false
                            },
                        )
                    }
                }
            }
            state.reservationError?.let { ErrorText(it) }

            Text(
                "Tipo de incidente",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            IncidentType.entries.forEach { type ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = state.incidentType == type,
                            onClick = { controller.onTypeChange(type) },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = state.incidentType == type,
                        onClick = { controller.onTypeChange(type) },
                    )
                    Text(type.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
            state.typeError?.let { ErrorText(it) }

            OutlinedTextField(
                value = state.description,
                onValueChange = controller::onDescriptionChange,
                label = { Text("Descripción") },
                minLines = 3,
                isError = state.descriptionError != null,
                supportingText = {
                    Text("${state.description.length}/1000")
                },
                modifier = Modifier.fillMaxWidth(),
            )
            state.descriptionError?.let { ErrorText(it) }

            state.formError?.let { ErrorText(it) }

            if (state.sent) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Reporte enviado con éxito",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = controller::submit,
                enabled = !state.submitting && !state.sent,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Enviar reporte", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}
