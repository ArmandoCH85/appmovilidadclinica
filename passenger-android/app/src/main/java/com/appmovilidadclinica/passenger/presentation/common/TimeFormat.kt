package com.appmovilidadclinica.passenger.presentation.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formatea un Instant a hora legible en zona horaria de Peru (America/Lima).
 * Ejemplo: 2026-07-14T13:00:00Z -> "08:00 AM"
 */
fun Instant.toPeruTime(): String {
    val lima = ZoneId.of("America/Lima")
    val formatter = DateTimeFormatter.ofPattern("hh:mm a", Locale("es", "PE"))
    return this.atZone(lima).format(formatter)
}

/**
 * Formatea un Instant a fecha + hora legible en Peru.
 * Ejemplo: 2026-07-14T13:00:00Z -> "lun 14 jul, 08:00 AM"
 */
fun Instant.toPeruDateTime(): String {
    val lima = ZoneId.of("America/Lima")
    val formatter = DateTimeFormatter.ofPattern("EEE d MMM, hh:mm a", Locale("es", "PE"))
    return this.atZone(lima).format(formatter)
}
