<script setup lang="ts">
// Tiempos de segmento — la matriz manual de tiempos (route_segment_travel_times),
// que el dominio modela como una MATRIZ tramo × perfil horario, no como una
// lista plana de celdas sueltas (ver Documentacion/diccionario_datos_*.md §5.9
// y vw_route_time_matrix). Cada celda = minutos de un tramo bajo un perfil.
//
// El generador de viajes NO usa el perfil que el admin elige a mano: elige
// solo el perfil aplicable por dia/hora/vigencia/prioridad
// (fn_select_travel_time_profile en 0001_schema.up.sql). Por eso el riesgo
// real que marca el diccionario es "todo tramo necesita al menos un perfil
// aplicable a todo momento, si no el horario queda con huecos".
//
// Diseno de esta pantalla (elegido con el usuario): selector de ruta ->
// grilla filas=tramos, columnas=perfiles, celda=minutos editables inline.
// - Columnas mostradas: solo perfiles YA usados en la ruta + los `is_default`.
//   Un perfil sin uso en la ruta no ensucia la grilla con una columna vacia.
// - Celda vacia dentro de una columna mostrada = hueco probable -> ⚠, porque
//   ese perfil ya se usa en otros tramos de la misma ruta.
// - Click en celda = editar/crear con tramo+perfil ya fijados (nunca se elige
//   un id a ciegas, que era la queja original).
import { computed, onMounted, reactive, ref, watch } from 'vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputNumber from 'primevue/inputnumber'
import Textarea from 'primevue/textarea'
import Select from 'primevue/select'
import ProgressSpinner from 'primevue/progressspinner'
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

interface TravelTimeProfile {
  id: number
  code: string
  name: string
  valid_from?: string | null
  valid_until?: string | null
  start_time?: string | null
  end_time?: string | null
  is_all_day: boolean
  monday: boolean
  tuesday: boolean
  wednesday: boolean
  thursday: boolean
  friday: boolean
  saturday: boolean
  sunday: boolean
  priority: number
  is_default: boolean
  active: boolean
}

interface SegmentTravelTime {
  id: number
  route_segment_id: number
  profile_id: number
  travel_minutes: number
  notes?: string | null
}

const toast = useToast()

// -- Ruta: selector de contexto (arriba de todo) --
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

// -- Datos de la ruta elegida --
const routeSegments = ref<RouteSegment[]>([])
const routeStops = ref<RouteStop[]>([])
const stops = ref<Stop[]>([])
const profiles = ref<TravelTimeProfile[]>([])
const times = ref<SegmentTravelTime[]>([])
const loading = ref(false)
const error = ref('')

const stopNameById = computed(() => new Map(stops.value.map((s) => [s.id, s.name])))

function stopNameForRouteStop(routeStopId: number): string {
  const rs = routeStops.value.find((r) => r.id === routeStopId)
  if (!rs) return `parada #${routeStopId}`
  return stopNameById.value.get(rs.stop_id) ?? `parada #${rs.stop_id}`
}

// Filas: tramos de la ruta, ordenados por segment_order.
const segmentRows = computed(() =>
  [...routeSegments.value].sort((a, b) => a.segment_order - b.segment_order)
)

function segmentLabel(seg: RouteSegment): string {
  return `${stopNameForRouteStop(seg.from_route_stop_id)} → ${stopNameForRouteStop(seg.to_route_stop_id)}`
}

// Celdas indexadas por "segmentId:profileId" -> registro (con su id para editar).
const cellByKey = computed(() => {
  const map = new Map<string, SegmentTravelTime>()
  for (const t of times.value) map.set(`${t.route_segment_id}:${t.profile_id}`, t)
  return map
})

function cellFor(segmentId: number, profileId: number): SegmentTravelTime | undefined {
  return cellByKey.value.get(`${segmentId}:${profileId}`)
}

// Columnas: perfiles YA usados en algun tramo de esta ruta + los default.
// Orden: default primero, luego prioridad desc, luego id (mismo criterio de
// desempate que fn_select_travel_time_profile).
const segmentIdSet = computed(() => new Set(routeSegments.value.map((s) => s.id)))
const usedProfileIds = computed(() => {
  const set = new Set<number>()
  for (const t of times.value) {
    if (segmentIdSet.value.has(t.route_segment_id)) set.add(t.profile_id)
  }
  return set
})

const profileColumns = computed(() =>
  profiles.value
    .filter((p) => p.is_default || usedProfileIds.value.has(p.id))
    .sort((a, b) => {
      if (a.is_default !== b.is_default) return a.is_default ? -1 : 1
      if (a.priority !== b.priority) return b.priority - a.priority
      return a.id - b.id
    })
)

const DAY_ABBR = ['L', 'M', 'X', 'J', 'V', 'S', 'D'] // lunes..domingo
function daysSummary(p: TravelTimeProfile): string {
  const flags = [p.monday, p.tuesday, p.wednesday, p.thursday, p.friday, p.saturday, p.sunday]
  const weekdays = flags.slice(0, 5).every(Boolean) && !p.saturday && !p.sunday
  const weekend = p.saturday && p.sunday && !flags.slice(0, 5).some(Boolean)
  if (flags.every(Boolean)) return 'L a D'
  if (weekdays) return 'L a V'
  if (weekend) return 'S y D'
  return flags.map((on, i) => (on ? DAY_ABBR[i] : null)).filter(Boolean).join('·') || 'sin días'
}

function hhmm(t?: string | null): string {
  return t ? t.slice(0, 5) : ''
}
function timeSummary(p: TravelTimeProfile): string {
  if (p.is_all_day) return 'todo el día'
  if (p.start_time && p.end_time) return `${hhmm(p.start_time)}–${hhmm(p.end_time)}`
  return 'sin horario'
}
function profileMeta(p: TravelTimeProfile): string {
  const base = `${daysSummary(p)} · ${timeSummary(p)} · P${p.priority}`
  return p.is_default ? `${base} · default` : base
}

async function loadRouteData(routeId: number): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [segRes, routeStopsRes, stopsRes, profRes, timesRes] = await Promise.all([
      request<{ items: RouteSegment[] }>('GET', '/admin/route-segments?page=1&page_size=200'),
      request<{ items: RouteStop[] }>('GET', `/admin/routes/${routeId}/stops?page=1&page_size=100`),
      stops.value.length ? Promise.resolve(null) : request<{ items: Stop[] }>('GET', '/admin/stops?page=1&page_size=200'),
      profiles.value.length ? Promise.resolve(null) : request<{ items: TravelTimeProfile[] }>('GET', '/admin/travel-profiles?page=1&page_size=100'),
      request<{ items: SegmentTravelTime[] }>('GET', '/admin/segment-times?page=1&page_size=200'),
    ])
    // El backend no filtra route-segments por ruta (GET no acepta route_id),
    // se filtra en cliente — volumen chico para un MVP.
    routeSegments.value = segRes.items.filter((s) => s.route_id === routeId)
    routeStops.value = routeStopsRes.items
    if (stopsRes) stops.value = stopsRes.items
    if (profRes) profiles.value = profRes.items.filter((p) => p.active)
    times.value = timesRes.items
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : 'No se pudo cargar la matriz de tiempos.'
    routeSegments.value = []
    times.value = []
  } finally {
    loading.value = false
  }
}

watch(selectedRouteId, (routeId) => {
  if (routeId !== null) loadRouteData(routeId)
})

// ---------------------------------------------------------------------------
// Alta / edicion — el tramo y el perfil vienen FIJOS de la celda que se toca;
// el formulario solo pide minutos + notas.
// ---------------------------------------------------------------------------
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const activeSegment = ref<RouteSegment | null>(null)
const activeProfile = ref<TravelTimeProfile | null>(null)
const formMinutes = ref<number | null>(null)
const formNotes = ref('')
const fieldErrors = reactive<Record<string, string>>({})
const formErrorMessage = ref('')
const submitting = ref(false)

function resetFormState(): void {
  formErrorMessage.value = ''
  for (const key of Object.keys(fieldErrors)) delete fieldErrors[key]
}

function openCell(seg: RouteSegment, prof: TravelTimeProfile): void {
  resetFormState()
  activeSegment.value = seg
  activeProfile.value = prof
  const existing = cellFor(seg.id, prof.id)
  editingId.value = existing?.id ?? null
  formMinutes.value = existing?.travel_minutes ?? null
  formNotes.value = existing?.notes ?? ''
  dialogVisible.value = true
}

function closeDialog(): void {
  dialogVisible.value = false
}

function validateClientSide(): boolean {
  if (!formMinutes.value || formMinutes.value <= 0) {
    fieldErrors.travel_minutes = 'Debe ser mayor a 0.'
    return false
  }
  if (formNotes.value.length > 255) {
    fieldErrors.notes = 'Máximo 255 caracteres.'
    return false
  }
  return true
}

const FIELD_ERROR_RE = /^campo (\w+) invalido:/i
const SERVER_FIELD_MAP: Record<string, string> = {
  travelminutes: 'travel_minutes',
  notes: 'notes',
  routesegmentid: 'travel_minutes',
  profileid: 'travel_minutes',
}

function mapServerFieldError(message: string): string | null {
  const match = FIELD_ERROR_RE.exec(message)
  if (!match) return null
  return SERVER_FIELD_MAP[match[1].toLowerCase()] ?? null
}

async function onSubmit(): Promise<void> {
  resetFormState()
  if (!validateClientSide() || !activeSegment.value || !activeProfile.value) return

  submitting.value = true
  const wasCreate = editingId.value === null
  const body = {
    route_segment_id: activeSegment.value.id,
    profile_id: activeProfile.value.id,
    travel_minutes: formMinutes.value,
    notes: formNotes.value.trim() || null,
  }
  try {
    if (wasCreate) {
      await request('POST', '/admin/segment-times', body)
    } else {
      await request('PUT', `/admin/segment-times/${editingId.value}`, body)
    }
    dialogVisible.value = false
    if (selectedRouteId.value !== null) await loadRouteData(selectedRouteId.value)
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

const dialogHeader = computed(() =>
  editingId.value === null ? 'Cargar tiempo' : 'Editar tiempo'
)
</script>

<template>
  <section class="times-view">
    <header class="times-header">
      <div>
        <h1><i class="pi pi-stopwatch times-header-icon" aria-hidden="true"></i> Tiempos de segmento</h1>
        <p class="times-subtitle">
          Matriz de minutos por tramo y perfil horario — la base con la que el generador calcula los horarios.
        </p>
      </div>
    </header>

    <div class="times-filter">
      <label for="times-route-select">Ruta</label>
      <Select
        id="times-route-select"
        v-model="selectedRouteId"
        :options="routes"
        optionLabel="name"
        optionValue="id"
        placeholder="Elija una ruta para ver su matriz de tiempos"
        :loading="loadingRoutes"
        filter
      />
    </div>

    <p v-if="routesError" role="alert" class="times-error">{{ routesError }}</p>

    <template v-if="selectedRouteId !== null">
      <p v-if="error" role="alert" class="times-error">
        {{ error }}
        <Button label="Reintentar" text size="small" @click="loadRouteData(selectedRouteId)" />
      </p>

      <div v-if="loading" class="times-loading">
        <ProgressSpinner style="width: 2.5rem; height: 2.5rem" strokeWidth="4" aria-label="Cargando" />
      </div>

      <template v-else>
        <p v-if="segmentRows.length === 0" class="times-hint">
          <i class="pi pi-info-circle" aria-hidden="true"></i>
          Esta ruta todavía no tiene tramos. Cargá los tramos en "Segmentos de ruta" antes de definir tiempos.
        </p>
        <p v-else-if="profileColumns.length === 0" class="times-hint">
          <i class="pi pi-info-circle" aria-hidden="true"></i>
          No hay perfiles de tiempo activos. Creá al menos un perfil base en "Perfiles de tiempo".
        </p>

        <template v-else>
          <p class="times-legend">
            Tocá una celda para cargar o editar los minutos. <i class="pi pi-exclamation-triangle times-warn-icon" aria-hidden="true"></i>
            marca un tramo sin tiempo para un perfil que ya se usa en esta ruta.
          </p>

          <div class="times-matrix-scroll">
            <table class="times-matrix">
              <thead>
                <tr>
                  <th scope="col" class="times-corner">Tramo</th>
                  <th v-for="prof in profileColumns" :key="prof.id" scope="col" class="times-col-head">
                    <span class="times-col-title" :title="prof.name">
                      {{ prof.code }}
                      <i v-if="prof.is_default" class="pi pi-star-fill times-default-star" aria-hidden="true" title="Perfil base / default"></i>
                    </span>
                    <span class="times-col-meta">{{ profileMeta(prof) }}</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="seg in segmentRows" :key="seg.id">
                  <th scope="row" class="times-row-head">
                    <span class="times-row-order">{{ seg.segment_order }}</span>
                    <span class="times-row-label">{{ segmentLabel(seg) }}</span>
                  </th>
                  <td v-for="prof in profileColumns" :key="prof.id" class="times-cell">
                    <button
                      v-if="cellFor(seg.id, prof.id)"
                      type="button"
                      class="times-cell-btn times-cell-filled"
                      :aria-label="`Editar ${segmentLabel(seg)} · ${prof.code}: ${cellFor(seg.id, prof.id)!.travel_minutes} minutos`"
                      @click="openCell(seg, prof)"
                    >
                      <span class="times-cell-value">{{ cellFor(seg.id, prof.id)!.travel_minutes }}</span>
                      <span class="times-cell-unit">min</span>
                    </button>
                    <button
                      v-else
                      type="button"
                      class="times-cell-btn times-cell-empty"
                      :aria-label="`Cargar tiempo para ${segmentLabel(seg)} · ${prof.code} (sin cargar)`"
                      @click="openCell(seg, prof)"
                    >
                      <i class="pi pi-exclamation-triangle times-cell-warn" aria-hidden="true"></i>
                      <i class="pi pi-plus times-cell-plus" aria-hidden="true"></i>
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </template>
    </template>
    <p v-else class="times-hint">Elija una ruta arriba para ver y editar su matriz de tiempos.</p>

    <Dialog v-model:visible="dialogVisible" modal :header="dialogHeader" :style="{ width: '30rem' }">
      <form novalidate @submit.prevent="onSubmit">
        <p v-if="formErrorMessage" role="alert" class="form-error">{{ formErrorMessage }}</p>

        <div v-if="activeSegment && activeProfile" class="times-context">
          <div class="times-context-row">
            <span class="times-context-label">Tramo</span>
            <span class="times-context-value">{{ segmentLabel(activeSegment) }}</span>
          </div>
          <div class="times-context-row">
            <span class="times-context-label">Perfil</span>
            <span class="times-context-value">{{ activeProfile.code }} — {{ activeProfile.name }}</span>
          </div>
          <span class="times-context-meta">{{ profileMeta(activeProfile) }}</span>
        </div>

        <div class="field">
          <label for="time-minutes">Minutos de viaje <span class="required-mark" aria-hidden="true">*</span></label>
          <InputNumber
            inputId="time-minutes"
            v-model="formMinutes"
            :min="1"
            :useGrouping="false"
            suffix=" min"
            autofocus
            aria-required="true"
            :aria-invalid="!!fieldErrors.travel_minutes"
            :aria-describedby="fieldErrors.travel_minutes ? 'time-minutes-error' : undefined"
          />
          <p v-if="fieldErrors.travel_minutes" id="time-minutes-error" role="alert" class="field-error">
            {{ fieldErrors.travel_minutes }}
          </p>
        </div>

        <div class="field">
          <label for="time-notes">Notas</label>
          <Textarea
            id="time-notes"
            v-model="formNotes"
            rows="2"
            placeholder="Ej.: desvío por obra, calibrado con GPS…"
            :aria-invalid="!!fieldErrors.notes"
            :aria-describedby="fieldErrors.notes ? 'time-notes-error' : undefined"
          />
          <p v-if="fieldErrors.notes" id="time-notes-error" role="alert" class="field-error">
            {{ fieldErrors.notes }}
          </p>
        </div>

        <div class="dialog-actions">
          <Button type="button" :label="LABELS.cancel" severity="secondary" text @click="closeDialog" />
          <Button type="submit" :label="LABELS.save" :loading="submitting" />
        </div>
      </form>
    </Dialog>
  </section>
</template>

<style scoped>
.times-view {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}
.times-header h1 {
  margin: 0 0 0.25rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.times-header-icon {
  color: #059669;
}
.times-subtitle {
  margin: 0;
  color: #52525b;
  font-size: 0.9375rem;
}
.times-filter {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  max-width: 26rem;
}
.times-filter label {
  font-weight: 600;
  font-size: 0.875rem;
}
.times-error {
  color: #b91c1c;
  margin: 0;
}
.times-hint {
  margin: 0;
  color: #71717a;
  font-size: 0.9375rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.times-legend {
  margin: 0;
  color: #52525b;
  font-size: 0.875rem;
}
.times-warn-icon {
  color: #d97706;
}
.times-loading {
  display: flex;
  justify-content: center;
  padding: 2rem 0;
}

/* -- Matriz -- */
.times-matrix-scroll {
  overflow-x: auto;
  border: 1px solid #e4e4e7;
  border-radius: 0.5rem;
}
.times-matrix {
  border-collapse: collapse;
  width: 100%;
  min-width: 32rem;
}
.times-matrix th,
.times-matrix td {
  border-bottom: 1px solid #f4f4f5;
  border-right: 1px solid #f4f4f5;
}
.times-corner {
  text-align: left;
  padding: 0.625rem 0.875rem;
  font-size: 0.8125rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: #71717a;
  background: #fafafa;
  position: sticky;
  left: 0;
  z-index: 2;
}
.times-col-head {
  text-align: left;
  padding: 0.5rem 0.875rem;
  background: #fafafa;
  min-width: 8rem;
  vertical-align: top;
}
.times-col-title {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-weight: 700;
  font-size: 0.9375rem;
}
.times-default-star {
  color: #059669;
  font-size: 0.75rem;
}
.times-col-meta {
  display: block;
  margin-top: 0.125rem;
  font-size: 0.75rem;
  color: #71717a;
  font-variant-numeric: tabular-nums;
}
.times-row-head {
  text-align: left;
  padding: 0.5rem 0.875rem;
  background: #fff;
  position: sticky;
  left: 0;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 14rem;
}
.times-row-order {
  flex-shrink: 0;
  width: 1.5rem;
  height: 1.5rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: #ecfdf5;
  color: #059669;
  font-size: 0.75rem;
  font-weight: 700;
}
.times-row-label {
  font-weight: 500;
  font-size: 0.9375rem;
}
.times-cell {
  padding: 0;
  text-align: center;
}
.times-cell-btn {
  width: 100%;
  min-height: 3rem;
  border: 0;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 0.1875rem;
  padding: 0.5rem;
  transition: background-color 150ms ease;
}
.times-cell-btn:hover,
.times-cell-btn:focus-visible {
  background: #ecfdf5;
  outline: none;
}
.times-cell-value {
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  font-size: 1.0625rem;
}
.times-cell-unit {
  font-size: 0.75rem;
  color: #71717a;
}
.times-cell-empty {
  align-items: center;
}
.times-cell-warn {
  color: #d97706;
  font-size: 0.875rem;
}
.times-cell-plus {
  color: #a1a1aa;
  font-size: 0.75rem;
}
.times-cell-empty:hover .times-cell-plus,
.times-cell-empty:focus-visible .times-cell-plus {
  color: #059669;
}

/* -- Contexto en el dialog -- */
.times-context {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding: 0.75rem 1rem;
  border-radius: 0.5rem;
  background: #f4f4f5;
  margin-bottom: 1.25rem;
}
.times-context-row {
  display: flex;
  gap: 0.5rem;
  font-size: 0.9375rem;
}
.times-context-label {
  flex-shrink: 0;
  width: 3.5rem;
  color: #71717a;
  font-size: 0.8125rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  align-self: center;
}
.times-context-value {
  font-weight: 600;
}
.times-context-meta {
  margin-top: 0.25rem;
  font-size: 0.8125rem;
  color: #71717a;
  font-variant-numeric: tabular-nums;
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
.required-mark {
  color: #b91c1c;
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

@media (prefers-color-scheme: dark) {
  .times-subtitle,
  .times-legend {
    color: #a1a1aa;
  }
  .times-error,
  .field-error,
  .form-error,
  .required-mark {
    color: #fca5a5;
  }
  .times-header-icon {
    color: #34d399;
  }
  .times-hint {
    color: #a1a1aa;
  }
  .times-matrix-scroll {
    border-color: #3f3f46;
  }
  .times-matrix th,
  .times-matrix td {
    border-color: #27272a;
  }
  .times-corner,
  .times-col-head {
    background: #18181b;
    color: #a1a1aa;
  }
  .times-row-head {
    background: #09090b;
  }
  .times-col-meta,
  .times-cell-unit,
  .times-context-meta,
  .times-context-label {
    color: #a1a1aa;
  }
  .times-row-order {
    background: #052e22;
    color: #34d399;
  }
  .times-default-star {
    color: #34d399;
  }
  .times-cell-btn:hover,
  .times-cell-btn:focus-visible {
    background: #052e22;
  }
  .times-context {
    background: #18181b;
  }
  .times-warn-icon,
  .times-cell-warn {
    color: #fbbf24;
  }
}
</style>
