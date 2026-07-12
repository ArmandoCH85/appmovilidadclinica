<script setup lang="ts">
// Usuarios — 4to recurso del rollout visual (ver memoria
// "admin/crud-visual-redesign-pattern"). Mismo hallazgo que vehicle-seats:
// `preferred_stop_id` era un numero crudo sin relacion visible con la parada
// real — el admin tenia que adivinar el ID. Fix: se trae `/admin/stops`
// (mismo patron que vehiculos en VehicleSeatsView) y se arma un lookup +
// Select buscable en vez de <input type=number>.
import { computed, onMounted, reactive, ref } from 'vue'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import ToggleSwitch from 'primevue/toggleswitch'
import Password from 'primevue/password'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import { useCrudResource } from '../api/crud'
import { request, ApiError } from '../api/client'
import { LABELS } from '../messages'
import type { User, Stop } from '../types'

const { items, page, pageSize, total, loading, error, list, create, update, softDelete } =
  useCrudResource<User>('/admin/users')
const toast = useToast()

// -- Paradas: catálogo para lookup (tabla) y Select (formulario) --
const stops = ref<Stop[]>([])
const loadingStops = ref(false)
const stopById = computed(() => new Map(stops.value.map((s) => [s.id, s])))
const stopOptions = computed(() => stops.value.map((s) => ({ value: s.id, label: s.name })))

function stopLabel(stopId: number | null | undefined): string {
  if (!stopId) return '—'
  const s = stopById.value.get(stopId)
  return s ? s.name : `Parada #${stopId} (no encontrada)`
}

onMounted(async () => {
  loadingStops.value = true
  try {
    const res = await request<{ items: Stop[] }>('GET', '/admin/stops?page=1&page_size=100')
    stops.value = res.items
  } catch {
    // El lookup queda degradado (muestra "Parada #N") pero la tabla de
    // usuarios sigue siendo usable — no bloquea la pantalla.
  } finally {
    loadingStops.value = false
  }
  list()
})

function onPage(event: { page: number; rows: number }): void {
  page.value = event.page + 1
  pageSize.value = event.rows
  list()
}

const ROLE_OPTIONS = [
  { value: 'ADMIN', label: 'Administrador' },
  { value: 'DRIVER', label: 'Conductor' },
  { value: 'WORKER', label: 'Trabajador' },
]

function roleTagProps(role: User['role']) {
  if (role === 'ADMIN') return { value: 'Administrador', icon: 'pi pi-shield', severity: 'contrast' as const }
  if (role === 'DRIVER') return { value: 'Conductor', icon: 'pi pi-car', severity: 'info' as const }
  return { value: 'Trabajador', icon: 'pi pi-user', severity: 'secondary' as const }
}

// ---------------------------------------------------------------------------
// Alta / edicion
// ---------------------------------------------------------------------------
type FormState = {
  employee_code: string
  document_number: string
  password: string
  full_name: string
  role: User['role'] | ''
  department: string
  phone: string
  preferred_stop_id: number | null
  active: boolean
}

function blankForm(): FormState {
  return {
    employee_code: '',
    document_number: '',
    password: '',
    full_name: '',
    role: '',
    department: '',
    phone: '',
    preferred_stop_id: null,
    active: true,
  }
}

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formData = reactive<FormState>(blankForm())
const fieldErrors = reactive<Record<string, string>>({})
const formErrorMessage = ref('')
const submitting = ref(false)

const isCreate = computed(() => editingId.value === null)

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

function openEdit(row: User): void {
  editingId.value = row.id
  resetFormState()
  formData.employee_code = row.employee_code
  formData.document_number = row.document_number
  formData.password = ''
  formData.full_name = row.full_name
  formData.role = row.role
  formData.department = row.department ?? ''
  formData.phone = row.phone ?? ''
  formData.preferred_stop_id = row.preferred_stop_id ?? null
  formData.active = row.active
  dialogVisible.value = true
}

function closeDialog(): void {
  dialogVisible.value = false
}

function validateClientSide(): boolean {
  if (!formData.employee_code.trim()) {
    fieldErrors.employee_code = LABELS.requiredField
    return false
  }
  if (formData.employee_code.length > 30) {
    fieldErrors.employee_code = 'Máximo 30 caracteres.'
    return false
  }
  if (!formData.document_number.trim()) {
    fieldErrors.document_number = LABELS.requiredField
    return false
  }
  if (formData.document_number.length > 20) {
    fieldErrors.document_number = 'Máximo 20 caracteres.'
    return false
  }
  if (isCreate.value && !formData.password) {
    fieldErrors.password = LABELS.requiredField
    return false
  }
  if (!formData.full_name.trim()) {
    fieldErrors.full_name = LABELS.requiredField
    return false
  }
  if (formData.full_name.length > 150) {
    fieldErrors.full_name = 'Máximo 150 caracteres.'
    return false
  }
  if (!formData.role) {
    fieldErrors.role = LABELS.requiredField
    return false
  }
  if (formData.department.length > 100) {
    fieldErrors.department = 'Máximo 100 caracteres.'
    return false
  }
  if (formData.phone.length > 25) {
    fieldErrors.phone = 'Máximo 25 caracteres.'
    return false
  }
  return true
}

const FIELD_ERROR_RE = /^campo (\w+) invalido:/i
const SERVER_FIELD_MAP: Record<string, keyof FormState> = {
  employeecode: 'employee_code',
  documentnumber: 'document_number',
  password: 'password',
  fullname: 'full_name',
  role: 'role',
  department: 'department',
  phone: 'phone',
  preferredstopid: 'preferred_stop_id',
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
  const wasCreate = isCreate.value
  const body: Record<string, unknown> = {
    employee_code: formData.employee_code,
    document_number: formData.document_number,
    full_name: formData.full_name,
    role: formData.role,
    department: formData.department || null,
    phone: formData.phone || null,
    preferred_stop_id: formData.preferred_stop_id,
    active: formData.active,
  }
  // password: obligatoria solo al crear; en edicion, vacia significa "no
  // cambiar" (UserUpdateParams.Password sin required — ver service.go).
  if (wasCreate || formData.password) {
    body.password = formData.password
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
// Baja logica — User SÍ tiene columna `active` real, el softDelete generico
// funciona bien acá (a diferencia de vehicle-seats, que usa `status`).
// ---------------------------------------------------------------------------
const confirmTarget = ref<User | null>(null)
const deactivating = ref(false)

function askDeactivate(row: User): void {
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
  <section class="users-view">
    <header class="users-header">
      <div>
        <h1>Usuarios</h1>
        <p class="users-subtitle">Administradores, conductores y trabajadores con acceso al sistema.</p>
      </div>
      <Button label="Nuevo usuario" icon="pi pi-plus" @click="openCreate" />
    </header>

    <p v-if="error" role="alert" class="users-error">
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
      class="users-table"
      @page="onPage"
    >
      <template #empty>
        <div class="users-empty">
          <i class="pi pi-users users-empty-icon" aria-hidden="true"></i>
          <p>Todavía no cargaste ningún usuario.</p>
          <Button label="Nuevo usuario" icon="pi pi-plus" text @click="openCreate" />
        </div>
      </template>

      <Column field="employee_code" header="Legajo">
        <template #body="{ data }"><span class="users-code">{{ data.employee_code }}</span></template>
      </Column>

      <Column field="full_name" header="Nombre completo" />

      <Column field="role" header="Rol">
        <template #body="{ data }"><Tag v-bind="roleTagProps(data.role)" /></template>
      </Column>

      <Column header="Parada preferida">
        <template #body="{ data }">
          <span class="users-stop">{{ stopLabel(data.preferred_stop_id) }}</span>
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
          <div class="users-actions">
            <Button icon="pi pi-pencil" text rounded aria-label="Editar usuario" @click="openEdit(data)" />
            <Button
              icon="pi pi-ban"
              text
              rounded
              severity="danger"
              aria-label="Desactivar usuario"
              @click="askDeactivate(data)"
            />
          </div>
        </template>
      </Column>
    </DataTable>

    <Dialog
      v-model:visible="dialogVisible"
      modal
      :header="isCreate ? 'Nuevo usuario' : 'Editar usuario'"
      :style="{ width: '36rem' }"
    >
      <form novalidate @submit.prevent="onSubmit">
        <p v-if="formErrorMessage" role="alert" class="form-error">{{ formErrorMessage }}</p>

        <fieldset class="field-group">
          <legend>Identificación</legend>
          <div class="field-grid">
            <div class="field">
              <label for="user-code">Legajo <span class="required-mark" aria-hidden="true">*</span></label>
              <InputText
                id="user-code"
                v-model="formData.employee_code"
                aria-required="true"
                :aria-invalid="!!fieldErrors.employee_code"
                :aria-describedby="fieldErrors.employee_code ? 'user-code-error' : undefined"
              />
              <p v-if="fieldErrors.employee_code" id="user-code-error" role="alert" class="field-error">
                {{ fieldErrors.employee_code }}
              </p>
            </div>
            <div class="field">
              <label for="user-document">Documento <span class="required-mark" aria-hidden="true">*</span></label>
              <InputText
                id="user-document"
                v-model="formData.document_number"
                autocomplete="off"
                aria-required="true"
                :aria-invalid="!!fieldErrors.document_number"
                :aria-describedby="fieldErrors.document_number ? 'user-document-error' : undefined"
              />
              <p v-if="fieldErrors.document_number" id="user-document-error" role="alert" class="field-error">
                {{ fieldErrors.document_number }}
              </p>
            </div>
          </div>
          <div class="field">
            <label for="user-name">Nombre completo <span class="required-mark" aria-hidden="true">*</span></label>
            <InputText
              id="user-name"
              v-model="formData.full_name"
              aria-required="true"
              :aria-invalid="!!fieldErrors.full_name"
              :aria-describedby="fieldErrors.full_name ? 'user-name-error' : undefined"
            />
            <p v-if="fieldErrors.full_name" id="user-name-error" role="alert" class="field-error">
              {{ fieldErrors.full_name }}
            </p>
          </div>
        </fieldset>

        <fieldset class="field-group">
          <legend>Acceso y rol</legend>
          <div class="field-grid">
            <div class="field">
              <label for="user-password">
                Contraseña <span v-if="isCreate" class="required-mark" aria-hidden="true">*</span>
              </label>
              <Password
                inputId="user-password"
                v-model="formData.password"
                :feedback="false"
                toggleMask
                fluid
                autocomplete="new-password"
                :aria-required="isCreate"
                :aria-invalid="!!fieldErrors.password"
                :aria-describedby="fieldErrors.password ? 'user-password-error' : 'user-password-help'"
              />
              <p v-if="!isCreate" id="user-password-help" class="field-help">Dejar vacío para no cambiarla.</p>
              <p v-if="fieldErrors.password" id="user-password-error" role="alert" class="field-error">
                {{ fieldErrors.password }}
              </p>
            </div>
            <div class="field">
              <label for="user-role">Rol <span class="required-mark" aria-hidden="true">*</span></label>
              <Select
                id="user-role"
                v-model="formData.role"
                :options="ROLE_OPTIONS"
                optionLabel="label"
                optionValue="value"
                placeholder="Elegir…"
                aria-required="true"
                :aria-invalid="!!fieldErrors.role"
                :aria-describedby="fieldErrors.role ? 'user-role-error' : undefined"
              />
              <p v-if="fieldErrors.role" id="user-role-error" role="alert" class="field-error">
                {{ fieldErrors.role }}
              </p>
            </div>
          </div>
        </fieldset>

        <fieldset class="field-group">
          <legend>Contacto y logística</legend>
          <div class="field-grid">
            <div class="field">
              <label for="user-department">Área</label>
              <InputText
                id="user-department"
                v-model="formData.department"
                :aria-invalid="!!fieldErrors.department"
                :aria-describedby="fieldErrors.department ? 'user-department-error' : undefined"
              />
              <p v-if="fieldErrors.department" id="user-department-error" role="alert" class="field-error">
                {{ fieldErrors.department }}
              </p>
            </div>
            <div class="field">
              <label for="user-phone">Teléfono</label>
              <InputText
                id="user-phone"
                v-model="formData.phone"
                :aria-invalid="!!fieldErrors.phone"
                :aria-describedby="fieldErrors.phone ? 'user-phone-error' : undefined"
              />
              <p v-if="fieldErrors.phone" id="user-phone-error" role="alert" class="field-error">
                {{ fieldErrors.phone }}
              </p>
            </div>
          </div>
          <div class="field">
            <label for="user-stop">Parada preferida</label>
            <Select
              id="user-stop"
              v-model="formData.preferred_stop_id"
              :options="stopOptions"
              optionLabel="label"
              optionValue="value"
              placeholder="Sin preferencia"
              showClear
              filter
              :loading="loadingStops"
              :aria-describedby="'user-stop-help'"
            />
            <p id="user-stop-help" class="field-help">Dónde el trabajador prefiere que lo suban/bajen.</p>
          </div>
        </fieldset>

        <fieldset class="field-group field-group-inline">
          <legend class="sr-only">Estado</legend>
          <label for="user-active" class="switch-label">
            <ToggleSwitch id="user-active" v-model="formData.active" />
            Usuario activo
          </label>
        </fieldset>

        <div class="dialog-actions">
          <Button type="button" :label="LABELS.cancel" severity="secondary" text @click="closeDialog" />
          <Button type="submit" :label="LABELS.save" :loading="submitting" />
        </div>
      </form>
    </Dialog>

    <Dialog :visible="confirmTarget !== null" modal :closable="false" header="Confirmar" :style="{ width: '24rem' }">
      <p>¿Desactivar a "{{ confirmTarget?.full_name }}"? Podrá revertirlo editándolo luego.</p>
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
.users-view {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}
.users-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}
.users-header h1 {
  margin: 0 0 0.25rem;
}
.users-subtitle {
  margin: 0;
  color: #52525b;
  font-size: 0.9375rem;
}
.users-error {
  color: #b91c1c;
  margin: 0;
}
.users-code {
  font-family: ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
  font-variant-numeric: tabular-nums;
  font-size: 0.875rem;
  letter-spacing: 0.02em;
}
.users-stop {
  color: #52525b;
}
.users-actions {
  display: flex;
  gap: 0.25rem;
}
.users-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 2rem 0;
}
.users-empty-icon {
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
  .users-header {
    flex-direction: column;
  }
}

@media (prefers-color-scheme: dark) {
  .users-subtitle,
  .users-stop {
    color: #a1a1aa;
  }
  .users-error,
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
  .users-empty-icon {
    color: #71717a;
  }
}
</style>
