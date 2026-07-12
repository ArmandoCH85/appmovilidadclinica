<script setup lang="ts">
// Perfiles de tiempo de viaje — rediseño visual (ver memoria
// "admin/crud-visual-redesign-pattern"). Auditado contra
// TravelTimeProfileCreateParams/UpdateParams (repository.go) y la tabla
// travel_time_profiles (0001_schema.up.sql): code/name/valid_from/valid_until/
// start_time/end_time/is_all_day/monday..sunday/priority/is_default/active.
// Sin FKs crudos: este recurso es maestro, no referencia a otros.
import { computed, onMounted, reactive, ref } from 'vue'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import ToggleSwitch from 'primevue/toggleswitch'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import { useCrudResource } from '../api/crud'
import { ApiError } from '../api/client'
import { LABELS } from '../messages'
import type { TravelTimeProfile } from '../types'

const { items, page, pageSize, total, loading, error, list, create, update, softDelete } =
  useCrudResource<TravelTimeProfile>('/admin/travel-profiles')
const toast = useToast()

onMounted(() => list())

function onPage(event: { page: number; rows: number }): void {
  page.value = event.page + 1
  pageSize.value = event.rows
  list()
}

function formatTime(value: string | null | undefined): string {
  if (!value) return '—'
  return value.slice(0, 5)
}

function formatDate(value: string | null | undefined): string {
  if (!value) return '—'
  const datePart = value.split('T')[0]
  const [y, m, d] = datePart.split('-').map(Number)
  if (!y || !m || !d) return value
  const months = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic']
  return `${String(d).padStart(2, '0')} ${months[m - 1]} ${y}`
}

function scheduleDays(profile: TravelTimeProfile): string[] {
  const days: string[] = []
  if (profile.monday) days.push('Lun')
  if (profile.tuesday) days.push('Mar')
  if (profile.wednesday) days.push('Mie')
  if (profile.thursday) days.push('Jue')
  if (profile.friday) days.push('Vie')
  if (profile.saturday) days.push('Sab')
  if (profile.sunday) days.push('Dom')
  return days
}

function scheduleTag(profile: TravelTimeProfile): { value: string; icon: string; severity: 'info' | 'secondary' } {
  if (profile.is_all_day) {
    return { value: 'Todo el día', icon: 'pi pi-clock', severity: 'info' }
  }
  if (profile.start_time && profile.end_time) {
    return { value: `${formatTime(profile.start_time)} a ${formatTime(profile.end_time)}`, icon: 'pi pi-clock', severity: 'secondary' }
  }
  return { value: 'Sin horario', icon: 'pi pi-clock', severity: 'secondary' }
}

// ---------------------------------------------------------------------------
// Alta / edición
// ---------------------------------------------------------------------------
type DayKey = 'monday' | 'tuesday' | 'wednesday' | 'thursday' | 'friday' | 'saturday' | 'sunday'
const daysList: Array<{ key: DayKey; label: string }> = [
  { key: 'monday', label: 'Lun' },
  { key: 'tuesday', label: 'Mar' },
  { key: 'wednesday', label: 'Mie' },
  { key: 'thursday', label: 'Jue' },
  { key: 'friday', label: 'Vie' },
  { key: 'saturday', label: 'Sab' },
  { key: 'sunday', label: 'Dom' },
]

type FormState = {
  code: string
  name: string
  valid_from: string | null
  valid_until: string | null
  start_time: string | null
  end_time: string | null
  is_all_day: boolean
  monday: boolean
  tuesday: boolean
  wednesday: boolean
  thursday: boolean
  friday: boolean
  saturday: boolean
  sunday: boolean
  priority: number | null
  is_default: boolean
  active: boolean
}

function blankForm(): FormState {
  return {
    code: '',
    name: '',
    valid_from: null,
    valid_until: null,
    start_time: null,
    end_time: null,
    is_all_day: false,
    monday: true,
    tuesday: true,
    wednesday: true,
    thursday: true,
    friday: true,
    saturday: false,
    sunday: false,
    priority: 0,
    is_default: false,
    active: true,
  }
}

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formData = reactive<FormState>(blankForm())
const fieldErrors = reactive<Record<string, string>>({})
const formErrorMessage = ref('')
const submitting = ref(false)

const timeFieldsDisabled = computed(() => formData.is_all_day)

function resetFormState(): void {
  formErrorMessage.value = ''
  for (const key of Object.keys(fieldErrors)) delete fieldErrors[key]
}

function stringOrNull(value: string | null | undefined): string | null {
  return value?.trim() || null
}

function openCreate(): void {
  editingId.value = null
  resetFormState()
  Object.assign(formData, blankForm())
  dialogVisible.value = true
}

function openEdit(row: TravelTimeProfile): void {
  editingId.value = row.id
  resetFormState()
  formData.code = row.code
  formData.name = row.name
  formData.valid_from = row.valid_from ?? null
  formData.valid_until = row.valid_until ?? null
  formData.start_time = row.start_time ?? null
  formData.end_time = row.end_time ?? null
  formData.is_all_day = row.is_all_day
  formData.monday = row.monday
  formData.tuesday = row.tuesday
  formData.wednesday = row.wednesday
  formData.thursday = row.thursday
  formData.friday = row.friday
  formData.saturday = row.saturday
  formData.sunday = row.sunday
  formData.priority = row.priority
  formData.is_default = row.is_default
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
  if (formData.code.length > 40) {
    fieldErrors.code = 'Máximo 40 caracteres.'
    return false
  }
  if (!formData.name.trim()) {
    fieldErrors.name = LABELS.requiredField
    return false
  }
  if (formData.name.length > 120) {
    fieldErrors.name = 'Máximo 120 caracteres.'
    return false
  }
  if (formData.valid_from && formData.valid_until && formData.valid_from > formData.valid_until) {
    fieldErrors.valid_until = 'La vigencia hasta debe ser igual o posterior a la vigencia desde.'
    return false
  }
  if (!formData.is_all_day) {
    if (!formData.start_time || !TIME_RE.test(formData.start_time)) {
      fieldErrors.start_time = 'Ingrese hora de inicio válida (HH:MM o HH:MM:SS).'
      return false
    }
    if (!formData.end_time || !TIME_RE.test(formData.end_time)) {
      fieldErrors.end_time = 'Ingrese hora de fin válida (HH:MM o HH:MM:SS).'
      return false
    }
    if (formData.start_time && formData.end_time && formData.start_time >= formData.end_time) {
      fieldErrors.end_time = 'La hora de fin debe ser posterior a la de inicio.'
      return false
    }
  }
  if (formData.priority === null || formData.priority < 0) {
    fieldErrors.priority = 'La prioridad debe ser 0 o mayor.'
    return false
  }
  return true
}

const FIELD_ERROR_RE = /^campo (\w+) invalido:/i
const SERVER_FIELD_MAP: Record<string, keyof FormState> = {
  code: 'code',
  name: 'name',
  validfrom: 'valid_from',
  validuntil: 'valid_until',
  starttime: 'start_time',
  endtime: 'end_time',
  isallday: 'is_all_day',
  priority: 'priority',
  isdefault: 'is_default',
  active: 'active',
}

function mapServerFieldError(message: string): string | null {
  const match = FIELD_ERROR_RE.exec(message)
  if (!match) return null
  return SERVER_FIELD_MAP[match[1].toLowerCase()] ?? null
}

function normalizeTime(value: string | null): string | null {
  if (!value) return null
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
    valid_from: stringOrNull(formData.valid_from),
    valid_until: stringOrNull(formData.valid_until),
    start_time: formData.is_all_day ? null : normalizeTime(formData.start_time),
    end_time: formData.is_all_day ? null : normalizeTime(formData.end_time),
    is_all_day: formData.is_all_day,
    monday: formData.monday,
    tuesday: formData.tuesday,
    wednesday: formData.wednesday,
    thursday: formData.thursday,
    friday: formData.friday,
    saturday: formData.saturday,
    sunday: formData.sunday,
    priority: formData.priority ?? 0,
    is_default: formData.is_default,
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
const confirmTarget = ref<TravelTimeProfile | null>(null)
const deactivating = ref(false)

function askDeactivate(row: TravelTimeProfile): void {
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
  <section class="profiles-view">
    <header class="profiles-header">
      <div>
        <h1>Perfiles de tiempo de viaje</h1>
        <p class="profiles-subtitle">Condiciones horarias que aplica el generador a cada tramo.</p>
      </div>
      <Button label="Nuevo perfil" icon="pi pi-plus" @click="openCreate" />
    </header>

    <p v-if="error" role="alert" class="profiles-error">
      {{ error }}
      <Button label="Reintentar" text size="small" @click="list()" />
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
      class="profiles-table"
      @page="onPage"
    >
      <template #empty>
        <div class="profiles-empty">
          <i class="pi pi-clock profiles-empty-icon" aria-hidden="true"></i>
          <p>Todavía no cargaste ningún perfil.</p>
          <Button label="Nuevo perfil" icon="pi pi-plus" text @click="openCreate" />
        </div>
      </template>

      <Column field="code" header="Código">
        <template #body="{ data }"><span class="profiles-code">{{ data.code }}</span></template>
      </Column>

      <Column field="name" header="Nombre" />

      <Column header="Vigencia">
        <template #body="{ data }">
          {{ formatDate(data.valid_from) }} — {{ formatDate(data.valid_until) }}
        </template>
      </Column>

      <Column header="Horario">
        <template #body="{ data }"><Tag v-bind="scheduleTag(data)" /></template>
      </Column>

      <Column header="Días">
        <template #body="{ data }">
          <span class="profiles-days">{{ scheduleDays(data).join(', ') || 'Ninguno' }}</span>
        </template>
      </Column>

      <Column field="priority" header="Prioridad" />

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
          <div class="profiles-actions">
            <Button icon="pi pi-pencil" text rounded aria-label="Editar perfil" @click="openEdit(data)" />
            <Button
              icon="pi pi-ban"
              text
              rounded
              severity="danger"
              aria-label="Desactivar perfil"
              @click="askDeactivate(data)"
            />
          </div>
        </template>
      </Column>
    </DataTable>

    <Dialog
      v-model:visible="dialogVisible"
      modal
      :header="editingId === null ? 'Nuevo perfil' : 'Editar perfil'"
      :style="{ width: '38rem' }"
    >
      <form novalidate @submit.prevent="onSubmit">
        <p v-if="formErrorMessage" role="alert" class="form-error">{{ formErrorMessage }}</p>

        <fieldset class="field-group">
          <legend>Identificación</legend>
          <div class="field-grid">
            <div class="field">
              <label for="profile-code">Código <span class="required-mark" aria-hidden="true">*</span></label>
              <InputText
                id="profile-code"
                v-model="formData.code"
                aria-required="true"
                :aria-invalid="!!fieldErrors.code"
                :aria-describedby="fieldErrors.code ? 'profile-code-error' : undefined"
              />
              <p v-if="fieldErrors.code" id="profile-code-error" role="alert" class="field-error">{{ fieldErrors.code }}</p>
            </div>
            <div class="field">
              <label for="profile-priority">Prioridad <span class="required-mark" aria-hidden="true">*</span></label>
              <InputNumber
                id="profile-priority"
                v-model="formData.priority"
                :min="0"
                :useGrouping="false"
                aria-required="true"
                :aria-invalid="!!fieldErrors.priority"
                :aria-describedby="fieldErrors.priority ? 'profile-priority-error' : undefined"
              />
              <p v-if="fieldErrors.priority" id="profile-priority-error" role="alert" class="field-error">{{ fieldErrors.priority }}</p>
            </div>
          </div>
          <div class="field">
            <label for="profile-name">Nombre <span class="required-mark" aria-hidden="true">*</span></label>
            <InputText
              id="profile-name"
              v-model="formData.name"
              aria-required="true"
              :aria-invalid="!!fieldErrors.name"
              :aria-describedby="fieldErrors.name ? 'profile-name-error' : undefined"
            />
            <p v-if="fieldErrors.name" id="profile-name-error" role="alert" class="field-error">{{ fieldErrors.name }}</p>
          </div>
        </fieldset>

        <fieldset class="field-group">
          <legend>Vigencia</legend>
          <div class="field-grid">
            <div class="field">
              <label for="profile-valid-from">Vigente desde</label>
              <input
                id="profile-valid-from"
                v-model="formData.valid_from"
                type="date"
                class="native-date"
              />
            </div>
            <div class="field">
              <label for="profile-valid-until">Vigente hasta</label>
              <input
                id="profile-valid-until"
                v-model="formData.valid_until"
                type="date"
                class="native-date"
              />
              <p v-if="fieldErrors.valid_until" role="alert" class="field-error">{{ fieldErrors.valid_until }}</p>
            </div>
          </div>
        </fieldset>

        <fieldset class="field-group">
          <legend>Horario</legend>
          <div class="field-row">
            <label for="profile-is-all-day" class="switch-label">
              <ToggleSwitch id="profile-is-all-day" v-model="formData.is_all_day" />
              Aplica todo el día
            </label>
          </div>
          <div class="field-grid" :class="{ 'fields-disabled': timeFieldsDisabled }">
            <div class="field">
              <label for="profile-start-time">Hora de inicio</label>
              <InputText
                id="profile-start-time"
                v-model="formData.start_time"
                placeholder="HH:MM:SS"
                :disabled="timeFieldsDisabled"
                :aria-invalid="!!fieldErrors.start_time"
                :aria-describedby="fieldErrors.start_time ? 'profile-start-time-error' : undefined"
              />
              <p v-if="fieldErrors.start_time" id="profile-start-time-error" role="alert" class="field-error">{{ fieldErrors.start_time }}</p>
            </div>
            <div class="field">
              <label for="profile-end-time">Hora de fin</label>
              <InputText
                id="profile-end-time"
                v-model="formData.end_time"
                placeholder="HH:MM:SS"
                :disabled="timeFieldsDisabled"
                :aria-invalid="!!fieldErrors.end_time"
                :aria-describedby="fieldErrors.end_time ? 'profile-end-time-error' : undefined"
              />
              <p v-if="fieldErrors.end_time" id="profile-end-time-error" role="alert" class="field-error">{{ fieldErrors.end_time }}</p>
            </div>
          </div>
        </fieldset>

        <fieldset class="field-group">
          <legend>Días de la semana</legend>
          <div class="days-row">
            <label v-for="day in daysList" :key="day.key" class="day-chip">
              <input v-model="formData[day.key]" type="checkbox" />
              <span>{{ day.label }}</span>
            </label>
          </div>
        </fieldset>

        <fieldset class="field-group field-group-inline">
          <legend class="sr-only">Opciones</legend>
          <div class="switches-row">
            <label for="profile-is-default" class="switch-label">
              <ToggleSwitch id="profile-is-default" v-model="formData.is_default" />
              Perfil por defecto
            </label>
            <label for="profile-active" class="switch-label">
              <ToggleSwitch id="profile-active" v-model="formData.active" />
              Activo
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
      <p>¿Desactivar el perfil "{{ confirmTarget?.name }}"? Podrá revertirlo editándolo luego.</p>
      <template #footer>
        <Button :label="LABELS.cancel" severity="secondary" text @click="cancelDeactivate" />
        <Button :label="LABELS.deactivate" severity="danger" :loading="deactivating" autofocus @click="confirmDeactivate" />
      </template>
    </Dialog>
  </section>
</template>

<style scoped>
.profiles-view {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}
.profiles-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}
.profiles-header h1 {
  margin: 0 0 0.25rem;
}
.profiles-subtitle {
  margin: 0;
  color: #52525b;
  font-size: 0.9375rem;
}
.profiles-error {
  color: #b91c1c;
  margin: 0;
}
.profiles-code {
  font-family: ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
  font-variant-numeric: tabular-nums;
  font-size: 0.875rem;
  letter-spacing: 0.02em;
}
.profiles-days {
  color: #52525b;
  font-size: 0.875rem;
}
.profiles-actions {
  display: flex;
  gap: 0.25rem;
}
.profiles-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 2rem 0;
}
.profiles-empty-icon {
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
.field-grid.fields-disabled {
  opacity: 0.6;
  pointer-events: none;
}
.field-row {
  display: flex;
  gap: 1rem;
  align-items: center;
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
.days-row {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}
.day-chip {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.375rem 0.625rem;
  border-radius: 9999px;
  border: 1px solid #6b7280;
  cursor: pointer;
  font-weight: 600;
  font-size: 0.875rem;
}
.day-chip input {
  margin: 0;
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
.native-date {
  font: inherit;
  padding: 0.5rem 0.75rem;
  border: 1px solid #6b7280;
  border-radius: 6px;
  background: transparent;
  color: inherit;
}

@media (max-width: 30rem) {
  .field-grid {
    grid-template-columns: 1fr;
  }
  .profiles-header {
    flex-direction: column;
  }
}

@media (prefers-color-scheme: dark) {
  .profiles-subtitle,
  .profiles-days {
    color: #a1a1aa;
  }
  .profiles-error,
  .field-error,
  .form-error,
  .required-mark {
    color: #fca5a5;
  }
  .field-group legend {
    color: #34d399;
  }
  .profiles-empty-icon {
    color: #71717a;
  }
}
</style>
