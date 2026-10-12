package com.appmovilidadclinica.passenger.shared.ui.login

import com.appmovilidadclinica.passenger.shared.data.local.SessionStore
import com.appmovilidadclinica.passenger.shared.data.local.StoredSession
import com.appmovilidadclinica.passenger.shared.domain.error.AppError
import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * El login de iOS y Android comparten este controlador. Se prueba con la
 * operacion de red inyectada (fake), sin backend.
 */
class LoginControllerTest {

    private val session = StoredSession(
        token = "jwt",
        userId = 1L,
        employeeCode = "E-0001",
        fullName = "Ana Quispe",
        role = "WORKER",
        department = null,
        phone = null,
    )

    private fun store() = SessionStore(MapSettings())

    @Test
    fun loginExitosoGuardaLaSesionYPersiste() = runTest {
        val store = store()
        val controller = LoginController(
            sessionStore = store,
            signIn = { _, _ -> AppResult.Success(session) },
            scope = this,
        )

        controller.onDocumentNumberChange("12345678")
        controller.onPasswordChange("secreta")
        controller.submit()
        advanceUntilIdle()

        // La sesion queda guardada: la app cambia sola a la pantalla autenticada.
        assertEquals(session, store.sessionState.value)
        assertFalse(controller.uiState.value.submitting)
        assertNull(controller.uiState.value.errorMessage)
    }

    @Test
    fun credencialesInvalidasMuestranMensajeYNoGuardanSesion() = runTest {
        val store = store()
        val controller = LoginController(
            sessionStore = store,
            signIn = { _, _ -> AppResult.Failure(AppError.Unauthorized("no autorizado")) },
            scope = this,
        )

        controller.onDocumentNumberChange("12345678")
        controller.onPasswordChange("mala")
        controller.submit()
        advanceUntilIdle()

        assertEquals("Usuario o contraseña incorrectos.", controller.uiState.value.errorMessage)
        assertNull(store.sessionState.value)
        assertFalse(controller.uiState.value.submitting)
    }

    @Test
    fun sinConexionAvisaAlUsuario() = runTest {
        val controller = LoginController(
            sessionStore = store(),
            signIn = { _, _ -> AppResult.Failure(AppError.Network("timeout")) },
            scope = this,
        )

        controller.onDocumentNumberChange("12345678")
        controller.onPasswordChange("secreta")
        controller.submit()
        advanceUntilIdle()

        assertEquals(
            "No se pudo conectar con el servidor. Verifique su conexión.",
            controller.uiState.value.errorMessage,
        )
    }

    @Test
    fun camposVaciosNoLlamanAlServidor() = runTest {
        var llamado = false
        val controller = LoginController(
            sessionStore = store(),
            signIn = { _, _ -> llamado = true; AppResult.Success(session) },
            scope = this,
        )

        controller.onPasswordChange("secreta")
        controller.submit()
        advanceUntilIdle()

        assertFalse(llamado, "No deberia llamar al backend con el usuario vacio")
        assertEquals("Complete el usuario y la contraseña.", controller.uiState.value.errorMessage)
    }

    @Test
    fun noEnviaDosVecesSiYaHayUnEnvioEnCurso() = runTest {
        var llamadas = 0
        val controller = LoginController(
            sessionStore = store(),
            signIn = { _, _ -> llamadas++; AppResult.Success(session) },
            scope = this,
        )

        controller.onDocumentNumberChange("12345678")
        controller.onPasswordChange("secreta")
        controller.submit()
        controller.submit() // doble toque rapido
        advanceUntilIdle()

        assertEquals(1, llamadas, "El segundo toque no debe disparar otro login")
        assertTrue(controller.uiState.value.errorMessage == null)
    }
}
