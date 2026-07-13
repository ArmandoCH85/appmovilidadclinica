package com.appmovilidadclinica.driver.presentation.incident

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.appmovilidadclinica.driver.di.AppModule
import com.appmovilidadclinica.driver.domain.model.IncidentType
import com.appmovilidadclinica.driver.presentation.common.icon
import com.appmovilidadclinica.driver.presentation.common.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentScreen(
    tripId: Long,
    onBack: () -> Unit,
    viewModel: IncidentViewModel = viewModel(
        factory = viewModelFactory {
            initializer { IncidentViewModel(tripId, AppModule.provideDriverRepository()) }
        },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.submitted) {
        if (state.submitted) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reportar incidencia") },
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
            Text(
                "Tipo de incidencia",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))

            IncidentType.entries.forEach { type ->
                IncidentTypeOption(
                    type = type,
                    selected = state.incidentType == type,
                    onClick = { viewModel.onTypeSelected(type) },
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Descripción") },
                placeholder = { Text("Describa la incidencia (máx. ${INCIDENT_DESCRIPTION_MAX_LENGTH} caracteres)") },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${state.description.length} / $INCIDENT_DESCRIPTION_MAX_LENGTH",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )

            if (state.errorMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    state.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = viewModel::askSubmit,
                enabled = !state.submitting,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Enviar reporte")
                }
            }
        }
    }

    if (state.showConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirm,
            title = { Text("Reportar incidencia") },
            text = { Text("¿Confirma el envío de este reporte de incidencia?") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmSubmit) { Text("Sí, enviar") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConfirm) { Text("Volver") }
            },
        )
    }
}

@Composable
private fun IncidentTypeOption(type: IncidentType, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                type.icon(),
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                type.label(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}
