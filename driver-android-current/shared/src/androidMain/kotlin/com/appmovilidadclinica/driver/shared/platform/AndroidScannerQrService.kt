package com.appmovilidadclinica.driver.shared.platform

import android.content.Context
import co.touchlab.kermit.Logger

/**
 * Stub Android del ScannerQrService. La integracion completa con
 * CameraX+MLKit queda para la siguiente iteracion; mientras tanto
 * el binding existe para que Koin lo provea y las screens lo consulten
 * via `koinInject<ScannerQrService>()`.
 *
 * Cuando se invoque [scan] en Android realmente, se debe reemplazar este
 * stub por una impl que abra CameraX+MLKit y suspenda hasta detectar
 * un codigo. La UI de preview CameraX sigue viviendo en
 * :app/presentation/qrscan/QrScanScreen.kt porque usa PreviewView
 * que no es KMP-friendly.
 */
class AndroidScannerQrService(private val context: Context) : ScannerQrService {
    private val logger = Logger.withTag("ScannerQr")

    override suspend fun scan(): String {
        logger.w { "AndroidScannerQrService.scan() todavia no implementado en :shared; use :app's QrScanScreen" }
        error("Scanner QR: la UI todavia reside en :app (CameraX+MLKit)")
    }

    override fun isAvailable(): Boolean = true
}
