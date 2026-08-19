package com.appmovilidadclinica.passenger.shared.data.remote.dto

import kotlinx.serialization.Serializable

/** Shape exacto de `apperror.WriteJSONError` (backend Go) â€” `{"error":{"code","message"}}`. */
@Serializable
data class ErrorResponseDto(val error: ErrorBodyDto)

@Serializable
data class ErrorBodyDto(val code: Int, val message: String)
