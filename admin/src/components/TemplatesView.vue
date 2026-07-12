<script setup lang="ts">
// Plantillas de viaje (trip_templates) — rediseño visual con relaciones
// resueltas. Los FKs (ruta, calendario, vehículo, conductor) se muestran con
// nombres/códigos reales en la tabla y como Selects en el formulario.
// Auditado contra TemplateCreateParams/UpdateParams (repository.go).
import { computed, onMounted, reactive, ref } from 'vue'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Select from 'primevue/select'
import ToggleSwitch from 'primevue/toggleswitch'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import { useCrudResource } from '../api/crud'
import { request, ApiError } from '../api/client'
import { LABELS } from '../messages'
import type { Template, Route, Calendar, Vehicle, User } from '../types'

const { items, page, pageSize, total, loading, error, list, create, update, softDelete } =
  useCrudResource<Template>('/admin/templates')
const toast = useToast()

onMounted(() => {
  list()
  loadRelations()
})

function onPage(event: { page: number; rows: number }): void {
  page.value = event.page + 1
  pageSize.value = event.rows
  list()
}

// ---------------------------------------------------------------------------
// Relaciones: cargamos una sola vez y resolvemos nombres en tabla y formulario
// ---------------------------------------------------------------------------
const routes = ref<Route[]>([])
const calendars = ref<Calendar[]>([])
const vehicles = ref<Vehicle[]>([])
const drivers = ref<User[]>([])
const relationsError = ref('')
const loadingRelations = ref(false)

async function loadRelations(): Promise<void> {
  loadingRelations.value = true
  relationsError.value = ''
  try {
    const [routesRes, calendarsRes, vehiclesRes, usersRes] = await Promise.all([
      request<{ items: Route[] }>('GET', '/admin/routes?page=1&page_size=200'),
      request<{ items: Calendar[] }>('GET', '/admin/calendars?page=1&page_size=200'),
      request<{ items: Vehicle[] }>('GET', '/admin/vehicles?page=1&page_size=200'),
      request<{ items: User[] }>('GET', '/admin/users?page=1&page_size=200'),
    ])
    routes.value = routesRes.items
    calendars.value = calendarsRes.items
    vehicles.value = vehiclesRes.items
    drivers.value = usersRes.items.filter((u) => u.role === 'DRIVER')
  } catch (err) {
    relationsError.value = err instanceof ApiError ? err.message : 'No se pudieron cargar las relaciones.'
  } finally {
    loadingRelations.value = false
  }
}

const routeById = computed(() => new Map(routes.value.map((r) => [r.id, r])))
const calendarById = computed(() => new Map(calendars.value.map((c) => [c.id, c])))
const vehicleById = computed(() => new Map(vehicles.value.map((v) => [v.id, v])))
const driverById = computed(() => new Map(drivers.value.map((d) => [d.id, d])))

function routeLabel(id: number): string {
  const r = routeById.value.get(id)
  return r ? `${r.code} — ${r.name}` : `ID ${id}`
}
function calendarLabel(id: number): string {
  const c = calendarById.value.get(id)
  return c ? c.name : `ID ${id}`
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
const calendarOptions = computed(() =>
  calendars.value.map((c) => ({ label: c.name, value: c.id }))
)
const vehicleOptions = computed(() =>
  vehicles.value.map((v) => ({ label: `${v.internal_code} (${v.plate})`, value: v.id }))
)
const driverOptions = computed(() =>
  drivers.value.map((d) => ({ label: d.full_name, value: d.id }))
)

const PROFILE_REFERENCE_OPTIONS: Array<{ label: string; value: 'TRIP_DEPARTURE' | 'SEGMENT_DEPARTURE' }> = [
  { label: 'Salida del viaje', value: 'TRIP_DEPARTURE' },
  { label: 'Salida del segmento', value: 'SEGMENT_DEPARTURE' },
]

function formatTime(value: string | null | undefined): string {
  if (!value) return '—'
  return value.slice(0, 5)
}

// ---------------------------------------------------------------------------
// Alta / edición
// ---------------------------------------------------------------------------
type FormState = {
  code: string
  name: string
  route_id: number | null
  service_calendar_id: number | null
  departure_time: string
  default_vehicle_id: number | null
  default_driver_id: number | null
  profile_reference_mode: 'TRIP_DEPARTURE' | 'SEGMENT_DEPARTURE' | null
  booking_open_days_before: number | null
  booking_close_minutes_before: number | null
  no_show_tolerance_minutes: number | null
  automatic_publish: boolean
  active: boolean
}

function blankForm(): FormState {
  return {
    code: '',
    name: '',
    route_id: null,
    service_calendar_id: null,
    departure_time: '',
    default_vehicle_id: null,
    default_driver_id: null,
    profile_reference_mode: null,
    booking_open_days_before: 0,
    booking_close_minutes_before: 0,
    no_show_tolerance_minutes: 0,
    automatic_publish: false,
    active: true,
  }
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

function openEdit(row: Template): void {
  editingId.value = row.id
  resetFormState()
  formData.code = row.code
  formData.name = row.name
  formData.route_id = row.route_id
  formData.service_calendar_id = row.service_calendar_id
  formData.departure_time = row.departure_time
  formData.default_vehicle_id = row.default_vehicle_id
  formData.default_driver_id = row.default_driver_id
  formData.profile_reference_mode = row.profile_reference_mode
  formData.booking_open_days_before = row.booking_open_days_before
  formData.booking_close_minutes_before = row.booking_close_minutes_before
  formData.no_show_tolerance_minutes = row.no_show_tolerance_minutes
  formData.automatic_publish = row.automatic_publish
  formData.active = row.active
  dialogVisible.value = true
}

function closeDialog(): void {
  dialogVisible.value = false
}

const TIME_RE = /^([01]\d|2[0-3]):([0-5]\d)(:([0-5]\d))?$/

function validateClientSide(): boolean {
  if (!formData.code.trim()) {
    fieldErrors.code = LABELS.requiredField
    return false
  }
  if (formData.code.length > 50) {
    fieldErrors.code = 'Máximo 50 caracteres.'
    return false
  }
  if (!formData.name.trim()) {
    fieldErrors.name = LABELS.requiredField
    return false
  }
  if (formData.name.length > 150) {
    fieldErrors.name = 'Máximo 150 caracteres.'
    return false
  }
  if (!formData.route_id) {
    fieldErrors.route_id = LABELS.requiredField
    return false
  }
  if (!formData.service_calendar_id) {
    fieldErrors.service_calendar_id = LABELS.requiredField
    return false
  }
  if (!formData.departure_time || !TIME_RE.test(formData.departure_time)) {
    fieldErrors.departure_time = 'Ingrese hora válida (HH:MM o HH:MM:SS).'
    return false
  }
  if (!formData.default_vehicle_id) {
    fieldErrors.default_vehicle_id = LABELS.requiredField
    return false
  }
  if (!formData.default_driver_id) {
    fieldErrors.default_driver_id = LABELS.requiredField
    return false
  }
  if (!formData.profile_reference_mode) {
    fieldErrors.profile_reference_mode = LABELS.requiredField
    return false
  }
  if (formData.booking_open_days_before === null || formData.booking_open_days_before < 0) {
    fieldErrors.booking_open_days_before = 'Debe ser 0 o mayor.'
    return false
  }
  if (formData.booking_close_minutes_before === null || formData.booking_close_minutes_before < 0) {
    fieldErrors.booking_close_minutes_before = 'Debe ser 0 o mayor.'
    return false
  }
  if (formData.no_show_tolerance_minutes === null || formData.no_show_tolerance_minutes < 0) {
    fieldErrors.no_show_tolerance_minutes = 'Debe ser 0 o mayor.'
    return false
  }
  return true
}

const FIELD_ERROR_RE = /^campo (\w+) invalido:/i
const SERVER_FIELD_MAP: Record<string, keyof FormState> = {
  code: 'code',
  name: 'name',
  routeid: 'route_id',
  servicecalendarid: 'service_calendar_id',
  departuretime: 'departure_time',
  defaultvehicleid: 'default_vehicle_id',
  defaultdriverid: 'default_driver_id',
  profilereferencemode: 'profile_reference_mode',
  bookingopendaysbefore: 'booking_open_days_before',
  bookingcloseminutesbefore: 'booking_close_minutes_before',
  noshowtoleranceminutes: 'no_show_tolerance_minutes',
  automaticpublish: 'automatic_publish',
  active: 'active',
}

function mapServerFieldError(message: string): string | null {
  const match = FIELD_ERROR_RE.exec(message)
  if (!match) return null
  return SERVER_FIELD_MAP[match[1].toLowerCase()] ?? null
}

function normalizeTime(value: string): string {
  return value.length === 5 ? `${value}:00` : value
}

async function onSubmit(): Promise<void> {
  resetFormState()
  if (!validateClientSide()) return

  submitting.value = true
  const wasCreate = editingId.value === null
  const body = {
    code: formData.code,
    name: formData.name,
    route_id: formData.route_id,
    service_calendar_id: formData.service_calendar_id,
    departure_time: normalizeTime(formData.departure_time),
    default_vehicle_id: formData.default_vehicle_id,
    default_driver_id: formData.default_driver_id,
    profile_reference_mode: formData.profile_reference_mode,
    booking_open_days_before: formData.booking_open_days_before ?? 0,
    booking_close_minutes_before: formData.booking_close_minutes_before ?? 0,
    no_show_tolerance_minutes: formData.no_show_tolerance_minutes ?? 0,
    automatic_publish: formData.automatic_publish,
    active: formData.active,
  }
  try {
    if (wasCreate) {
      await create(body)
    } else {
      await update(editingId.value as number, body)
    }
    dialogVisible.value = false
    await list()
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
// Baja lógica
// ---------------------------------------------------------------------------
const confirmTarget = ref<Template | null>(null)
const deactivating = ref(false)

function askDeactivate(row: Template): void {
  confirmTarget.value = row
}

function cancelDeactivate(): void {
  confirmTarget.value = null
}

async function confirmDeactivate(): Promise<void> {
  if (!confirmTarget.value) return
  deactivating.value = true
  try {
    await softDelete(confirmTarget.value)
    confirmTarget.value = null
    await list()
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
  <section class="templates-view">
    <header class="templates-header">
      <div>
        <h1>Plantillas de viaje</h1>
        <p class="templates-subtitle">Patrones de servicio que el generador materializa en viajes.</p>
      </div>
      <Button label="Nueva plantilla" icon="pi pi-plus" @click="openCreate" />
    </header>

    <p v-if="error" role="alert" class="templates-error">
      {{ error }}
      <Button label="Reintentar" text size="small" @click="list()" />
    </p>
    <p v-if="relationsError" role="alert" class="templates-error">
      {{ relationsError }}
      <Button label="Reintentar" text size="small" @click="loadRelations()" />
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
      class="templates-table"
      @page="onPage"
    >
      <template #empty>
        <div class="templates-empty">
          <i class="pi pi-file templates-empty-icon" aria-hidden="true"></i>
          <p>Todavía no cargaste ninguna plantilla.</p>
          <Button label="Nueva plantilla" icon="pi pi-plus" text @click="openCreate" />
        </div>
      </template>

      <Column field="code" header="Código">
        <template #body="{ data }"><span class="templates-code">{{ data.code }}</span></template>
      </Column>
      <Column field="name" header="Nombre" />
      <Column header="Ruta">
        <template #body="{ data }">{{ routeLabel(data.route_id) }}</template>
      </Column>
      <Column header="Calendario">
        <template #body="{ data }">{{ calendarLabel(data.service_calendar_id) }}</template>
      </Column>
      <Column header="Salida">
        <template #body="{ data }"><Tag value="formatTime(data.departure_time)" icon="pi pi-clock" severity="secondary" /></template>
      </Column>
      <Column header="Vehículo">
        <template #body="{ data }">{{ vehicleLabel(data.default_vehicle_id) }}</template>
      </Column>
      <Column header="Conductor">
        <template #body="{ data }">{{ driverLabel(data.default_driver_id) }}</template>
      </Column>
      <Column header="Referencia">
        <template #body="{ data }">
          <Tag
            :value="data.profile_reference_mode === 'TRIP_DEPARTURE' ? 'Salida del viaje' : 'Salida del segmento'"
            severity="info"
          />
        </template>
      </Column>
      <Column header="Estado">
        <template #body="{ data }">
          <Tag
            :value="data.active ? 'Activa' : 'Inactiva'"
            :icon="data.active ? 'pi pi-check-circle' : 'pi pi-ban'"
            :severity="data.active ? 'success' : 'danger'"
          />
        </template>
      </Column>
      <Column header="Acciones" :exportable="false">
        <template #body="{ data }">
          <div class="templates-actions">
            <Button icon="pi pi-pencil" text rounded aria-label="Editar plantilla" @click="openEdit(data)" />
            <Button
              icon="pi pi-ban"
              text
              rounded
              severity="danger"
              aria-label="Desactivar plantilla"
              @click="askDeactivate(data)"
            />
          </div>
        </template>
      </Column>
    </DataTable>

    <Dialog
      v-model:visible="dialogVisible"
      modal
      :header="editingId === null ? 'Nueva plantilla' : 'Editar plantilla'"
      :style="{ width: '42rem' }"
    >
      <form novalidate @submit.prevent="onSubmit">
        <p v-if="formErrorMessage" role="alert" class="form-error">{{ formErrorMessage }}</p>

        <fieldset class="field-group">
          <legend>Identificación</legend>
          <div class="field-grid">
            <div class="field">
              <label for="template-code">Código <span class="required-mark" aria-hidden="true">*</span></label>
              <InputText
                id="template-code"
                v-model="formData.code"
                aria-required="true"
                :aria-invalid="!!fieldErrors.code"
                :aria-describedby="fieldErrors.code ? 'template-code-error' : undefined"
              />
              <p v-if="fieldErrors.code" id="template-code-error" role="alert" class="field-error">{{ fieldErrors.code }}</p>
            </div>
            <div class="field">
              <label for="template-name">Nombre <span class="required-mark" aria-hidden="true">*</span></label>
              <InputText
                id="template-name"
                v-model="formData.name"
                aria-required="true"
                :aria-invalid="!!fieldErrors.name"
                :aria-describedby="fieldErrors.name ? 'template-name-error' : undefined"
              />
              <p v-if="fieldErrors.name" id="template-name-error" role="alert" class="field-error">{{ fieldErrors.name }}</p>
            </div>
          </div>
        </fieldset>

        <fieldset class="field-group">
          <legend>Ruta y calendario</legend>
          <div class="field">
            <label for="template-route">Ruta <span class="required-mark" aria-hidden="true">*</span></label>
            <Select
              id="template-route"
              v-model="formData.route_id"
              :options="routeOptions"
              option-label="label"
              option-value="value"
              placeholder="Elegir ruta…"
              show-clear
              filter
              :loading="loadingRelations"
              :aria-invalid="!!fieldErrors.route_id"
              :aria-describedby="fieldErrors.route_id ? 'template-route-error' : undefined"
            />
            <p v-if="fieldErrors.route_id" id="template-route-error" role="alert" class="field-error">{{ fieldErrors.route_id }}</p>
          </div>
          <div class="field">
            <label for="template-calendar">Calendario de servicio <span class="required-mark" aria-hidden="true">*</span></label>
            <Select
              id="template-calendar"
              v-model="formData.service_calendar_id"
              :options="calendarOptions"
              option-label="label"
              option-value="value"
              placeholder="Elegir calendario…"
              show-clear
              filter
              :loading="loadingRelations"
              :aria-invalid="!!fieldErrors.service_calendar_id"
              :aria-describedby="fieldErrors.service_calendar_id ? 'template-calendar-error' : undefined"
            />
            <p v-if="fieldErrors.service_calendar_id" id="template-calendar-error" role="alert" class="field-error">{{ fieldErrors.service_calendar_id }}</p>
          </div>
        </fieldset>

        <fieldset class="field-group">
          <legend>Salida y asignación por defecto</legend>
          <div class="field-grid">
            <div class="field">
              <label for="template-departure">Hora de salida <span class="required-mark" aria-hidden="true">*</span></label>
              <InputText
                id="template-departure"
                v-model="formData.departure_time"
                placeholder="HH:MM:SS"
                :aria-invalid="!!fieldErrors.departure_time"
                :aria-describedby="fieldErrors.departure_time ? 'template-departure-error' : undefined"
              />
              <p v-if="fieldErrors.departure_time" id="template-departure-error" role="alert" class="field-error">{{ fieldErrors.departure_time }}</p>
            </div>
            <div class="field">
              <label for="template-reference">Referencia de perfil <span class="required-mark" aria-hidden="true">*</span></label>
              <Select
                id="template-reference"
                v-model="formData.profile_reference_mode"
                :options="PROFILE_REFERENCE_OPTIONS"
                option-label="label"
                option-value="value"
                placeholder="Elegir modo…"
                show-clear
                :aria-invalid="!!fieldErrors.profile_reference_mode"
                :aria-describedby="fieldErrors.profile_reference_mode ? 'template-reference-error' : undefined"
              />
              <p v-if="fieldErrors.profile_reference_mode" id="template-reference-error" role="alert" class="field-error">{{ fieldErrors.profile_reference_mode }}</p>
            </div>
          </div>
          <div class="field-grid">
            <div class="field">
              <label for="template-vehicle">Vehículo por defecto <span class="required-mark" aria-hidden="true">*</span></label>
              <Select
                id="template-vehicle"
                v-model="formData.default_vehicle_id"
                :options="vehicleOptions"
                option-label="label"
                option-value="value"
                placeholder="Elegir vehículo…"
                show-clear
                filter
                :loading="loadingRelations"
                :aria-invalid="!!fieldErrors.default_vehicle_id"
                :aria-describedby="fieldErrors.default_vehicle_id ? 'template-vehicle-error' : undefined"
              />
              <p v-if="fieldErrors.default_vehicle_id" id="template-vehicle-error" role="alert" class="field-error">{{ fieldErrors.default_vehicle_id }}</p>
            </div>
            <div class="field">
              <label for="template-driver">Conductor por defecto <span class="required-mark" aria-hidden="true">*</span></label>
              <Select
                id="template-driver"
                v-model="formData.default_driver_id"
                :options="driverOptions"
                option-label="label"
                option-value="value"
                placeholder="Elegir conductor…"
                show-clear
                filter
                :loading="loadingRelations"
                :aria-invalid="!!fieldErrors.default_driver_id"
                :aria-describedby="fieldErrors.default_driver_id ? 'template-driver-error' : undefined"
              />
              <p v-if="fieldErrors.default_driver_id" id="template-driver-error" role="alert" class="field-error">{{ fieldErrors.default_driver_id }}</p>
            </div>
          </div>
        </fieldset>

        <fieldset class="field-group">
          <legend>Reservas y tolerancia</legend>
          <div class="field-grid">
            <div class="field">
              <label for="template-booking-open">Apertura de reserva (días antes)</label>
              <InputNumber
                id="template-booking-open"
                v-model="formData.booking_open_days_before"
                :min="0"
                :useGrouping="false"
                :aria-invalid="!!fieldErrors.booking_open_days_before"
                :aria-describedby="fieldErrors.booking_open_days_before ? 'template-booking-open-error' : undefined"
              />
              <p v-if="fieldErrors.booking_open_days_before" id="template-booking-open-error" role="alert" class="field-error">{{ fieldErrors.booking_open_days_before }}</p>
            </div>
            <div class="field">
              <label for="template-booking-close">Cierre de reserva (minutos antes)</label>
              <InputNumber
                id="template-booking-close"
                v-model="formData.booking_close_minutes_before"
                :min="0"
                :useGrouping="false"
                :aria-invalid="!!fieldErrors.booking_close_minutes_before"
                :aria-describedby="fieldErrors.booking_close_minutes_before ? 'template-booking-close-error' : undefined"
              />
              <p v-if="fieldErrors.booking_close_minutes_before" id="template-booking-close-error" role="alert" class="field-error">{{ fieldErrors.booking_close_minutes_before }}</p>
            </div>
            <div class="field">
              <label for="template-no-show">Tolerancia de inasistencia (min)</label>
              <InputNumber
                id="template-no-show"
                v-model="formData.no_show_tolerance_minutes"
                :min="0"
                :useGrouping="false"
                :aria-invalid="!!fieldErrors.no_show_tolerance_minutes"
                :aria-describedby="fieldErrors.no_show_tolerance_minutes ? 'template-no-show-error' : undefined"
              />
              <p v-if="fieldErrors.no_show_tolerance_minutes" id="template-no-show-error" role="alert" class="field-error">{{ fieldErrors.no_show_tolerance_minutes }}</p>
            </div>
          </div>
        </fieldset>

        <fieldset class="field-group field-group-inline">
          <legend class="sr-only">Opciones</legend>
          <div class="switches-row">
            <label for="template-publish" class="switch-label">
              <ToggleSwitch id="template-publish" v-model="formData.automatic_publish" />
              Publicación automática
            </label>
            <label for="template-active" class="switch-label">
              <ToggleSwitch id="template-active" v-model="formData.active" />
              Activa
            </label>
          </div>
        </fieldset>

        <div class="dialog-actions">
          <Button type="button" :label="LABELS.cancel" severity="secondary" text @click="closeDialog" />
          <Button type="submit" :label="LABELS.save" :loading="submitting" />
        </div>
      </form>
    </Dialog>

    <Dialog :visible="confirmTarget !== null" modal :closable="false" header="Confirmar" :style="{ width: '24rem' }">
      <p>¿Desactivar la plantilla "{{ confirmTarget?.name }}"? Podrá revertirlo editándola luego.</p>
      <template #footer>
        <Button :label="LABELS.cancel" severity="secondary" text @click="cancelDeactivate" />
        <Button :label="LABELS.deactivate" severity="danger" :loading="deactivating" autofocus @click="confirmDeactivate" />
      </template>
    </Dialog>
  </section>
</template>

<style scoped>
.templates-view {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}
.templates-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}
.templates-header h1 {
  margin: 0 0 0.25rem;
}
.templates-subtitle {
  margin: 0;
  color: #52525b;
  font-size: 0.9375rem;
}
.templates-error {
  color: #b91c1c;
  margin: 0;
}
.templates-code {
  font-family: ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
  font-variant-numeric: tabular-nums;
  font-size: 0.875rem;
  letter-spacing: 0.02em;
}
.templates-actions {
  display: flex;
  gap: 0.25rem;
}
.templates-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 2rem 0;
}
.templates-empty-icon {
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
.field-group-inline {
  margin-bottom: 0.5rem;
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
.required-mark {
  color: #b91c1c;
}
.switch-label {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  font-weight: 600;
  width: fit-content;
}
.switches-row {
  display: flex;
  gap: 1.5rem;
  flex-wrap: wrap;
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
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@media (max-width: 30rem) {
  .field-grid {
    grid-template-columns: 1fr;
  }
  .templates-header {
    flex-direction: column;
  }
}

@media (prefers-color-scheme: dark) {
  .templates-subtitle {
    color: #a1a1aa;
  }
  .templates-error,
  .field-error,
  .form-error,
  .required-mark {
    color: #fca5a5;
  }
  .field-group legend {
    color: #34d399;
  }
  .templates-empty-icon {
    color: #71717a;
  }
}
</style>
