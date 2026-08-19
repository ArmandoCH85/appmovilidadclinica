package com.appmovilidadclinica.driver.shared.domain.model

data class AuthResult(
    val token: String,
    val user: User
)
