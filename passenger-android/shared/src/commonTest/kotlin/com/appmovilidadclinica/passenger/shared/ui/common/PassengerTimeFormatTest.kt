package com.appmovilidadclinica.passenger.shared.ui.common

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regresión: el día de la semana se mostraba corrido un lugar porque se usaba
 * `DayOfWeek.ordinal` (lunes = 0) contra una lista que empezaba en "dom".
 */
class PassengerTimeFormatTest {

    private fun fechaDe(iso: String): String =
        Instant.parse(iso).toSharedPeruDateTime().substringBefore(", ")

    @Test
    fun viernesVeinteDeNoviembreNoSeMuestraComoJueves() {
        // 2026-11-20 06:05 en Lima (UTC-5) = 11:05Z -> viernes
        assertEquals("vie 20 nov", fechaDe("2026-11-20T11:05:00Z"))
    }

    @Test
    fun sabadoVeintiunoDeNoviembreNoSeMuestraComoViernes() {
        // 2026-11-21 06:05 en Lima = 11:05Z -> sábado (el caso que reportó el usuario)
        assertEquals("sáb 21 nov", fechaDe("2026-11-21T11:05:00Z"))
    }

    @Test
    fun domingoY_lunesEnLosBordesDeLaSemana() {
        // 2026-11-22 es domingo; 2026-11-23 es lunes.
        assertEquals("dom 22 nov", fechaDe("2026-11-22T15:00:00Z"))
        assertEquals("lun 23 nov", fechaDe("2026-11-23T15:00:00Z"))
    }

    @Test
    fun laHoraRespetaElHorarioDePeru() {
        // 23:30Z = 18:30 en Lima del MISMO día (formato 12 h).
        assertEquals("06:30 PM", Instant.parse("2026-11-21T23:30:00Z").toSharedPeruTime())
        // 02:00Z del 22 = 21:00 del día anterior en Lima.
        assertEquals("09:00 PM", Instant.parse("2026-11-22T02:00:00Z").toSharedPeruTime())
        // 06:05 en Lima = 11:05Z.
        assertEquals("06:05 AM", Instant.parse("2026-11-21T11:05:00Z").toSharedPeruTime())
    }
}
