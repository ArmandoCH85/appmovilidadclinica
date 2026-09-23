package com.appmovilidadclinica.passenger.presentation.reportincident

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appmovilidadclinica.passenger.shared.domain.model.Reservation

// Etiquetas legibles para el enum del backend (spec §3: se reusa el enum).
private val INCIDENT_TYPE_LABELS = listOf(
    "DELAY" to "Retraso / el bus no pasó",
    "BREAKDOWN" to "Problemas con la unidad",
    "ACCIDENT" to "Accidente",
    "OTHER" to "Otro",
)

private val MintButton = Color(0xFFB2F2D5)
private val DarkGreenText = Color(0xFF0B3D2E)
private val FieldBorder = Color(0xFF4A4A4A)
private val SubtitleGray = Color(0xFF9E9E9E)
private val FieldShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportIncidentScreen(
    onBack: () -> Unit,
    viewModel: ReportIncidentViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activeReservations by viewModel.activeReservations.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Exito: toast (sobrevive al pop) y volver, igual que ChangePasswordScreen.
    LaunchedEffect(state.sent) {
        if (state.sent) {
            Toast.makeText(context, "Reporte enviado con éxito", Toast.LENGTH_SHORT).show()
            onBack()
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
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (activeReservations.isEmpty()) {
                Text(
                    "No tienes viajes activos para reportar.",
                    color = SubtitleGray,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                ReservationDropdown(
                    reservations = activeReservations,
                    selectedId = state.selectedReservationId,
                    onSelected = viewModel::onReservationSelected,
                    error = state.reservationError,
                )
            }

            Spacer(Modifier.height(12.dp))

            IncidentTypeDropdown(
                selectedCode = state.incidentType,
                onSelected = viewModel::onTypeChange,
                error = state.typeError,
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Descripción") },
                minLines = 4,
                isError = state.descriptionError != null,
                supportingText = {
                    when {
                        state.descriptionError != null ->
                            Text(
                                state.descriptionError.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                            )
                        else -> Text(
                            "${state.description.length}/1000 · Mínimo 10 caracteres.",
                            color = SubtitleGray,
                        )
                    }
                },
                shape = FieldShape,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = FieldBorder,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.formError != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    state.formError.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = viewModel::submit,
                enabled = !state.submitting && activeReservations.isNotEmpty(),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MintButton,
                    contentColor = DarkGreenText,
                    disabledContainerColor = MintButton.copy(alpha = 0.4f),
                    disabledContentColor = DarkGreenText.copy(alpha = 0.6f),
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = DarkGreenText,
                    )
                } else {
                    Text("Enviar reporte", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            TextButton(onClick = onBack) {
                Text("Cancelar")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReservationDropdown(
    reservations: List<Reservation>,
    selectedId: Long?,
    onSelected: (Long) -> Unit,
    error: String?,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = reservations.find { it.reservationId == selectedId }
        ?.let { "${it.originName} → ${it.destinationName} · Asiento ${it.seatLabel}" }
        .orEmpty()

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Viaje") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            isError = error != null,
            supportingText = {
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            },
            shape = FieldShape,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = FieldBorder,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            reservations.forEach { reservation ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "${reservation.originName} → " +
                                "${reservation.destinationName} · " +
                                "Asiento ${reservation.seatLabel}",
                        )
                    },
                    onClick = {
                        onSelected(reservation.reservationId)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IncidentTypeDropdown(
    selectedCode: String,
    onSelected: (String) -> Unit,
    error: String?,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = INCIDENT_TYPE_LABELS.find { it.first == selectedCode }?.second.orEmpty()

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Tipo de incidente") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            isError = error != null,
            supportingText = {
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            },
            shape = FieldShape,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = FieldBorder,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            INCIDENT_TYPE_LABELS.forEach { (code, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelected(code)
                        expanded = false
                    },
                )
            }
        }
    }
}
