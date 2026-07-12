package com.appmovilidadclinica.passenger.domain.repository

import com.appmovilidadclinica.passenger.domain.error.AppResult
import com.appmovilidadclinica.passenger.domain.model.Stop

/**
 * CONTRATO NUEVO propuesto (`GET /api/stops`, ver diseño técnico #2) — hoy
 * el unico listado de paradas es `/admin/stops`, exclusivo rol ADMIN
 * (verificado: `requireAdmin` en `admin/service.go`). Contra el backend
 * actual, `list()` devuelve AppError.Forbidden o AppError.NotFound segun
 * como se implemente el endpoint nuevo.
 */
interface StopsRepository {
    suspend fun list(): AppResult<List<Stop>>
}
