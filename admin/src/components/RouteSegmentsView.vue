<script setup lang="ts">
// Segmentos de ruta — 6to recurso del rollout visual (ver memoria
// "admin/crud-visual-redesign-pattern"), y el peor caso de IDs crudos visto
// hasta ahora: from_route_stop_id/to_route_stop_id no apuntan a paradas
// directo, apuntan a `route_stops` (tabla intermedia SIN endpoint de listado
// plano — GET /admin/routes/{id}/stops es el unico camino, ver
// RouteStopsView.vue). Encima el trigger trg_route_segments_validate_insert/
// update (0001_schema.up.sql) exige que "hasta" sea EXACTAMENTE la parada
// siguiente de "desde" en la misma ruta — si no, SIGNAL '45000' (ahora
// traducido a 409 en repository.go, pero antes era un 500 pelado).
//
// Fix real, no solo cosmetico: en vez de dos <input type=number> a ciegas,
// el admin elige una ruta (contexto), despues solo elige "Desde" entre las
// paradas de esa ruta — "Hasta" y el orden del segmento se CALCULAN solos
// (la parada siguiente). Es imposible construir un tramo invalido desde la
// UI.
import { computed, onMounted, reactive, ref, watch } from 'vue'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import Select from 'primevue/select'
import ToggleSwitch from 'primevue/toggleswitch'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import { request, ApiError } from '../api/client'
import { LABELS } from '../messages'
import type { Route, RouteStop, Stop } from '../types'

interface RouteSegment {
  id: number
  route_id: number
  segment_order: number
  from_route_stop_id: number
  to_route_stop_id: number
  active: boolean
}

const toast = useToast()

// -- Rutas: selector de contexto (arriba de todo, como RouteStopsView) --
const routes = ref<Route[]>([])
const loadingRoutes = ref(false)
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
  loadSegmentIdsWithTimes()
})
const routesError = ref('')

// -- Candado de integridad: un tramo que ya tiene tiempos cargados en la
// matriz (route_segment_travel_times) no puede reasignar sus paradas — el
// backend no lo impide (la FK protege el id, no from/to_route_stop_id), asi
// que cambiar "Desde" de un tramo existente dejaria minutos calculados para
// una conexion fisica que ya no es la real, sin ningun aviso. Se resuelve
// en el cliente reusando /admin/segment-times (mismo endpoint que
// SegmentTimesView.vue) para saber que tramos ya tienen celdas cargadas.
const segmentIdsWithTimes = ref<Set<number>>(new Set())

async function loadSegmentIdsWithTimes(): Promise<void> {
  try {
    const res = await request<{ items: Array<{ route_segment_id: number }> }>(
      'GET',
      '/admin/segment-times?page=1&page_size=200'
    )
    segmentIdsWithTimes.value = new Set(res.items.map((t) => t.route_segment_id))
  } catch {
    // Degradado: si falla, el candado queda deshabilitado — no bloquea la pantalla.
  }
}

// -- Paradas de la ruta elegida: base de todo lo demas --
const routeStops = ref<RouteStop[]>([])
const loadingRouteStops = ref(false)
const stops = ref<Stop[]>([])
const stopNameById = computed(() => new Map(stops.value.map((s) => [s.id, s.name])))

function routeStopLabel(routeStopId: number): string {
  const rs = routeStops.value.find((r) => r.id === routeStopId)
  if (!rs) return `Parada de ruta #${routeStopId} (no encontrada)`
  const name = stopNameById.value.get(rs.stop_id) ?? `parada #${rs.stop_id}`
  return `${rs.stop_order}. ${name}`
}

async function loadRouteStops(routeId: number): Promise<void> {
  loadingRouteStops.value = true
  try {
    const [stopsRes, routeStopsRes] = await Promise.all([
      stops.value.length ? Promise.resolve(null) : request<{ items: Stop[] }>('GET', '/admin/stops?page=1&page_size=100'),
      request<{ items: RouteStop[] }>('GET', `/admin/routes/${routeId}/stops?page=1&page_size=100`),
    ])
    if (stopsRes) stops.value = stopsRes.items
    routeStops.value = routeStopsRes.items
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : 'No se pudieron cargar las paradas de la ruta.'
  } finally {
    loadingRouteStops.value = false
  }
}

// -- Segmentos: el backend no filtra por ruta (GET /admin/route-segments no
// acepta route_id), asi que se trae todo una vez y se filtra en el cliente
// — volumen chico para un MVP (cada ruta tiene N-1 segmentos de N paradas). --
const allSegments = ref<RouteSegment[]>([])
const loading = ref(false)
const error = ref('')

const segments = computed(() =>
  allSegments.value
    .filter((s) => s.route_id === selectedRouteId.value)
    .sort((a, b) => a.segment_order - b.segment_order)
)

async function loadSegments(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const res = await request<{ items: RouteSegment[] }>('GET', '/admin/route-segments?page=1&page_size=200')
    allSegments.value = res.items
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : 'No se pudo cargar el listado.'
    allSegments.value = []
  } finally {
    loading.value = false
  }
}

watch(selectedRouteId, async (routeId) => {
  if (routeId === null) return
  await Promise.all([loadRouteStops(routeId), loadSegments()])
  loadSegmentIdsWithTimes()
})

// ---------------------------------------------------------------------------
// Alta / edicion — "Desde" es la unica decision real; "Hasta" y el orden se
// derivan de la lista ordenada de paradas de la ruta.
// ---------------------------------------------------------------------------
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const fromRouteStopId = ref<number | null>(null)
const active = ref(true)
const submitting = ref(false)
const formErrorMessage = ref('')
const fieldErrors = reactive<Record<string, string>>({})

const editLocked = computed(
  () => editingId.value !== null && segmentIdsWithTimes.value.has(editingId.value)
)

// Ordenadas por stop_order (el backend ya las devuelve asi, ver
// ListRouteStops: "ORDER BY stop_order").
const orderedRouteStops = computed(() => [...routeStops.value].sort((a, b) => a.stop_order - b.stop_order))

// "Desde" nunca puede ser la ultima parada — no hay a donde ir.
const fromOptions = computed(() =>
  orderedRouteStops.value.slice(0, -1).map((rs) => ({ value: rs.id, label: routeStopLabel(rs.id) }))
)

const toRouteStop = computed<RouteStop | null>(() => {
  if (fromRouteStopId.value === null) return null
  const from = routeStops.value.find((r) => r.id === fromRouteStopId.value)
  if (!from) return null
  return orderedRouteStops.value.find((r) => r.stop_order === from.stop_order + 1) ?? null
})

const computedSegmentOrder = computed(() => {
  const from = routeStops.value.find((r) => r.id === fromRouteStopId.value)
  return from?.stop_order ?? null
})

function resetFormState(): void {
  formErrorMessage.value = ''
  for (const key of Object.keys(fieldErrors)) delete fieldErrors[key]
}

function openCreate(): void {
  editingId.value = null
  resetFormState()
  fromRouteStopId.value = null
  active.value = true
  dialogVisible.value = true
}

function openEdit(row: RouteSegment): void {
  editingId.value = row.id
  resetFormState()
  fromRouteStopId.value = row.from_route_stop_id
  active.value = row.active
  dialogVisible.value = true
}

function closeDialog(): void {
  dialogVisible.value = false
}

async function onSubmit(): Promise<void> {
  resetFormState()
  if (fromRouteStopId.value === null || toRouteStop.value === null || computedSegmentOrder.value === null) {
    fieldErrors.from = 'Elegí la parada de origen.'
    return
  }
  if (selectedRouteId.value === null) return

  submitting.value = true
  const wasCreate = editingId.value === null
  const body = {
    route_id: selectedRouteId.value,
    segment_order: computedSegmentOrder.value,
    from_route_stop_id: fromRouteStopId.value,
    to_route_stop_id: toRouteStop.value.id,
    active: active.value,
  }
  try {
    if (wasCreate) {
      await request('POST', '/admin/route-segments', body)
    } else {
      await request('PUT', `/admin/route-segments/${editingId.value}`, body)
    }
    dialogVisible.value = false
    await loadSegments()
    toast.add({ severity: 'success', summary: wasCreate ? LABELS.created : LABELS.updated, life: 4000 })
  } catch (err) {
    formErrorMessage.value = err instanceof ApiError ? err.message : 'Ocurrió un error inesperado.'
  } finally {
    submitting.value = false
  }
}

// ---------------------------------------------------------------------------
// Baja logica — RouteSegment SÍ tiene columna `active` real.
// ---------------------------------------------------------------------------
const confirmTarget = ref<RouteSegment | null>(null)
const deactivating = ref(false)

function askDeactivate(row: RouteSegment): void {
  confirmTarget.value = row
}

function cancelDeactivate(): void {
  confirmTarget.value = null
}

async function confirmDeactivate(): Promise<void> {
  if (!confirmTarget.value) return
  deactivating.value = true
  const seg = confirmTarget.value
  try {
    await request('PUT', `/admin/route-segments/${seg.id}`, {
      route_id: seg.route_id,
      segment_order: seg.segment_order,
      from_route_stop_id: seg.from_route_stop_id,
      to_route_stop_id: seg.to_route_stop_id,
      active: false,
    })
    confirmTarget.value = null
    await loadSegments()
    toast.add({ severity: 'success', summary: LABELS.deactivated, life: 4000 })
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'No se pudo desactivar',
      detail: err instanceof ApiError ? err.message : 'Ocurrió un error inesperado.',
      life: 6000,
    })
  } finally {
    deactivating.value = false
  }
}
</script>

<template>
  <section class="segments-view">
    <header class="segments-header">
      <div>
        <h1>Segmentos de ruta</h1>
        <p class="segments-subtitle">Tramos entre paradas consecutivas — la base para calcular tiempos de viaje.</p>
      </div>
    </header>

    <div class="segments-filter">
      <label for="segments-route-select">Ruta</label>
      <Select
        id="segments-route-select"
        v-model="selectedRouteId"
        :options="routes"
        optionLabel="name"
        optionValue="id"
        placeholder="Elija una ruta para ver sus tramos"
        :loading="loadingRoutes"
        filter
      />
    </div>

    <p v-if="routesError" role="alert" class="segments-error">{{ routesError }}</p>

    <template v-if="selectedRouteId !== null">
      <p v-if="error" role="alert" class="segments-error">
        {{ error }}
        <Button label="Reintentar" text size="small" @click="loadSegments" />
      </p>

      <p v-if="!loadingRouteStops && fromOptions.length === 0" class="segments-hint">
        Esta ruta necesita al menos 2 paradas cargadas en "Paradas de ruta" antes de poder armar tramos.
      </p>
      <Button
        v-else
        label="Nuevo tramo"
        icon="pi pi-plus"
        :disabled="loadingRouteStops"
        @click="openCreate"
      />

      <DataTable :value="segments" :loading="loading || loadingRouteStops" dataKey="id" class="segments-table">
        <template #empty>
          <div class="segments-empty">
            <i class="pi pi-sitemap segments-empty-icon" aria-hidden="true"></i>
            <p>Esta ruta todavía no tiene tramos.</p>
          </div>
        </template>

        <Column field="segment_order" header="Orden" />

        <Column header="Desde">
          <template #body="{ data }">{{ routeStopLabel(data.from_route_stop_id) }}</template>
        </Column>

        <Column header="Hasta">
          <template #body="{ data }">{{ routeStopLabel(data.to_route_stop_id) }}</template>
        </Column>

        <Column header="Estado">
          <template #body="{ data }">
            <Tag
              :value="data.active ? 'Activo' : 'Inactivo'"
              :icon="data.active ? 'pi pi-check-circle' : 'pi pi-ban'"
              :severity="data.active ? 'success' : 'danger'"
            />
          </template>
        </Column>

        <Column header="Acciones" :exportable="false">
          <template #body="{ data }">
            <div class="segments-actions">
              <Button icon="pi pi-pencil" text rounded aria-label="Editar tramo" @click="openEdit(data)" />
              <Button
                icon="pi pi-ban"
                text
                rounded
                severity="danger"
                aria-label="Desactivar tramo"
                @click="askDeactivate(data)"
              />
            </div>
          </template>
        </Column>
      </DataTable>
    </template>
    <p v-else>Elija una ruta arriba para ver y administrar sus tramos.</p>

    <Dialog
      v-model:visible="dialogVisible"
      modal
      :header="editingId === null ? 'Nuevo tramo' : 'Editar tramo'"
      :style="{ width: '30rem' }"
    >
      <form novalidate @submit.prevent="onSubmit">
        <p v-if="formErrorMessage" role="alert" class="form-error">{{ formErrorMessage }}</p>

        <div class="field">
          <label for="segment-from">Desde <span class="required-mark" aria-hidden="true">*</span></label>
          <Select
            id="segment-from"
            v-model="fromRouteStopId"
            :options="fromOptions"
            optionLabel="label"
            optionValue="value"
            placeholder="Elegir parada de origen…"
            :disabled="editLocked"
            aria-required="true"
            :aria-invalid="!!fieldErrors.from"
            :aria-describedby="fieldErrors.from ? 'segment-from-error' : editLocked ? 'segment-from-locked' : undefined"
          />
          <p v-if="fieldErrors.from" id="segment-from-error" role="alert" class="field-error">
            {{ fieldErrors.from }}
          </p>
          <p v-else-if="editLocked" id="segment-from-locked" class="field-help field-locked">
            <i class="pi pi-lock" aria-hidden="true"></i> Este tramo ya tiene tiempos cargados en la matriz — no se
            puede reasignar sus paradas. Desactivalo y creá uno nuevo si necesitás otra conexión.
          </p>
          <p v-else class="field-help">
            "Hasta" y el orden del tramo se calculan solos: siempre es la parada siguiente en la ruta — el sistema no
            permite tramos entre paradas no consecutivas.
          </p>
        </div>

        <div v-if="toRouteStop" class="segment-preview">
          <span class="segment-preview-label">Tramo calculado</span>
          <div class="segment-preview-route">
            <span>{{ routeStopLabel(fromRouteStopId!) }}</span>
            <i class="pi pi-arrow-right" aria-hidden="true"></i>
            <span>{{ routeStopLabel(toRouteStop.id) }}</span>
          </div>
          <span class="segment-preview-order">Segmento {{ computedSegmentOrder }}</span>
        </div>

        <div class="field field-inline">
          <label for="segment-active" class="switch-label">
            <ToggleSwitch id="segment-active" v-model="active" />
            Tramo activo
          </label>
        </div>

        <div class="dialog-actions">
          <Button type="button" :label="LABELS.cancel" severity="secondary" text @click="closeDialog" />
          <Button type="submit" :label="LABELS.save" :loading="submitting" :disabled="!toRouteStop" />
        </div>
      </form>
    </Dialog>

    <Dialog :visible="confirmTarget !== null" modal :closable="false" header="Confirmar" :style="{ width: '24rem' }">
      <p>¿Desactivar el tramo {{ confirmTarget ? routeStopLabel(confirmTarget.from_route_stop_id) : '' }} →
        {{ confirmTarget ? routeStopLabel(confirmTarget.to_route_stop_id) : '' }}?</p>
      <template #footer>
        <Button :label="LABELS.cancel" severity="secondary" text @click="cancelDeactivate" />
        <Button
          :label="LABELS.deactivate"
          severity="danger"
          :loading="deactivating"
          autofocus
          @click="confirmDeactivate"
        />
      </template>
    </Dialog>
  </section>
</template>

<style scoped>
.segments-view {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}
.segments-header h1 {
  margin: 0 0 0.25rem;
}
.segments-subtitle {
  margin: 0;
  color: #52525b;
  font-size: 0.9375rem;
}
.segments-filter {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  max-width: 24rem;
}
.segments-filter label {
  font-weight: 600;
  font-size: 0.875rem;
}
.segments-error {
  color: #b91c1c;
  margin: 0;
}
.segments-hint {
  margin: 0;
  color: #71717a;
  font-size: 0.9375rem;
}
.segments-actions {
  display: flex;
  gap: 0.25rem;
}
.segments-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 2rem 0;
}
.segments-empty-icon {
  font-size: 1.75rem;
  color: #a1a1aa;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  margin-bottom: 1rem;
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
.field-error {
  color: #b91c1c;
  font-size: 0.875rem;
  margin: 0;
}
.field-locked {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  color: #b45309;
}
.field-inline {
  margin-bottom: 0.5rem;
}
.switch-label {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  font-weight: 600;
  width: fit-content;
}
.form-error {
  color: #b91c1c;
  font-size: 0.875rem;
  margin: 0 0 0.75rem;
}
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 0.25rem;
}

.segment-preview {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
  padding: 0.75rem 1rem;
  border-radius: 0.5rem;
  background: #ecfdf5;
  margin-bottom: 1rem;
}
.segment-preview-label {
  font-size: 0.75rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: #059669;
}
.segment-preview-route {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 600;
}
.segment-preview-order {
  font-size: 0.8125rem;
  color: #52525b;
}

@media (prefers-color-scheme: dark) {
  .segments-subtitle {
    color: #a1a1aa;
  }
  .segments-error,
  .form-error,
  .required-mark,
  .field-error {
    color: #fca5a5;
  }
  .segments-hint,
  .field-help,
  .segment-preview-order {
    color: #a1a1aa;
  }
  .field-locked {
    color: #fbbf24;
  }
  .segments-empty-icon {
    color: #71717a;
  }
  .segment-preview {
    background: #052e22;
  }
  .segment-preview-label {
    color: #34d399;
  }
}
</style>
