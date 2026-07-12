package com.appmovilidadclinica.passenger.domain.error

/**
 * Todo error que puede llegar desde `data` a `domain`/`presentation`,
 * mapeado 1:1 desde el shape de error del backend
 * (`{"error":{"code","message"}}`, ver `apperror.WriteJSONError` en Go) —
 * mismo patron ya usado en el panel admin (`admin/src/api/client.ts`).
 *
 * El ViewModel nunca deberia ver una excepcion cruda: todo caso de uso
 * devuelve `AppResult<T>`, no lanza.
 */
sealed class AppError {
    /** 401 — token ausente/invalido/expirado. Dispara logout forzado. */
    data class Unauthorized(val message: String) : AppError()

    /** 403 — rol sin permiso (ej. un ADMIN/DRIVER intentando accion de pasajero). */
    data class Forbidden(val message: String) : AppError()

    /** 404 — recurso no encontrado (viaje, reserva). */
    data class NotFound(val message: String) : AppError()

    /**
     * 409 — choque de regla de negocio (asiento ocupado, reserva activa
     * duplicada, fuera de ventana de reserva, etc.). El mensaje YA viene en
     * espanol desde el backend (literal del SIGNAL del SP) — mostrar tal
     * cual, no reinterpretar.
     */
    data class Conflict(val message: String) : AppError()

    /** 422 — validacion de campos. No deberia pasar si la app arma bien el request. */
    data class Validation(val field: String?, val message: String) : AppError()

    /** Sin conexion, timeout, DNS, etc. — nunca llego a haber respuesta HTTP. */
    data class Network(val message: String) : AppError()

    /** 500 o cualquier status no mapeado explicitamente. */
    data class Unknown(val message: String) : AppError()
}

/** Resultado de toda operacion que puede fallar contra el backend. */
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Failure(val error: AppError) : AppResult<Nothing>()
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(data)
    return this
}

inline fun <T> AppResult<T>.onFailure(action: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) action(error)
    return this
}
