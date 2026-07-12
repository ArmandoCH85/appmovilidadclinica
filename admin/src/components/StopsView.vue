<script setup lang="ts">
// Prototipo de rediseño visual para Paradas (frontend-design skill, a pedido
// explicito: SOLO esta seccion por ahora, sin tocar CrudView.vue compartido
// — si el patron gusta, se replica al resto en un pase aparte, ver memoria
// "admin/crud-visual-redesign-pattern"). Reusa `useCrudResource` (logica de datos,
// no visual) pero escribe su propia tabla/formulario porque conoce los 7
// campos reales de `Stop` de antemano — no necesita el loop generico
// dirigido por config que usa CrudView para los otros 13 recursos.
import { reactive, ref } from 'vue'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Textarea from 'primevue/textarea'
import Select from 'primevue/select'
import ToggleSwitch from 'primevue/toggleswitch'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import { useCrudResource } from '../api/crud'
import { ApiError } from '../api/client'
import { LABELS } from '../messages'
import type { Stop } from '../types'

const { items, page, pageSize, total, loading, error, list, create, update, softDelete } =
  useCrudResource<Stop>('/admin/stops')
const toast = useToast()

list()

function onPage(event: { page: number; rows: number }): void {
  page.value = event.page + 1
  pageSize.value = event.rows
  list()
}

// Señal visual deliberada (no decorativa): en este dominio hay UNA Sede
// (destino/origen final de cada ruta) y VARIOS Paraderos (puntos de paso).
// El pill solido vs. el outline traduce esa jerarquía real de un vistazo,
// sin depender solo del texto "Sede"/"Paradero" para distinguirlas.
function stopTypeTagProps(stopType: Stop['stop_type']) {
  return stopType === 'SEDE'
    ? { value: 'Sede', icon: 'pi pi-building', severity: 'success' as const }
    : { value: 'Paradero', icon: 'pi pi-map-marker', severity: 'secondary' as const }
}

// ---------------------------------------------------------------------------
// Alta / edicion
// ---------------------------------------------------------------------------
type FormState = {
  code: string
  name: string
  stop_type: Stop['stop_type'] | ''
  reference_text: string
  latitude: number | null
  longitude: number | null
  active: boolean
}

function blankForm(): FormState {
  return { code: '', name: '', stop_type: '', reference_text: '', latitude: null, longitude: null, active: true }
}

const STOP_TYPE_OPTIONS = [
  { value: 'SEDE', label: 'Sede' },
  { value: 'PARADERO', label: 'Paradero' },
]

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

function openEdit(row: Stop): void {
  editingId.value = row.id
  resetFormState()
  formData.code = row.code
  formData.name = row.name
  formData.stop_type = row.stop_type
  formData.reference_text = row.reference_text ?? ''
  formData.latitude = row.latitude ?? null
  formData.longitude = row.longitude ?? null
  formData.active = row.active
  dialogVisible.value = true
}

function closeDialog(): void {
  dialogVisible.value = false
}

function validateClientSide(): boolean {
  if (!formData.code.trim()) {
    fieldErrors.code = LABELS.requiredField
    return false
  }
  if (formData.code.length > 30) {
    fieldErrors.code = 'Máximo 30 caracteres.'
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
  if (!formData.stop_type) {
    fieldErrors.stop_type = LABELS.requiredField
    return false
  }
  if (formData.reference_text.length > 255) {
    fieldErrors.reference_text = 'Máximo 255 caracteres.'
    return false
  }
  return true
}

const FIELD_ERROR_RE = /^campo (\w+) invalido:/i
const SERVER_FIELD_MAP: Record<string, keyof FormState> = {
  code: 'code',
  name: 'name',
  stoptype: 'stop_type',
  referencetext: 'reference_text',
  latitude: 'latitude',
  longitude: 'longitude',
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
    code: formData.code,
    name: formData.name,
    stop_type: formData.stop_type,
    reference_text: formData.reference_text || null,
    latitude: formData.latitude,
    longitude: formData.longitude,
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
const confirmTarget = ref<(Stop) | null>(null)
const deactivating = ref(false)

function askDeactivate(row: Stop): void {
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
  <section class="stops-view">
    <header class="stops-header">
      <div>
        <h1>Paradas</h1>
        <p class="stops-subtitle">La Sede y los paraderos donde el transporte recoge o deja gente.</p>
      </div>
      <Button label="Nueva parada" icon="pi pi-plus" @click="openCreate" />
    </header>

    <p v-if="error" role="alert" class="stops-error">
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
      class="stops-table"
      @page="onPage"
    >
      <template #empty>
        <div class="stops-empty">
          <i class="pi pi-map-marker stops-empty-icon" aria-hidden="true"></i>
          <p>Todavía no cargaste ninguna parada.</p>
          <Button label="Nueva parada" icon="pi pi-plus" text @click="openCreate" />
        </div>
      </template>

      <Column field="code" header="Código">
        <template #body="{ data }"><span class="stops-code">{{ data.code }}</span></template>
      </Column>

      <Column field="name" header="Nombre" />

      <Column field="stop_type" header="Tipo">
        <template #body="{ data }">
          <Tag v-bind="stopTypeTagProps(data.stop_type)" />
        </template>
      </Column>

      <Column field="reference_text" header="Referencia">
        <template #body="{ data }">
          <span class="stops-reference">{{ data.reference_text || '—' }}</span>
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
          <div class="stops-actions">
            <Button icon="pi pi-pencil" text rounded aria-label="Editar parada" @click="openEdit(data)" />
            <Button
              icon="pi pi-ban"
              text
              rounded
              severity="danger"
              aria-label="Desactivar parada"
              @click="askDeactivate(data)"
            />
          </div>
        </template>
      </Column>
    </DataTable>

    <Dialog
      v-model:visible="dialogVisible"
      modal
      :header="editingId === null ? 'Nueva parada' : 'Editar parada'"
      :style="{ width: '34rem' }"
      class="stops-dialog"
    >
      <form novalidate @submit.prevent="onSubmit">
        <p v-if="formErrorMessage" role="alert" class="form-error">{{ formErrorMessage }}</p>

        <fieldset class="field-group">
          <legend>Identificación</legend>
          <div class="field-grid">
            <div class="field">
              <label for="stop-code">Código <span class="required-mark" aria-hidden="true">*</span></label>
              <InputText
                id="stop-code"
                ref="codeInputEl"
                v-model="formData.code"
                aria-required="true"
                :aria-invalid="!!fieldErrors.code"
                :aria-describedby="fieldErrors.code ? 'stop-code-error' : undefined"
              />
              <p v-if="fieldErrors.code" id="stop-code-error" role="alert" class="field-error">
                {{ fieldErrors.code }}
              </p>
            </div>
            <div class="field">
              <label for="stop-type">Tipo <span class="required-mark" aria-hidden="true">*</span></label>
              <Select
                id="stop-type"
                v-model="formData.stop_type"
                :options="STOP_TYPE_OPTIONS"
                optionLabel="label"
                optionValue="value"
                placeholder="Elegir…"
                aria-required="true"
                :aria-invalid="!!fieldErrors.stop_type"
                :aria-describedby="fieldErrors.stop_type ? 'stop-type-error' : undefined"
              />
              <p v-if="fieldErrors.stop_type" id="stop-type-error" role="alert" class="field-error">
                {{ fieldErrors.stop_type }}
              </p>
            </div>
          </div>
          <div class="field">
            <label for="stop-name">Nombre <span class="required-mark" aria-hidden="true">*</span></label>
            <InputText
              id="stop-name"
              v-model="formData.name"
              aria-required="true"
              :aria-invalid="!!fieldErrors.name"
              :aria-describedby="fieldErrors.name ? 'stop-name-error' : undefined"
            />
            <p v-if="fieldErrors.name" id="stop-name-error" role="alert" class="field-error">
              {{ fieldErrors.name }}
            </p>
          </div>
        </fieldset>

        <fieldset class="field-group">
          <legend>Ubicación</legend>
          <div class="field">
            <label for="stop-reference">Referencia</label>
            <Textarea
              id="stop-reference"
              v-model="formData.reference_text"
              rows="2"
              placeholder="Ej.: frente al banco, portón lateral…"
              :aria-invalid="!!fieldErrors.reference_text"
              :aria-describedby="fieldErrors.reference_text ? 'stop-reference-error' : 'stop-reference-help'"
            />
            <p id="stop-reference-help" class="field-help">Ayuda al chofer a ubicar el punto sin coordenadas.</p>
            <p v-if="fieldErrors.reference_text" id="stop-reference-error" role="alert" class="field-error">
              {{ fieldErrors.reference_text }}
            </p>
          </div>
          <div class="field-grid">
            <div class="field">
              <label for="stop-lat">Latitud</label>
              <InputNumber
                inputId="stop-lat"
                v-model="formData.latitude"
                :minFractionDigits="0"
                :maxFractionDigits="8"
                :useGrouping="false"
              />
            </div>
            <div class="field">
              <label for="stop-lng">Longitud</label>
              <InputNumber
                inputId="stop-lng"
                v-model="formData.longitude"
                :minFractionDigits="0"
                :maxFractionDigits="8"
                :useGrouping="false"
              />
            </div>
          </div>
        </fieldset>

        <fieldset class="field-group field-group-inline">
          <legend class="sr-only">Estado</legend>
          <label for="stop-active" class="switch-label">
            <ToggleSwitch id="stop-active" v-model="formData.active" />
            Parada activa
          </label>
        </fieldset>

        <div class="dialog-actions">
          <Button type="button" :label="LABELS.cancel" severity="secondary" text @click="closeDialog" />
          <Button type="submit" :label="LABELS.save" :loading="submitting" />
        </div>
      </form>
    </Dialog>

    <Dialog :visible="confirmTarget !== null" modal :closable="false" header="Confirmar" :style="{ width: '24rem' }">
      <p>¿Desactivar la parada "{{ confirmTarget?.name }}"? Podrá revertirlo editándola luego.</p>
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
.stops-view {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}
.stops-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}
.stops-header h1 {
  margin: 0 0 0.25rem;
}
.stops-subtitle {
  margin: 0;
  color: #52525b;
  font-size: 0.9375rem;
}
.stops-error {
  color: #b91c1c;
  margin: 0;
}
.stops-code {
  font-family: ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
  font-variant-numeric: tabular-nums;
  font-size: 0.875rem;
  letter-spacing: 0.02em;
}
.stops-reference {
  color: #52525b;
}
.stops-actions {
  display: flex;
  gap: 0.25rem;
}
.stops-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 2rem 0;
}
.stops-empty-icon {
  font-size: 1.75rem;
  color: #a1a1aa;
}

/* Formulario: fieldsets agrupan por tema real (Identificacion/Ubicacion/
   Estado), no por orden de columnas del backend — reduce carga cognitiva
   frente a una lista plana de 7 campos sin jerarquia. */
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
  .stops-header {
    flex-direction: column;
  }
}

@media (prefers-color-scheme: dark) {
  .stops-subtitle,
  .stops-reference {
    color: #a1a1aa;
  }
  .stops-error,
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
  .stops-empty-icon {
    color: #71717a;
  }
}
</style>
