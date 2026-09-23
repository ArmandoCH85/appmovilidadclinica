package com.appmovilidadclinica.driver.shared.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Comportamiento del back stack del que depende el botón/gesto de volver:
 *
 *  - con pantallas apiladas, `pop()` desapila y vuelve a la anterior,
 *  - en la raíz (login / lista de viajes) `pop()` devuelve `false`, que es la
 *    señal que usa `MainActivity` para dejar que el sistema cierre la app.
 */
class DriverNavStateTest {

    @Test
    fun enLaRaizNoHayNadaQueDesapilar() {
        val state = DriverNavState(Route.Login)

        assertEquals(Route.Login, state.current)
        assertFalse(state.pop(), "En la raíz pop() debe devolver false para que el sistema cierre la app")
        assertEquals(Route.Login, state.current)
    }

    @Test
    fun volverDesdeElDetalleRegresaALaListaDeViajes() {
        val state = DriverNavState(Route.Dashboard)
        state.push(Route.TripDetail(tripId = 55L))

        assertEquals(Route.TripDetail(55L), state.current)
        assertTrue(state.pop())
        assertEquals(Route.Dashboard, state.current)
        assertFalse(state.pop())
    }

    @Test
    fun volverDesdeUnaPantallaApiladaSobreElDetalleRegresaAlDetalle() {
        val state = DriverNavState(Route.Dashboard)
        state.push(Route.TripDetail(tripId = 55L))
        state.push(Route.SeatMap(tripId = 55L))

        assertTrue(state.pop())
        assertEquals(Route.TripDetail(55L), state.current)
        assertTrue(state.pop())
        assertEquals(Route.Dashboard, state.current)
    }
}
