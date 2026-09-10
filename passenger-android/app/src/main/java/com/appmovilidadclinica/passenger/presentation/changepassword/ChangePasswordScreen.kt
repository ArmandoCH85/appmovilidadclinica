package com.appmovilidadclinica.passenger.presentation.changepassword

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val MintIcon = Color(0xFF9FE7C4)
private val MintButton = Color(0xFFB2F2D5)
private val DarkGreenText = Color(0xFF0B3D2E)
private val FieldBorder = Color(0xFF4A4A4A)
private val SubtitleGray = Color(0xFF9E9E9E)
private val MatchGreen = Color(0xFF4CAF50)
private val FieldShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(
    onBack: () -> Unit,
    viewModel: ChangePasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Exito: toast (sobrevive al pop) y volver sin perder nada mas.
    LaunchedEffect(state.saved) {
        if (state.saved) {
            Toast.makeText(context, "Clave actualizada con éxito", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cambiar clave") },
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
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MintIcon,
                modifier = Modifier.size(44.dp),
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "Protege tu cuenta",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 25.sp,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Ingresa tu clave actual y elige una nueva.",
                color = SubtitleGray,
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(24.dp))

            PasswordField(
                value = state.currentPassword,
                onValueChange = viewModel::onCurrentChange,
                label = "Clave actual",
                visible = state.currentVisible,
                onToggleVisibility = viewModel::toggleCurrentVisibility,
                error = state.currentError,
                helper = null,
            )

            Spacer(Modifier.height(12.dp))

            PasswordField(
                value = state.newPassword,
                onValueChange = viewModel::onNewChange,
                label = "Nueva clave",
                visible = state.newVisible,
                onToggleVisibility = viewModel::toggleNewVisibility,
                error = state.newError,
                helper = "Mínimo 8 caracteres.",
            )

            Spacer(Modifier.height(12.dp))

            PasswordField(
                value = state.confirmPassword,
                onValueChange = viewModel::onConfirmChange,
                label = "Confirmar nueva clave",
                visible = state.confirmVisible,
                onToggleVisibility = viewModel::toggleConfirmVisibility,
                error = state.confirmError,
                helper = null,
            )

            if (state.newPassword.isNotEmpty() && state.newPassword == state.confirmPassword) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MatchGreen,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Las claves coinciden",
                        color = MatchGreen,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

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
                enabled = !state.submitting,
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
                    Text("Guardar cambios", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            TextButton(onClick = onBack) {
                Text("Cancelar")
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
    onToggleVisibility: () -> Unit,
    error: String?,
    helper: String?,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "Ocultar clave" else "Mostrar clave",
                )
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        isError = error != null,
        supportingText = {
            when {
                error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                helper != null -> Text(helper, color = SubtitleGray)
            }
        },
        shape = FieldShape,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = FieldBorder,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
