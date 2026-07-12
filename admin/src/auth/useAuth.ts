// Composable de sesion — singleton a nivel de modulo (sin Pinia, decision de
// diseno). token+user viven en localStorage (sobreviven reload; JWT sin
// refresh token, tradeoff decidido — NUNCA cambiar a memoria sin revisar esa
// decision). `exp` se decodifica client-side solo para UX (countdown/banner);
// la autorizacion real la sigue validando el backend en cada request.
import { computed, ref } from 'vue'
import { useLocalStorage, useTimestamp } from '@vueuse/core'
import { request } from '../api/client'
import type { User } from '../types'

interface LoginResponse {
  token: string
  user: User
}

const token = useLocalStorage<string | null>('admin_token', null)
const user = useLocalStorage<User | null>('admin_user', null)

// Backstop reactivo del 401 (diseno #2): api/client.ts lo enciende, AppLayout
// lo escucha para mostrar el modal bloqueante "Sesión expirada".
const sessionExpired = ref(false)

// Reloj reactivo (1 tick/seg) para el countdown de expiracion — evita
// hand-rolled setInterval (regla de proyecto: vueuse antes que wrapper
// casero de browser APIs).
const now = useTimestamp({ interval: 1000 })

/** Decodifica el campo `exp` (unix seconds) del payload del JWT via atob —
 * sin libreria, alcanza para un solo campo. null si el token es invalido. */
function decodeExp(jwt: string): number | null {
  const payload = jwt.split('.')[1]
  if (!payload) return null
  try {
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const claims = JSON.parse(atob(base64)) as { exp?: number }
    return typeof claims.exp === 'number' ? claims.exp : null
  } catch {
    return null
  }
}

const expiresAtMs = computed(() => {
  if (!token.value) return null
  const exp = decodeExp(token.value)
  return exp ? exp * 1000 : null
})

const secondsLeft = computed(() => {
  if (!expiresAtMs.value) return 0
  return Math.max(0, Math.floor((expiresAtMs.value - now.value) / 1000))
})

const isAuthenticated = computed(() => !!token.value && secondsLeft.value > 0)

async function login(documentNumber: string, password: string): Promise<void> {
  const res = await request<LoginResponse>(
    'POST',
    '/auth/login',
    { document_number: documentNumber, password },
    { skipSessionExpiry: true }
  )
  token.value = res.token
  user.value = res.user
}

function logout(): void {
  token.value = null
  user.value = null
}

function triggerSessionExpired(): void {
  sessionExpired.value = true
}

function dismissSessionExpired(): void {
  sessionExpired.value = false
}

function getToken(): string | null {
  return token.value
}

export function useAuth() {
  return {
    user,
    isAuthenticated,
    secondsLeft,
    sessionExpired,
    login,
    logout,
    dismissSessionExpired,
  }
}

// Exports de funcion sueltos — los usa api/client.ts (import circular
// intencional: solo se invocan dentro de request(), nunca en tope de modulo,
// asi que el ciclo ESM resuelve sin problema).
export { getToken, logout, triggerSessionExpired }
