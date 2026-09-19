package com.appmovilidadclinica.driver.shared.ui.common

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val LIMA_TZ = TimeZone.of("America/Lima")

/**
 * Formatea un Instant a hora legible en zona horaria de Peru (America/Lima).
 * Ejemplo: 2026-07-14T13:00:00Z -> "08:00 AM"
 */
fun Instant.toPeruTime(): String {
    val ldt = this.toLocalDateTime(LIMA_TZ)
    val hour24 = ldt.hour
    val hour12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    val amPm = if (hour24 < 12) "AM" else "PM"
    val hh = hour12.toString().padStart(2, '0')
    val mm = ldt.minute.toString().padStart(2, '0')
    return "$hh:$mm $amPm"
}

/**
 * Formatea un Instant a fecha + hora legible en Peru.
 * Ejemplo: 2026-07-14T13:00:00Z -> "lun 14 jul, 08:00 AM"
 */
fun Instant.toPeruDateTime(): String {
    val ldt = this.toLocalDateTime(LIMA_TZ)
    val weekdays = listOf("dom", "lun", "mar", "mié", "jue", "vie", "sáb")
    val months = listOf(
        "ene", "feb", "mar", "abr", "may", "jun",
        "jul", "ago", "sep", "oct", "nov", "dic",
    )
    val weekday = weekdays[ldt.dayOfWeek.ordinal]
    val month = months[ldt.monthNumber - 1]
    val day = ldt.dayOfMonth
    val time = toPeruTime()
    return "$weekday $day $month, $time"
}
