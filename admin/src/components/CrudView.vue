<script setup lang="ts">
// Motor CRUD generico (Fase 4, tareas 4.3/4.4). Una sola vista, dirigida por
// `CrudResourceConfig` (resources.ts), cubre lista+alta+edicion+baja logica
// para los 7 recursos (wiring real: Fase 5, prop `config` por ruta).
import { onMounted, reactive, ref } from 'vue'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Textarea from 'primevue/textarea'
import Select from 'primevue/select'
import ToggleSwitch from 'primevue/toggleswitch'
import Password from 'primevue/password'
import { useCrudResource } from '../api/crud'
import { ApiError } from '../api/client'
import { LABELS } from '../messages'
import type { CrudField, CrudResourceConfig } from '../resources'

// `listPath` (Fase 5): override del path de listado para recursos cuyo
// GET real no coincide con `config.path` (route-stops — ver RouteStopsView.vue
// y el comentario en api/crud.ts `list()`). Default: usa `config.path`.
const props = defineProps<{ config: CrudResourceConfig; listPath?: string }>()

type Row = Record<string, any>
type ResourceRow = Row & { id: number }

const { items, page, pageSize, total, loading, error, list, create, update, softDelete } =
  useCrudResource<ResourceRow>(props.config.path)

onMounted(() => {
  list(props.listPath)
})

// DataTable en modo lazy: el evento trae el indice 0-based de pagina.
function onPage(event: { page: number; rows: number }): void {
  page.value = event.page + 1
  pageSize.value = event.rows
  list(props.listPath)
}

/** Nunca renderiza booleanos crudos ("true"/"false") ni celdas en blanco sin
 * texto — a11y: nunca solo color, siempre texto real. */
function formatCell(value: unknown): string {
  if (typeof value === 'boolean') return value ? 'Sí' : 'No'
  if (value === null || value === undefined || value === '') return '—'
  return String(value)
}

// ---------------------------------------------------------------------------
// Alta / edicion
// ---------------------------------------------------------------------------
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formData = reactive<Row>({})
const fieldErrors = reactive<Record<string, string>>({})
const formErrorMessage = ref('')
const submitting = ref(false)
const fieldEls = ref<Record<string, unknown>>({})

function defaultValue(field: CrudField): unknown {
  if (field.type === 'boolean') return false
  if (field.type === 'number') return null
  return ''
}

function resetFormState(): void {
  formErrorMessage.value = ''
  for (const key of Object.keys(fieldErrors)) delete fieldErrors[key]
}

function openCreate(): void {
  editingId.value = null
  resetFormState()
  for (const field of props.config.fields) {
    formData[field.key] = defaultValue(field)
  }
  dialogVisible.value = true
}

function openEdit(row: ResourceRow): void {
  editingId.value = row.id
  resetFormState()
  for (const field of props.config.fields) {
    formData[field.key] = row[field.key] ?? defaultValue(field)
  }
  dialogVisible.value = true
}

function closeDialog(): void {
  dialogVisible.value = false
}

function fieldDomId(field: CrudField): string {
  return `crud-field-${field.key}`
}

/** Los componentes de formulario de PrimeVue exponen `focus()` en su
 * instancia (o, si no, en `$el`) — usado tanto por la validacion
 * client-side como por el mapeo de 422 para mover el foco al campo
 * invalido (a11y: el error nunca es solo visual). */
function focusField(key: string): void {
  const el = fieldEls.value[key] as { focus?: () => void; $el?: { focus?: () => void } } | undefined
  if (!el) return
  if (typeof el.focus === 'function') el.focus()
  else el.$el?.focus?.()
}

/** Validacion client-side (tarea 4.4): refleja required/maxLength de la
 * config antes de pegarle al server — la mayoria de errores 422 nunca
 * deberian llegar a disparar (backstop raro, ver diseno). */
function validateClientSide(): boolean {
  for (const field of props.config.fields) {
    const value = formData[field.key]
    // `required:'create'` (ej. password de usuarios): solo obligatorio al
    // dar de alta — en edicion, vacio significa "no cambiar" (ver resources.ts).
    const isRequired = field.required === true || (field.required === 'create' && editingId.value === null)
    if (isRequired && (value === '' || value === null || value === undefined)) {
      fieldErrors[field.key] = LABELS.requiredField
      focusField(field.key)
      return false
    }
    if (field.maxLength && typeof value === 'string' && value.length > field.maxLength) {
      fieldErrors[field.key] = `Máximo ${field.maxLength} caracteres.`
      focusField(field.key)
      return false
    }
  }
  return true
}

// El backend (validate.ToAppError, backend/internal/shared/validate/validate.go)
// arma el mensaje 422 como "campo {NombreGoDelStruct} invalido: ...", donde el
// nombre es el del struct field en PascalCase (ej. "EmployeeCode"), no el
// json tag. Se compara sin guiones bajos contra `field.key` para mapear al
// campo del formulario.
const FIELD_ERROR_RE = /^campo (\w+) invalido:/i

function mapServerFieldError(message: string): string | null {
  const match = FIELD_ERROR_RE.exec(message)
  if (!match) return null
  const goField = match[1].toLowerCase()
  const field = props.config.fields.find((f) => f.key.replace(/_/g, '').toLowerCase() === goField)
  return field?.key ?? null
}

async function onSubmit(): Promise<void> {
  resetFormState()
  if (!validateClientSide()) return

  submitting.value = true
  try {
    if (editingId.value === null) {
      await create({ ...formData })
    } else {
      await update(editingId.value, { ...formData })
    }
    dialogVisible.value = false
    await list(props.listPath)
  } catch (err) {
    if (err instanceof ApiError && err.code === 422) {
      const key = mapServerFieldError(err.message)
      if (key) {
        fieldErrors[key] = err.message
        focusField(key)
      } else {
        formErrorMessage.value = err.message
      }
    } else {
      formErrorMessage.value = err instanceof ApiError ? err.message : 'Ocurrió un error inesperado.'
    }
  } finally {
    submitting.value = false
  }
}

// ---------------------------------------------------------------------------
// Baja logica (nunca DELETE — ver useCrudResource.softDelete)
// ---------------------------------------------------------------------------
const confirmTarget = ref<ResourceRow | null>(null)
const deactivating = ref(false)

function askDeactivate(row: ResourceRow): void {
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
    await list(props.listPath)
  } finally {
    deactivating.value = false
  }
}
</script>

<template>
  <section class="crud-view">
    <header class="crud-header">
      <h1>{{ props.config.labelPlural }}</h1>
      <Button :label="`Nuevo ${props.config.labelSingular}`" icon="pi pi-plus" @click="openCreate" />
    </header>

    <p v-if="error" role="alert" class="crud-error">
      {{ error }}
      <Button label="Reintentar" text size="small" @click="list(props.listPath)" />
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
      @page="onPage"
    >
      <template #empty>
        <p>{{ LABELS.empty }}</p>
      </template>

      <Column v-for="col in props.config.columns" :key="col.key" :field="col.key" :header="col.label">
        <template #body="{ data }">{{ formatCell(data[col.key]) }}</template>
      </Column>

      <Column header="Acciones" :exportable="false">
        <template #body="{ data }">
          <Button
            icon="pi pi-pencil"
            text
            rounded
            :aria-label="`Editar ${props.config.labelSingular}`"
            @click="openEdit(data)"
          />
          <Button
            icon="pi pi-ban"
            text
            rounded
            severity="danger"
            :aria-label="`Desactivar ${props.config.labelSingular}`"
            @click="askDeactivate(data)"
          />
        </template>
      </Column>
    </DataTable>

    <Dialog
      v-model:visible="dialogVisible"
      modal
      :header="editingId === null ? `Nuevo ${props.config.labelSingular}` : `Editar ${props.config.labelSingular}`"
      :style="{ width: '28rem' }"
    >
      <form novalidate @submit.prevent="onSubmit">
        <p v-if="formErrorMessage" role="alert" class="form-error">{{ formErrorMessage }}</p>

        <div v-for="field in props.config.fields" :key="field.key" class="field">
          <label :for="fieldDomId(field)">{{ field.label }}</label>

          <InputText
            v-if="field.type === 'text'"
            :id="fieldDomId(field)"
            :ref="(el) => { fieldEls[field.key] = el }"
            v-model="formData[field.key]"
            :aria-invalid="!!fieldErrors[field.key]"
            :aria-describedby="fieldErrors[field.key] ? `${fieldDomId(field)}-error` : undefined"
          />
          <Textarea
            v-else-if="field.type === 'textarea'"
            :id="fieldDomId(field)"
            :ref="(el) => { fieldEls[field.key] = el }"
            v-model="formData[field.key]"
            :aria-invalid="!!fieldErrors[field.key]"
            :aria-describedby="fieldErrors[field.key] ? `${fieldDomId(field)}-error` : undefined"
          />
          <InputNumber
            v-else-if="field.type === 'number'"
            :inputId="fieldDomId(field)"
            :ref="(el) => { fieldEls[field.key] = el }"
            v-model="formData[field.key]"
            :aria-invalid="!!fieldErrors[field.key]"
            :aria-describedby="fieldErrors[field.key] ? `${fieldDomId(field)}-error` : undefined"
          />
          <Select
            v-else-if="field.type === 'select'"
            :inputId="fieldDomId(field)"
            :ref="(el) => { fieldEls[field.key] = el }"
            v-model="formData[field.key]"
            :options="field.options"
            optionLabel="label"
            optionValue="value"
            :aria-invalid="!!fieldErrors[field.key]"
            :aria-describedby="fieldErrors[field.key] ? `${fieldDomId(field)}-error` : undefined"
          />
          <ToggleSwitch
            v-else-if="field.type === 'boolean'"
            :inputId="fieldDomId(field)"
            :ref="(el) => { fieldEls[field.key] = el }"
            v-model="formData[field.key]"
          />
          <Password
            v-else-if="field.type === 'password'"
            :inputId="fieldDomId(field)"
            :ref="(el) => { fieldEls[field.key] = el }"
            v-model="formData[field.key]"
            :feedback="false"
            toggleMask
            fluid
            :aria-invalid="!!fieldErrors[field.key]"
            :aria-describedby="fieldErrors[field.key] ? `${fieldDomId(field)}-error` : undefined"
          />
          <input
            v-else-if="field.type === 'date'"
            :id="fieldDomId(field)"
            :ref="(el) => { fieldEls[field.key] = el }"
            type="date"
            class="native-date"
            v-model="formData[field.key]"
            :aria-invalid="!!fieldErrors[field.key]"
            :aria-describedby="fieldErrors[field.key] ? `${fieldDomId(field)}-error` : undefined"
          />

          <p v-if="fieldErrors[field.key]" :id="`${fieldDomId(field)}-error`" role="alert" class="field-error">
            {{ fieldErrors[field.key] }}
          </p>
        </div>

        <div class="dialog-actions">
          <Button type="button" :label="LABELS.cancel" severity="secondary" text @click="closeDialog" />
          <Button type="submit" :label="LABELS.save" :loading="submitting" />
        </div>
      </form>
    </Dialog>

    <Dialog :visible="confirmTarget !== null" modal :closable="false" header="Confirmar" :style="{ width: '24rem' }">
      <p>¿Desactivar este {{ props.config.labelSingular }}? Podés revertirlo editándolo luego.</p>
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
.crud-view {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.crud-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.crud-error {
  color: #b91c1c;
  margin: 0;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  margin-bottom: 1rem;
}
.field label {
  font-weight: 600;
}
.native-date {
  font: inherit;
  padding: 0.5rem 0.75rem;
  border: 1px solid rgba(0, 0, 0, 0.3);
  border-radius: 6px;
  background: transparent;
  color: inherit;
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
  margin-top: 0.5rem;
}
@media (prefers-color-scheme: dark) {
  .crud-error,
  .field-error,
  .form-error {
    color: #fca5a5;
  }
}
</style>
