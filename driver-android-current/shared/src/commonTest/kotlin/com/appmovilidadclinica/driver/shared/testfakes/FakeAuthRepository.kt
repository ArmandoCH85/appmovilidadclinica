package com.appmovilidadclinica.driver.shared.testfakes

import com.appmovilidadclinica.driver.shared.domain.model.AuthResult
import com.appmovilidadclinica.driver.shared.domain.model.User
import com.appmovilidadclinica.driver.shared.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Fake de [AuthRepository] para tests. El campo [loginResult] mutable
 * permite programar respuestas por test (exitosas, errores, role != DRIVER, etc).
 */
open class FakeAuthRepository(
    initialToken: String? = null,
    initialUser: User? = null,
) : AuthRepository {

    private val tokenState = MutableStateFlow(initialToken)
    private val userState = MutableStateFlow(initialUser)

    var loginResult: Result<AuthResult> = Result.failure(IllegalStateException("Sin programar"))
    var cleared = false

    override suspend fun login(documentNumber: String, password: String): Result<AuthResult> {
        val r = loginResult
        if (r.isSuccess) {
            tokenState.value = r.getOrNull()!!.token
            userState.value = r.getOrNull()!!.user
        }
        return r
    }

    override suspend fun logout() {
        tokenState.value = null
        userState.value = null
        cleared = true
    }

    override fun isLoggedIn(): Flow<Boolean> = tokenState.asStateFlow().let {
        kotlinx.coroutines.flow.MutableStateFlow(it.value != null).asStateFlow()
    }

    override fun getCurrentUser(): Flow<User?> = userState.asStateFlow()

    override fun getToken(): Flow<String?> = tokenState.asStateFlow()

    override suspend fun clearSession() {
        tokenState.value = null
        userState.value = null
        cleared = true
    }

    override fun observeSessionExpired(): Flow<Unit> = emptyFlow()
}

/** Builder rapido para tests. */
fun testUser(role: String = "DRIVER"): User = User(
    id = 1L,
    employeeCode = "C0001",
    documentNumber = "12345678",
    fullName = "Conductor Test",
    role = role,
    department = null,
    phone = null,
    driverLicenseNumber = null,
    driverLicenseCategory = null,
    driverLicenseExpiresOn = null,
    active = true,
)

fun testAuthResult(token: String = "jwt-test", role: String = "DRIVER"): AuthResult =
    AuthResult(token = token, user = testUser(role))
