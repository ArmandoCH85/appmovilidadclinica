<script setup lang="ts">
// Incidencias de viaje — 10mo recurso del rollout visual (ver memoria
// "admin/crud-visual-redesign-pattern"). trip_incidents la CREA el driver
// desde la app (POST /api/driver/trips/{id}/incidents); el admin NO la
// da de alta — inventar un form de alta iria contra el modelo (no es
// el admin quien reporta incidencias en la operacion). Lo que SÍ hace
// el admin es GESTIONARLAS: cambiar el estado (OPEN -> IN_REVIEW ->
// RESOLVED) y dejar notas de resolucion. Eso se cablea via PATCH.
//
// La vista muestra:
//  - Listado enriquecido con JOIN a trip_instances (codigo+fecha del
//    viaje) y transport_routes (ruta), y JOIN a users (quien reporto).
//  - Filtros: status, incident_type, ventana de reported_at.
//  - Drill-down: dialog con la descripcion completa + datos del viaje
//    y del reporter, mas el form para resolver (cambiar status +
//    resolution_notes). Cuando pasa a RESOLVED, el backend fija
//    resolved_at; si pasa de RESOLVED a otro estado, lo limpia.
import { computed, onMounted, ref, watch } from 'vue'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import Select from 'primevue/select'
import DatePicker from 'primevue/datepicker'
import Textarea from 'primevue/textarea'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import { request, ApiError } from '../api/client'
import { LABELS } from '../messages'
import type { TripIncident } from '../types'

const STATUS_OPTIONS: Array<{ value: string; label: string }> = [
  { value: '', label: 'Todos' },
  { value: 'OPEN', label: 'Abierta' },
  { value: 'IN_REVIEW', label: 'En revisión' },
  { value: 'RESOLVED', label: 'Resuelta' },
]

const TYPE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: '', label: 'Todos' },
  { value: 'BREAKDOWN', label: 'Avería mecánica' },
  { value: 'DELAY', label: 'Retraso' },
  { value: 'ACCIDENT', label: 'Accidente' },
  { value: 'OTHER', label: 'Otro' },
]

const STATUS_SEVERITIES: Record<string, 'danger' | 'warn' | 'success'> = {
  OPEN: 'danger',
  IN_REVIEW: 'warn',
  RESOLVED: 'success',
}

const STATUS_LABELS: Record<string, string> = {
  OPEN: 'Abierta',
  IN_REVIEW: 'En revisión',
  RESOLVED: 'Resuelta',
}

const TYPE_LABELS: Record<string, string> = {
  BREAKDOWN: 'Avería',
  DELAY: 'Retraso',
  ACCIDENT: 'Accidente',
  OTHER: 'Otro',
}

const toast = useToast()

// -- Filtros --------------------------------------------------------------
const statusFilter = ref<string>('')
const typeFilter = ref<string>('')
const dateFromFilter = ref<Date | null>(null)
const dateToFilter = ref<Date | null>(null)

// -- Listado --------------------------------------------------------------
const items = ref<TripIncident[]>([])
const loading = ref(false)
const error = ref('')
const total = ref(0)

function ymd(d: Date | null): string | undefined {
  if (!d) return undefined
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  const params = new URLSearchParams({ page: '1', page_size: '200' })
  if (statusFilter.value) params.set('status', statusFilter.value)
  if (typeFilter.value) params.set('incident_type', typeFilter.value)
  const fromStr = ymd(dateFromFilter.value)
  if (fromStr) params.set('date_from', fromStr)
  const toStr = ymd(dateToFilter.value)
  if (toStr) params.set('date_to', toStr)
  try {
    const res = await request<{ items: TripIncident[]; total: number }>(
      'GET',
      `/admin/incidents?${params.toString()}`,
    )
    items.value = res.items
    total.value = res.total
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : 'No se pudo cargar el listado.'
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function clearFilters(): void {
  statusFilter.value = ''
  typeFilter.value = ''
  dateFromFilter.value = null
  dateToFilter.value = null
}

const hasActiveFilters = computed(
  () => Boolean(statusFilter.value || typeFilter.value || dateFromFilter.value || dateToFilter.value),
)

watch([statusFilter, typeFilter, dateFromFilter, dateToFilter], () => {
  load()
})

// -- Drill-down / resolucion ---------------------------------------------
const detailVisible = ref(false)
const detail = ref<TripIncident | null>(null)
const detailLoading = ref(false)
const formError = ref('')
const submitting = ref(false)
const draftStatus = ref<'OPEN' | 'IN_REVIEW' | 'RESOLVED'>('OPEN')
const draftNotes = ref('')
const detailDirty = ref(false)

async function openDetail(row: TripIncident): Promise<void> {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  formError.value = ''
  try {
    const res = await request<TripIncident>('GET', `/admin/incidents/${row.id}`)
    detail.value = res
    draftStatus.value = res.status
    draftNotes.value = res.resolution_notes ?? ''
    detailDirty.value = false
  } catch (err) {
    formError.value = err instanceof ApiError ? err.message : 'No se pudo cargar el detalle.'
  } finally {
    detailLoading.value = false
  }
}

function closeDetail(): void {
  detailVisible.value = false
  detail.value = null
  formError.value = ''
  draftNotes.value = ''
  detailDirty.value = false
}

function onFormChange(): void {
  detailDirty.value = true
}

async function onSubmitResolution(): Promise<void> {
  if (!detail.value) return
  formError.value = ''
  // Trim notes — si quedo vacia la mandamos null (re-abrir limpia el campo)
  const notes = draftNotes.value.trim()
  const payload: { status: string; resolution_notes: string | null } = {
    status: draftStatus.value,
    resolution_notes: notes.length > 0 ? notes : null,
  }
  submitting.value = true
  try {
    const updated = await request<TripIncident>('PATCH', `/admin/incidents/${detail.value.id}`, payload)
    detail.value = updated
    detailDirty.value = false
    toast.add({ severity: 'success', summary: LABELS.updated, life: 4000 })
    await load()
  } catch (err) {
    formError.value = err instanceof ApiError ? err.message : 'Ocurrió un error inesperado.'
  } finally {
    submitting.value = false
  }
}

load()
</script>

<template>
  <section class="incidents-view">
    <header class="incidents-header">
      <div>
        <h1>Incidencias</h1>
        <p class="incidents-subtitle">
          Reportes que los conductores dejan sobre los viajes (averías, retrasos, accidentes). La carga la hace la app del
          conductor; acá solo gestionás el estado y dejás las notas de resolución.
        </p>
      </div>
    </header>

    <div class="incidents-filters">
      <div class="filter">
        <label for="inc-status">Estado</label>
        <Select
          id="inc-status"
          v-model="statusFilter"
          :options="STATUS_OPTIONS"
          optionLabel="label"
          optionValue="value"
        />
      </div>
      <div class="filter">
        <label for="inc-type">Tipo</label>
        <Select
          id="inc-type"
          v-model="typeFilter"
          :options="TYPE_OPTIONS"
          optionLabel="label"
          optionValue="value"
        />
      </div>
      <div class="filter">
        <label for="inc-from">Reportada desde</label>
        <DatePicker id="inc-from" v-model="dateFromFilter" date-format="yy-mm-dd" show-icon />
      </div>
      <div class="filter">
        <label for="inc-to">Reportada hasta</label>
        <DatePicker id="inc-to" v-model="dateToFilter" date-format="yy-mm-dd" show-icon />
      </div>
      <Button
        v-if="hasActiveFilters"
        label="Limpiar filtros"
        icon="pi pi-filter-slash"
        severity="secondary"
        text
        @click="clearFilters"
      />
    </div>

    <p v-if="error" role="alert" class="incidents-error">
      {{ error }}
      <Button label="Reintentar" text size="small" @click="load" />
    </p>

    <DataTable :value="items" :loading="loading" dataKey="id" class="incidents-table">
      <template #empty>
        <div class="incidents-empty">
          <i class="pi pi-exclamation-triangle incidents-empty-icon" aria-hidden="true"></i>
          <p>No hay incidencias que coincidan con los filtros.</p>
        </div>
      </template>

      <Column field="id" header="#" style="width: 4rem" />

      <Column header="Tipo">
        <template #body="{ data }">
          <Tag
            :value="TYPE_LABELS[data.incident_type] ?? data.incident_type"
            :severity="data.incident_type === 'ACCIDENT' ? 'danger' : data.incident_type === 'BREAKDOWN' ? 'warn' : 'info'"
          />
        </template>
      </Column>

      <Column header="Estado">
        <template #body="{ data }">
          <Tag
            :value="STATUS_LABELS[data.status] ?? data.status"
            :severity="STATUS_SEVERITIES[data.status] ?? 'secondary'"
          />
        </template>
      </Column>

      <Column header="Viaje">
        <template #body="{ data }">
          <div class="trip-cell">
            <span class="trip-code">{{ data.trip_code }}</span>
            <span class="trip-meta">{{ data.trip_service_date }} · {{ data.trip_route_name }}</span>
          </div>
        </template>
      </Column>

      <Column header="Reportada por">
        <template #body="{ data }">
          <div class="user-cell">
            <span class="user-name">{{ data.reported_by_full_name }}</span>
            <span class="user-code">{{ data.reported_by_employee_code }}</span>
          </div>
        </template>
      </Column>

      <Column header="Reportada el" style="width: 10rem">
        <template #body="{ data }">{{ data.reported_at }}</template>
      </Column>

      <Column header="Acciones" :exportable="false" style="width: 5rem">
        <template #body="{ data }">
          <Button
            icon="pi pi-eye"
            text
            rounded
            aria-label="Ver detalle y resolver"
            @click="openDetail(data)"
          />
        </template>
      </Column>
    </DataTable>

    <p v-if="!loading && !error" class="incidents-total">{{ total }} incidencia(s) en total</p>

    <!-- Drill-down + resolucion -->
    <Dialog
      v-model:visible="detailVisible"
      modal
      :header="detail ? `Incidencia #${detail.id} — ${detail.trip_code}` : 'Detalle de incidencia'"
      :style="{ width: '40rem' }"
      :closable="!submitting"
    >
      <p v-if="detailLoading" class="detail-loading">Cargando detalle…</p>
      <p v-else-if="formError && !detail" role="alert" class="incidents-error">{{ formError }}</p>
      <template v-else-if="detail">
        <div class="detail-meta">
          <div class="detail-meta-row">
            <span class="detail-meta-label">Viaje</span>
            <span class="detail-meta-value">
              <strong>{{ detail.trip_code }}</strong> · {{ detail.trip_service_date }} ·
              {{ detail.trip_route_name }}
            </span>
          </div>
          <div class="detail-meta-row">
            <span class="detail-meta-label">Reportada por</span>
            <span class="detail-meta-value">
              {{ detail.reported_by_full_name }} ({{ detail.reported_by_employee_code }})
            </span>
          </div>
          <div class="detail-meta-row">
            <span class="detail-meta-label">Reportada el</span>
            <span class="detail-meta-value">{{ detail.reported_at }}</span>
          </div>
          <div class="detail-meta-row">
            <span class="detail-meta-label">Resuelta el</span>
            <span class="detail-meta-value">{{ detail.resolved_at ?? '—' }}</span>
          </div>
        </div>

        <h3 class="detail-section-title">Descripción</h3>
        <p class="detail-description">{{ detail.description }}</p>

        <h3 class="detail-section-title">Resolución</h3>
        <p v-if="formError" role="alert" class="form-error">{{ formError }}</p>
        <form novalidate @submit.prevent="onSubmitResolution">
          <div class="field">
            <label for="inc-detail-status">Estado</label>
            <Select
              id="inc-detail-status"
              v-model="draftStatus"
              :options="[
                { value: 'OPEN', label: 'Abierta' },
                { value: 'IN_REVIEW', label: 'En revisión' },
                { value: 'RESOLVED', label: 'Resuelta' },
              ]"
              optionLabel="label"
              optionValue="value"
              @change="onFormChange"
            />
            <p class="field-help">
              Pasar a <strong>Resuelta</strong> fija la fecha de resolución; pasar a otro estado la limpia (vuelve a
              quedar pendiente).
            </p>
          </div>
          <div class="field">
            <label for="inc-detail-notes">Notas de resolución</label>
            <Textarea
              id="inc-detail-notes"
              v-model="draftNotes"
              rows="4"
              maxlength="1000"
              placeholder="Qué se hizo, qué se decidió, etc."
              @input="onFormChange"
            />
          </div>
          <div class="dialog-actions">
            <Button
              type="button"
              :label="LABELS.cancel"
              severity="secondary"
              text
              :disabled="submitting"
              @click="closeDetail"
            />
            <Button
              type="submit"
              :label="LABELS.save"
              :loading="submitting"
              :disabled="!detailDirty"
            />
          </div>
        </form>
      </template>
    </Dialog>
  </section>
</template>

<style scoped>
.incidents-view {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}
.incidents-header h1 {
  margin: 0 0 0.25rem;
}
.incidents-subtitle {
  margin: 0;
  color: #52525b;
  font-size: 0.9375rem;
  max-width: 56rem;
}
.incidents-error {
  color: #b91c1c;
  margin: 0;
}
.incidents-table :deep(.p-datatable-tbody td) {
  vertical-align: middle;
}

.incidents-filters {
  display: flex;
  align-items: flex-end;
  gap: 0.75rem;
  flex-wrap: wrap;
}
.filter {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}
.filter label {
  font-size: 0.8125rem;
  font-weight: 600;
  color: #52525b;
}

.trip-cell {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}
.trip-code {
  font-weight: 700;
}
.trip-meta {
  font-size: 0.8125rem;
  color: #71717a;
}

.user-cell {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}
.user-name {
  font-weight: 600;
}
.user-code {
  font-size: 0.8125rem;
  color: #71717a;
}

.incidents-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 2rem 0;
}
.incidents-empty-icon {
  font-size: 1.75rem;
  color: #a1a1aa;
}

.incidents-total {
  margin: 0;
  font-size: 0.875rem;
  color: #71717a;
}

.detail-loading,
.detail-empty {
  margin: 1rem 0;
  color: #71717a;
}

.detail-meta {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
  padding: 0.75rem 1rem;
  background: #f4f4f5;
  border-radius: 0.5rem;
  margin-bottom: 1rem;
}
.detail-meta-row {
  display: grid;
  grid-template-columns: 9rem 1fr;
  gap: 0.5rem;
  align-items: baseline;
}
.detail-meta-label {
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: #71717a;
  font-weight: 700;
}
.detail-meta-value {
  font-size: 0.9375rem;
  color: #18181b;
  word-break: break-word;
}

.detail-section-title {
  margin: 1rem 0 0.5rem;
  font-size: 0.9375rem;
}
.detail-description {
  margin: 0;
  white-space: pre-wrap;
  padding: 0.75rem 1rem;
  background: #f9fafb;
  border-radius: 0.5rem;
  border: 1px solid #e4e4e7;
  color: #18181b;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
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

@media (prefers-color-scheme: dark) {
  .incidents-subtitle {
    color: #a1a1aa;
  }
  .incidents-error,
  .form-error {
    color: #fca5a5;
  }
  .incidents-empty-icon {
    color: #71717a;
  }
  .incidents-total {
    color: #a1a1aa;
  }
  .filter label {
    color: #a1a1aa;
  }
  .trip-meta,
  .user-code {
    color: #a1a1aa;
  }
  .detail-meta {
    background: #27272a;
  }
  .detail-meta-label {
    color: #a1a1aa;
  }
  .detail-meta-value {
    color: #fafafa;
  }
  .detail-description {
    background: #18181b;
    border-color: #3f3f46;
    color: #fafafa;
  }
  .field-help {
    color: #a1a1aa;
  }
}
</style>