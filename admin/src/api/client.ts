// Wrapper unico sobre fetch (diseno "Contrato auth + fetch wrapper", Fase 3).
// Chokepoint unico: agrega el header Bearer, parsea el shape de error del
// backend ({"error":{"code","message"}}, ver apperror.WriteJSONError) y
// centraliza el 401 (backstop reactivo de sesion expirada). Ningun componente
// llama fetch() directo — todo pasa por request().
import { getToken, logout, triggerSessionExpired } from '../auth/useAuth'
import { getErrorMessage, ERROR_NETWORK } from '../messages'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api'

export class ApiError extends Error {
  code: number
  constructor(code: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

// El AppLayout escucha `useAuth().sessionExpired` (seteado por
// triggerSessionExpired) para mostrar el modal bloqueante — este error es
// principalmente para que el `catch` del componente que disparo el request
// pueda cortar su propio flujo (ej. no seguir un submit).
export class SessionExpiredError extends Error {
  constructor() {
    super('sesión expirada')
    this.name = 'SessionExpiredError'
  }
}

interface RequestOptions {
  /** true solo en /auth/login: un 401 ahi es "credenciales invalidas" (no
   * hay sesion todavia que haya expirado) — no debe disparar el modal. */
  skipSessionExpiry?: boolean
}

/** GET/POST/PUT contra el API admin. 200/201→json, 204→void, 401 (fuera de
 * login)→logout()+dispara el modal+throw SessionExpiredError, 4xx/5xx→throw
 * ApiError(status, mensaje ya en espanol tomado de la respuesta del server). */
export async function request<T = void>(
  method: string,
  path: string,
  body?: unknown,
  opts: RequestOptions = {}
): Promise<T> {
  let res: Response
  try {
    const token = getToken()
    res = await fetch(`${API_BASE}${path}`, {
      method,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new ApiError(0, ERROR_NETWORK)
  }

  if (res.status === 401 && !opts.skipSessionExpiry) {
    logout()
    triggerSessionExpired()
    throw new SessionExpiredError()
  }

  if (!res.ok) {
    throw new ApiError(res.status, await extractMessage(res))
  }

  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

async function extractMessage(res: Response): Promise<string> {
  try {
    const data = (await res.json()) as { error?: { message?: string } }
    return getErrorMessage(res.status, data.error?.message)
  } catch {
    return getErrorMessage(res.status)
  }
}
