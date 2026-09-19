package com.appmovilidadclinica.driver.shared.platform

/**
 * Servicio multiplatform para escanear codigos QR de pasajeros.
 *
 * Android: implementacion real con CameraX + MLKit Barcode.
 * iOS: stub que tira error hasta Fase 7 (AVFoundation + Vision).
 *
 * Usado por las screens que necesitan verificar un QR (ej. verificarQr).
 */
interface ScannerQrService {
    /**
     * Escanea un QR y devuelve su texto. Suspende hasta tener un resultado
     * o el usuario cancela. En iOS tira `NotImplementedError` por ahora.
     */
    suspend fun scan(): String

    /**
     * False en iOS stub. Usado por la UI para decidir si muestra el preview
     * de la camara o una pantalla de "no implementado".
     */
    fun isAvailable(): Boolean
}
