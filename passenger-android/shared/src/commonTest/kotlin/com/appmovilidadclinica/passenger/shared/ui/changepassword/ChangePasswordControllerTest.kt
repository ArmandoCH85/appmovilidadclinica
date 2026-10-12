package com.appmovilidadclinica.passenger.shared.ui.changepassword

import com.appmovilidadclinica.passenger.shared.domain.error.AppError
import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChangePasswordControllerTest {

    private fun controller(
        scope: CoroutineScope,
        onChange: suspend (String, String) -> AppResult<Unit> = { _, _ -> AppResult.Success(Unit) },
    ) = ChangePasswordController(changePassword = onChange, scope = scope)

    @Test
    fun validaClaveCorta() = runTest {
        val c = controller(this)
        c.onCurrentChange("actual123")
        c.onNewChange("corta")
        c.onConfirmChange("corta")

        c.submit()
        advanceUntilIdle()

        assertEquals("Mínimo 8 caracteres.", c.uiState.value.newError)
    }

    @Test
    fun validaQueLaNuevaSeaDistinta() = runTest {
        val c = controller(this)
        c.onCurrentChange("mismaclave")
        c.onNewChange("mismaclave")
        c.onConfirmChange("mismaclave")

        c.submit()
        advanceUntilIdle()

        assertEquals("La nueva clave debe ser distinta a la actual.", c.uiState.value.newError)
    }

    @Test
    fun validaLaConfirmacion() = runTest {
        val c = controller(this)
        c.onCurrentChange("actual123")
        c.onNewChange("nuevaClave1")
        c.onConfirmChange("otraCosa")

        c.submit()
        advanceUntilIdle()

        assertEquals("Las claves no coinciden.", c.uiState.value.confirmError)
    }

    @Test
    fun cambioExitosoMarcaSaved() = runTest {
        var par: Pair<String, String>? = null
        val c = controller(this) { actual, nueva -> par = actual to nueva; AppResult.Success(Unit) }

        c.onCurrentChange("actual123")
        c.onNewChange("nuevaClave1")
        c.onConfirmChange("nuevaClave1")
        c.submit()
        advanceUntilIdle()

        assertTrue(c.uiState.value.saved)
        assertEquals("actual123" to "nuevaClave1", par)
        assertNull(c.uiState.value.formError)
    }

    @Test
    fun claveActualIncorrectaMuestraElMensajeDelBackend() = runTest {
        val c = controller(this) { _, _ ->
            AppResult.Failure(AppError.Unauthorized("La clave actual es incorrecta."))
        }

        c.onCurrentChange("malaClave1")
        c.onNewChange("nuevaClave1")
        c.onConfirmChange("nuevaClave1")
        c.submit()
        advanceUntilIdle()

        assertEquals("La clave actual es incorrecta.", c.uiState.value.formError)
        assertTrue(!c.uiState.value.saved)
        // Los campos escritos se conservan.
        assertEquals("nuevaClave1", c.uiState.value.newPassword)
    }
}
