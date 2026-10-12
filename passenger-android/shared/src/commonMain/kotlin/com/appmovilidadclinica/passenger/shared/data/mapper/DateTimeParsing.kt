package com.appmovilidadclinica.passenger.shared.data.mapper

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Parseo de fechas del backend, 100% multiplataforma (antes vivia en el modulo
 * Android con `java.time`, que no existe en iOS).
 *
 * El backend serializa con offset local (`2026-07-14T13:00:00-05:00`), que
 * `Instant.parse` acepta tal cual. Si viniera sin offset (hora de Lima), se
 * interpreta en la zona de Peru.
 */
internal val LIMA_TIME_ZONE: TimeZone = TimeZone.of("America/Lima")

internal fun parseInstantOrNull(raw: String?): Instant? {
    if (raw.isNullOrBlank()) return null
    return runCatching { Instant.parse(raw) }.getOrElse {
        runCatching { LocalDateTime.parse(raw).toInstant(LIMA_TIME_ZONE) }.getOrNull()
    }
}

internal fun parseInstant(raw: String): Instant =
    parseInstantOrNull(raw) ?: error("Fecha inválida del backend: $raw")

/**
 * `service_date` puede venir como fecha pura (`2026-07-14`) o con hora y
 * offset (`2026-07-14T00:00:00-05:00`): nos quedamos con la parte de fecha.
 */
internal fun parseLocalDate(raw: String): LocalDate =
    LocalDate.parse(if (raw.length >= 10 && raw[10] == 'T') raw.substring(0, 10) else raw)
