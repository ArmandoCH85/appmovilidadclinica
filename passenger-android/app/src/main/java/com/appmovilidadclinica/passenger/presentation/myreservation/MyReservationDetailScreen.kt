package com.appmovilidadclinica.passenger.presentation.myreservation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appmovilidadclinica.passenger.domain.model.ReservationStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyReservationDetailScreen(
    onBack: () -> Unit,
    viewModel: MyReservationDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val reservation = state.reservation

    Scaffold(topBar = { TopAppBar(title = { Text("Mi reserva") }) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (reservation == null) {
                Text("Cargando…")
                return@Column
            }

            Text(reservation.reservationCode, style = MaterialTheme.typography.titleLarge)
            Text(statusLabel(reservation.status))

            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 16.dp))

            state.qrBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Código QR de la reserva",
                    modifier = Modifier.size(240.dp),
                )
            }

            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 16.dp))

            Text("${reservation.originName} → ${reservation.destinationName}")
            Text("Asiento ${reservation.seatLabel}")
            Text("Salida: ${reservation.originDepartureAt}")

            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 24.dp))

            if (reservation.status == ReservationStatus.CONFIRMED) {
                // Ver Specs #5: solo habilitado en la ventana horaria del viaje —
                // contingencia si falla la lectura del QR, no un boton "abordar
                // cuando quiera".
                Button(
                    onClick = viewModel::selfCheckin,
                    enabled = viewModel.canSelfCheckin && !state.checkingIn,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.checkingIn) "Confirmando…" else "Confirmar abordaje sin QR")
                }
                if (!viewModel.canSelfCheckin) {
                    Text(
                        "Disponible solo cerca del horario de salida.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 12.dp))

                OutlinedButton(
                    onClick = viewModel::askCancel,
                    enabled = !state.cancelling,
                    modifier = Modifier.fillMaxWidth(),
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
}

private fun statusLabel(status: ReservationStatus): String = when (status) {
    ReservationStatus.CONFIRMED -> "Confirmada"
    ReservationStatus.BOARDED -> "Abordada"
    ReservationStatus.COMPLETED -> "Completada"
    ReservationStatus.NO_SHOW -> "No se presentó"
    ReservationStatus.CANCELLED -> "Cancelada"
}
