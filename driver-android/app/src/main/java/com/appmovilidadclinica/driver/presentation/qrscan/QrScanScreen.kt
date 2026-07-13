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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.appmovilidadclinica.driver.di.AppModule
import com.appmovilidadclinica.driver.domain.model.ReservationStatus
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGetImage::class)
@Composable
fun QrScanScreen(
    tripId: Long,
    onBack: () -> Unit,
    viewModel: QrScanViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                QrScanViewModel(tripId, AppModule.provideBookingRepository(), AppModule.provideDriverRepository())
            }
        },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
            if (!hasPermission) {
                PermissionRationale(onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) })
            } else {
                CameraPreview(
                    onQrDetected = viewModel::onQrDetected,
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
private fun CameraPreview(onQrDetected: (String) -> Unit, paused: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val callback = rememberUpdatedState(onQrDetected)
    val isPaused = rememberUpdatedState(paused)

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            val executor = Executors.newSingleThreadExecutor()

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(executor, QrCodeAnalyzer { token ->
                            if (!isPaused.value) callback.value(token)
                        })
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                } catch (e: Exception) {
                    // Camara no disponible en este dispositivo/emulador — la pantalla
                    // queda vacia pero no crashea la app.
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
    )
}
