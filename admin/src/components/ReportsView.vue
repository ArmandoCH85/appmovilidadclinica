<script setup lang="ts">
// Reportes — 11mo recurso del rollout visual (ver memoria
// "admin/crud-visual-redesign-pattern"). Las 3 vistas SQL son read-only
// por definicion (son SELECT sobre vw_*); NO hay formulario de alta ni
// de edicion — inventar uno iria contra el modelo. Lo que aporta valor:
//  - Tabs (TabView) para no apilar 3 secciones en una sola pantalla.
//  - Cada tab carga al activarse (lazy load) para no pedir 3 listados al
//    entrar a la pagina.
//  - Filtros por tab que matchean 1:1 columnas reales de la vista SQL
//    correspondiente. El backend rechaza valores invalidos con 422.
//  - DataTable con Tags de severidad para state (seat availability) y
//    resource_type (conflictos), formato de fechas amigable.
import { onMounted, reactive, ref, computed } from 'vue'
import Tabs from 'primevue/tabs'
import TabList from 'primevue/tablist'
import Tab from 'primevue/tab'
import TabPanels from 'primevue/tabpanels'
import TabPanel from 'primevue/tabpanel'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import InputNumber from 'primevue/inputnumber'
import Select from 'primevue/select'
import DatePicker from 'primevue/datepicker'
import Tag from 'primevue/tag'
import { request, ApiError } from '../api/client'
import { LABELS } from '../messages'
import type { ScheduleConflict, RouteTimeMatrixEntry, TripSeatAvailability } from '../types'

// ---------------------------------------------------------------------------
// Helpers de formato
// ---------------------------------------------------------------------------

function formatCell(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  return String(value)
}

// ---------------------------------------------------------------------------
// Tab 1: Conflictos de horario (vw_schedule_conflicts)
// ---------------------------------------------------------------------------

const RESOURCE_TYPE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: '', label: 'Todos' },
  { value: 'VEHICLE', label: 'Vehículo' },
  { value: 'DRIVER', label: 'Conductor' },
]

const RESOURCE_TYPE_SEVERITIES: Record<string, 'warn' | 'danger'> = {
  VEHICLE: 'warn',
  DRIVER: 'danger',
}

const RESOURCE_TYPE_LABELS: Record<string, string> = {
  VEHICLE: 'Vehículo',
  DRIVER: 'Conductor',
}

const conflicts = ref<ScheduleConflict[]>([])
const conflictsLoading = ref(false)
const conflictsError = ref('')
const conflictsFilter = reactive<{ resourceType: string; dateFrom: Date | null; dateTo: Date | null }>({
  resourceType: '',
  dateFrom: null,
  dateTo: null,
})

function ymd(d: Date | null): string | undefined {
  if (!d) return undefined
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}

async function loadConflicts(): Promise<void> {
  conflictsLoading.value = true
  conflictsError.value = ''
  const params = new URLSearchParams()
  if (conflictsFilter.resourceType) params.set('resource_type', conflictsFilter.resourceType)
  const fromStr = ymd(conflictsFilter.dateFrom)
  if (fromStr) params.set('date_from', fromStr)
  const toStr = ymd(conflictsFilter.dateTo)
  if (toStr) params.set('date_to', toStr)
  const qs = params.toString()
  try {
    const res = await request<{ items: ScheduleConflict[] }>(
      'GET',
      `/admin/reports/conflicts${qs ? `?${qs}` : ''}`,
    )
    conflicts.value = res.items
  } catch (err) {
    conflictsError.value = err instanceof ApiError ? err.message : 'No se pudo cargar el reporte.'
    conflicts.value = []
  } finally {
    conflictsLoading.value = false
  }
}

function clearConflictsFilters(): void {
  conflictsFilter.resourceType = ''
  conflictsFilter.dateFrom = null
  conflictsFilter.dateTo = null
}

const hasConflictsFilters = computed(
  () => Boolean(conflictsFilter.resourceType || conflictsFilter.dateFrom || conflictsFilter.dateTo),
)

// ---------------------------------------------------------------------------
// Tab 2: Matriz de tiempos (vw_route_time_matrix)
// ---------------------------------------------------------------------------

const DIRECTION_OPTIONS: Array<{ value: string; label: string }> = [
  { value: '', label: 'Todos' },
  { value: 'IDA', label: 'Ida' },
  { value: 'VUELTA', label: 'Vuelta' },
]

const matrix = ref<RouteTimeMatrixEntry[]>([])
const matrixLoading = ref(false)
const matrixError = ref('')
const matrixFilter = reactive<{ routeID: number | null; direction: string; profileID: number | null }>({
  routeID: null,
  direction: '',
  profileID: null,
})

async function loadMatrix(): Promise<void> {
  matrixLoading.value = true
  matrixError.value = ''
  const params = new URLSearchParams()
  if (matrixFilter.routeID && matrixFilter.routeID > 0) params.set('route_id', String(matrixFilter.routeID))
  if (matrixFilter.direction) params.set('direction', matrixFilter.direction)
  if (matrixFilter.profileID && matrixFilter.profileID > 0) params.set('profile_id', String(matrixFilter.profileID))
  const qs = params.toString()
  try {
    const res = await request<{ items: RouteTimeMatrixEntry[] }>(
      'GET',
      `/admin/reports/time-matrix${qs ? `?${qs}` : ''}`,
    )
    matrix.value = res.items
  } catch (err) {
    matrixError.value = err instanceof ApiError ? err.message : 'No se pudo cargar el reporte.'
    matrix.value = []
  } finally {
    matrixLoading.value = false
  }
}

function clearMatrixFilters(): void {
  matrixFilter.routeID = null
  matrixFilter.direction = ''
  matrixFilter.profileID = null
}

const hasMatrixFilters = computed(
  () =>
    Boolean(
      (matrixFilter.routeID && matrixFilter.routeID > 0) ||
        matrixFilter.direction ||
        (matrixFilter.profileID && matrixFilter.profileID > 0),
    ),
)

// ---------------------------------------------------------------------------
// Tab 3: Disponibilidad de asientos (vw_trip_segment_seat_availability)
// ---------------------------------------------------------------------------

const SEAT_STATE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: '', label: 'Todos' },
  { value: 'AVAILABLE', label: 'Disponible' },
  { value: 'OCCUPIED_IN_REQUESTED_RANGE', label: 'Ocupado (rango solicitado)' },
  { value: 'BLOCKED', label: 'Bloqueado' },
]

const SEAT_STATE_SEVERITIES: Record<string, 'success' | 'danger' | 'warn' | 'info'> = {
  AVAILABLE: 'success',
  OCCUPIED_IN_REQUESTED_RANGE: 'danger',
  BLOCKED: 'warn',
}

const SEAT_STATE_LABELS: Record<string, string> = {
  AVAILABLE: 'Disponible',
  OCCUPIED_IN_REQUESTED_RANGE: 'Ocupado',
  BLOCKED: 'Bloqueado',
}

const seats = ref<TripSeatAvailability[]>([])
const seatsLoading = ref(false)
const seatsError = ref('')
const seatsFieldError = ref('')
const seatsSearched = ref(false)
const seatFilter = reactive<{ tripId: number | null; state: string }>({ tripId: null, state: '' })

async function searchSeats(): Promise<void> {
  seatsFieldError.value = ''
  if (!seatFilter.tripId || seatFilter.tripId < 1) {
    seatsFieldError.value = LABELS.requiredField
    return
  }
  seatsLoading.value = true
  seatsError.value = ''
  seatsSearched.value = true
  const params = new URLSearchParams({ trip_id: String(seatFilter.tripId) })
  if (seatFilter.state) params.set('state', seatFilter.state)
  try {
    const res = await request<{ items: TripSeatAvailability[] }>(
      'GET',
      `/admin/reports/seat-availability?${params.toString()}`,
    )
    seats.value = res.items
  } catch (err) {
    seatsError.value = err instanceof ApiError ? err.message : 'No se pudo cargar el reporte.'
    seats.value = []
  } finally {
    seatsLoading.value = false
  }
}

// ---------------------------------------------------------------------------
// Tabs (lazy load: cada tab dispara su consulta al activarse por primera vez)
// ---------------------------------------------------------------------------

const activeTab = ref<'conflicts' | 'matrix' | 'seats'>('conflicts')
const conflictsLoaded = ref(false)
const matrixLoaded = ref(false)

function onTabChange(value: string | number | undefined): void {
  const tab = String(value ?? '')
  activeTab.value = tab as typeof activeTab.value
  if (tab === 'conflicts' && !conflictsLoaded.value) {
    conflictsLoaded.value = true
    loadConflicts()
  } else if (tab === 'matrix' && !matrixLoaded.value) {
    matrixLoaded.value = true
    loadMatrix()
  }
}

// ---------------------------------------------------------------------------
// Init: tab 0 ya cargado onMounted
// ---------------------------------------------------------------------------

onMounted(() => {
  conflictsLoaded.value = true
  loadConflicts()
})
</script>

<template>
  <section class="reports-view">
    <header class="reports-header">
      <h1>Reportes</h1>
      <p class="reports-subtitle">
        Vistas SQL de solo lectura sobre datos ya materializados: solapamientos de vehículo/conductor, matriz de
        tiempos por ruta y disponibilidad de asientos por viaje.
      </p>
    </header>

    <Tabs :value="activeTab" @update:value="onTabChange">
      <TabList>
        <Tab value="conflicts">
          <i class="pi pi-exclamation-circle tab-icon" aria-hidden="true"></i>
          Conflictos de horario
        </Tab>
        <Tab value="matrix">
          <i class="pi pi-stopwatch tab-icon" aria-hidden="true"></i>
          Matriz de tiempos
        </Tab>
        <Tab value="seats">
          <i class="pi pi-th-large tab-icon" aria-hidden="true"></i>
          Disponibilidad de asientos
        </Tab>
      </TabList>

      <TabPanels>
        <!-- Tab 1: Conflictos -->
        <TabPanel value="conflicts">
          <div class="report-filters">
            <div class="filter">
              <label for="conf-resource">Recurso</label>
              <Select
                id="conf-resource"
                v-model="conflictsFilter.resourceType"
                :options="RESOURCE_TYPE_OPTIONS"
                optionLabel="label"
                optionValue="value"
              />
            </div>
            <div class="filter">
              <label for="conf-from">Desde</label>
              <DatePicker
                id="conf-from"
                v-model="conflictsFilter.dateFrom"
                date-format="yy-mm-dd"
                show-icon
              />
            </div>
            <div class="filter">
              <label for="conf-to">Hasta</label>
              <DatePicker
                id="conf-to"
                v-model="conflictsFilter.dateTo"
                date-format="yy-mm-dd"
                show-icon
              />
            </div>
            <div class="filter-actions">
              <Button
                label="Aplicar"
                icon="pi pi-search"
                :loading="conflictsLoading"
                @click="loadConflicts"
              />
              <Button
                v-if="hasConflictsFilters"
                label="Limpiar"
                icon="pi pi-filter-slash"
                severity="secondary"
                text
                @click="clearConflictsFilters"
              />
            </div>
          </div>

          <p v-if="conflictsError" role="alert" class="reports-error">
            {{ conflictsError }}
            <Button label="Reintentar" text size="small" @click="loadConflicts" />
          </p>

          <DataTable
            :value="conflicts"
            :loading="conflictsLoading"
            dataKey="first_trip_id"
            paginator
            :rows="10"
            class="reports-table"
          >
            <template #empty>
              <p class="reports-empty">
                <i class="pi pi-check-circle" aria-hidden="true"></i>
                Sin conflictos para los filtros aplicados.
              </p>
            </template>
            <Column header="Recurso" style="width: 8rem">
              <template #body="{ data }">
                <Tag
                  :value="RESOURCE_TYPE_LABELS[data.resource_type] ?? data.resource_type"
                  :severity="RESOURCE_TYPE_SEVERITIES[data.resource_type] ?? 'secondary'"
                />
              </template>
            </Column>
            <Column field="resource_id" header="ID recurso" style="width: 6rem" />
            <Column field="first_trip_id" header="Viaje 1" style="width: 6rem" />
            <Column field="first_start_at" header="Inicio V1" />
            <Column field="first_end_at" header="Fin V1" />
            <Column field="second_trip_id" header="Viaje 2" style="width: 6rem" />
            <Column field="second_start_at" header="Inicio V2" />
            <Column field="second_end_at" header="Fin V2" />
          </DataTable>

          <p v-if="!conflictsLoading && !conflictsError" class="reports-total">
            {{ conflicts.length }} conflicto(s) detectado(s)
          </p>
        </TabPanel>

        <!-- Tab 2: Matriz de tiempos -->
        <TabPanel value="matrix">
          <div class="report-filters">
            <div class="filter">
              <label for="mtx-route">ID de ruta</label>
              <InputNumber
                inputId="mtx-route"
                v-model="matrixFilter.routeID"
                :min="0"
                placeholder="Todas"
              />
            </div>
            <div class="filter">
              <label for="mtx-direction">Sentido</label>
              <Select
                id="mtx-direction"
                v-model="matrixFilter.direction"
                :options="DIRECTION_OPTIONS"
                optionLabel="label"
                optionValue="value"
              />
            </div>
            <div class="filter">
              <label for="mtx-profile">ID de perfil</label>
              <InputNumber
                inputId="mtx-profile"
                v-model="matrixFilter.profileID"
                :min="0"
                placeholder="Todos"
              />
            </div>
            <div class="filter-actions">
              <Button
                label="Aplicar"
                icon="pi pi-search"
                :loading="matrixLoading"
                @click="loadMatrix"
              />
              <Button
                v-if="hasMatrixFilters"
                label="Limpiar"
                icon="pi pi-filter-slash"
                severity="secondary"
                text
                @click="clearMatrixFilters"
              />
            </div>
          </div>

          <p v-if="matrixError" role="alert" class="reports-error">
            {{ matrixError }}
            <Button label="Reintentar" text size="small" @click="loadMatrix" />
          </p>

          <DataTable :value="matrix" :loading="matrixLoading" paginator :rows="15" class="reports-table">
            <template #empty>
              <p class="reports-empty">No hay entradas para los filtros aplicados.</p>
            </template>
            <Column field="route_code" header="Ruta" style="width: 6rem" />
            <Column field="direction" header="Sentido" style="width: 5rem" />
            <Column field="from_stop_name" header="Desde" />
            <Column field="to_stop_name" header="Hasta" />
            <Column field="profile_name" header="Perfil" />
            <Column field="travel_minutes" header="Min" style="width: 4rem" />
            <Column field="priority" header="Prioridad" style="width: 5rem" />
          </DataTable>

          <p v-if="!matrixLoading && !matrixError" class="reports-total">
            {{ matrix.length }} entrada(s) en la matriz
          </p>
        </TabPanel>

        <!-- Tab 3: Disponibilidad de asientos -->
        <TabPanel value="seats">
          <form novalidate class="seat-filter" @submit.prevent="searchSeats">
            <div class="filter">
              <label for="seats-trip-id">ID de viaje <span class="required-mark" aria-hidden="true">*</span></label>
              <InputNumber
                inputId="seats-trip-id"
                v-model="seatFilter.tripId"
                :min="1"
                :aria-invalid="!!seatsFieldError"
              />
              <p v-if="seatsFieldError" role="alert" class="field-error">{{ seatsFieldError }}</p>
            </div>
            <div class="filter">
              <label for="seats-state">Estado</label>
              <Select
                id="seats-state"
                v-model="seatFilter.state"
                :options="SEAT_STATE_OPTIONS"
                optionLabel="label"
                optionValue="value"
              />
            </div>
            <div class="filter-actions">
              <Button type="submit" label="Buscar" icon="pi pi-search" :loading="seatsLoading" />
            </div>
          </form>

          <p v-if="seatsError" role="alert" class="reports-error">
            {{ seatsError }}
            <Button label="Reintentar" text size="small" @click="searchSeats" />
          </p>

          <DataTable
            v-if="seatsSearched"
            :value="seats"
            :loading="seatsLoading"
            paginator
            :rows="15"
            class="reports-table"
          >
            <template #empty>
              <p class="reports-empty">No hay asientos para los filtros aplicados.</p>
            </template>
            <Column field="seat_label" header="Asiento" style="width: 5rem" />
            <Column field="segment_order" header="Segmento" style="width: 5rem" />
            <Column header="Estado" style="width: 9rem">
              <template #body="{ data }">
                <Tag
                  :value="SEAT_STATE_LABELS[data.state] ?? data.state"
                  :severity="SEAT_STATE_SEVERITIES[data.state] ?? 'info'"
                />
              </template>
            </Column>
            <Column field="available_or_occupied_from" header="Desde" />
            <Column field="available_or_occupied_until" header="Hasta" />
            <Column field="reservation_code" header="Código de reserva">
              <template #body="{ data }">{{ formatCell(data.reservation_code) }}</template>
            </Column>
          </DataTable>
          <p v-else class="reports-hint">Ingresá un ID de viaje para ver la disponibilidad de sus asientos.</p>
        </TabPanel>
      </TabPanels>
    </Tabs>
  </section>
</template>

<style scoped>
.reports-view {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}
.reports-header h1 {
  margin: 0 0 0.25rem;
}
.reports-subtitle {
  margin: 0;
  color: #52525b;
  font-size: 0.9375rem;
  max-width: 56rem;
}
.tab-icon {
  font-size: 0.875rem;
  margin-right: 0.375rem;
}

.report-filters,
.seat-filter {
  display: flex;
  align-items: flex-end;
  gap: 0.75rem;
  flex-wrap: wrap;
  margin: 1rem 0;
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
.filter-actions {
  display: flex;
  gap: 0.5rem;
}

.reports-table :deep(.p-datatable-tbody td) {
  vertical-align: middle;
}

.reports-error {
  color: #b91c1c;
  margin: 0;
}
.reports-empty {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 1rem 0;
  color: #71717a;
}
.reports-total {
  margin: 0.5rem 0 0;
  font-size: 0.875rem;
  color: #71717a;
}
.reports-hint {
  margin: 1rem 0;
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

@media (prefers-color-scheme: dark) {
  .reports-subtitle {
    color: #a1a1aa;
  }
  .reports-error,
  .field-error,
  .required-mark {
    color: #fca5a5;
  }
  .reports-empty,
  .reports-total,
  .reports-hint {
    color: #a1a1aa;
  }
  .filter label {
    color: #a1a1aa;
  }
}
</style>