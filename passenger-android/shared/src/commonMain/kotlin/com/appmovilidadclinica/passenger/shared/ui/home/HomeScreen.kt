package com.appmovilidadclinica.passenger.shared.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.appmovilidadclinica.passenger.shared.data.local.StoredSession

/**
 * Pantalla autenticada de inicio. En Android es la que ya existia
 * (buscador + menu); aca vive la version multiplataforma: la accion principal
 * es buscar viajes y el resto se va habilitando por etapas.
 */
@Composable
fun HomeScreen(
    session: StoredSession,
    onSearchTrips: () -> Unit,
    onOpenReservations: () -> Unit,
    onChangePassword: () -> Unit,
    onReportIncident: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Movilidad clínica - cuidadores",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Sesión iniciada",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
                SessionRow("Nombre", session.fullName)
                SessionRow("Código", session.employeeCode)
                if (!session.department.isNullOrBlank()) {
                    SessionRow("Área", session.department.orEmpty())
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = onSearchTrips,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("Buscar viajes")
        }

        OutlinedButton(
            onClick = onOpenReservations,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("Mis reservas")
        }

        OutlinedButton(
            onClick = onChangePassword,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("Cambiar contraseña")
        }

        OutlinedButton(
            onClick = onReportIncident,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("Reportar incidencia")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("Cerrar sesión")
        }
    }
}

@Composable
private fun SessionRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
    Spacer(Modifier.height(8.dp))
}
