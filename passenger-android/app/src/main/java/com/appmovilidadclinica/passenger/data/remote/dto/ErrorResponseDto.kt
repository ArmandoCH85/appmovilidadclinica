package com.appmovilidadclinica.passenger.data.remote.dto

import kotlinx.serialization.Serializable

/** Shape exacto de `apperror.WriteJSONError` (backend Go) — `{"error":{"code","message"}}`. */
@Serializable
data class ErrorResponseDto(val error: ErrorBodyDto)

@Serializable
data class ErrorBodyDto(val code: Int, val message: String)
