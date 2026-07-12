<script setup lang="ts">
// Calendarios de servicio — 8vo recurso del rollout visual (ver memoria
// "admin/crud-visual-redesign-pattern"). El formulario CrudView generico
// mostraba 7 booleanos sueltos (lunes..domingo) y dos datepickers sin
// validar el rango — facil dejar un calendario "al reves" (valid_from >
// valid_until) sin que la UI se quejara, y daba pereza marcar cada dia.
//
// Fix: un toggle por dia (7 chips, lunes a domingo), preview del rango
// calculado en pantalla, y la tabla muestra dias resumidos como
// "L M M J V" (los activos) y conteos de uso (excepciones + plantillas
// referenciando el calendario) para que el admin vea cuantos registros
// dependen del calendario antes de tocarlo.
import { computed, ref } from 'vue'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import DatePicker from 'primevue/datepicker'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import ToggleSwitch from 'primevue/toggleswitch'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import { request, ApiError } from '../api/client'
import { LABELS } from '../messages'
import type { Calendar } from '../types'

interface WeekdayDef {
  key: keyof Pick<
    Calendar,
    'monday' | 'tuesday' | 'wednesday' | 'thursday' | 'friday' | 'saturday' | 'sunday'
  >
  short: string
  long: string
}

// Orden lunes -> domingo (no domingo primero — es la convencion hispanohablante
// y arranca la semana laboral).
const WEEKDAYS: WeekdayDef[] = [
  { key: 'monday', short: 'L', long: 'Lunes' },
  { key: 'tuesday', short: 'M', long: 'Martes' },
  { key: 'wednesday', short: 'M', long: 'Miércoles' },
  { key: 'thursday', short: 'J', long: 'Jueves' },
  { key: 'friday', short: 'V', long: 'Viernes' },
  { key: 'saturday', short: 'S', long: 'Sábado' },
  { key: 'sunday', short: 'D', long: 'Domingo' },
]

const toast = useToast()

// -- Listado -------------------------------------------------------------
const items = ref<Calendar[]>([])
const loading = ref(false)
const error = ref('')

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const res = await request<{ items: Calendar[] }>('GET', '/admin/calendars?page=1&page_size=200')
    items.value = res.items
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : 'No se pudo cargar el listado.'
    items.value = []
  } finally {
    loading.value = false
  }
}

function weekdaysSummary(c: Calendar): string {
  return WEEKDAYS.filter((d) => c[d.key]).map((d) => d.short).join(' ')
}

function formatDate(value: string | null | undefined): string {
  if (!value) return '—'
  const datePart = value.split('T')[0]
  const [y, m, d] = datePart.split('-').map(Number)
  if (!y || !m || !d) return value
  const months = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic']
  return `${String(d).padStart(2, '0')} ${months[m - 1]} ${y}`
}

function rangeLabel(c: Calendar): string {
  if (!c.valid_from || !c.valid_until) return '—'
  return `${formatDate(c.valid_from)} → ${formatDate(c.valid_until)}`
}

// -- Alta / edicion ------------------------------------------------------
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const code = ref('')
const name = ref('')
const validFrom = ref<Date | null>(null)
const validUntil = ref<Date | null>(null)
const active = ref(true)
const weekdayState = ref<Record<string, boolean>>({
  monday: true, tuesday: true, wednesday: true, thursday: true,
  friday: true, saturday: false, sunday: false,
})
const submitting = ref(false)
const formErrorMessage = ref('')

function ymd(d: Date): string {
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}

const rangeError = computed<string>(() => {
  if (!validFrom.value || !validUntil.value) return ''
  if (validFrom.value.getTime() > validUntil.value.getTime()) {
    return 'La fecha "Vigente hasta" debe ser igual o posterior a "Vigente desde".'
  }
  return ''
})

const anyWeekday = computed(() => WEEKDAYS.some((d) => weekdayState.value[d.key]))

function resetFormState(): void {
  formErrorMessage.value = ''
}

function openCreate(): void {
  editingId.value = null
  resetFormState()
  code.value = ''
  name.value = ''
  validFrom.value = null
  validUntil.value = null
  active.value = true
  weekdayState.value = {
    monday: true, tuesday: true, wednesday: true, thursday: true,
    friday: true, saturday: false, sunday: false,
  }
  dialogVisible.value = true
}

function openEdit(row: Calendar): void {
  editingId.value = row.id
  resetFormState()
  code.value = row.code
  name.value = row.name
  validFrom.value = row.valid_from ? parseYmdLocal(row.valid_from) : null
  validUntil.value = row.valid_until ? parseYmdLocal(row.valid_until) : null
  active.value = row.active
  weekdayState.value = {
    monday: row.monday, tuesday: row.tuesday, wednesday: row.wednesday,
    thursday: row.thursday, friday: row.friday, saturday: row.saturday, sunday: row.sunday,
  }
  dialogVisible.value = true
}

// new Date('YYYY-MM-DD') lo interpretaria como UTC midnight; en zonas
// horarias negativas eso cae en el dia anterior. Parseamos manual para
// mantener el dia que ve el admin.
function parseYmdLocal(s: string): Date {
  const [y, m, d] = s.split('-').map(Number)
  return new Date(y, m - 1, d)
}

function closeDialog(): void {
  dialogVisible.value = false
}

async function onSubmit(): Promise<void> {
  resetFormState()
  if (!code.value.trim() || !name.value.trim()) {
    formErrorMessage.value = 'Código y nombre son obligatorios.'
    return
  }
  if (!validFrom.value || !validUntil.value) {
    formErrorMessage.value = 'Indicá el rango de vigencia.'
    return
  }
  if (rangeError.value) {
    formErrorMessage.value = rangeError.value
    return
  }
  if (!anyWeekday.value) {
    formErrorMessage.value = 'Activá al menos un día de la semana.'
    return
  }

  submitting.value = true
  const wasCreate = editingId.value === null
  const body = {
    code: code.value.trim(),
    name: name.value.trim(),
    valid_from: ymd(validFrom.value),
    valid_until: ymd(validUntil.value),
    monday: weekdayState.value.monday,
    tuesday: weekdayState.value.tuesday,
    wednesday: weekdayState.value.wednesday,
    thursday: weekdayState.value.thursday,
    friday: weekdayState.value.friday,
    saturday: weekdayState.value.saturday,
    sunday: weekdayState.value.sunday,
    active: active.value,
  }
  try {
    if (wasCreate) {
      await request('POST', '/admin/calendars', body)
    } else {
      await request('PUT', `/admin/calendars/${editingId.value}`, body)
    }
    dialogVisible.value = false
    await load()
    toast.add({ severity: 'success', summary: wasCreate ? LABELS.created : LABELS.updated, life: 4000 })
  } catch (err) {
    formErrorMessage.value = err instanceof ApiError ? err.message : 'Ocurrió un error inesperado.'
  } finally {
    submitting.value = false
  }
}

// -- Baja logica ---------------------------------------------------------
const confirmTarget = ref<Calendar | null>(null)
const deactivating = ref(false)

function askDeactivate(row: Calendar): void {
  confirmTarget.value = row
}

function cancelDeactivate(): void {
  confirmTarget.value = null
}

async function confirmDeactivate(): Promise<void> {
  if (!confirmTarget.value) return
  deactivating.value = true
  const cal = confirmTarget.value
  try {
    await request('PUT', `/admin/calendars/${cal.id}`, {
      code: cal.code,
      name: cal.name,
      valid_from: cal.valid_from,
      valid_until: cal.valid_until,
      monday: cal.monday, tuesday: cal.tuesday, wednesday: cal.wednesday,
      thursday: cal.thursday, friday: cal.friday, saturday: cal.saturday, sunday: cal.sunday,
      active: false,
    })
    confirmTarget.value = null
    await load()
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

load()
</script>

<template>
  <section class="calendars-view">
    <header class="calendars-header">
      <div>
        <h1>Calendarios de servicio</h1>
        <p class="calendars-subtitle">
          Días de la semana y rango de fechas en los que opera un servicio. Las plantillas de viaje referencian
          estos calendarios para saber cuándo materializar viajes.
        </p>
      </div>
      <Button label="Nuevo calendario" icon="pi pi-plus" @click="openCreate" />
    </header>

    <p v-if="error" role="alert" class="calendars-error">
      {{ error }}
      <Button label="Reintentar" text size="small" @click="load" />
    </p>

    <DataTable :value="items" :loading="loading" dataKey="id" class="calendars-table">
      <template #empty>
        <div class="calendars-empty">
          <i class="pi pi-calendar calendars-empty-icon" aria-hidden="true"></i>
          <p>Todavía no hay calendarios cargados.</p>
        </div>
      </template>

      <Column field="code" header="Código" />

      <Column header="Nombre">
        <template #body="{ data }">
          <span class="calendars-name">{{ data.name }}</span>
        </template>
      </Column>

      <Column header="Vigencia">
        <template #body="{ data }">{{ rangeLabel(data) }}</template>
      </Column>

      <Column header="Días">
        <template #body="{ data }">
          <div v-if="weekdaysSummary(data)" class="weekdays-summary" :title="weekdaysSummary(data)">
            <span
              v-for="d in WEEKDAYS"
              :key="d.key"
              class="weekday-chip"
              :class="{ 'weekday-chip-on': data[d.key] }"
            >{{ d.short }}</span>
          </div>
          <span v-else class="calendars-muted">—</span>
        </template>
      </Column>

      <Column header="Uso">
        <template #body="{ data }">
          <span class="calendars-usage">
            <i class="pi pi-calendar-times" aria-hidden="true"></i>
            <span>{{ data.exception_count }}</span>
            <span class="calendars-usage-sep">·</span>
            <i class="pi pi-file" aria-hidden="true"></i>
            <span>{{ data.template_count }}</span>
          </span>
        </template>
      </Column>

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
          <div class="calendars-actions">
            <Button icon="pi pi-pencil" text rounded aria-label="Editar calendario" @click="openEdit(data)" />
            <Button
              v-if="data.active"
              icon="pi pi-ban"
              text
              rounded
              severity="danger"
              aria-label="Desactivar calendario"
              @click="askDeactivate(data)"
            />
          </div>
        </template>
      </Column>
    </DataTable>

    <Dialog
      v-model:visible="dialogVisible"
      modal
      :header="editingId === null ? 'Nuevo calendario' : 'Editar calendario'"
      :style="{ width: '34rem' }"
    >
      <form novalidate @submit.prevent="onSubmit">
        <p v-if="formErrorMessage" role="alert" class="form-error">{{ formErrorMessage }}</p>

        <div class="field-grid">
          <div class="field">
            <label for="cal-code">Código <span class="required-mark" aria-hidden="true">*</span></label>
            <InputText
              id="cal-code"
              v-model="code"
              maxlength="40"
              placeholder="Identificador único, ej. LJV-2025"
              autocomplete="off"
            />
          </div>
          <div class="field">
            <label for="cal-name">Nombre <span class="required-mark" aria-hidden="true">*</span></label>
            <InputText
              id="cal-name"
              v-model="name"
              maxlength="120"
              placeholder="Descripción legible"
              autocomplete="off"
            />
          </div>
        </div>

        <div class="field-grid">
          <div class="field">
            <label for="cal-from">Vigente desde <span class="required-mark" aria-hidden="true">*</span></label>
            <DatePicker
              id="cal-from"
              v-model="validFrom"
              date-format="yy-mm-dd"
              show-icon
              fluid
            />
          </div>
          <div class="field">
            <label for="cal-until">Vigente hasta <span class="required-mark" aria-hidden="true">*</span></label>
            <DatePicker
              id="cal-until"
              v-model="validUntil"
              date-format="yy-mm-dd"
              show-icon
              :invalid="Boolean(rangeError)"
              fluid
            />
            <p v-if="rangeError" class="field-error">{{ rangeError }}</p>
          </div>
        </div>

        <div class="field">
          <label>Días de operación</label>
          <div class="weekday-toggles">
            <button
              v-for="d in WEEKDAYS"
              :key="d.key"
              type="button"
              class="weekday-toggle"
              :class="{ 'weekday-toggle-on': weekdayState[d.key] }"
              :aria-pressed="weekdayState[d.key]"
              :aria-label="d.long"
              @click="weekdayState[d.key] = !weekdayState[d.key]"
            >
              <span class="weekday-toggle-letter">{{ d.short }}</span>
              <span class="weekday-toggle-name">{{ d.long }}</span>
            </button>
          </div>
          <p class="field-help">Activá al menos un día — sin esto el calendario nunca operará.</p>
        </div>

        <div class="field field-inline">
          <label for="cal-active" class="switch-label">
            <ToggleSwitch id="cal-active" v-model="active" />
            Calendario activo
          </label>
        </div>

        <div class="dialog-actions">
          <Button type="button" :label="LABELS.cancel" severity="secondary" text @click="closeDialog" />
          <Button type="submit" :label="LABELS.save" :loading="submitting" />
        </div>
      </form>
    </Dialog>

    <Dialog
      :visible="confirmTarget !== null"
      modal
      :closable="false"
      header="Confirmar desactivación"
      :style="{ width: '28rem' }"
    >
      <p>
        ¿Desactivar el calendario <strong>{{ confirmTarget?.name }}</strong> ({{ confirmTarget?.code }})?
      </p>
      <p v-if="confirmTarget && (confirmTarget.exception_count > 0 || confirmTarget.template_count > 0)" class="confirm-warning">
        <i class="pi pi-exclamation-triangle" aria-hidden="true"></i>
        Hay
        <strong>{{ confirmTarget.template_count }}</strong> plantilla(s) y
        <strong>{{ confirmTarget.exception_count }}</strong> excepción(es) que lo referencian.
        La generación de viajes usará el calendario activo disponible que aplique a cada plantilla.
      </p>
      <template #footer>
        <Button :label="LABELS.cancel" severity="secondary" text @click="cancelDeactivate" />
        <Button
          :label="LABELS.deactivate"
          severity="danger"
          :loading="deactivating"
          autofocus
          @click="confirmDeactivate"
        />
      </template>
    </Dialog>
  </section>
</template>

<style scoped>
.calendars-view {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}
.calendars-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
}
.calendars-header h1 {
  margin: 0 0 0.25rem;
}
.calendars-subtitle {
  margin: 0;
  color: #52525b;
  font-size: 0.9375rem;
  max-width: 48rem;
}
.calendars-error {
  color: #b91c1c;
  margin: 0;
}
.calendars-table :deep(.p-datatable-tbody td) {
  vertical-align: middle;
}
.calendars-name {
  font-weight: 600;
}
.calendars-muted {
  color: #a1a1aa;
}
.calendars-actions {
  display: flex;
  gap: 0.25rem;
}
.calendars-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 2rem 0;
}
.calendars-empty-icon {
  font-size: 1.75rem;
  color: #a1a1aa;
}

.weekdays-summary {
  display: inline-flex;
  gap: 0.25rem;
}
.weekday-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.5rem;
  height: 1.5rem;
  border-radius: 0.375rem;
  font-size: 0.75rem;
  font-weight: 600;
  background: #f4f4f5;
  color: #a1a1aa;
}
.weekday-chip-on {
  background: #ecfdf5;
  color: #047857;
}
.calendars-usage {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  color: #52525b;
  font-size: 0.9375rem;
}
.calendars-usage-sep {
  color: #d4d4d8;
}
.calendars-usage i {
  font-size: 0.875rem;
  color: #71717a;
}

.field-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
  margin-bottom: 0.25rem;
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
.field-error {
  margin: 0;
  font-size: 0.8125rem;
  color: #b91c1c;
}
.required-mark {
  color: #b91c1c;
}
.field-inline {
  margin-bottom: 0.5rem;
}
.switch-label {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  font-weight: 600;
  width: fit-content;
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

.weekday-toggles {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}
.weekday-toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0.75rem;
  border-radius: 0.5rem;
  border: 1px solid #e4e4e7;
  background: #ffffff;
  font: inherit;
  cursor: pointer;
  transition: background-color 120ms ease, border-color 120ms ease, color 120ms ease;
}
.weekday-toggle:hover {
  border-color: #a1a1aa;
}
.weekday-toggle-letter {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.5rem;
  height: 1.5rem;
  border-radius: 0.375rem;
  background: #f4f4f5;
  color: #71717a;
  font-weight: 700;
}
.weekday-toggle-name {
  font-size: 0.875rem;
  color: #3f3f46;
}
.weekday-toggle-on {
  border-color: #10b981;
  background: #ecfdf5;
}
.weekday-toggle-on .weekday-toggle-letter {
  background: #10b981;
  color: #ffffff;
}
.weekday-toggle-on .weekday-toggle-name {
  color: #047857;
  font-weight: 600;
}
.weekday-toggle:focus-visible {
  outline: 2px solid #60a5fa;
  outline-offset: 2px;
}

.confirm-warning {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  margin: 0.75rem 0 0;
  padding: 0.625rem 0.75rem;
  background: #fef3c7;
  border-radius: 0.5rem;
  color: #78350f;
  font-size: 0.875rem;
}
.confirm-warning i {
  font-size: 1rem;
  margin-top: 0.0625rem;
}

@media (prefers-color-scheme: dark) {
  .calendars-subtitle {
    color: #a1a1aa;
  }
  .calendars-error,
  .form-error,
  .field-error,
  .required-mark {
    color: #fca5a5;
  }
  .calendars-empty-icon {
    color: #71717a;
  }
  .weekday-chip {
    background: #27272a;
    color: #71717a;
  }
  .weekday-chip-on {
    background: #052e22;
    color: #6ee7b7;
  }
  .calendars-usage {
    color: #a1a1aa;
  }
  .calendars-usage i,
  .calendars-usage-sep {
    color: #71717a;
  }
  .field-help {
    color: #a1a1aa;
  }
  .weekday-toggle {
    background: #18181b;
    border-color: #3f3f46;
  }
  .weekday-toggle:hover {
    border-color: #71717a;
  }
  .weekday-toggle-letter {
    background: #27272a;
    color: #a1a1aa;
  }
  .weekday-toggle-name {
    color: #d4d4d8;
  }
  .weekday-toggle-on {
    border-color: #10b981;
    background: #052e22;
  }
  .weekday-toggle-on .weekday-toggle-letter {
    background: #10b981;
    color: #052e16;
  }
  .weekday-toggle-on .weekday-toggle-name {
    color: #6ee7b7;
  }
  .confirm-warning {
    background: #422006;
    color: #fcd34d;
  }
}
</style>