<script setup lang="ts">
// Fase 6, tarea 6.2: 3 reportes de solo lectura, groundeados 1:1 contra
// backend/internal/modules/admin/handler.go (seccion "Reportes (vistas)") +
// repository.go (Conflict/MatrixEntry/SeatAvail, ver types.ts). Sin
// mutaciones: cada seccion es list-only. Conflictos y matriz de tiempos no
// aceptan filtros (el backend los expone sin query params); disponibilidad
// de asientos exige `trip_id` (el backend responde 422 sin un entero
// positivo). Un solo archivo con 3 secciones (ponytail: fewest files —
// tasks.md no pide separarlos en vistas propias).
import { onMounted, reactive, ref } from 'vue'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import InputNumber from 'primevue/inputnumber'
import { request, ApiError } from '../api/client'
import { LABELS } from '../messages'
import type { ScheduleConflict, RouteTimeMatrixEntry, TripSeatAvailability } from '../types'

/** Nunca celdas en blanco sin texto (a11y, mismo criterio que CrudView). */
function formatCell(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  return String(value)
}

// ---------------------------------------------------------------------------
// Conflictos de horario — GET /admin/reports/conflicts (sin filtros)
// ---------------------------------------------------------------------------
const conflicts = ref<ScheduleConflict[]>([])
const conflictsLoading = ref(false)
const conflictsError = ref('')

async function loadConflicts(): Promise<void> {
  conflictsLoading.value = true
  conflictsError.value = ''
  try {
    const res = await request<{ items: ScheduleConflict[] }>('GET', '/admin/reports/conflicts')
    conflicts.value = res.items
  } catch (err) {
    conflictsError.value = err instanceof ApiError ? err.message : 'No se pudo cargar el reporte.'
    conflicts.value = []
  } finally {
    conflictsLoading.value = false
  }
}

// ---------------------------------------------------------------------------
// Matriz de tiempos de ruta — GET /admin/reports/time-matrix (sin filtros)
// ---------------------------------------------------------------------------
const matrix = ref<RouteTimeMatrixEntry[]>([])
const matrixLoading = ref(false)
const matrixError = ref('')

async function loadMatrix(): Promise<void> {
  matrixLoading.value = true
  matrixError.value = ''
  try {
    const res = await request<{ items: RouteTimeMatrixEntry[] }>('GET', '/admin/reports/time-matrix')
    matrix.value = res.items
  } catch (err) {
    matrixError.value = err instanceof ApiError ? err.message : 'No se pudo cargar el reporte.'
    matrix.value = []
  } finally {
    matrixLoading.value = false
  }
}

// ---------------------------------------------------------------------------
// Disponibilidad de asientos — GET /admin/reports/seat-availability?trip_id=
// ---------------------------------------------------------------------------
const seatFilter = reactive<{ tripId: number | null }>({ tripId: null })
const seatFieldError = ref('')
const seats = ref<TripSeatAvailability[]>([])
const seatsLoading = ref(false)
const seatsError = ref('')
const seatsSearched = ref(false)

async function searchSeats(): Promise<void> {
  seatFieldError.value = ''
  if (!seatFilter.tripId || seatFilter.tripId < 1) {
    seatFieldError.value = LABELS.requiredField
    return
  }
  seatsLoading.value = true
  seatsError.value = ''
  seatsSearched.value = true
  try {
    const res = await request<{ items: TripSeatAvailability[] }>(
      'GET',
      `/admin/reports/seat-availability?trip_id=${seatFilter.tripId}`
    )
    seats.value = res.items
  } catch (err) {
    seatsError.value = err instanceof ApiError ? err.message : 'No se pudo cargar el reporte.'
    seats.value = []
  } finally {
    seatsLoading.value = false
  }
}

onMounted(() => {
  loadConflicts()
  loadMatrix()
})
</script>

<template>
  <section class="reports-view">
    <h1>Reportes</h1>

    <section class="report-card" aria-labelledby="conflicts-heading">
      <header class="report-header">
        <h2 id="conflicts-heading">Conflictos de horario</h2>
        <Button label="Actualizar" text size="small" @click="loadConflicts" />
      </header>
      <p v-if="conflictsError" role="alert" class="report-error">
        {{ conflictsError }}
        <Button label="Reintentar" text size="small" @click="loadConflicts" />
      </p>
      <DataTable :value="conflicts" :loading="conflictsLoading" paginator :rows="10">
        <template #empty><p>{{ LABELS.empty }}</p></template>
        <Column field="resource_type" header="Recurso" />
        <Column field="resource_id" header="ID de recurso" />
        <Column field="first_trip_id" header="Viaje 1" />
        <Column field="second_trip_id" header="Viaje 2" />
        <Column field="first_start_at" header="Inicio viaje 1">
          <template #body="{ data }">{{ formatCell(data.first_start_at) }}</template>
        </Column>
        <Column field="first_end_at" header="Fin viaje 1">
          <template #body="{ data }">{{ formatCell(data.first_end_at) }}</template>
        </Column>
        <Column field="second_start_at" header="Inicio viaje 2">
          <template #body="{ data }">{{ formatCell(data.second_start_at) }}</template>
        </Column>
        <Column field="second_end_at" header="Fin viaje 2">
          <template #body="{ data }">{{ formatCell(data.second_end_at) }}</template>
        </Column>
      </DataTable>
    </section>

    <section class="report-card" aria-labelledby="matrix-heading">
      <header class="report-header">
        <h2 id="matrix-heading">Matriz de tiempos de ruta</h2>
        <Button label="Actualizar" text size="small" @click="loadMatrix" />
      </header>
      <p v-if="matrixError" role="alert" class="report-error">
        {{ matrixError }}
        <Button label="Reintentar" text size="small" @click="loadMatrix" />
      </p>
      <DataTable :value="matrix" :loading="matrixLoading" paginator :rows="10">
        <template #empty><p>{{ LABELS.empty }}</p></template>
        <Column field="route_code" header="Ruta" />
        <Column field="direction" header="Sentido" />
        <Column field="from_stop_name" header="Desde" />
        <Column field="to_stop_name" header="Hasta" />
        <Column field="profile_name" header="Perfil" />
        <Column field="travel_minutes" header="Minutos de viaje" />
        <Column field="priority" header="Prioridad" />
      </DataTable>
    </section>

    <section class="report-card" aria-labelledby="seats-heading">
      <header class="report-header">
        <h2 id="seats-heading">Disponibilidad de asientos por viaje</h2>
      </header>
      <form novalidate class="seat-filter" @submit.prevent="searchSeats">
        <div class="field">
          <label for="seats-trip-id">ID de viaje</label>
          <InputNumber
            inputId="seats-trip-id"
            v-model="seatFilter.tripId"
            :min="1"
            :aria-invalid="!!seatFieldError"
            :aria-describedby="seatFieldError ? 'seats-trip-id-error' : undefined"
          />
          <p v-if="seatFieldError" id="seats-trip-id-error" role="alert" class="field-error">
            {{ seatFieldError }}
          </p>
        </div>
        <Button type="submit" label="Buscar" :loading="seatsLoading" />
      </form>
      <p v-if="seatsError" role="alert" class="report-error">
        {{ seatsError }}
        <Button label="Reintentar" text size="small" @click="searchSeats" />
      </p>
      <DataTable v-if="seatsSearched" :value="seats" :loading="seatsLoading" paginator :rows="10">
        <template #empty><p>{{ LABELS.empty }}</p></template>
        <Column field="seat_label" header="Asiento" />
        <Column field="segment_order" header="Segmento" />
        <Column field="state" header="Estado" />
        <Column field="available_or_occupied_from" header="Desde" />
        <Column field="available_or_occupied_until" header="Hasta" />
        <Column field="reservation_code" header="Código de reserva">
          <template #body="{ data }">{{ formatCell(data.reservation_code) }}</template>
        </Column>
      </DataTable>
      <p v-else>Ingresá un ID de viaje para ver la disponibilidad de sus asientos.</p>
    </section>
  </section>
</template>

<style scoped>
.reports-view {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}
.report-card {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1rem;
  border: 1px solid rgba(0, 0, 0, 0.15);
  border-radius: 8px;
}
.report-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.report-error {
  color: #b91c1c;
  margin: 0;
}
.seat-filter {
  display: flex;
  align-items: flex-end;
  gap: 0.75rem;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}
.field label {
  font-weight: 600;
}
.field-error {
  color: #b91c1c;
  font-size: 0.875rem;
  margin: 0;
}
@media (prefers-color-scheme: dark) {
  .report-card {
    border-color: rgba(255, 255, 255, 0.15);
  }
  .report-error,
  .field-error {
    color: #fca5a5;
  }
}
</style>
