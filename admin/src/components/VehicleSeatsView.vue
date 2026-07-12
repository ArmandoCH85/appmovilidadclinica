<script setup lang="ts">
// Asientos de vehículo — 3er recurso del rollout visual (ver memoria
// "admin/crud-visual-redesign-pattern"), pero este arranca de un BUG real,
// no solo de diseño:
//
// 1. La tabla y el formulario mostraban `vehicle_id` como numero crudo, sin
//    relacion visible con el vehiculo real (internal_code/plate) — el admin
//    tenia que adivinar. Fix: se trae la lista de vehiculos (mismo patron
//    que RouteStopsView con rutas) y se arma un lookup id->vehiculo, mas un
//    Select real en el formulario en vez de un <input type=number>.
// 2. El backend soporta filtrar por vehiculo (GET .../vehicle-seats?vehicle_id=X,
//    ver handler.go ListVehicleSeats) pero el composable generico nunca lo
//    usaba. Fix: `useCrudResource.list()` ahora acepta `extraParams`
//    (api/crud.ts) y esta vista suma un selector de filtro.
// 3. El boton "Desactivar" generico (`useCrudResource.softDelete`) manda
//    `{...item, active:false}` — vehicle_seats NO tiene columna `active`
//    (tiene `status` ACTIVE/BLOCKED/RETIRED), asi que el backend ignora ese
//    campo desconocido y el asiento queda intacto: exito falso, sin cambio
//    real. Fix: accion propia "Retirar" que manda `status:'RETIRED'`
//    explicito.
import { computed, onMounted, reactive, ref } from 'vue'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Textarea from 'primevue/textarea'
import Select from 'primevue/select'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import { useCrudResource } from '../api/crud'
import { request, ApiError } from '../api/client'
import { LABELS } from '../messages'
import type { Vehicle } from '../types'

interface VehicleSeat {
  id: number
  vehicle_id: number
  seat_number: number
  seat_label: string
  status: 'ACTIVE' | 'BLOCKED' | 'RETIRED'
  block_reason?: string | null
}

const { items, page, pageSize, total, loading, error, list, create, update } =
  useCrudResource<VehicleSeat>('/admin/vehicle-seats')
const toast = useToast()

// -- Vehículos: catálogo para lookup (tabla) y Select (formulario/filtro) --
const vehicles = ref<Vehicle[]>([])
const loadingVehicles = ref(false)
const vehicleById = computed(() => new Map(vehicles.value.map((v) => [v.id, v])))
const vehicleOptions = computed(() =>
  vehicles.value.map((v) => ({ value: v.id, label: `${v.internal_code} — ${v.plate}` }))
)

function vehicleLabel(vehicleId: number): string {
  const v = vehicleById.value.get(vehicleId)
  return v ? `${v.internal_code} — ${v.plate}` : `Vehículo #${vehicleId} (no encontrado)`
}

const filterVehicleId = ref<number | null>(null)

function currentFilter(): Record<string, string | number> | undefined {
  return filterVehicleId.value ? { vehicle_id: filterVehicleId.value } : undefined
}

function onFilterChange(): void {
  page.value = 1
  list(undefined, currentFilter())
}

onMounted(async () => {
  loadingVehicles.value = true
  try {
    const res = await request<{ items: Vehicle[] }>('GET', '/admin/vehicles?page=1&page_size=100')
    vehicles.value = res.items
  } catch {
    // El filtro/lookup queda degradado (muestra "Vehículo #N") pero la
    // tabla de asientos sigue siendo usable — no bloquea la pantalla.
  } finally {
    loadingVehicles.value = false
  }
  list(undefined, currentFilter())
})

function onPage(event: { page: number; rows: number }): void {
  page.value = event.page + 1
  pageSize.value = event.rows
  list(undefined, currentFilter())
}

const STATUS_OPTIONS = [
  { value: 'ACTIVE', label: 'Activo' },
  { value: 'BLOCKED', label: 'Bloqueado' },
  { value: 'RETIRED', label: 'Retirado' },
]

function statusTagProps(status: VehicleSeat['status']) {
  if (status === 'ACTIVE') return { value: 'Activo', icon: 'pi pi-check-circle', severity: 'success' as const }
  if (status === 'BLOCKED') return { value: 'Bloqueado', icon: 'pi pi-exclamation-triangle', severity: 'warn' as const }
  return { value: 'Retirado', icon: 'pi pi-ban', severity: 'danger' as const }
}

// ---------------------------------------------------------------------------
// Alta / edicion
// ---------------------------------------------------------------------------
type FormState = {
  vehicle_id: number | null
  seat_number: number | null
  seat_label: string
  status: VehicleSeat['status'] | ''
  block_reason: string
}

function blankForm(): FormState {
  return { vehicle_id: filterVehicleId.value, seat_number: null, seat_label: '', status: 'ACTIVE', block_reason: '' }
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

function openEdit(row: VehicleSeat): void {
  editingId.value = row.id
  resetFormState()
  formData.vehicle_id = row.vehicle_id
  formData.seat_number = row.seat_number
  formData.seat_label = row.seat_label
  formData.status = row.status
  formData.block_reason = row.block_reason ?? ''
  dialogVisible.value = true
}

function closeDialog(): void {
  dialogVisible.value = false
}

function validateClientSide(): boolean {
  if (!formData.vehicle_id) {
    fieldErrors.vehicle_id = LABELS.requiredField
    return false
  }
  if (!formData.seat_number || formData.seat_number <= 0) {
    fieldErrors.seat_number = 'Debe ser mayor a 0.'
    return false
  }
  if (!formData.seat_label.trim()) {
    fieldErrors.seat_label = LABELS.requiredField
    return false
  }
  if (formData.seat_label.length > 10) {
    fieldErrors.seat_label = 'Máximo 10 caracteres.'
    return false
  }
  if (!formData.status) {
    fieldErrors.status = LABELS.requiredField
    return false
  }
  if (formData.block_reason.length > 255) {
    fieldErrors.block_reason = 'Máximo 255 caracteres.'
    return false
  }
  return true
}

const FIELD_ERROR_RE = /^campo (\w+) invalido:/i
const SERVER_FIELD_MAP: Record<string, keyof FormState> = {
  vehicleid: 'vehicle_id',
  seatnumber: 'seat_number',
  seatlabel: 'seat_label',
  status: 'status',
  blockreason: 'block_reason',
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
    vehicle_id: formData.vehicle_id,
    seat_number: formData.seat_number,
    seat_label: formData.seat_label,
    status: formData.status,
    block_reason: formData.status === 'BLOCKED' ? formData.block_reason || null : null,
  }
  try {
    if (wasCreate) {
      await create(body)
    } else {
      await update(editingId.value as number, body)
    }
    dialogVisible.value = false
    await list(undefined, currentFilter())
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

// ---------------------------------------------------------------------------
// Retirar asiento — reemplaza la "baja logica" generica rota (ver nota de
// arriba): manda status:'RETIRED' explicito, no un `active` que no existe.
// ---------------------------------------------------------------------------
const confirmTarget = ref<VehicleSeat | null>(null)
const retiring = ref(false)

function askRetire(row: VehicleSeat): void {
  confirmTarget.value = row
}

function cancelRetire(): void {
  confirmTarget.value = null
}

async function confirmRetire(): Promise<void> {
  if (!confirmTarget.value) return
  retiring.value = true
  const seat = confirmTarget.value
  try {
    await update(seat.id, {
      vehicle_id: seat.vehicle_id,
      seat_number: seat.seat_number,
      seat_label: seat.seat_label,
      status: 'RETIRED',
      block_reason: null,
    })
    confirmTarget.value = null
    await list(undefined, currentFilter())
    toast.add({ severity: 'success', summary: 'Asiento retirado correctamente.', life: 4000 })
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'No se pudo retirar el asiento',
      detail: err instanceof ApiError ? err.message : 'Ocurrió un error inesperado.',
      life: 6000,
    })
  } finally {
    retiring.value = false
  }
}
</script>

<template>
  <section class="seats-view">
    <header class="seats-header">
      <div>
        <h1>Asientos de vehículos</h1>
        <p class="seats-subtitle">Inventario físico de asientos por vehículo — activos, bloqueados y retirados.</p>
      </div>
      <Button label="Nuevo asiento" icon="pi pi-plus" @click="openCreate" />
    </header>

    <div class="seats-filter">
      <label for="seats-vehicle-filter">Filtrar por vehículo</label>
      <Select
        id="seats-vehicle-filter"
        v-model="filterVehicleId"
        :options="vehicleOptions"
        optionLabel="label"
        optionValue="value"
        placeholder="Todos los vehículos"
        showClear
        :loading="loadingVehicles"
        @update:modelValue="onFilterChange"
      />
    </div>

    <p v-if="error" role="alert" class="seats-error">
      {{ error }}
      <Button label="Reintentar" text size="small" @click="list(undefined, currentFilter())" />
    </p>

    <DataTable
      :value="items"
      :loading="loading"
      lazy
      paginator
      dataKey="id"
      :rows="pageSize"
      :totalRecords="total"
      :first="(page - 1) * pageSize"
      class="seats-table"
      @page="onPage"
    >
      <template #empty>
        <div class="seats-empty">
          <i class="pi pi-th-large seats-empty-icon" aria-hidden="true"></i>
          <p>{{ filterVehicleId ? 'Este vehículo todavía no tiene asientos cargados.' : 'Todavía no cargaste ningún asiento.' }}</p>
          <Button label="Nuevo asiento" icon="pi pi-plus" text @click="openCreate" />
        </div>
      </template>

      <Column header="Vehículo">
        <template #body="{ data }"><span class="seats-vehicle">{{ vehicleLabel(data.vehicle_id) }}</span></template>
      </Column>

      <Column header="Asiento">
        <template #body="{ data }">
          <span class="seats-number">{{ data.seat_number }}</span>
          <span class="seats-label">{{ data.seat_label }}</span>
        </template>
      </Column>

      <Column field="status" header="Estado">
        <template #body="{ data }"><Tag v-bind="statusTagProps(data.status)" /></template>
      </Column>

      <Column header="Motivo de bloqueo">
        <template #body="{ data }">
          <span class="seats-reason">{{ data.status === 'BLOCKED' ? data.block_reason || '—' : '—' }}</span>
        </template>
      </Column>

      <Column header="Acciones" :exportable="false">
        <template #body="{ data }">
          <div class="seats-actions">
            <Button icon="pi pi-pencil" text rounded aria-label="Editar asiento" @click="openEdit(data)" />
            <Button
              v-if="data.status !== 'RETIRED'"
              icon="pi pi-ban"
              text
              rounded
              severity="danger"
              aria-label="Retirar asiento"
              @click="askRetire(data)"
            />
          </div>
        </template>
      </Column>
    </DataTable>

    <Dialog
      v-model:visible="dialogVisible"
      modal
      :header="editingId === null ? 'Nuevo asiento' : 'Editar asiento'"
      :style="{ width: '34rem' }"
    >
      <form novalidate @submit.prevent="onSubmit">
        <p v-if="formErrorMessage" role="alert" class="form-error">{{ formErrorMessage }}</p>

        <fieldset class="field-group">
          <legend>Identificación</legend>
          <div class="field">
            <label for="seat-vehicle">Vehículo <span class="required-mark" aria-hidden="true">*</span></label>
            <Select
              id="seat-vehicle"
              v-model="formData.vehicle_id"
              :options="vehicleOptions"
              optionLabel="label"
              optionValue="value"
              placeholder="Elegir vehículo…"
              filter
              :loading="loadingVehicles"
              aria-required="true"
              :aria-invalid="!!fieldErrors.vehicle_id"
              :aria-describedby="fieldErrors.vehicle_id ? 'seat-vehicle-error' : undefined"
            />
            <p v-if="fieldErrors.vehicle_id" id="seat-vehicle-error" role="alert" class="field-error">
              {{ fieldErrors.vehicle_id }}
            </p>
          </div>
          <div class="field-grid">
            <div class="field">
              <label for="seat-number">Número <span class="required-mark" aria-hidden="true">*</span></label>
              <InputNumber
                inputId="seat-number"
                v-model="formData.seat_number"
                :min="1"
                :useGrouping="false"
                aria-required="true"
                :aria-invalid="!!fieldErrors.seat_number"
                :aria-describedby="fieldErrors.seat_number ? 'seat-number-error' : undefined"
              />
              <p v-if="fieldErrors.seat_number" id="seat-number-error" role="alert" class="field-error">
                {{ fieldErrors.seat_number }}
              </p>
            </div>
            <div class="field">
              <label for="seat-label">Etiqueta <span class="required-mark" aria-hidden="true">*</span></label>
              <InputText
                id="seat-label"
                v-model="formData.seat_label"
                placeholder="Ej.: 3C"
                aria-required="true"
                :aria-invalid="!!fieldErrors.seat_label"
                :aria-describedby="fieldErrors.seat_label ? 'seat-label-error' : undefined"
              />
              <p v-if="fieldErrors.seat_label" id="seat-label-error" role="alert" class="field-error">
                {{ fieldErrors.seat_label }}
              </p>
            </div>
          </div>
        </fieldset>

        <fieldset class="field-group">
          <legend>Estado</legend>
          <div class="field">
            <label for="seat-status">Estado <span class="required-mark" aria-hidden="true">*</span></label>
            <Select
              id="seat-status"
              v-model="formData.status"
              :options="STATUS_OPTIONS"
              optionLabel="label"
              optionValue="value"
              aria-required="true"
              :aria-invalid="!!fieldErrors.status"
              :aria-describedby="fieldErrors.status ? 'seat-status-error' : 'seat-status-help'"
            />
            <p id="seat-status-help" class="field-help">
              "Retirado" excluye el asiento del conteo de capacidad del vehículo (debe coincidir con "Cantidad de
              asientos" en Vehículos).
            </p>
            <p v-if="fieldErrors.status" id="seat-status-error" role="alert" class="field-error">
              {{ fieldErrors.status }}
            </p>
          </div>
          <!-- Progressive disclosure: el motivo solo tiene sentido si esta
               bloqueado, no ensucia el formulario para el caso comun (activo). -->
          <div v-if="formData.status === 'BLOCKED'" class="field">
            <label for="seat-block-reason">Motivo de bloqueo</label>
            <Textarea
              id="seat-block-reason"
              v-model="formData.block_reason"
              rows="2"
              placeholder="Ej.: tapizado roto, cinturón sin repuesto…"
              :aria-invalid="!!fieldErrors.block_reason"
              :aria-describedby="fieldErrors.block_reason ? 'seat-block-reason-error' : undefined"
            />
            <p v-if="fieldErrors.block_reason" id="seat-block-reason-error" role="alert" class="field-error">
              {{ fieldErrors.block_reason }}
            </p>
          </div>
        </fieldset>

        <div class="dialog-actions">
          <Button type="button" :label="LABELS.cancel" severity="secondary" text @click="closeDialog" />
          <Button type="submit" :label="LABELS.save" :loading="submitting" />
        </div>
      </form>
    </Dialog>

    <Dialog :visible="confirmTarget !== null" modal :closable="false" header="Confirmar" :style="{ width: '26rem' }">
      <p>
        ¿Retirar el asiento {{ confirmTarget?.seat_label }} de
        {{ confirmTarget ? vehicleLabel(confirmTarget.vehicle_id) : '' }}? Va a quedar excluido del conteo de
        capacidad del vehículo. Podés reactivarlo después editándolo.
      </p>
      <template #footer>
        <Button :label="LABELS.cancel" severity="secondary" text @click="cancelRetire" />
        <Button label="Retirar" severity="danger" :loading="retiring" autofocus @click="confirmRetire" />
      </template>
    </Dialog>
  </section>
</template>

<style scoped>
.seats-view {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}
.seats-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}
.seats-header h1 {
  margin: 0 0 0.25rem;
}
.seats-subtitle {
  margin: 0;
  color: #52525b;
  font-size: 0.9375rem;
}
.seats-filter {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  max-width: 22rem;
}
.seats-filter label {
  font-weight: 600;
  font-size: 0.875rem;
}
.seats-error {
  color: #b91c1c;
  margin: 0;
}
.seats-vehicle {
  font-weight: 500;
}
.seats-number {
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  margin-right: 0.375rem;
}
.seats-label {
  color: #52525b;
}
.seats-reason {
  color: #52525b;
}
.seats-actions {
  display: flex;
  gap: 0.25rem;
}
.seats-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 2rem 0;
  text-align: center;
}
.seats-empty-icon {
  font-size: 1.75rem;
  color: #a1a1aa;
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
  .seats-header {
    flex-direction: column;
  }
}

@media (prefers-color-scheme: dark) {
  .seats-subtitle,
  .seats-label,
  .seats-reason {
    color: #a1a1aa;
  }
  .seats-error,
  .field-error,
  .form-error,
  .required-mark {
    color: #fca5a5;
  }
  .field-group legend {
    color: #34d399;
  }
  .field-help {
    color: #a1a1aa;
  }
  .seats-empty-icon {
    color: #71717a;
  }
}
</style>
