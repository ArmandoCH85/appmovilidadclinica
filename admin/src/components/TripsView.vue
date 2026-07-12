<script setup lang="ts">
// Viajes (trip_instances) — rediseño visual. Es un listado de solo lectura
// con filtros y una acción para cambiar estado. Las relaciones (ruta,
// vehículo, conductor) se resuelven a nombres reales en la tabla.
import { computed, onMounted, ref } from 'vue'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import Select from 'primevue/select'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import { request, ApiError } from '../api/client'
import { LABELS } from '../messages'
import type { TripInstance, Route, Vehicle, User } from '../types'

const toast = useToast()

const items = ref<TripInstance[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const error = ref('')

// Filtros
const filterDate = ref('')
const filterStatus = ref('')
const filterRouteId = ref<number | null>(null)

const STATUS_OPTIONS = [
  { label: 'Borrador', value: 'DRAFT' },
  { label: 'Publicado', value: 'PUBLISHED' },
  { label: 'Embarcando', value: 'BOARDING' },
  { label: 'En progreso', value: 'IN_PROGRESS' },
  { label: 'Completado', value: 'COMPLETED' },
  { label: 'Cancelado', value: 'CANCELLED' },
]

// Relaciones
const routes = ref<Route[]>([])
const vehicles = ref<Vehicle[]>([])
const drivers = ref<User[]>([])
const relationsError = ref('')
const loadingRelations = ref(false)

onMounted(() => {
  loadRelations()
  list()
})

async function loadRelations(): Promise<void> {
  loadingRelations.value = true
  relationsError.value = ''
  try {
    const [routesRes, vehiclesRes, usersRes] = await Promise.all([
      request<{ items: Route[] }>('GET', '/admin/routes?page=1&page_size=200'),
      request<{ items: Vehicle[] }>('GET', '/admin/vehicles?page=1&page_size=200'),
      request<{ items: User[] }>('GET', '/admin/users?page=1&page_size=200'),
    ])
    routes.value = routesRes.items
    vehicles.value = vehiclesRes.items
    drivers.value = usersRes.items.filter((u) => u.role === 'DRIVER')
  } catch (err) {
    relationsError.value = err instanceof ApiError ? err.message : 'No se pudieron cargar las relaciones.'
  } finally {
    loadingRelations.value = false
  }
}

const routeById = computed(() => new Map(routes.value.map((r) => [r.id, r])))
const vehicleById = computed(() => new Map(vehicles.value.map((v) => [v.id, v])))
const driverById = computed(() => new Map(drivers.value.map((d) => [d.id, d])))

function routeLabel(id: number): string {
  const r = routeById.value.get(id)
  return r ? `${r.code} — ${r.name}` : `ID ${id}`
}
function vehicleLabel(id: number): string {
  const v = vehicleById.value.get(id)
  return v ? `${v.internal_code} (${v.plate})` : `ID ${id}`
}
function driverLabel(id: number): string {
  const d = driverById.value.get(id)
  return d ? d.full_name : `ID ${id}`
}

const routeOptions = computed(() =>
  routes.value.map((r) => ({ label: `${r.code} — ${r.name}`, value: r.id }))
)

function buildQueryParams(): string {
  const params = new URLSearchParams({
    page: String(page.value),
    page_size: String(pageSize.value),
  })
  if (filterDate.value) params.set('date', filterDate.value)
  if (filterStatus.value) params.set('status', filterStatus.value)
  if (filterRouteId.value) params.set('route_id', String(filterRouteId.value))
  return params.toString()
}

async function list(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const res = await request<{ items: TripInstance[]; page: number; page_size: number; total: number }>('GET', `/admin/trips?${buildQueryParams()}`)
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

function onPage(event: { page: number; rows: number }): void {
  page.value = event.page + 1
  pageSize.value = event.rows
  list()
}

function onFiltersChange(): void {
  page.value = 1
  list()
}

function clearFilters(): void {
  filterDate.value = ''
  filterStatus.value = ''
  filterRouteId.value = null
  page.value = 1
  list()
}

function formatDateTime(value: string): string {
  const d = new Date(value)
  if (isNaN(d.getTime())) return value
  return d.toLocaleString('es-AR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function statusSeverity(status: string): 'success' | 'info' | 'warn' | 'danger' | 'secondary' {
  switch (status) {
    case 'COMPLETED':
      return 'success'
    case 'PUBLISHED':
    case 'BOARDING':
      return 'info'
    case 'IN_PROGRESS':
      return 'warn'
    case 'CANCELLED':
      return 'danger'
    default:
      return 'secondary'
  }
}

function statusLabel(status: string): string {
  const found = STATUS_OPTIONS.find((s) => s.value === status)
  return found?.label ?? status
}

// ---------------------------------------------------------------------------
// Cambio de estado
// ---------------------------------------------------------------------------
const statusDialogVisible = ref(false)
const statusTarget = ref<TripInstance | null>(null)
const newStatus = ref('')
const statusSubmitting = ref(false)
const statusError = ref('')

function openStatusDialog(row: TripInstance): void {
  statusTarget.value = row
  newStatus.value = row.status
  statusError.value = ''
  statusDialogVisible.value = true
}

function closeStatusDialog(): void {
  statusDialogVisible.value = false
  statusTarget.value = null
  newStatus.value = ''
  statusError.value = ''
}

async function confirmStatusChange(): Promise<void> {
  if (!statusTarget.value || !newStatus.value) return
  statusSubmitting.value = true
  statusError.value = ''
  try {
    await request<void>('POST', `/admin/trips/${statusTarget.value.id}/status`, { status: newStatus.value })
    statusDialogVisible.value = false
    await list()
    toast.add({ severity: 'success', summary: 'Estado actualizado', life: 4000 })
  } catch (err) {
    statusError.value = err instanceof ApiError ? err.message : 'Ocurrió un error inesperado.'
  } finally {
    statusSubmitting.value = false
  }
}
</script>

<template>
  <section class="trips-view">
    <header class="trips-header">
      <div>
        <h1>Viajes</h1>
        <p class="trips-subtitle">Instancias de viaje generadas por el motor y su estado actual.</p>
      </div>
    </header>

    <p v-if="error" role="alert" class="trips-error">
      {{ error }}
      <Button label="Reintentar" text size="small" @click="list()" />
    </p>
    <p v-if="relationsError" role="alert" class="trips-error">
      {{ relationsError }}
      <Button label="Reintentar" text size="small" @click="loadRelations()" />
    </p>

    <div class="trips-filters">
      <div class="field">
        <label for="trip-filter-date">Fecha de servicio</label>
        <input id="trip-filter-date" v-model="filterDate" type="date" class="native-date" @change="onFiltersChange" />
      </div>
      <div class="field">
        <label for="trip-filter-status">Estado</label>
        <Select
          id="trip-filter-status"
          v-model="filterStatus"
          :options="STATUS_OPTIONS"
          option-label="label"
          option-value="value"
          placeholder="Todos"
          show-clear
          @change="onFiltersChange"
        />
      </div>
      <div class="field">
        <label for="trip-filter-route">Ruta</label>
        <Select
          id="trip-filter-route"
          v-model="filterRouteId"
          :options="routeOptions"
          option-label="label"
          option-value="value"
          placeholder="Todas"
          show-clear
          filter
          :loading="loadingRelations"
          @change="onFiltersChange"
        />
      </div>
      <div class="trips-filter-actions">
        <Button label="Limpiar filtros" icon="pi pi-filter-slash" text @click="clearFilters" />
      </div>
    </div>

    <DataTable
      :value="items"
      :loading="loading"
      lazy
      paginator
      dataKey="id"
      :rows="pageSize"
      :totalRecords="total"
      :first="(page - 1) * pageSize"
      class="trips-table"
      @page="onPage"
    >
      <template #empty>
        <div class="trips-empty">
          <i class="pi pi-send trips-empty-icon" aria-hidden="true"></i>
          <p>No hay viajes para los filtros seleccionados.</p>
          <Button label="Limpiar filtros" icon="pi pi-filter-slash" text @click="clearFilters" />
        </div>
      </template>

      <Column field="trip_code" header="Código">
        <template #body="{ data }"><span class="trips-code">{{ data.trip_code }}</span></template>
      </Column>
      <Column field="service_date" header="Fecha de servicio">
        <template #body="{ data }">{{ formatDateTime(data.service_date).split(' ')[0] }}</template>
      </Column>
      <Column header="Ruta">
        <template #body="{ data }">{{ routeLabel(data.route_id) }}</template>
      </Column>
      <Column header="Vehículo">
        <template #body="{ data }">{{ vehicleLabel(data.vehicle_id) }}</template>
      </Column>
      <Column header="Conductor">
        <template #body="{ data }">{{ driverLabel(data.driver_id) }}</template>
      </Column>
      <Column header="Salida programada">
        <template #body="{ data }">{{ formatDateTime(data.scheduled_start_at) }}</template>
      </Column>
      <Column header="Estado">
        <template #body="{ data }">
          <Tag
            :value="statusLabel(data.status)"
            :icon="data.status === 'CANCELLED' ? 'pi pi-ban' : 'pi pi-flag'"
            :severity="statusSeverity(data.status)"
          />
        </template>
      </Column>
      <Column header="Acciones" :exportable="false">
        <template #body="{ data }">
          <Button icon="pi pi-pencil" text rounded aria-label="Cambiar estado" @click="openStatusDialog(data)" />
        </template>
      </Column>
    </DataTable>

    <Dialog
      v-model:visible="statusDialogVisible"
      modal
      :header="`Cambiar estado — ${statusTarget?.trip_code}`"
      :style="{ width: '26rem' }"
    >
      <p v-if="statusError" role="alert" class="form-error">{{ statusError }}</p>
      <div class="field">
        <label for="trip-new-status">Nuevo estado</label>
        <Select
          id="trip-new-status"
          v-model="newStatus"
          :options="STATUS_OPTIONS"
          option-label="label"
          option-value="value"
          placeholder="Elegir estado…"
          :aria-invalid="!newStatus"
        />
      </div>
      <div class="dialog-actions">
        <Button type="button" :label="LABELS.cancel" severity="secondary" text @click="closeStatusDialog" />
        <Button :label="LABELS.save" :loading="statusSubmitting" :disabled="!newStatus" @click="confirmStatusChange" />
      </div>
    </Dialog>
  </section>
</template>

<style scoped>
.trips-view {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}
.trips-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}
.trips-header h1 {
  margin: 0 0 0.25rem;
}
.trips-subtitle {
  margin: 0;
  color: #52525b;
  font-size: 0.9375rem;
}
.trips-error {
  color: #b91c1c;
  margin: 0;
}
.trips-filters {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr));
  gap: 0.875rem;
  align-items: flex-end;
  padding: 1rem;
  border: 1px solid rgba(0, 0, 0, 0.09);
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.02);
}
.trips-filter-actions {
  display: flex;
  justify-content: flex-end;
}
.trips-code {
  font-family: ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
  font-variant-numeric: tabular-nums;
  font-size: 0.875rem;
  letter-spacing: 0.02em;
}
.trips-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 2rem 0;
}
.trips-empty-icon {
  font-size: 1.75rem;
  color: #a1a1aa;
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
.native-date {
  font: inherit;
  padding: 0.5rem 0.75rem;
  border: 1px solid #6b7280;
  border-radius: 6px;
  background: transparent;
  color: inherit;
}
.form-error {
  color: #b91c1c;
  font-size: 0.875rem;
  margin: 0;
}
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 0.5rem;
}

@media (max-width: 30rem) {
  .trips-header {
    flex-direction: column;
  }
  .trips-filters {
    grid-template-columns: 1fr;
  }
}

@media (prefers-color-scheme: dark) {
  .trips-subtitle {
    color: #a1a1aa;
  }
  .trips-error,
  .form-error {
    color: #fca5a5;
  }
  .trips-empty-icon {
    color: #71717a;
  }
  .trips-filters {
    background: rgba(255, 255, 255, 0.03);
    border-color: rgba(255, 255, 255, 0.08);
  }
}
</style>
