package com.appmovilidadclinica.driver.shared.platform

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * MLKit ImageAnalysis.Analyzer que decodifica QR codes en cada frame.
 *
 * Reusado por [CameraQrScannerContent] — vive en :shared/androidMain/
 * porque depende 100% de libs Android (CameraX + MLKit).
 */
@OptIn(ExperimentalGetImage::class)
class QrCodeAnalyzer(
    private val onCodeDetected: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees,
        )
        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull()?.rawValue?.let(onCodeDetected)
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}
