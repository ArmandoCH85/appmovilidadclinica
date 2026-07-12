<script setup lang="ts">
// Tiempos de segmento — extension del rollout visual (ver memoria
// "admin/crud-visual-redesign-pattern"): route_segment_id/profile_id se
// mostraban como numeros crudos en tabla y formulario, sin forma de saber a
// que tramo/perfil corresponden sin ir a buscarlo a mano en otra pantalla.
//
// Fix: se arma un lookup id->etiqueta para ambos FKs.
// - Perfil: trivial, GET /admin/travel-profiles ya trae code+name.
// - Segmento: GET /admin/route-segments + GET /admin/routes dan
//   "{route.code} · Tramo {segment_order}" como piso minimo. Se enriquece con
//   nombres reales de parada reusando GET /admin/reports/time-matrix
//   (vw_route_time_matrix, ya joinea segment+stops+route) para cualquier
//   segmento que ya tenga al menos un tiempo cargado (con cualquier perfil) —
//   sin esto habria que encadenar /admin/routes/{id}/stops por ruta (no hay
//   listado plano de route_stops, ver RouteSegmentsView.vue).
//
// Esta tabla no tiene columna `active` (confirmado en el modelo Go
// RouteSegmentTravelTime) — no hay baja logica posible, solo Editar.
import { computed, onMounted, reactive, ref } from 'vue'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputNumber from 'primevue/inputnumber'
import Textarea from 'primevue/textarea'
import Select from 'primevue/select'
import { useToast } from 'primevue/usetoast'
import { useCrudResource } from '../api/crud'
import { request, ApiError } from '../api/client'
import { LABELS } from '../messages'
import type { Route, RouteTimeMatrixEntry } from '../types'

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
}

interface SegmentTravelTime {
  id: number
  route_segment_id: number
  profile_id: number
  travel_minutes: number
  notes?: string | null
}

const { items, page, pageSize, total, loading, error, list, create, update } =
  useCrudResource<SegmentTravelTime>('/admin/segment-times')
const toast = useToast()

// -- Catalogos para resolver los dos FK (segmento, perfil) --
const routeSegments = ref<RouteSegment[]>([])
const routes = ref<Route[]>([])
const profiles = ref<TravelTimeProfile[]>([])
const matrixEntries = ref<RouteTimeMatrixEntry[]>([])
const loadingCatalogs = ref(false)

const routeSegmentById = computed(() => new Map(routeSegments.value.map((s) => [s.id, s])))
const routeById = computed(() => new Map(routes.value.map((r) => [r.id, r])))
const profileById = computed(() => new Map(profiles.value.map((p) => [p.id, p])))

// Primer match por route_segment_id: los nombres de parada dependen solo del
// segmento, no del perfil, asi que cualquier fila de la matriz sirve.
const stopNamesBySegmentId = computed(() => {
  const map = new Map<number, { from: string; to: string }>()
  for (const e of matrixEntries.value) {
    if (!map.has(e.route_segment_id)) map.set(e.route_segment_id, { from: e.from_stop_name, to: e.to_stop_name })
  }
  return map
})

function segmentLabel(segmentId: number): string {
  const seg = routeSegmentById.value.get(segmentId)
  if (!seg) return `Tramo #${segmentId} (no encontrado)`
  const route = routeById.value.get(seg.route_id)
  const routeLabel = route ? route.code : `ruta #${seg.route_id}`
  const stopNames = stopNamesBySegmentId.value.get(segmentId)
  if (stopNames) return `${routeLabel} · ${stopNames.from} → ${stopNames.to}`
  return `${routeLabel} · Tramo ${seg.segment_order}`
}

function profileLabel(profileId: number): string {
  const p = profileById.value.get(profileId)
  return p ? `${p.code} — ${p.name}` : `Perfil #${profileId} (no encontrado)`
}

const segmentOptions = computed(() =>
  routeSegments.value.map((s) => ({ value: s.id, label: segmentLabel(s.id) }))
)
const profileOptions = computed(() =>
  profiles.value.map((p) => ({ value: p.id, label: profileLabel(p.id) }))
)

async function loadCatalogs(): Promise<void> {
  loadingCatalogs.value = true
  try {
    const [segRes, routeRes, profRes, matrixRes] = await Promise.all([
      request<{ items: RouteSegment[] }>('GET', '/admin/route-segments?page=1&page_size=200'),
      request<{ items: Route[] }>('GET', '/admin/routes?page=1&page_size=100'),
      request<{ items: TravelTimeProfile[] }>('GET', '/admin/travel-profiles?page=1&page_size=100'),
      request<{ items: RouteTimeMatrixEntry[] }>('GET', '/admin/reports/time-matrix'),
    ])
    routeSegments.value = segRes.items
    routes.value = routeRes.items
    profiles.value = profRes.items
    matrixEntries.value = matrixRes.items
  } catch {
    // Los lookups quedan degradados (muestra "Tramo #N (no encontrado)") pero
    // la tabla y el alta/edicion siguen siendo usables — no bloquea la pantalla.
  } finally {
    loadingCatalogs.value = false
  }
}

onMounted(async () => {
  await loadCatalogs()
  await list()
})

function onPage(event: { page: number; rows: number }): void {
  page.value = event.page + 1
  pageSize.value = event.rows
  list()
}

// ---------------------------------------------------------------------------
// Alta / edicion
// ---------------------------------------------------------------------------
type FormState = {
  route_segment_id: number | null
  profile_id: number | null
  travel_minutes: number | null
  notes: string
}

function blankForm(): FormState {
  return { route_segment_id: null, profile_id: null, travel_minutes: null, notes: '' }
}

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formData = reactive<FormState>(blankForm())
const fieldErrors = reactive<Record<string, string>>({})
const formErrorMessage = ref('')
const submitting = ref(false)

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

function openEdit(row: SegmentTravelTime): void {
  editingId.value = row.id
  resetFormState()
  formData.route_segment_id = row.route_segment_id
  formData.profile_id = row.profile_id
  formData.travel_minutes = row.travel_minutes
  formData.notes = row.notes ?? ''
  dialogVisible.value = true
}

function closeDialog(): void {
  dialogVisible.value = false
}

function validateClientSide(): boolean {
  if (!formData.route_segment_id) {
    fieldErrors.route_segment_id = LABELS.requiredField
    return false
  }
  if (!formData.profile_id) {
    fieldErrors.profile_id = LABELS.requiredField
    return false
  }
  if (!formData.travel_minutes || formData.travel_minutes <= 0) {
    fieldErrors.travel_minutes = 'Debe ser mayor a 0.'
    return false
  }
  if (formData.notes.length > 255) {
    fieldErrors.notes = 'Máximo 255 caracteres.'
    return false
  }
  return true
}

const FIELD_ERROR_RE = /^campo (\w+) invalido:/i
const SERVER_FIELD_MAP: Record<string, keyof FormState> = {
  routesegmentid: 'route_segment_id',
  profileid: 'profile_id',
  travelminutes: 'travel_minutes',
  notes: 'notes',
}

function mapServerFieldError(message: string): string | null {
  const match = FIELD_ERROR_RE.exec(message)
  if (!match) return null
  return SERVER_FIELD_MAP[match[1].toLowerCase()] ?? null
}

async function onSubmit(): Promise<void> {
  resetFormState()
  if (!validateClientSide()) return

  submitting.value = true
  const wasCreate = editingId.value === null
  const body = {
    route_segment_id: formData.route_segment_id,
    profile_id: formData.profile_id,
    travel_minutes: formData.travel_minutes,
    notes: formData.notes.trim() || null,
  }
  try {
    if (wasCreate) {
      await create(body)
    } else {
      await update(editingId.value as number, body)
    }
    dialogVisible.value = false
    await Promise.all([list(), loadCatalogs()])
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
  <section class="times-view">
    <header class="times-header">
      <div>
        <h1><i class="pi pi-stopwatch times-header-icon" aria-hidden="true"></i> Tiempos de segmento</h1>
        <p class="times-subtitle">Minutos de viaje por tramo y perfil horario — la base del cálculo de horarios.</p>
      </div>
      <Button label="Nuevo tiempo" icon="pi pi-plus" @click="openCreate" />
    </header>

    <p v-if="error" role="alert" class="times-error">
      {{ error }}
      <Button label="Reintentar" text size="small" @click="list()" />
    </p>

    <DataTable
      :value="items"
      :loading="loading || loadingCatalogs"
      lazy
      paginator
      dataKey="id"
      :rows="pageSize"
      :totalRecords="total"
      :first="(page - 1) * pageSize"
      class="times-table"
      @page="onPage"
    >
      <template #empty>
        <div class="times-empty">
          <i class="pi pi-stopwatch times-empty-icon" aria-hidden="true"></i>
          <p>Todavía no cargaste ningún tiempo de segmento.</p>
          <Button label="Nuevo tiempo" icon="pi pi-plus" text @click="openCreate" />
        </div>
      </template>

      <Column header="Tramo">
        <template #body="{ data }">
          <span class="times-segment"><i class="pi pi-sitemap" aria-hidden="true"></i> {{ segmentLabel(data.route_segment_id) }}</span>
        </template>
      </Column>

      <Column header="Perfil">
        <template #body="{ data }">
          <span class="times-profile"><i class="pi pi-clock" aria-hidden="true"></i> {{ profileLabel(data.profile_id) }}</span>
        </template>
      </Column>

      <Column header="Minutos" class="times-minutes-col">
        <template #body="{ data }"><span class="times-minutes">{{ data.travel_minutes }}</span></template>
      </Column>

      <Column header="Notas">
        <template #body="{ data }"><span class="times-notes">{{ data.notes || '—' }}</span></template>
      </Column>

      <Column header="Acciones" :exportable="false">
        <template #body="{ data }">
          <Button icon="pi pi-pencil" text rounded aria-label="Editar tiempo de segmento" @click="openEdit(data)" />
        </template>
      </Column>
    </DataTable>

    <Dialog
      v-model:visible="dialogVisible"
      modal
      :header="editingId === null ? 'Nuevo tiempo de segmento' : 'Editar tiempo de segmento'"
      :style="{ width: '32rem' }"
    >
      <form novalidate @submit.prevent="onSubmit">
        <p v-if="formErrorMessage" role="alert" class="form-error">{{ formErrorMessage }}</p>

        <div class="field">
          <label for="time-segment">Tramo <span class="required-mark" aria-hidden="true">*</span></label>
          <Select
            id="time-segment"
            v-model="formData.route_segment_id"
            :options="segmentOptions"
            optionLabel="label"
            optionValue="value"
            placeholder="Elegir tramo…"
            filter
            :loading="loadingCatalogs"
            aria-required="true"
            :aria-invalid="!!fieldErrors.route_segment_id"
            :aria-describedby="fieldErrors.route_segment_id ? 'time-segment-error' : undefined"
          />
          <p v-if="fieldErrors.route_segment_id" id="time-segment-error" role="alert" class="field-error">
            {{ fieldErrors.route_segment_id }}
          </p>
        </div>

        <div class="field">
          <label for="time-profile">Perfil <span class="required-mark" aria-hidden="true">*</span></label>
          <Select
            id="time-profile"
            v-model="formData.profile_id"
            :options="profileOptions"
            optionLabel="label"
            optionValue="value"
            placeholder="Elegir perfil…"
            filter
            :loading="loadingCatalogs"
            aria-required="true"
            :aria-invalid="!!fieldErrors.profile_id"
            :aria-describedby="fieldErrors.profile_id ? 'time-profile-error' : undefined"
          />
          <p v-if="fieldErrors.profile_id" id="time-profile-error" role="alert" class="field-error">
            {{ fieldErrors.profile_id }}
          </p>
        </div>

        <div class="field">
          <label for="time-minutes">Minutos de viaje <span class="required-mark" aria-hidden="true">*</span></label>
          <InputNumber
            inputId="time-minutes"
            v-model="formData.travel_minutes"
            :min="1"
            :useGrouping="false"
            suffix=" min"
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
            v-model="formData.notes"
            rows="2"
            placeholder="Ej.: desvío por obra, horario pico…"
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
.times-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
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
.times-error {
  color: #b91c1c;
  margin: 0;
}
.times-segment,
.times-profile {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
}
.times-segment i,
.times-profile i {
  color: #a1a1aa;
}
.times-minutes {
  font-variant-numeric: tabular-nums;
  font-weight: 700;
}
.times-notes {
  color: #52525b;
}
.times-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 2rem 0;
  text-align: center;
}
.times-empty-icon {
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

@media (max-width: 30rem) {
  .times-header {
    flex-direction: column;
  }
}

@media (prefers-color-scheme: dark) {
  .times-subtitle,
  .times-notes {
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
  .times-segment i,
  .times-profile i,
  .times-empty-icon {
    color: #71717a;
  }
}
</style>
