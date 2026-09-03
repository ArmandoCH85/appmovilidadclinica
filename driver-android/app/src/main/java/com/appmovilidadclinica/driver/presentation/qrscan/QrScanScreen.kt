package com.appmovilidadclinica.driver.presentation.qrscan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.appmovilidadclinica.driver.shared.domain.model.ReservationStatus
import com.appmovilidadclinica.driver.shared.domain.repository.BookingRepository
import com.appmovilidadclinica.driver.shared.domain.repository.DriverRepository
import com.appmovilidadclinica.driver.shared.platform.CameraQrScannerContent
import com.appmovilidadclinica.driver.shared.platform.ScannerQrService
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGetImage::class)
@Composable
fun QrScanScreen(
    tripId: Long,
    onBack: () -> Unit,
) {
    val bookingRepository: BookingRepository = koinInject()
    val driverRepository: DriverRepository = koinInject()
    val viewModel: QrScanViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                QrScanViewModel(tripId, bookingRepository, driverRepository)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scannerQrService: ScannerQrService = koinInject()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(state.toastMessage) {
        if (state.toastMessage != null) {
            delay(2000)
            viewModel.dismissToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Escanear QR") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (!scannerQrService.isAvailable()) {
                StubUnavailableContent(onBack = onBack)
            } else if (!hasPermission) {
                PermissionRationale(onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) })
            } else {
                CameraQrScannerContent(
                    onScanned = viewModel::onQrDetected,
                    paused = state.reservation != null || state.verifying,
                )

                Text(
                    "Apunte la cámara al código QR del pasajero",
                    color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                )

                if (state.verifying) {
                    Surface(
                        modifier = Modifier.align(Alignment.Center),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Verificando…")
                        }
                    }
                }

                if (state.toastMessage != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp)
                            .fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(state.toastMessage.orEmpty(), modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
    }

    state.reservation?.let { reservation ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(state.passenger?.workerFullName ?: reservation.reservationCode) },
            text = {
                Column {
                    Text("Código: ${reservation.reservationCode}")
                    state.passenger?.let { p ->
                        Text("Asiento ${p.seatLabel} · ${p.originStopName} → ${p.destinationStopName}")
                    }
                    Text("Estado: ${statusLabel(reservation.status)}")
                }
            },
            confirmButton = {
                Row {
                    when (reservation.status) {
                        ReservationStatus.CONFIRMED -> {
                            TextButton(onClick = viewModel::board, enabled = !state.actionInProgress) {
                                Text("Abordar")
                            }
                            TextButton(onClick = viewModel::noShow, enabled = !state.actionInProgress) {
                                Text("No presentado")
                            }
                        }
                        ReservationStatus.BOARDED -> {
                            TextButton(onClick = viewModel::alight, enabled = !state.actionInProgress) {
                                Text("Bajar")
                            }
                        }
                        else -> {}
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::scanNext) { Text("Cerrar") }
            },
        )
    }

    if (state.errorMessage != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("Error") },
            text = { Text(state.errorMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) { Text("Aceptar") }
            },
        )
    }
}

private fun statusLabel(status: ReservationStatus): String = when (status) {
    ReservationStatus.CONFIRMED -> "Confirmado"
    ReservationStatus.BOARDED -> "Abordado"
    ReservationStatus.NO_SHOW -> "No se presentó"
    ReservationStatus.COMPLETED -> "Completado"
    ReservationStatus.CANCELLED -> "Cancelado"
}

@Composable
private fun StubUnavailableContent(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.QrCodeScanner,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Scanner QR no disponible",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Esta función estará disponible en iOS próximamente. En Android " +
                "ya está operativa.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onBack) { Text("Volver") }
    }
}

@Composable
private fun PermissionRationale(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Se requiere permiso de cámara para escanear los códigos QR de los pasajeros.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text("Conceder permiso") }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreviewRemovedMovedToShared() {
    // CameraPreview + QrCodeAnalyzer fueron movidos a :shared/androidMain/ .../platform/
    // (CameraQrScannerContent + QrCodeAnalyzer). Esto es un placeholder vacio
    // para no dejar codigo muerto en :app.
}
