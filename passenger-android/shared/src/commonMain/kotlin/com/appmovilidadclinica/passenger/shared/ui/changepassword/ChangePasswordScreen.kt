package com.appmovilidadclinica.passenger.shared.ui.changepassword

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Cambio de contraseña, comun a Android e iOS. Mismas validaciones que el
 * modulo Android (minimo 8 caracteres, distinta a la actual, confirmacion).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(
    controller: ChangePasswordController,
    onBack: () -> Unit,
) {
    val state by controller.uiState.collectAsState()
    var visibleActual by remember { mutableStateOf(false) }
    var visibleNueva by remember { mutableStateOf(false) }

    DisposableEffect(controller) { onDispose { controller.dispose() } }

    // Al guardar, se avisa y se vuelve.
    LaunchedEffect(state.saved) {
        if (state.saved) {
            kotlinx.coroutines.delay(1_200)
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cambiar contraseña") },
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
                .padding(20.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Ingrese su clave actual y la nueva (mínimo 8 caracteres).",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PasswordField(
                value = state.currentPassword,
                onValueChange = controller::onCurrentChange,
                label = "Clave actual",
                visible = visibleActual,
                onToggle = { visibleActual = !visibleActual },
                error = state.currentError,
                imeAction = ImeAction.Next,
            )

            PasswordField(
                value = state.newPassword,
                onValueChange = controller::onNewChange,
                label = "Nueva clave",
                visible = visibleNueva,
                onToggle = { visibleNueva = !visibleNueva },
                error = state.newError,
                imeAction = ImeAction.Next,
            )

            PasswordField(
                value = state.confirmPassword,
                onValueChange = controller::onConfirmChange,
                label = "Repita la nueva clave",
                visible = visibleNueva,
                onToggle = { visibleNueva = !visibleNueva },
                error = state.confirmError,
                imeAction = ImeAction.Done,
            )

            state.formError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
            }

            if (state.saved) {
                Text(
                    "Contraseña actualizada.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = controller::submit,
                enabled = !state.submitting,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Guardar cambios", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggle: () -> Unit,
    error: String?,
    imeAction: ImeAction,
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            isError = error != null,
            trailingIcon = {
                TextButton(onClick = onToggle) {
                    Text(if (visible) "Ocultar" else "Mostrar", style = MaterialTheme.typography.labelSmall)
                }
            },
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = imeAction,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }
    }
}
