<script setup lang="ts">
// Rutas — 5to recurso del rollout visual (ver memoria
// "admin/crud-visual-redesign-pattern"). Auditado campo por campo contra
// RouteCreateParams/UpdateParams (repository.go) y la tabla transport_routes
// (0001_schema.up.sql): code/name/direction/paired_route_id/active
// coinciden 1:1 — sin campos faltantes ni de más. El único problema es el
// mismo patron relacional ya visto en vehicle-seats/users: paired_route_id
// (FK auto-referencial a otra ruta) se mostraba como numero crudo.
import { computed, onMounted, reactive, ref } from 'vue'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import ToggleSwitch from 'primevue/toggleswitch'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import { useCrudResource } from '../api/crud'
import { request, ApiError } from '../api/client'
import { LABELS } from '../messages'
import type { Route } from '../types'

const { items, page, pageSize, total, loading, error, list, create, update, softDelete } =
  useCrudResource<Route>('/admin/routes')
const toast = useToast()

// -- Rutas: catálogo propio para el lookup de "ruta emparejada" --
const allRoutes = ref<Route[]>([])
const loadingRoutes = ref(false)
const routeById = computed(() => new Map(allRoutes.value.map((r) => [r.id, r])))

function routeLabel(routeId: number | null | undefined): string {
  if (!routeId) return '—'
  const r = routeById.value.get(routeId)
  return r ? `${r.code} — ${r.name}` : `Ruta #${routeId} (no encontrada)`
}

async function loadRoutes(): Promise<void> {
  loadingRoutes.value = true
  try {
    const res = await request<{ items: Route[] }>('GET', '/admin/routes?page=1&page_size=100')
    allRoutes.value = res.items
  } catch {
    // El lookup queda degradado (muestra "Ruta #N") pero la tabla sigue usable.
  } finally {
    loadingRoutes.value = false
  }
}

onMounted(async () => {
  await loadRoutes()
  list()
})

function onPage(event: { page: number; rows: number }): void {
  page.value = event.page + 1
  pageSize.value = event.rows
  list()
}

// Flecha de sentido: IDA hacia adelante, VUELTA hacia atrás — la direccion
// del icono es literal, no decorativa (una ruta IDA/VUELTA es, por diseño,
// un par emparejado en sentidos opuestos, ver seed_demo_data.sql).
function directionTagProps(direction: Route['direction']) {
  return direction === 'IDA'
    ? { value: 'Ida', icon: 'pi pi-arrow-right', severity: 'info' as const }
    : { value: 'Vuelta', icon: 'pi pi-arrow-left', severity: 'secondary' as const }
}

// ---------------------------------------------------------------------------
// Alta / edicion
// ---------------------------------------------------------------------------
type FormState = {
  code: string
  name: string
  direction: Route['direction'] | ''
  paired_route_id: number | null
  active: boolean
}

function blankForm(): FormState {
  return { code: '', name: '', direction: '', paired_route_id: null, active: true }
}

const DIRECTION_OPTIONS = [
  { value: 'IDA', label: 'Ida' },
  { value: 'VUELTA', label: 'Vuelta' },
]

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formData = reactive<FormState>(blankForm())
const fieldErrors = reactive<Record<string, string>>({})
const formErrorMessage = ref('')
const submitting = ref(false)

// Una ruta no puede emparejarse consigo misma — se excluye de sus propias
// opciones (la FK de la DB no lo impide, pero no tiene sentido de dominio).
const pairOptions = computed(() =>
  allRoutes.value
    .filter((r) => r.id !== editingId.value)
    .map((r) => ({ value: r.id, label: `${r.code} — ${r.name}` }))
)

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

function openEdit(row: Route): void {
  editingId.value = row.id
  resetFormState()
  formData.code = row.code
  formData.name = row.name
  formData.direction = row.direction
  formData.paired_route_id = row.paired_route_id ?? null
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
  if (formData.code.length > 40) {
    fieldErrors.code = 'Máximo 40 caracteres.'
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
  if (!formData.direction) {
    fieldErrors.direction = LABELS.requiredField
    return false
  }
  return true
}

const FIELD_ERROR_RE = /^campo (\w+) invalido:/i
const SERVER_FIELD_MAP: Record<string, keyof FormState> = {
  code: 'code',
  name: 'name',
  direction: 'direction',
  pairedrouteid: 'paired_route_id',
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
    direction: formData.direction,
    paired_route_id: formData.paired_route_id,
    active: formData.active,
  }
  try {
    if (wasCreate) {
      await create(body)
    } else {
      await update(editingId.value as number, body)
    }
    dialogVisible.value = false
    await Promise.all([list(), loadRoutes()])
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
// Baja logica — Route SÍ tiene columna `active` real, el softDelete generico
// funciona bien acá.
// ---------------------------------------------------------------------------
const confirmTarget = ref<Route | null>(null)
const deactivating = ref(false)

function askDeactivate(row: Route): void {
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
    await Promise.all([list(), loadRoutes()])
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
  <section class="routes-view">
    <header class="routes-header">
      <div>
        <h1>Rutas</h1>
        <p class="routes-subtitle">Recorridos IDA/VUELTA, emparejados entre sí.</p>
      </div>
      <Button label="Nueva ruta" icon="pi pi-plus" @click="openCreate" />
    </header>

    <p v-if="error" role="alert" class="routes-error">
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
      class="routes-table"
      @page="onPage"
    >
      <template #empty>
        <div class="routes-empty">
          <i class="pi pi-directions routes-empty-icon" aria-hidden="true"></i>
          <p>Todavía no cargaste ninguna ruta.</p>
          <Button label="Nueva ruta" icon="pi pi-plus" text @click="openCreate" />
        </div>
      </template>

      <Column field="code" header="Código">
        <template #body="{ data }"><span class="routes-code">{{ data.code }}</span></template>
      </Column>

      <Column field="name" header="Nombre" />

      <Column field="direction" header="Sentido">
        <template #body="{ data }"><Tag v-bind="directionTagProps(data.direction)" /></template>
      </Column>

      <Column header="Ruta emparejada">
        <template #body="{ data }">
          <span class="routes-pair">{{ routeLabel(data.paired_route_id) }}</span>
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
          <div class="routes-actions">
            <Button icon="pi pi-pencil" text rounded aria-label="Editar ruta" @click="openEdit(data)" />
            <Button
              icon="pi pi-ban"
              text
              rounded
              severity="danger"
              aria-label="Desactivar ruta"
              @click="askDeactivate(data)"
            />
          </div>
        </template>
      </Column>
    </DataTable>

    <Dialog
      v-model:visible="dialogVisible"
      modal
      :header="editingId === null ? 'Nueva ruta' : 'Editar ruta'"
      :style="{ width: '34rem' }"
    >
      <form novalidate @submit.prevent="onSubmit">
        <p v-if="formErrorMessage" role="alert" class="form-error">{{ formErrorMessage }}</p>

        <fieldset class="field-group">
          <legend>Identificación</legend>
          <div class="field-grid">
            <div class="field">
              <label for="route-code">Código <span class="required-mark" aria-hidden="true">*</span></label>
              <InputText
                id="route-code"
                v-model="formData.code"
                aria-required="true"
                :aria-invalid="!!fieldErrors.code"
                :aria-describedby="fieldErrors.code ? 'route-code-error' : undefined"
              />
              <p v-if="fieldErrors.code" id="route-code-error" role="alert" class="field-error">
                {{ fieldErrors.code }}
              </p>
            </div>
            <div class="field">
              <label for="route-direction">Sentido <span class="required-mark" aria-hidden="true">*</span></label>
              <Select
                id="route-direction"
                v-model="formData.direction"
                :options="DIRECTION_OPTIONS"
                optionLabel="label"
                optionValue="value"
                placeholder="Elegir…"
                aria-required="true"
                :aria-invalid="!!fieldErrors.direction"
                :aria-describedby="fieldErrors.direction ? 'route-direction-error' : undefined"
              />
              <p v-if="fieldErrors.direction" id="route-direction-error" role="alert" class="field-error">
                {{ fieldErrors.direction }}
              </p>
            </div>
          </div>
          <div class="field">
            <label for="route-name">Nombre <span class="required-mark" aria-hidden="true">*</span></label>
            <InputText
              id="route-name"
              v-model="formData.name"
              aria-required="true"
              :aria-invalid="!!fieldErrors.name"
              :aria-describedby="fieldErrors.name ? 'route-name-error' : undefined"
            />
            <p v-if="fieldErrors.name" id="route-name-error" role="alert" class="field-error">
              {{ fieldErrors.name }}
            </p>
          </div>
        </fieldset>

        <fieldset class="field-group">
          <legend>Emparejamiento</legend>
          <div class="field">
            <label for="route-pair">Ruta emparejada</label>
            <Select
              id="route-pair"
              v-model="formData.paired_route_id"
              :options="pairOptions"
              optionLabel="label"
              optionValue="value"
              placeholder="Sin emparejar"
              showClear
              filter
              :loading="loadingRoutes"
              :aria-describedby="'route-pair-help'"
            />
            <p id="route-pair-help" class="field-help">
              La ruta de sentido contrario del mismo recorrido (ej. esta IDA con su VUELTA).
            </p>
          </div>
        </fieldset>

        <fieldset class="field-group field-group-inline">
          <legend class="sr-only">Estado</legend>
          <label for="route-active" class="switch-label">
            <ToggleSwitch id="route-active" v-model="formData.active" />
            Ruta activa
          </label>
        </fieldset>

        <div class="dialog-actions">
          <Button type="button" :label="LABELS.cancel" severity="secondary" text @click="closeDialog" />
          <Button type="submit" :label="LABELS.save" :loading="submitting" />
        </div>
      </form>
    </Dialog>

    <Dialog :visible="confirmTarget !== null" modal :closable="false" header="Confirmar" :style="{ width: '24rem' }">
      <p>¿Desactivar la ruta "{{ confirmTarget?.name }}"? Podrá revertirlo editándola luego.</p>
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
.routes-view {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}
.routes-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}
.routes-header h1 {
  margin: 0 0 0.25rem;
}
.routes-subtitle {
  margin: 0;
  color: #52525b;
  font-size: 0.9375rem;
}
.routes-error {
  color: #b91c1c;
  margin: 0;
}
.routes-code {
  font-family: ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
  font-variant-numeric: tabular-nums;
  font-size: 0.875rem;
  letter-spacing: 0.02em;
}
.routes-pair {
  color: #52525b;
}
.routes-actions {
  display: flex;
  gap: 0.25rem;
}
.routes-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 2rem 0;
}
.routes-empty-icon {
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
  .routes-header {
    flex-direction: column;
  }
}

@media (prefers-color-scheme: dark) {
  .routes-subtitle,
  .routes-pair {
    color: #a1a1aa;
  }
  .routes-error,
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
  .routes-empty-icon {
    color: #71717a;
  }
}
</style>
