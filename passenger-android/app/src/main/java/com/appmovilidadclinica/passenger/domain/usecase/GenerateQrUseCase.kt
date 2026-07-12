package com.appmovilidadclinica.passenger.domain.usecase

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import javax.inject.Inject

/**
 * Excepcion deliberada a "domain no depende de framework": `zxing:core` (NO
 * `zxing-android-embedded`) es una libreria Java pura, sin dependencia de
 * `android.*` — `BitMatrix` es una matriz de booleanos, no un `Bitmap` de
 * Android. La conversion BitMatrix -> Bitmap (esa si es Android-especifica)
 * vive en `presentation/common/QrBitmap.kt`, no aca. Se documenta la
 * excepcion en vez de forzar una capa de indireccion que no aporta nada
 * (ver diseño técnico, tabla de stack).
 */
class GenerateQrUseCase @Inject constructor() {
    operator fun invoke(qrToken: String, sizePx: Int = 512): BitMatrix {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        return QRCodeWriter().encode(qrToken, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    }
}
