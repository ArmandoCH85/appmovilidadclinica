<script setup lang="ts">
// Vehículos con el mismo patrón visual que Paradas (ver memoria
// "admin/crud-visual-redesign-pattern" — 2do recurso del rollout, todavía
// sección por sección, no generalizado a CrudView.vue). Reusa
// `useCrudResource` (logica de datos, no visual); escribe su propio
// template porque conoce los 5 campos reales de `Vehicle` de antemano.
import { reactive, ref } from 'vue'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Textarea from 'primevue/textarea'
import ToggleSwitch from 'primevue/toggleswitch'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import { useCrudResource } from '../api/crud'
import { ApiError } from '../api/client'
import { LABELS } from '../messages'
import type { Vehicle } from '../types'

const { items, page, pageSize, total, loading, error, list, create, update, softDelete } =
  useCrudResource<Vehicle>('/admin/vehicles')
const toast = useToast()

list()

function onPage(event: { page: number; rows: number }): void {
  page.value = event.page + 1
  pageSize.value = event.rows
  list()
}

// ---------------------------------------------------------------------------
// Alta / edicion
// ---------------------------------------------------------------------------
type FormState = {
  internal_code: string
  plate: string
  description: string
  seat_capacity: number | null
  active: boolean
}

function blankForm(): FormState {
  return { internal_code: '', plate: '', description: '', seat_capacity: null, active: true }
}

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formData = reactive<FormState>(blankForm())
const fieldErrors = reactive<Record<string, string>>({})
const formErrorMessage = ref('')
const submitting = ref(false)
const codeInputEl = ref<{ focus?: () => void; $el?: { focus?: () => void } } | null>(null)

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

function openEdit(row: Vehicle): void {
  editingId.value = row.id
  resetFormState()
  formData.internal_code = row.internal_code
  formData.plate = row.plate
  formData.description = row.description ?? ''
  formData.seat_capacity = row.seat_capacity
  formData.active = row.active
  dialogVisible.value = true
}

function closeDialog(): void {
  dialogVisible.value = false
}

function validateClientSide(): boolean {
  if (!formData.internal_code.trim()) {
    fieldErrors.internal_code = LABELS.requiredField
    return false
  }
  if (formData.internal_code.length > 30) {
    fieldErrors.internal_code = 'Máximo 30 caracteres.'
    return false
  }
  if (!formData.plate.trim()) {
    fieldErrors.plate = LABELS.requiredField
    return false
  }
  if (formData.plate.length > 15) {
    fieldErrors.plate = 'Máximo 15 caracteres.'
    return false
  }
  if (formData.description.length > 120) {
    fieldErrors.description = 'Máximo 120 caracteres.'
    return false
  }
  if (!formData.seat_capacity || formData.seat_capacity <= 0) {
    fieldErrors.seat_capacity = 'Debe ser mayor a 0.'
    return false
  }
  return true
}

const FIELD_ERROR_RE = /^campo (\w+) invalido:/i
const SERVER_FIELD_MAP: Record<string, keyof FormState> = {
  internalcode: 'internal_code',
  plate: 'plate',
  description: 'description',
  seatcapacity: 'seat_capacity',
  active: 'active',
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
    internal_code: formData.internal_code,
    plate: formData.plate,
    description: formData.description || null,
    seat_capacity: formData.seat_capacity,
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
// Baja logica (nunca DELETE — ver comentario en api/crud.ts softDelete)
// ---------------------------------------------------------------------------
const confirmTarget = ref<Vehicle | null>(null)
const deactivating = ref(false)

function askDeactivate(row: Vehicle): void {
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
  <section class="vehicles-view">
    <header class="vehicles-header">
      <div>
        <h1>Vehículos</h1>
        <p class="vehicles-subtitle">La flota de buses/vans y su capacidad de asientos.</p>
      </div>
      <Button label="Nuevo vehículo" icon="pi pi-plus" @click="openCreate" />
    </header>

    <p v-if="error" role="alert" class="vehicles-error">
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
      class="vehicles-table"
      @page="onPage"
    >
      <template #empty>
        <div class="vehicles-empty">
          <i class="pi pi-car vehicles-empty-icon" aria-hidden="true"></i>
          <p>Todavía no cargaste ningún vehículo.</p>
          <Button label="Nuevo vehículo" icon="pi pi-plus" text @click="openCreate" />
        </div>
      </template>

      <Column field="internal_code" header="Código interno">
        <template #body="{ data }"><span class="vehicles-code">{{ data.internal_code }}</span></template>
      </Column>

      <Column field="plate" header="Patente">
        <template #body="{ data }"><span class="vehicles-plate">{{ data.plate }}</span></template>
      </Column>

      <Column field="seat_capacity" header="Capacidad">
        <template #body="{ data }">
          <Tag :value="`${data.seat_capacity} asientos`" icon="pi pi-users" severity="info" />
        </template>
      </Column>

      <Column field="description" header="Descripción">
        <template #body="{ data }">
          <span class="vehicles-description">{{ data.description || '—' }}</span>
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
          <div class="vehicles-actions">
            <Button icon="pi pi-pencil" text rounded aria-label="Editar vehículo" @click="openEdit(data)" />
            <Button
              icon="pi pi-ban"
              text
              rounded
              severity="danger"
              aria-label="Desactivar vehículo"
              @click="askDeactivate(data)"
            />
          </div>
        </template>
      </Column>
    </DataTable>

    <Dialog
      v-model:visible="dialogVisible"
      modal
      :header="editingId === null ? 'Nuevo vehículo' : 'Editar vehículo'"
      :style="{ width: '34rem' }"
      class="vehicles-dialog"
    >
      <form novalidate @submit.prevent="onSubmit">
        <p v-if="formErrorMessage" role="alert" class="form-error">{{ formErrorMessage }}</p>

        <fieldset class="field-group">
          <legend>Identificación</legend>
          <div class="field-grid">
            <div class="field">
              <label for="vehicle-code">Código interno <span class="required-mark" aria-hidden="true">*</span></label>
              <InputText
                id="vehicle-code"
                ref="codeInputEl"
                v-model="formData.internal_code"
                aria-required="true"
                :aria-invalid="!!fieldErrors.internal_code"
                :aria-describedby="fieldErrors.internal_code ? 'vehicle-code-error' : undefined"
              />
              <p v-if="fieldErrors.internal_code" id="vehicle-code-error" role="alert" class="field-error">
                {{ fieldErrors.internal_code }}
              </p>
            </div>
            <div class="field">
              <label for="vehicle-plate">Patente <span class="required-mark" aria-hidden="true">*</span></label>
              <InputText
                id="vehicle-plate"
                v-model="formData.plate"
                aria-required="true"
                :aria-invalid="!!fieldErrors.plate"
                :aria-describedby="fieldErrors.plate ? 'vehicle-plate-error' : undefined"
              />
              <p v-if="fieldErrors.plate" id="vehicle-plate-error" role="alert" class="field-error">
                {{ fieldErrors.plate }}
              </p>
            </div>
          </div>
        </fieldset>

        <fieldset class="field-group">
          <legend>Capacidad y detalle</legend>
          <div class="field">
            <label for="vehicle-capacity">
              Cantidad de asientos <span class="required-mark" aria-hidden="true">*</span>
            </label>
            <InputNumber
              inputId="vehicle-capacity"
              v-model="formData.seat_capacity"
              :min="1"
              :useGrouping="false"
              aria-required="true"
              :aria-invalid="!!fieldErrors.seat_capacity"
              :aria-describedby="fieldErrors.seat_capacity ? 'vehicle-capacity-error' : 'vehicle-capacity-help'"
            />
            <p id="vehicle-capacity-help" class="field-help">
              Debe coincidir con la cantidad de asientos activos cargados en "Asientos de vehículos" — si no
              coinciden, la generación de viajes rechaza este vehículo.
            </p>
            <p v-if="fieldErrors.seat_capacity" id="vehicle-capacity-error" role="alert" class="field-error">
              {{ fieldErrors.seat_capacity }}
            </p>
          </div>
          <div class="field">
            <label for="vehicle-description">Descripción</label>
            <Textarea
              id="vehicle-description"
              v-model="formData.description"
              rows="2"
              placeholder="Ej.: bus corporativo, 2 puertas…"
              :aria-invalid="!!fieldErrors.description"
              :aria-describedby="fieldErrors.description ? 'vehicle-description-error' : undefined"
            />
            <p v-if="fieldErrors.description" id="vehicle-description-error" role="alert" class="field-error">
              {{ fieldErrors.description }}
            </p>
          </div>
        </fieldset>

        <fieldset class="field-group field-group-inline">
          <legend class="sr-only">Estado</legend>
          <label for="vehicle-active" class="switch-label">
            <ToggleSwitch id="vehicle-active" v-model="formData.active" />
            Vehículo activo
          </label>
        </fieldset>

        <div class="dialog-actions">
          <Button type="button" :label="LABELS.cancel" severity="secondary" text @click="closeDialog" />
          <Button type="submit" :label="LABELS.save" :loading="submitting" />
        </div>
      </form>
    </Dialog>

    <Dialog :visible="confirmTarget !== null" modal :closable="false" header="Confirmar" :style="{ width: '24rem' }">
      <p>¿Desactivar el vehículo "{{ confirmTarget?.internal_code }}"? Podrá revertirlo editándolo luego.</p>
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
.vehicles-view {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}
.vehicles-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}
.vehicles-header h1 {
  margin: 0 0 0.25rem;
}
.vehicles-subtitle {
  margin: 0;
  color: #52525b;
  font-size: 0.9375rem;
}
.vehicles-error {
  color: #b91c1c;
  margin: 0;
}
.vehicles-code {
  font-family: ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
  font-variant-numeric: tabular-nums;
  font-size: 0.875rem;
  letter-spacing: 0.02em;
}
.vehicles-plate {
  font-family: ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
  font-weight: 700;
  letter-spacing: 0.06em;
  border: 1.5px solid currentColor;
  border-radius: 0.25rem;
  padding: 0.125rem 0.4rem;
  font-size: 0.8125rem;
}
.vehicles-description {
  color: #52525b;
}
.vehicles-actions {
  display: flex;
  gap: 0.25rem;
}
.vehicles-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 2rem 0;
}
.vehicles-empty-icon {
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
.field-help {
  margin: 0;
  font-size: 0.8125rem;
  color: #71717a;
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
  .vehicles-header {
    flex-direction: column;
  }
}

@media (prefers-color-scheme: dark) {
  .vehicles-subtitle,
  .vehicles-description {
    color: #a1a1aa;
  }
  .vehicles-error,
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
  .vehicles-empty-icon {
    color: #71717a;
  }
}
</style>
