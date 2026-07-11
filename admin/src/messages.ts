// Strings de sistema en espanol neutro (unico locale — sin vue-i18n, ver
// decision de diseno #4) + mapa de codigo HTTP -> texto de respaldo.
//
// El backend ya devuelve `error.message` en espanol (ver
// `backend/internal/shared/apperror`), asi que el mapa de abajo solo se usa
// como respaldo cuando no hay mensaje del servidor (error de red, timeout,
// respuesta sin cuerpo). `error.code` en esa respuesta es el status HTTP
// (numero), no un codigo de dominio propio.

export const APP_TITLE = 'Panel administrativo'

export const LABELS = {
  loading: 'Cargando…',
  save: 'Guardar',
  cancel: 'Cancelar',
  edit: 'Editar',
  deactivate: 'Desactivar',
  confirm: 'Confirmar',
  empty: 'No hay registros para mostrar.',
} as const

const ERROR_BY_STATUS: Record<number, string> = {
  401: 'Sesión expirada. Iniciá sesión nuevamente.',
  403: 'No tenés permisos para realizar esta acción.',
  404: 'El recurso solicitado no existe.',
  409: 'La operación entra en conflicto con datos existentes.',
  422: 'Hay campos inválidos en el formulario.',
  500: 'Error interno del servidor.',
}

const ERROR_FALLBACK_DEFAULT = 'Ocurrió un error inesperado. Intentá nuevamente.'
export const ERROR_NETWORK = 'No se pudo conectar con el servidor. Verificá tu conexión.'

/** Resuelve el texto a mostrar para un error de API: prioriza el mensaje del
 * servidor (ya en espanol) y cae al mapa por status, luego al default. */
export function getErrorMessage(status: number, serverMessage?: string): string {
  return serverMessage || ERROR_BY_STATUS[status] || ERROR_FALLBACK_DEFAULT
}
