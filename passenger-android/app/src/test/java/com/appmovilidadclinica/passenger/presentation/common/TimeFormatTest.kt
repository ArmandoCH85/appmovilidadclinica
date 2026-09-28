package com.appmovilidadclinica.passenger.presentation.common

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El formateo de fechas se reescribio de java.time a kotlinx-datetime al
 * agregar los targets iOS al modulo shared. Estos tests fijan el formato
 * exacto que ya se mostraba en la app ("08:00 AM" y "lun 14 jul, 08:00 AM")
 * para que la migracion no cambie lo que ve el pasajero.
 */
class TimeFormatTest {

    // 13:00Z == 08:00 AM en America/Lima (UTC-5)
    private val instant = Instant.parse("2026-07-14T13:00:00Z")

    @Test
    fun toPeruTimeUsaHoraDeLimaDe12Horas() {
        assertEquals("08:00 AM", instant.toPeruTime())
    }

    @Test
    fun toPeruTimeDeMedianocheYMediodia() {
        assertEquals("07:00 PM", Instant.parse("2026-07-15T00:00:00Z").toPeruTime())
        assertEquals("12:00 PM", Instant.parse("2026-07-14T17:00:00Z").toPeruTime())
        assertEquals("12:00 AM", Instant.parse("2026-07-14T05:00:00Z").toPeruTime())
    }

    @Test
    fun toPeruDateTimeMantieneElFormatoConDiaMesYHora() {
        val formatted = instant.toPeruDateTime()

        // Forma: "<dia-semana> <dia> <mes>, hh:mm AM/PM" (todo en espanol, abreviado)
        assertTrue(
            "Formato inesperado: $formatted",
            Regex("""^[a-z]{3} \d{1,2} [a-z]{3}, \d{2}:\d{2} [AP]M$""").matches(formatted),
        )
        assertTrue("Falta el dia 14: $formatted", formatted.contains("14"))
        assertTrue("Falta el mes jul: $formatted", formatted.contains("jul"))
        assertTrue("Falta la hora 08:00 AM: $formatted", formatted.contains("08:00 AM"))
    }
}
