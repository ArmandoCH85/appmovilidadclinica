package com.appmovilidadclinica.passenger.domain.model

/** Espejo de dominio de `auth.User` (backend) — solo los campos que la app usa. */
data class User(
    val id: Long,
    val employeeCode: String,
    val fullName: String,
    val role: UserRole,
    val department: String?,
    val phone: String?,
)

/**
 * `users.role` es ENUM('ADMIN','DRIVER','WORKER') en el backend. Esta app es
 * exclusiva del rol WORKER (ver Specs #1) — ADMIN/DRIVER pueden loguearse
 * (el backend no lo impide en /login) pero la navegacion los bloquea con un
 * mensaje, en vez de dejarlos entrar a pantallas que no les corresponden.
 */
enum class UserRole {
    ADMIN,
    DRIVER,
    WORKER,
    ;

    companion object {
        fun fromRaw(raw: String): UserRole = entries.find { it.name == raw } ?: WORKER
    }
}
