package com.appmovilidadclinica.driver.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.appmovilidadclinica.driver.di.AppModule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ProfileViewModel(AppModule.provideAuthRepository(), AppModule.provideDriverRepository())
            }
        },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // No navegamos aca al cerrar sesion: DriverNavHost observa isLoggedIn y
    // redirige solo. Navegar en dos lugares a la vez (aca + el LaunchedEffect
    // de la raiz) corrompia el arbol de composicion de Nav Compose y
    // crasheaba la app (IndexOutOfBoundsException en Composer/Stack.pop).

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfil") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        val user = state.user
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            if (user == null) {
                Text("Cargando…")
                return@Column
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary,
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        user.fullName.firstOrNull()?.uppercase() ?: "C",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                user.fullName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Conductor",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ProfileRow(Icons.Default.Badge, "Código", user.employeeCode)
                    user.department?.let {
                        Spacer(Modifier.height(10.dp))
                        ProfileRow(Icons.Default.Business, "Departamento", it)
                    }
                    user.phone?.let {
                        Spacer(Modifier.height(10.dp))
                        ProfileRow(Icons.Default.Phone, "Teléfono", it)
                    }
                    if (user.driverLicenseNumber != null) {
                        Spacer(Modifier.height(10.dp))
                        ProfileRow(
                            Icons.Default.CreditCard,
                            "Licencia",
                            "${user.driverLicenseNumber} (${user.driverLicenseCategory.orEmpty()})",
                        )
                    }
                    user.driverLicenseExpiresOn?.let {
                        Spacer(Modifier.height(10.dp))
                        ProfileRow(Icons.Default.EventAvailable, "Vence", it)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            state.todayTripCount?.let { count ->
                Text(
                    "Viajes hoy: $count",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(32.dp))

            OutlinedButton(
                onClick = viewModel::askLogout,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text("Cerrar sesión")
            }
        }
    }

    if (state.showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissLogout,
            title = { Text("Cerrar sesión") },
            text = { Text("¿Está seguro de que desea cerrar sesión?") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmLogout) { Text("Sí, cerrar sesión") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissLogout) { Text("Volver") }
            },
        )
    }
}

@Composable
private fun ProfileRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
