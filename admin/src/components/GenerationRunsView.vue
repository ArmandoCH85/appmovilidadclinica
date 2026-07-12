<script setup lang="ts">
// Corridas de generacion — 9no recurso del rollout visual (ver memoria
// "admin/crud-visual-redesign-pattern"). trip_generation_runs es
// APPEND-ONLY: la escribe el motor (sp_generate_trip_instance, job ticker
// de 6h) y TriggerManualGeneration; el admin NO la crea/edita a mano.
// Inventar un form de alta iria contra el modelo y seria peligroso
// (corridas sin trip_instances asociadas, conteos en cero, etc).
//
// Lo que SI aporta valor de UI/UX y respeta el modelo:
//  - Listado enriquecido: filtros por status, ventana de fechas y usuario
//    que la disparo. JOIN a users para mostrar el nombre, conteo de
//    trip_instances producidas (FK reversa), duracion calculada en SQL.
//  - Drill-down: dialog con el detalle + los trip_instances que produjo
//    cada corrida (vienen acotados a 200 desde el backend para no cargar
//    listas enormes si la ventana es amplia).
//  - Re-trigger: boton "Re-generar este rango" que llama al endpoint
//    existente TriggerManualGeneration (POST /admin/templates/{id}/generate
//    o similar — el admin ya lo tenia cableado para templates).
import { computed, onMounted, ref, watch } from 'vue'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import Select from 'primevue/select'
import DatePicker from 'primevue/datepicker'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import { request, ApiError } from '../api/client'
import { LABELS } from '../messages'
import type { GenerationRun, TripInstanceSummary } from '../types'

const STATUS_OPTIONS: Array<{ value: string; label: string }> = [
  { value: '', label: 'Todos' },
  { value: 'RUNNING', label: 'En curso' },
  { value: 'COMPLETED', label: 'Completada' },
  { value: 'COMPLETED_WITH_ERRORS', label: 'Completada con errores' },
  { value: 'FAILED', label: 'Fallida' },
]

const STATUS_SEVERITIES: Record<string, 'info' | 'success' | 'warn' | 'danger'> = {
  RUNNING: 'info',
  COMPLETED: 'success',
  COMPLETED_WITH_ERRORS: 'warn',
  FAILED: 'danger',
}

const STATUS_LABELS: Record<string, string> = {
  RUNNING: 'En curso',
  COMPLETED: 'Completada',
  COMPLETED_WITH_ERRORS: 'Completada con errores',
  FAILED: 'Fallida',
}

const toast = useToast()

// -- Filtros --------------------------------------------------------------
const statusFilter = ref<string>('')
const dateFromFilter = ref<Date | null>(null)
const dateToFilter = ref<Date | null>(null)
const triggeredByFilter = ref<number | null>(null)

const triggeredByOptions = ref<Array<{ value: number; label: string }>>([])
const triggeredByLoading = ref(false)

onMounted(async () => {
  // Cargamos usuarios ADMIN/DRIVER que potencialmente disparan corridas
  // manuales (el job automatico usa triggered_by_user_id = NULL). Volumen
  // chico, no paginamos.
  triggeredByLoading.value = true
  try {
    const res = await request<{ items: Array<{ id: number; full_name: string; employee_code: string }> }>(
      'GET',
      '/admin/users?page=1&page_size=200',
    )
    triggeredByOptions.value = res.items
      .filter((u) => u.full_name)
      .map((u) => ({ value: u.id, label: `${u.full_name} (${u.employee_code})` }))
  } catch {
    // Si falla la carga del filtro, el admin igual puede listar todas las
    // corridas — solo se pierde el filtro por usuario.
  } finally {
    triggeredByLoading.value = false
  }
})

// -- Listado --------------------------------------------------------------
const items = ref<GenerationRun[]>([])
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
  const fromStr = ymd(dateFromFilter.value)
  if (fromStr) params.set('date_from', fromStr)
  const toStr = ymd(dateToFilter.value)
  if (toStr) params.set('date_to', toStr)
  if (triggeredByFilter.value) params.set('triggered_by_user_id', String(triggeredByFilter.value))
  try {
    const res = await request<{ items: GenerationRun[]; total: number }>(
      'GET',
      `/admin/generation-runs?${params.toString()}`,
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
  dateFromFilter.value = null
  dateToFilter.value = null
  triggeredByFilter.value = null
}

watch([statusFilter, dateFromFilter, dateToFilter, triggeredByFilter], () => {
  load()
})

function durationLabel(seconds: number | null | undefined): string {
  if (seconds === null || seconds === undefined) return '—'
  if (seconds < 60) return `${seconds}s`
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  if (m < 60) return `${m}m ${s}s`
  const h = Math.floor(m / 60)
  const mm = m % 60
  return `${h}h ${mm}m`
}

function rangeLabel(run: GenerationRun): string {
  if (!run.window_start || !run.window_end) return '—'
  if (run.window_start === run.window_end) return run.window_start
  return `${run.window_start} → ${run.window_end}`
}

function triggeredByLabel(run: GenerationRun): string {
  if (run.triggered_by_full_name) return run.triggered_by_full_name
  if (run.triggered_by_user_id) return `Usuario #${run.triggered_by_user_id}`
  return 'Job automático'
}

// -- Drill-down ----------------------------------------------------------
const detailVisible = ref(false)
const detailRun = ref<GenerationRun | null>(null)
const detailTrips = ref<TripInstanceSummary[]>([])
const detailLoading = ref(false)
const detailError = ref('')

async function openDetail(run: GenerationRun): Promise<void> {
  detailRun.value = run
  detailVisible.value = true
  detailLoading.value = true
  detailError.value = ''
  detailTrips.value = []
  try {
    const res = await request<{ run: GenerationRun; trips: TripInstanceSummary[] }>(
      'GET',
      `/admin/generation-runs/${run.id}`,
    )
    detailRun.value = res.run
    detailTrips.value = res.trips
  } catch (err) {
    detailError.value = err instanceof ApiError ? err.message : 'No se pudo cargar el detalle.'
  } finally {
    detailLoading.value = false
  }
}

function closeDetail(): void {
  detailVisible.value = false
  detailRun.value = null
  detailTrips.value = []
  detailError.value = ''
}

// -- Re-trigger (re-generar el mismo rango) ------------------------------
const retriggerTarget = ref<GenerationRun | null>(null)
const retriggering = ref(false)

function askRetrigger(run: GenerationRun): void {
  retriggerTarget.value = run
}

function cancelRetrigger(): void {
  retriggerTarget.value = null
}

// El endpoint real es por plantilla+fecha (TriggerManualGeneration en
// service.go: POST manual de generacion). Como una corrida cubre multiples
// plantillas, re-disparar el MISMO rango de fechas no es trivial — seria
// necesario listar las plantillas que aplican en esa ventana y disparar
// cada una. Para esta iteracion dejamos el boton con un placeholder
// explicito (la UI avisa que va a regenerar viajes en el rango) y NO
// disparamos la regeneracion automatica para no duplicar viajes si el
// admin se equivoca. Se cablea en una iteracion siguiente con un endpoint
// dedicado /admin/generation-runs/{id}/rerun.
async function confirmRetrigger(): Promise<void> {
  if (!retriggerTarget.value) return
  retriggering.value = true
  // Dejamos un breve delay para feedback visual; la accion real se hara
  // cuando exista el endpoint /admin/generation-runs/{id}/rerun.
  await new Promise((r) => setTimeout(r, 600))
  retriggering.value = false
  retriggerTarget.value = null
  toast.add({
    severity: 'info',
    summary: 'Pendiente de endpoint',
    detail: 'La re-generacion automatica del rango se habilitara cuando este listo el endpoint dedicado. Hoy podes regenerar viaje por viaje desde Plantillas.',
    life: 6000,
  })
}

const hasActiveFilters = computed(
  () => Boolean(statusFilter.value || dateFromFilter.value || dateToFilter.value || triggeredByFilter.value),
)

load()
</script>

<template>
  <section class="genruns-view">
    <header class="genruns-header">
      <div>
        <h1>Corridas de generación</h1>
        <p class="genruns-subtitle">
          Auditoría del motor que materializa viajes desde las plantillas. Cada corrida cubre una ventana de fechas; los
          conteos muestran qué se generó, qué se omitió por calendario y qué falló.
        </p>
      </div>
    </header>

    <div class="genruns-filters">
      <div class="filter">
        <label for="genruns-status">Estado</label>
        <Select
          id="genruns-status"
          v-model="statusFilter"
          :options="STATUS_OPTIONS"
          optionLabel="label"
          optionValue="value"
        />
      </div>
      <div class="filter">
        <label for="genruns-from">Ventana desde</label>
        <DatePicker id="genruns-from" v-model="dateFromFilter" date-format="yy-mm-dd" show-icon />
      </div>
      <div class="filter">
        <label for="genruns-to">Ventana hasta</label>
        <DatePicker id="genruns-to" v-model="dateToFilter" date-format="yy-mm-dd" show-icon />
      </div>
      <div class="filter filter-grow">
        <label for="genruns-user">Disparada por</label>
        <Select
          id="genruns-user"
          v-model="triggeredByFilter"
          :options="triggeredByOptions"
          optionLabel="label"
          optionValue="value"
          placeholder="Todos"
          showClear
          filter
          :loading="triggeredByLoading"
        />
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

    <p v-if="error" role="alert" class="genruns-error">
      {{ error }}
      <Button label="Reintentar" text size="small" @click="load" />
    </p>

    <DataTable :value="items" :loading="loading" dataKey="id" class="genruns-table">
      <template #empty>
        <div class="genruns-empty">
          <i class="pi pi-sync genruns-empty-icon" aria-hidden="true"></i>
          <p>No hay corridas que coincidan con los filtros.</p>
        </div>
      </template>

      <Column field="id" header="#" style="width: 4rem" />

      <Column header="Ventana">
        <template #body="{ data }">{{ rangeLabel(data) }}</template>
      </Column>

      <Column header="Estado">
        <template #body="{ data }">
          <Tag
            :value="STATUS_LABELS[data.status] ?? data.status"
            :severity="STATUS_SEVERITIES[data.status] ?? 'secondary'"
          />
        </template>
      </Column>

      <Column header="Conteo">
        <template #body="{ data }">
          <div class="counts">
            <span class="count count-ok" :title="`${data.generated_count} viaje(s) generado(s)`">
              <i class="pi pi-check" aria-hidden="true"></i>{{ data.generated_count }}
            </span>
            <span class="count count-skip" :title="`${data.skipped_count} omitido(s) por calendario`">
              <i class="pi pi-forward" aria-hidden="true"></i>{{ data.skipped_count }}
            </span>
            <span class="count count-fail" :title="`${data.failed_count} con error`">
              <i class="pi pi-times" aria-hidden="true"></i>{{ data.failed_count }}
            </span>
          </div>
        </template>
      </Column>

      <Column header="Viajes producidos" style="width: 8rem">
        <template #body="{ data }">
          <span :class="['trip-count', { 'trip-count-zero': data.trip_count === 0 }]">{{ data.trip_count }}</span>
        </template>
      </Column>

      <Column header="Disparada por">
        <template #body="{ data }">
          <span :class="['user-cell', { 'user-cell-auto': !data.triggered_by_user_id }]">
            {{ triggeredByLabel(data) }}
          </span>
        </template>
      </Column>

      <Column header="Duración" style="width: 6rem">
        <template #body="{ data }">
          <span class="duration-cell">{{ durationLabel(data.duration_seconds) }}</span>
        </template>
      </Column>

      <Column header="Acciones" :exportable="false" style="width: 7rem">
        <template #body="{ data }">
          <div class="genruns-actions">
            <Button
              icon="pi pi-eye"
              text
              rounded
              aria-label="Ver detalle"
              @click="openDetail(data)"
            />
            <Button
              icon="pi pi-refresh"
              text
              rounded
              aria-label="Re-generar este rango"
              @click="askRetrigger(data)"
            />
          </div>
        </template>
      </Column>
    </DataTable>

    <p v-if="!loading && !error" class="genruns-total">
      {{ total }} corrida(s) en total
    </p>

    <!-- Drill-down -->
    <Dialog
      v-model:visible="detailVisible"
      modal
      :header="detailRun ? `Corrida #${detailRun.id} — ${rangeLabel(detailRun)}` : 'Detalle de corrida'"
      :style="{ width: '52rem' }"
    >
      <div v-if="detailRun" class="detail-summary">
        <div class="detail-stat">
          <span class="detail-stat-label">Estado</span>
          <Tag
            :value="STATUS_LABELS[detailRun.status] ?? detailRun.status"
            :severity="STATUS_SEVERITIES[detailRun.status] ?? 'secondary'"
          />
        </div>
        <div class="detail-stat">
          <span class="detail-stat-label">Generados</span>
          <span class="detail-stat-value">{{ detailRun.generated_count }}</span>
        </div>
        <div class="detail-stat">
          <span class="detail-stat-label">Omitidos</span>
          <span class="detail-stat-value">{{ detailRun.skipped_count }}</span>
        </div>
        <div class="detail-stat">
          <span class="detail-stat-label">Fallidos</span>
          <span class="detail-stat-value">{{ detailRun.failed_count }}</span>
        </div>
        <div class="detail-stat">
          <span class="detail-stat-label">Duración</span>
          <span class="detail-stat-value">{{ durationLabel(detailRun.duration_seconds) }}</span>
        </div>
        <div class="detail-stat">
          <span class="detail-stat-label">Disparada por</span>
          <span class="detail-stat-value">{{ triggeredByLabel(detailRun) }}</span>
        </div>
        <div class="detail-stat">
          <span class="detail-stat-label">Inicio</span>
          <span class="detail-stat-value">{{ detailRun.started_at }}</span>
        </div>
        <div class="detail-stat">
          <span class="detail-stat-label">Fin</span>
          <span class="detail-stat-value">{{ detailRun.finished_at ?? '—' }}</span>
        </div>
      </div>

      <div v-if="detailRun?.error_summary" class="detail-error">
        <strong>Resumen de errores:</strong>
        <p>{{ detailRun.error_summary }}</p>
      </div>

      <h3 class="detail-section-title">Viajes producidos ({{ detailTrips.length }})</h3>
      <p v-if="detailLoading" class="detail-loading">Cargando viajes…</p>
      <p v-else-if="detailError" role="alert" class="genruns-error">{{ detailError }}</p>
      <DataTable
        v-else
        :value="detailTrips"
        dataKey="id"
        class="detail-table"
        scrollable
        scrollHeight="20rem"
      >
        <template #empty>
          <p class="detail-empty">Esta corrida no produjo viajes.</p>
        </template>
        <Column field="trip_code" header="Código" />
        <Column field="service_date" header="Fecha" />
        <Column field="scheduled_start_at" header="Salida" />
        <Column field="scheduled_end_at" header="Llegada" />
        <Column field="status" header="Estado" />
      </DataTable>

      <template #footer>
        <Button :label="LABELS.cancel" severity="secondary" text @click="closeDetail" />
        <Button
          v-if="detailRun"
          label="Re-generar este rango"
          icon="pi pi-refresh"
          severity="warn"
          @click="askRetrigger(detailRun)"
        />
      </template>
    </Dialog>

    <Dialog
      :visible="retriggerTarget !== null"
      modal
      :closable="false"
      header="Re-generar rango"
      :style="{ width: '28rem' }"
    >
      <p v-if="retriggerTarget">
        Vas a regenerar viajes en el rango <strong>{{ retriggerTarget.window_start }} → {{ retriggerTarget.window_end }}</strong>
        (corrida #{{ retriggerTarget.id }}).
      </p>
      <p class="confirm-hint">
        <i class="pi pi-info-circle" aria-hidden="true"></i>
        Hoy la re-generación se hace plantilla por plantilla desde la sección "Plantillas de viaje". Esta acción masiva
        se habilitará cuando exista un endpoint dedicado.
      </p>
      <template #footer>
        <Button :label="LABELS.cancel" severity="secondary" text @click="cancelRetrigger" />
        <Button
          label="Entendido"
          severity="warn"
          :loading="retriggering"
          autofocus
          @click="confirmRetrigger"
        />
      </template>
    </Dialog>
  </section>
</template>

<style scoped>
.genruns-view {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}
.genruns-header h1 {
  margin: 0 0 0.25rem;
}
.genruns-subtitle {
  margin: 0;
  color: #52525b;
  font-size: 0.9375rem;
  max-width: 56rem;
}
.genruns-error {
  color: #b91c1c;
  margin: 0;
}
.genruns-table :deep(.p-datatable-tbody td) {
  vertical-align: middle;
}

.genruns-filters {
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
.filter-grow {
  min-width: 18rem;
  flex: 1 1 18rem;
}
.filter label {
  font-size: 0.8125rem;
  font-weight: 600;
  color: #52525b;
}

.counts {
  display: inline-flex;
  gap: 0.375rem;
}
.count {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.125rem 0.5rem;
  border-radius: 0.375rem;
  font-size: 0.8125rem;
  font-weight: 600;
}
.count-ok {
  background: #ecfdf5;
  color: #047857;
}
.count-skip {
  background: #fef9c3;
  color: #854d0e;
}
.count-fail {
  background: #fee2e2;
  color: #b91c1c;
}
.count i {
  font-size: 0.6875rem;
}

.trip-count {
  font-weight: 700;
  font-size: 0.9375rem;
  color: #047857;
}
.trip-count-zero {
  color: #a1a1aa;
  font-weight: 500;
}

.user-cell {
  font-size: 0.9375rem;
}
.user-cell-auto {
  color: #71717a;
  font-style: italic;
}

.duration-cell {
  font-variant-numeric: tabular-nums;
  color: #52525b;
}

.genruns-actions {
  display: flex;
  gap: 0.25rem;
}

.genruns-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 2rem 0;
}
.genruns-empty-icon {
  font-size: 1.75rem;
  color: #a1a1aa;
}

.genruns-total {
  margin: 0;
  font-size: 0.875rem;
  color: #71717a;
}

.detail-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.75rem 1.5rem;
  margin-bottom: 1rem;
  padding: 0.75rem 1rem;
  background: #f4f4f5;
  border-radius: 0.5rem;
}
.detail-stat {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
  min-width: 0;
}
.detail-stat-label {
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: #71717a;
  font-weight: 700;
}
.detail-stat-value {
  font-size: 0.9375rem;
  color: #18181b;
  word-break: break-word;
}
.detail-error {
  margin: 0 0 1rem;
  padding: 0.625rem 0.75rem;
  background: #fee2e2;
  border-radius: 0.5rem;
  color: #7f1d1d;
  font-size: 0.875rem;
}
.detail-error p {
  margin: 0.25rem 0 0;
  white-space: pre-wrap;
}
.detail-section-title {
  margin: 0 0 0.5rem;
  font-size: 0.9375rem;
}
.detail-loading,
.detail-empty {
  margin: 1rem 0;
  color: #71717a;
}
.detail-table {
  font-size: 0.875rem;
}

.confirm-hint {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  margin: 0.75rem 0 0;
  padding: 0.625rem 0.75rem;
  background: #eff6ff;
  border-radius: 0.5rem;
  color: #1e40af;
  font-size: 0.875rem;
}
.confirm-hint i {
  font-size: 1rem;
  margin-top: 0.0625rem;
}

@media (prefers-color-scheme: dark) {
  .genruns-subtitle {
    color: #a1a1aa;
  }
  .genruns-error {
    color: #fca5a5;
  }
  .genruns-empty-icon {
    color: #71717a;
  }
  .genruns-total {
    color: #a1a1aa;
  }
  .filter label {
    color: #a1a1aa;
  }
  .count-ok {
    background: #052e22;
    color: #6ee7b7;
  }
  .count-skip {
    background: #422006;
    color: #fcd34d;
  }
  .count-fail {
    background: #450a0a;
    color: #fca5a5;
  }
  .trip-count {
    color: #6ee7b7;
  }
  .trip-count-zero {
    color: #71717a;
  }
  .user-cell-auto {
    color: #71717a;
  }
  .duration-cell {
    color: #a1a1aa;
  }
  .detail-summary {
    background: #27272a;
  }
  .detail-stat-label {
    color: #a1a1aa;
  }
  .detail-stat-value {
    color: #fafafa;
  }
  .detail-error {
    background: #450a0a;
    color: #fecaca;
  }
  .confirm-hint {
    background: #1e3a8a;
    color: #bfdbfe;
  }
}
</style>