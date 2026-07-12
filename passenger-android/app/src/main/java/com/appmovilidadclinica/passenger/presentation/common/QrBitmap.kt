package com.appmovilidadclinica.passenger.presentation.common

import android.graphics.Bitmap
import com.google.zxing.common.BitMatrix

/**
 * Unica conversion Android-especifica de todo el flujo de QR — el dominio
 * (`GenerateQrUseCase`) se queda en `BitMatrix` (pure Java/Kotlin, ver esa
 * clase); esta funcion vive en `presentation` a proposito.
 */
fun BitMatrix.toBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    for (x in 0 until width) {
        for (y in 0 until height) {
            bitmap.setPixel(x, y, if (get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap
}
