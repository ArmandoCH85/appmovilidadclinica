package com.appmovilidadclinica.driver.shared.platform

import co.touchlab.kermit.Logger

/**
 * iOS stub del ScannerQrService. La impl nativa (AVFoundation + Vision)
 * llega en Fase 7 cuando haya macOS disponible.
 */
class StubScannerQrService : ScannerQrService {
    private val logger = Logger.withTag("ScannerQr")

    override suspend fun scan(): String {
        logger.e { "ScannerQrService.scan() no implementado en iOS todavía" }
        error("Scanner QR no implementado en iOS (Fase 7: AVFoundation + Vision)")
    }

    override fun isAvailable(): Boolean = false
}
