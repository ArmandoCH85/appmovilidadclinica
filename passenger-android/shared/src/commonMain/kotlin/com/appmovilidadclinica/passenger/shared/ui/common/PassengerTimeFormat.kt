package com.appmovilidadclinica.passenger.shared.ui.common

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val LIMA_TZ = TimeZone.of("America/Lima")

/**
 * Formateo de fechas de la UI compartida (Android + iOS).
 *
 * Ojo: el modulo Android tiene su propia copia (`presentation/common/TimeFormat.kt`),
 * con el mismo formato. Cuando la app Android pase a consumir estas pantallas,
 * se borra la del app y queda solo esta.
 */
fun Instant.toSharedPeruTime(): String {
    val ldt = toLocalDateTime(LIMA_TZ)
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

/** Fecha + hora en hora de Peru: "lun 14 jul, 08:00 AM". */
fun Instant.toSharedPeruDateTime(): String {
    val ldt = toLocalDateTime(LIMA_TZ)
    val weekdays = listOf("dom", "lun", "mar", "mié", "jue", "vie", "sáb")
    val months = listOf(
        "ene", "feb", "mar", "abr", "may", "jun",
        "jul", "ago", "sep", "oct", "nov", "dic",
    )
    val weekday = weekdays[ldt.dayOfWeek.ordinal]
    val month = months[ldt.monthNumber - 1]
    return "$weekday ${ldt.dayOfMonth} $month, ${toSharedPeruTime()}"
}
