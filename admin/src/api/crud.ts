// Motor CRUD generico (Fase 4, tarea 4.1). useCrudResource<T> encapsula
// list/create/update contra un basePath del API admin, mapeando la forma
// paginada del backend ({items,page,page_size,total}) a estado reactivo.
// Sin delete: la baja logica es un update() completo con active:false (ver
// softDelete) porque el backend valida el struct entero en cada PUT
// (validate:"required" en los campos de negocio de *UpdateParams — no hay
// PATCH parcial; confirmado en backend/internal/modules/admin/repository.go
// y handler.go, que solo registran GET/POST/PUT, nunca DELETE).
import { ref, type Ref } from 'vue'
import { request, ApiError } from './client'
import type { PaginatedResponse } from '../types'

const PAGE_SIZE_DEFAULT = 20

/** Contrato (diseno "useCrudResource<T>"): list/create/update + estado de
 * paginacion offset reactivo. `create`/`update` reciben el body crudo que
 * arma el formulario (Record), no `Partial<T>` — dos razones concretas
 * grounded en el backend real:
 * 1. El PUT reemplaza el struct entero (sin PATCH parcial), asi que un
 *    `Partial<T>` real igual tendria que completarse antes de mandarlo.
 * 2. El create de `users` necesita `password`, un campo que NO existe en el
 *    tipo de lectura `User` (write-only, nunca vuelve en una respuesta) —
 *    `Partial<T>` no podria expresarlo sin ensuciar el tipo de lectura. */
export function useCrudResource<T extends { id: number }>(basePath: string) {
  const items = ref([]) as Ref<T[]>
  const page = ref(1)
  const pageSize = ref(PAGE_SIZE_DEFAULT)
  const total = ref(0)
  const loading = ref(false)
  const error = ref('')

  async function list(): Promise<void> {
    loading.value = true
    error.value = ''
    try {
      const res = await request<PaginatedResponse<T>>(
        'GET',
        `${basePath}?page=${page.value}&page_size=${pageSize.value}`
      )
      items.value = res.items
      page.value = res.page
      pageSize.value = res.page_size
      total.value = res.total
    } catch (err) {
      error.value = err instanceof ApiError ? err.message : 'No se pudo cargar el listado.'
      items.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  async function create(body: Record<string, unknown>): Promise<T> {
    return request<T>('POST', basePath, body)
  }

  async function update(id: number, body: Record<string, unknown>): Promise<void> {
    await request<void>('PUT', `${basePath}/${id}`, body)
  }

  /** Baja logica: nunca DELETE (no existe esa ruta en el backend). Reenvia
   * el item completo con `active:false` — un `{active:false}` suelto
   * pisaria los demas campos required del struct con zero-values y el
   * backend respondería 422. */
  async function softDelete(item: T): Promise<void> {
    await update(item.id, { ...item, active: false })
  }

  return { items, page, pageSize, total, loading, error, list, create, update, softDelete }
}
