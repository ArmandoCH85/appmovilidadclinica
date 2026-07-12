package com.appmovilidadclinica.driver.domain.model

data class AuthResult(
    val token: String,
    val user: User
)
