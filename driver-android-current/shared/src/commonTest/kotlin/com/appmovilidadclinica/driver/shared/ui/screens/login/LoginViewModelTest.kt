package com.appmovilidadclinica.driver.shared.ui.screens.login

import com.appmovilidadclinica.driver.shared.domain.model.AppError
import com.appmovilidadclinica.driver.shared.testfakes.FakeAuthRepository
import com.appmovilidadclinica.driver.shared.testfakes.testAuthResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun estadoInicialVacio() = runTest {
        val vm = LoginViewModel(FakeAuthRepository())

        val state = vm.uiState.value
        assertEquals("", state.documentNumber)
        assertEquals("", state.password)
        assertEquals(false, state.submitting)
        assertNull(state.errorMessage)
    }

    @Test
    fun onDocumentNumberChangeActualizaStateYLimpiaError() = runTest {
        val vm = LoginViewModel(FakeAuthRepository())
        vm.onDocumentNumberChange("C0001")
        assertEquals("C0001", vm.uiState.value.documentNumber)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun onPasswordChangeActualizaStateYLimpiaError() = runTest {
        val vm = LoginViewModel(FakeAuthRepository())
        vm.onPasswordChange("12345678")
        assertEquals("12345678", vm.uiState.value.password)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun submitConCamposVaciosMuestraErrorSinLlamarAlRepo() = runTest {
        val fake = FakeAuthRepository()
        val vm = LoginViewModel(fake)

        vm.submit()

        val msg = vm.uiState.value.errorMessage
        assertNotNull(msg)
        assertTrue(msg!!.contains("Complete el usuario"))
    }

    @Test
    fun submitExitosoComoDriverLimpiaSubmittingSinError() = runTest(testDispatcher) {
        val fake = FakeAuthRepository().apply {
            loginResult = Result.success(testAuthResult(role = "DRIVER"))
        }
        val vm = LoginViewModel(fake)

        vm.onDocumentNumberChange("C0001")
        vm.onPasswordChange("12345678")
        vm.submit()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(false, state.submitting)
        assertNull(state.errorMessage)
    }

    @Test
    fun submitExitosoConRoleDistintoDeDriverMuestraErrorYLimpiaSession() = runTest(testDispatcher) {
        val fake = FakeAuthRepository().apply {
            loginResult = Result.success(testAuthResult(role = "WORKER"))
        }
        val vm = LoginViewModel(fake)

        vm.onDocumentNumberChange("W0001")
        vm.onPasswordChange("12345678")
        vm.submit()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(false, state.submitting)
        assertEquals("Esta app es para conductores", state.errorMessage)
        assertEquals(true, fake.cleared)
    }

    @Test
    fun submitFallidoUnauthorizedMuestraMensajeAmigable() = runTest(testDispatcher) {
        val fake = FakeAuthRepository().apply {
            loginResult = Result.failure(AppError.Unauthorized("credenciales inválidas"))
        }
        val vm = LoginViewModel(fake)

        vm.onDocumentNumberChange("C0001")
        vm.onPasswordChange("badpass")
        vm.submit()
        advanceUntilIdle()

        assertEquals(
            "Usuario o contraseña incorrectos.",
            vm.uiState.value.errorMessage,
        )
    }

    @Test
    fun submitFallidoNetworkMuestraMensajeDeConexion() = runTest(testDispatcher) {
        val fake = FakeAuthRepository().apply {
            loginResult = Result.failure(AppError.Network("timeout"))
        }
        val vm = LoginViewModel(fake)

        vm.onDocumentNumberChange("C0001")
        vm.onPasswordChange("12345678")
        vm.submit()
        advanceUntilIdle()

        assertEquals(
            "No se pudo conectar con el servidor. Verifique su conexión.",
            vm.uiState.value.errorMessage,
        )
    }

    @Test
    fun trimAlDocumentNumberAntesDeEnviar() = runTest(testDispatcher) {
        val captured = StringBuilder()
        val fake = object : FakeAuthRepository() {
            override suspend fun login(documentNumber: String, password: String): Result<com.appmovilidadclinica.driver.shared.domain.model.AuthResult> {
                captured.append(documentNumber)
                return super.login(documentNumber, password)
            }
        }
        fake.loginResult = Result.success(testAuthResult(role = "DRIVER"))
        val vm = LoginViewModel(fake)

        vm.onDocumentNumberChange("  C0001  ")
        vm.onPasswordChange("12345678")
        vm.submit()
        advanceUntilIdle()

        assertEquals("C0001", captured.toString())
    }
}
