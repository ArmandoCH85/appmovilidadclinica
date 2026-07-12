package com.appmovilidadclinica.passenger.presentation.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Modal bloqueante — mismo texto/espiritu que el modal de sesion expirada del panel admin. */
@Composable
fun SessionExpiredDialog(onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = {}, // no dismissable tocando afuera — accion obligatoria
        title = { Text("Sesión expirada") },
        text = { Text("Su sesión expiró. Inicie sesión nuevamente para continuar.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Ir a iniciar sesión") }
        },
    )
}

/** Banner T-2min — visible solo cuando quedan <=120s de sesion. */
@Composable
fun SessionExpiryBanner(secondsLeft: Long, modifier: Modifier = Modifier) {
    if (secondsLeft <= 0 || secondsLeft > 120) return
    val minutes = ((secondsLeft + 59) / 60).coerceAtLeast(1)
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            "Su sesión vence en $minutes minuto${if (minutes == 1L) "" else "s"}.",
            modifier = Modifier.padding(12.dp),
        )
    }
}
