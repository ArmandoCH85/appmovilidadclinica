package com.appmovilidadclinica.driver.shared.platform

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/**
 * Composable que envuelve CameraX+MLKit para escanear QR codes.
 * Vive en :shared/androidMain/ porque PreviewView/AndroidView
 * no son KMP-friendly.
 *
 * Usado por :app/presentation/qrscan/QrScanScreen.kt.
 *
 * @param onScanned callback cuando se detecta un codigo valido.
 * @param paused si true, deja de invocar onScanned. Util para evitar
 *   multiples llamadas mientras la UI procesa el resultado anterior.
 */
@Composable
fun CameraQrScannerContent(
    onScanned: (String) -> Unit,
    paused: Boolean,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onScannedState = rememberUpdatedState(onScanned)
    val pausedState = rememberUpdatedState(paused)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            val executor = Executors.newSingleThreadExecutor()

            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(executor) { proxy ->
                                if (!pausedState.value) {
                                    val analyzer = QrCodeAnalyzer { token ->
                                        onScannedState.value(token)
                                    }
                                    analyzer.analyze(proxy)
                                } else {
                                    proxy.close()
                                }
                            }
                        }

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                } catch (e: Exception) {
                    // Camara no disponible (emulador sin camara). No crashea.
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
    )
}
