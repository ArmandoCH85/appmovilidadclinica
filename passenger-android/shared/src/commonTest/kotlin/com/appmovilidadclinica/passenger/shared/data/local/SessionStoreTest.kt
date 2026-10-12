package com.appmovilidadclinica.passenger.shared.data.local

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * La sesion persistida es la base del login iOS/Android: el mismo
 * [SessionStore] corre en las dos plataformas, solo cambia el `Settings`
 * (SharedPreferences / NSUserDefaults). Aca se prueba con `MapSettings`
 * en memoria.
 */
class SessionStoreTest {

    private val settings = MapSettings()
    private val store = SessionStore(settings)

    private val session = StoredSession(
        token = "jwt-de-prueba",
        userId = 7L,
        employeeCode = "E-0007",
        fullName = "Ana Quispe",
        role = "WORKER",
        department = "Enfermería",
        phone = "999111222",
    )

    @Test
    fun guardaYLeeLaSesion() = runTest {
        store.save(session)

        assertEquals(session, store.sessionState.value)
        assertEquals(session.token, store.tokenFlow.first())
    }

    @Test
    fun laSesionPersisteParaUnaInstanciaNueva() {
        store.save(session)

        // Simula reabrir la app: otro SessionStore sobre el mismo almacen.
        val reabierto = SessionStore(settings)

        assertEquals(session, reabierto.sessionState.value)
    }

    @Test
    fun limpiarBorraLaSesion() {
        store.save(session)
        store.clear()

        assertNull(store.sessionState.value)
        assertNull(SessionStore(settings).sessionState.value)
    }

    @Test
    fun sinTokenNoHaySesion() {
        assertNull(store.sessionState.value)
    }
}
