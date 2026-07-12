<script setup lang="ts">
// Paradas de ruta — 7mo recurso del rollout visual (ver memoria
// "admin/crud-visual-redesign-pattern"). Reemplaza la version anterior que
// delegaba TODO a CrudView + routeStopsConfig — esa combinacion tenia 3
// problemas reales, no solo esteticos:
//
// 1. `stop_id` se mostraba como numero crudo (mismo patron ya visto en
//    vehicle-seats/users/routes). Fix: Select con nombre real, lookup contra
//    /admin/stops.
// 2. `route_id` era un campo del FORMULARIO que el admin tenia que
//    volver a tipear — a pesar de que la pantalla YA sabe que ruta es (el
//    selector de arriba). Se saca del formulario, se completa solo desde el
//    contexto.
// 3. route_stops NO tiene columna `active` (confirmado: RouteStop struct en
//    repository.go no la tiene) — el boton "Desactivar" generico de
//    CrudView mandaba {...item, active:false} igual que en vehicle-seats, y
//    fallaba en silencio de la misma forma. Como esta tabla tampoco tiene
//    DELETE ni ningun otro mecanismo de baja, directamente no hay accion de
//    "borrar" en esta vista — el CRUD real es Alta + Edicion, nada mas.
//
// Bonus: trg_route_stops_protect_structure (0001_schema.up.sql) bloquea
// cambiar route_id/stop_id/stop_order de una parada que YA tiene tramos
// armados en route_segments. Antes eso daba 500 (fix del traductor en
// repository.go, mismo mecanismo que route-segments). Acá ademas se detecta
// del lado del cliente: si la parada esta "trabada", esos 3 campos se
// deshabilitan con una explicacion, en vez de dejar que el usuario los
// edite y se choque con el error.
import { computed, onMounted, reactive, ref, watch } from 'vue'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputNumber from 'primevue/inputnumber'
import Select from 'primevue/select'
import ToggleSwitch from 'primevue/toggleswitch'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import { request, ApiError } from '../api/client'
import { LABELS } from '../messages'
import type { Route, RouteStop, Stop } from '../types'

interface RouteSegmentLite {
  id: number
  route_id: number
  from_route_stop_id: number
  to_route_stop_id: number
}

const toast = useToast()

// -- Ruta: selector de contexto --
const routes = ref<Route[]>([])
const loadingRoutes = ref(false)
const routesError = ref('')
const selectedRouteId = ref<number | null>(null)

onMounted(async () => {
  loadingRoutes.value = true
  try {
    const res = await request<{ items: Route[] }>('GET', '/admin/routes?page=1&page_size=100')
    routes.value = res.items
  } catch (err) {
    routesError.value = err instanceof ApiError ? err.message : 'No se pudieron cargar las rutas.'
  } finally {
    loadingRoutes.value = false
  }
})

// -- Paradas (catalogo global, para el Select del formulario) --
const stops = ref<Stop[]>([])
const loadingStops = ref(false)
const stopById = computed(() => new Map(stops.value.map((s) => [s.id, s])))
const stopOptions = computed(() => stops.value.map((s) => ({ value: s.id, label: s.name })))

function stopLabel(stopId: number): string {
  return stopById.value.get(stopId)?.name ?? `Parada #${stopId} (no encontrada)`
}

// -- Paradas de la ruta elegida --
const routeStops = ref<RouteStop[]>([])
const loading = ref(false)
const error = ref('')

async function loadRouteStops(routeId: number): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const res = await request<{ items: RouteStop[] }>('GET', `/admin/routes/${routeId}/stops?page=1&page_size=100`)
    routeStops.value = res.items
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : 'No se pudo cargar el listado.'
    routeStops.value = []
  } finally {
    loading.value = false
  }
}

// -- Segmentos de la ruta: solo para saber que paradas estan "trabadas" --
const lockedRouteStopIds = ref<Set<number>>(new Set())

async function loadLocks(routeId: number): Promise<void> {
  try {
    const res = await request<{ items: RouteSegmentLite[] }>('GET', '/admin/route-segments?page=1&page_size=200')
    const locked = new Set<number>()
    for (const seg of res.items) {
      if (seg.route_id !== routeId) continue
      locked.add(seg.from_route_stop_id)
      locked.add(seg.to_route_stop_id)
    }
    lockedRouteStopIds.value = locked
  } catch {
    // Degrada a "nada trabado" — en el peor caso el backend igual rechaza
    // el cambio (ya traducido a un mensaje claro, no un 500).
    lockedRouteStopIds.value = new Set()
  }
}

watch(selectedRouteId, async (routeId) => {
  if (routeId === null) return
  if (!stops.value.length) {
    loadingStops.value = true
    try {
      const res = await request<{ items: Stop[] }>('GET', '/admin/stops?page=1&page_size=100')
      stops.value = res.items
    } catch {
      // El lookup queda degradado, la tabla sigue usable.
    } finally {
      loadingStops.value = false
    }
  }
  await Promise.all([loadRouteStops(routeId), loadLocks(routeId)])
})

// ---------------------------------------------------------------------------
// Alta / edicion
// ---------------------------------------------------------------------------
type FormState = {
  stop_id: number | null
  stop_order: number | null
  dwell_minutes: number
  pickup_allowed: boolean
  dropoff_allowed: boolean
}

function blankForm(): FormState {
  return {
    stop_id: null,
    stop_order: routeStops.value.length + 1,
    dwell_minutes: 0,
    pickup_allowed: true,
    dropoff_allowed: true,
  }
}

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formData = reactive<FormState>(blankForm())
const fieldErrors = reactive<Record<string, string>>({})
const formErrorMessage = ref('')
const submitting = ref(false)

const isLocked = computed(() => editingId.value !== null && lockedRouteStopIds.value.has(editingId.value))

function resetFormState(): void {
  formErrorMessage.value = ''
  for (const key of Object.keys(fieldErrors)) delete fieldErrors[key]
}

function openCreate(): void {
  editingId.value = null
  resetFormState()
  Object.assign(formData, blankForm())
  dialogVisible.value = true
}

function openEdit(row: RouteStop): void {
  editingId.value = row.id
  resetFormState()
  formData.stop_id = row.stop_id
  formData.stop_order = row.stop_order
  formData.dwell_minutes = row.dwell_minutes
  formData.pickup_allowed = row.pickup_allowed
  formData.dropoff_allowed = row.dropoff_allowed
  dialogVisible.value = true
}

function closeDialog(): void {
  dialogVisible.value = false
}

function validateClientSide(): boolean {
  if (!formData.stop_id) {
    fieldErrors.stop_id = LABELS.requiredField
    return false
  }
  if (!formData.stop_order || formData.stop_order <= 0) {
    fieldErrors.stop_order = 'Debe ser mayor a 0.'
    return false
  }
  if (formData.dwell_minutes < 0) {
    fieldErrors.dwell_minutes = 'No puede ser negativo.'
    return false
  }
  return true
}

const FIELD_ERROR_RE = /^campo (\w+) invalido:/i
const SERVER_FIELD_MAP: Record<string, keyof FormState> = {
  stopid: 'stop_id',
  stoporder: 'stop_order',
  dwellminutes: 'dwell_minutes',
  pickupallowed: 'pickup_allowed',
  dropoffallowed: 'dropoff_allowed',
}

function mapServerFieldError(message: string): string | null {
  const match = FIELD_ERROR_RE.exec(message)
  if (!match) return null
  return SERVER_FIELD_MAP[match[1].toLowerCase()] ?? null
}

async function onSubmit(): Promise<void> {
  resetFormState()
  if (!validateClientSide()) return
  if (selectedRouteId.value === null) return

  submitting.value = true
  const wasCreate = editingId.value === null
  const body = {
    route_id: selectedRouteId.value,
    stop_id: formData.stop_id,
    stop_order: formData.stop_order,
    dwell_minutes: formData.dwell_minutes,
    pickup_allowed: formData.pickup_allowed,
    dropoff_allowed: formData.dropoff_allowed,
  }
  try {
    if (wasCreate) {
      await request('POST', '/admin/route-stops', body)
    } else {
      await request('PUT', `/admin/route-stops/${editingId.value}`, body)
    }
    dialogVisible.value = false
    await Promise.all([loadRouteStops(selectedRouteId.value), loadLocks(selectedRouteId.value)])
    toast.add({ severity: 'success', summary: wasCreate ? LABELS.created : LABELS.updated, life: 4000 })
  } catch (err) {
    if (err instanceof ApiError && err.code === 422) {
      const key = mapServerFieldError(err.message)
      if (key) fieldErrors[key] = err.message
      else formErrorMessage.value = err.message
    } else {
      formErrorMessage.value = err instanceof ApiError ? err.message : 'Ocurrió un error inesperado.'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="route-stops-view">
    <header class="route-stops-header">
      <div>
        <h1>Paradas de ruta</h1>
        <p class="route-stops-subtitle">Qué paradas tiene cada ruta, en qué orden, y si se puede subir o bajar ahí.</p>
      </div>
    </header>

    <div class="route-stops-filter">
      <label for="route-stops-route-select">Ruta</label>
      <Select
        id="route-stops-route-select"
        v-model="selectedRouteId"
        :options="routes"
        optionLabel="name"
        optionValue="id"
        placeholder="Elija una ruta para ver sus paradas"
        :loading="loadingRoutes"
        filter
      />
    </div>

    <p v-if="routesError" role="alert" class="route-stops-error">{{ routesError }}</p>

    <template v-if="selectedRouteId !== null">
      <p v-if="error" role="alert" class="route-stops-error">
        {{ error }}
        <Button label="Reintentar" text size="small" @click="loadRouteStops(selectedRouteId)" />
      </p>

      <Button label="Agregar parada" icon="pi pi-plus" @click="openCreate" />

      <DataTable :value="routeStops" :loading="loading || loadingStops" dataKey="id" class="route-stops-table">
        <template #empty>
          <div class="route-stops-empty">
            <i class="pi pi-map route-stops-empty-icon" aria-hidden="true"></i>
            <p>Esta ruta todavía no tiene paradas.</p>
          </div>
        </template>

        <Column field="stop_order" header="Orden" />

        <Column header="Parada">
          <template #body="{ data }">{{ stopLabel(data.stop_id) }}</template>
        </Column>

        <Column header="Espera">
          <template #body="{ data }">{{ data.dwell_minutes }} min</template>
        </Column>

        <Column header="Subida">
          <template #body="{ data }">
            <Tag
              :value="data.pickup_allowed ? 'Sí' : 'No'"
              :severity="data.pickup_allowed ? 'success' : 'secondary'"
            />
          </template>
        </Column>

        <Column header="Bajada">
          <template #body="{ data }">
            <Tag
              :value="data.dropoff_allowed ? 'Sí' : 'No'"
              :severity="data.dropoff_allowed ? 'success' : 'secondary'"
            />
          </template>
        </Column>

        <Column header="Acciones" :exportable="false">
          <template #body="{ data }">
            <Button icon="pi pi-pencil" text rounded aria-label="Editar parada de ruta" @click="openEdit(data)" />
          </template>
        </Column>
      </DataTable>
    </template>
    <p v-else>Elija una ruta arriba para ver y administrar sus paradas.</p>

    <Dialog
      v-model:visible="dialogVisible"
      modal
      :header="editingId === null ? 'Agregar parada' : 'Editar parada de ruta'"
      :style="{ width: '32rem' }"
    >
      <form novalidate @submit.prevent="onSubmit">
        <p v-if="formErrorMessage" role="alert" class="form-error">{{ formErrorMessage }}</p>
        <p v-if="isLocked" class="route-stops-locked-hint">
          <i class="pi pi-lock" aria-hidden="true"></i>
          Esta parada ya tiene tramos armados en "Segmentos de ruta" — no se puede cambiar cuál parada es ni su
          orden. Borrá los tramos primero si necesitás reordenar.
        </p>

        <fieldset class="field-group">
          <legend>Identificación</legend>
          <div class="field-grid">
            <div class="field">
              <label for="rs-stop">Parada <span class="required-mark" aria-hidden="true">*</span></label>
              <Select
                id="rs-stop"
                v-model="formData.stop_id"
                :options="stopOptions"
                optionLabel="label"
                optionValue="value"
                placeholder="Elegir…"
                filter
                :disabled="isLocked"
                :loading="loadingStops"
                aria-required="true"
                :aria-invalid="!!fieldErrors.stop_id"
                :aria-describedby="fieldErrors.stop_id ? 'rs-stop-error' : undefined"
              />
              <p v-if="fieldErrors.stop_id" id="rs-stop-error" role="alert" class="field-error">
                {{ fieldErrors.stop_id }}
              </p>
            </div>
            <div class="field">
              <label for="rs-order">Orden <span class="required-mark" aria-hidden="true">*</span></label>
              <InputNumber
                inputId="rs-order"
                v-model="formData.stop_order"
                :min="1"
                :useGrouping="false"
                :disabled="isLocked"
                aria-required="true"
                :aria-invalid="!!fieldErrors.stop_order"
                :aria-describedby="fieldErrors.stop_order ? 'rs-order-error' : undefined"
              />
              <p v-if="fieldErrors.stop_order" id="rs-order-error" role="alert" class="field-error">
                {{ fieldErrors.stop_order }}
              </p>
            </div>
          </div>
        </fieldset>

        <fieldset class="field-group">
          <legend>Operación en esta parada</legend>
          <div class="field">
            <label for="rs-dwell">Minutos de espera</label>
            <InputNumber
              inputId="rs-dwell"
              v-model="formData.dwell_minutes"
              :min="0"
              :useGrouping="false"
              :aria-invalid="!!fieldErrors.dwell_minutes"
              :aria-describedby="fieldErrors.dwell_minutes ? 'rs-dwell-error' : 'rs-dwell-help'"
            />
            <p id="rs-dwell-help" class="field-help">Cuánto se queda el vehículo detenido en esta parada.</p>
            <p v-if="fieldErrors.dwell_minutes" id="rs-dwell-error" role="alert" class="field-error">
              {{ fieldErrors.dwell_minutes }}
            </p>
          </div>
          <div class="field-grid">
            <label class="switch-label">
              <ToggleSwitch v-model="formData.pickup_allowed" />
              Permite que suban acá
            </label>
            <label class="switch-label">
              <ToggleSwitch v-model="formData.dropoff_allowed" />
              Permite que bajen acá
            </label>
          </div>
        </fieldset>

        <div class="dialog-actions">
          <Button type="button" :label="LABELS.cancel" severity="secondary" text @click="closeDialog" />
          <Button type="submit" :label="LABELS.save" :loading="submitting" />
        </div>
      </form>
    </Dialog>
  </section>
</template>

<style scoped>
.route-stops-view {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}
.route-stops-header h1 {
  margin: 0 0 0.25rem;
}
.route-stops-subtitle {
  margin: 0;
  color: #52525b;
  font-size: 0.9375rem;
}
.route-stops-filter {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  max-width: 24rem;
}
.route-stops-filter label {
  font-weight: 600;
  font-size: 0.875rem;
}
.route-stops-error {
  color: #b91c1c;
  margin: 0;
}
.route-stops-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 2rem 0;
}
.route-stops-empty-icon {
  font-size: 1.75rem;
  color: #a1a1aa;
}
.route-stops-locked-hint {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  border-radius: 0.5rem;
  background: #fffbeb;
  color: #92400e;
  font-size: 0.875rem;
  margin: 0 0 1rem;
}

.field-group {
  border: 0;
  padding: 0;
  margin: 0 0 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.875rem;
}
.field-group legend {
  padding: 0 0 0.5rem;
  font-size: 0.75rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: #059669;
}
.field-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.875rem;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}
.field label {
  font-weight: 600;
  font-size: 0.9375rem;
}
.field-help {
  margin: 0;
  font-size: 0.8125rem;
  color: #71717a;
}
.required-mark {
  color: #b91c1c;
}
.switch-label {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  font-weight: 600;
  font-size: 0.9375rem;
}
.field-error,
.form-error {
  color: #b91c1c;
  font-size: 0.875rem;
  margin: 0;
}
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 0.25rem;
}

@media (max-width: 30rem) {
  .field-grid {
    grid-template-columns: 1fr;
  }
}

@media (prefers-color-scheme: dark) {
  .route-stops-subtitle {
    color: #a1a1aa;
  }
  .route-stops-error,
  .field-error,
  .form-error,
  .required-mark {
    color: #fca5a5;
  }
  .field-help {
    color: #a1a1aa;
  }
  .route-stops-empty-icon {
    color: #71717a;
  }
  .field-group legend {
    color: #34d399;
  }
  .route-stops-locked-hint {
    background: #451a03;
    color: #fcd34d;
  }
}
</style>
