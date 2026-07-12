<script setup lang="ts">
// Operaciones de viaje — rediseño visual con relaciones resueltas. Reemplaza
// los IDs crudos por Selects que muestran código/nombre/fecha reales del
// viaje y de la plantilla. Auditado contra backend/handler.go sección
// "Operaciones de viajes" (POST /admin/trips/{id}/status y /admin/trips/generate).
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import Button from 'primevue/button'
import Select from 'primevue/select'
import InputNumber from 'primevue/inputnumber'
import Dialog from 'primevue/dialog'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import { request, ApiError } from '../api/client'
import { LABELS } from '../messages'
import type { TripInstance, Template } from '../types'

const toast = useToast()

const STATUS_OPTIONS = [
  { value: 'DRAFT', label: 'Borrador' },
  { value: 'PUBLISHED', label: 'Publicado' },
  { value: 'BOARDING', label: 'Embarcando' },
  { value: 'IN_PROGRESS', label: 'En curso' },
  { value: 'COMPLETED', label: 'Completado' },
  { value: 'CANCELLED', label: 'Cancelado' },
]

function statusLabel(value: string): string {
  return STATUS_OPTIONS.find((o) => o.value === value)?.label ?? value
}

function statusSeverity(value: string): 'success' | 'info' | 'warn' | 'danger' | 'secondary' {
  switch (value) {
    case 'COMPLETED':
      return 'success'
    case 'PUBLISHED':
    case 'BOARDING':
      return 'info'
    case 'IN_PROGRESS':
      return 'warn'
    case 'CANCELLED':
      return 'danger'
    default:
      return 'secondary'
  }
}

function formatDate(value: string | null | undefined): string {
  if (!value) return '—'
  const d = new Date(value)
  if (isNaN(d.getTime())) return value
  return d.toLocaleDateString('es-AR', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

// ---------------------------------------------------------------------------
// Relaciones: viajes y plantillas para los Selects
// ---------------------------------------------------------------------------
const trips = ref<TripInstance[]>([])
const templates = ref<Template[]>([])
const relationsLoading = ref(false)
const relationsError = ref('')

onMounted(() => loadRelations())

async function loadRelations(): Promise<void> {
  relationsLoading.value = true
  relationsError.value = ''
  try {
    const [tripsRes, templatesRes] = await Promise.all([
      request<{ items: TripInstance[] }>('GET', '/admin/trips?page=1&page_size=200'),
      request<{ items: Template[] }>('GET', '/admin/templates?page=1&page_size=200'),
    ])
    trips.value = tripsRes.items
    templates.value = templatesRes.items
  } catch (err) {
    relationsError.value = err instanceof ApiError ? err.message : 'No se pudieron cargar las relaciones.'
  } finally {
    relationsLoading.value = false
  }
}

const tripOptions = computed(() =>
  trips.value.map((t) => ({
    label: `${t.trip_code} — ${formatDate(t.service_date)}`,
    value: t.id,
    status: t.status,
  }))
)

const templateOptions = computed(() =>
  templates.value.map((t) => ({
    label: `${t.code} — ${t.name}`,
    value: t.id,
  }))
)

// ---------------------------------------------------------------------------
// Cambio de estado — POST /admin/trips/{id}/status {status}
// ---------------------------------------------------------------------------
const statusForm = reactive<{ tripId: number | null; status: string | null }>({
  tripId: null,
  status: null,
})
const statusFieldErrors = reactive<{ tripId: string; status: string }>({ tripId: '', status: '' })
const statusFormError = ref('')
const statusConfirmVisible = ref(false)
const statusSubmitting = ref(false)
const statusSelectEl = ref<{ focus?: () => void; $el?: { focus?: () => void } } | null>(null)

function focusEl(el: { focus?: () => void; $el?: { focus?: () => void } } | null): void {
  if (!el) return
  if (typeof el.focus === 'function') el.focus()
  else el.$el?.focus?.()
}

async function askStatusChange(): Promise<void> {
  statusFormError.value = ''
  statusFieldErrors.tripId = statusForm.tripId ? '' : LABELS.requiredField
  statusFieldErrors.status = statusForm.status ? '' : LABELS.requiredField
  if (statusFieldErrors.tripId || statusFieldErrors.status) {
    await nextTick()
    focusEl(statusSelectEl.value)
    return
  }
  statusConfirmVisible.value = true
}

function cancelStatusChange(): void {
  statusConfirmVisible.value = false
}

async function confirmStatusChange(): Promise<void> {
  if (statusForm.tripId == null || !statusForm.status) return
  statusSubmitting.value = true
  try {
    await request<void>('POST', `/admin/trips/${statusForm.tripId}/status`, { status: statusForm.status })
    statusConfirmVisible.value = false
    statusForm.tripId = null
    statusForm.status = null
    toast.add({ severity: 'success', summary: 'Estado actualizado', life: 4000 })
  } catch (err) {
    statusFormError.value = err instanceof ApiError ? err.message : 'Ocurrió un error inesperado.'
  } finally {
    statusSubmitting.value = false
  }
}

// ---------------------------------------------------------------------------
// Generacion manual — POST /admin/trips/generate {template_id, service_date}
// ---------------------------------------------------------------------------
const generateForm = reactive<{ templateId: number | null; serviceDate: string }>({
  templateId: null,
  serviceDate: '',
})
const generateFieldErrors = reactive<{ templateId: string; serviceDate: string }>({
  templateId: '',
  serviceDate: '',
})
const generateFormError = ref('')
const generateConfirmVisible = ref(false)
const generateSubmitting = ref(false)
const serviceDateEl = ref<HTMLInputElement | null>(null)

async function askGenerate(): Promise<void> {
  generateFormError.value = ''
  generateFieldErrors.templateId = generateForm.templateId ? '' : LABELS.requiredField
  generateFieldErrors.serviceDate = generateForm.serviceDate ? '' : LABELS.requiredField
  if (generateFieldErrors.templateId || generateFieldErrors.serviceDate) {
    await nextTick()
    serviceDateEl.value?.focus()
    return
  }
  generateConfirmVisible.value = true
}

function cancelGenerate(): void {
  generateConfirmVisible.value = false
}

async function confirmGenerate(): Promise<void> {
  if (generateForm.templateId == null || !generateForm.serviceDate) return
  generateSubmitting.value = true
  try {
    await request<void>('POST', '/admin/trips/generate', {
      template_id: generateForm.templateId,
      service_date: generateForm.serviceDate,
    })
    generateConfirmVisible.value = false
    generateForm.templateId = null
    generateForm.serviceDate = ''
    toast.add({ severity: 'success', summary: 'Viaje generado', life: 4000 })
  } catch (err) {
    generateFormError.value = err instanceof ApiError ? err.message : 'Ocurrió un error inesperado.'
  } finally {
    generateSubmitting.value = false
  }
}
</script>

<template>
  <section class="operations-view">
    <header class="operations-header">
      <div>
        <h1>Operaciones de viaje</h1>
        <p class="operations-subtitle">Acciones sobre instancias de viaje ya generadas.</p>
      </div>
    </header>

    <p v-if="relationsError" role="alert" class="operations-error">
      {{ relationsError }}
      <Button label="Reintentar" text size="small" @click="loadRelations()" />
    </p>

    <fieldset class="op-card">
      <legend>Cambiar estado de un viaje</legend>
      <form novalidate @submit.prevent="askStatusChange">
        <div class="field-grid">
          <div class="field">
            <label for="op-trip-id">Viaje <span class="required-mark" aria-hidden="true">*</span></label>
            <Select
              id="op-trip-id"
              v-model="statusForm.tripId"
              :options="tripOptions"
              option-label="label"
              option-value="value"
              placeholder="Elegir viaje…"
              show-clear
              filter
              :loading="relationsLoading"
              :aria-invalid="!!statusFieldErrors.tripId"
              :aria-describedby="statusFieldErrors.tripId ? 'op-trip-id-error' : undefined"
            >
              <template #option="slotProps">
                <div class="trip-option">
                  <span>{{ slotProps.option.label }}</span>
                  <Tag :value="statusLabel(slotProps.option.status)" :severity="statusSeverity(slotProps.option.status)" />
                </div>
              </template>
            </Select>
            <p v-if="statusFieldErrors.tripId" id="op-trip-id-error" role="alert" class="field-error">{{ statusFieldErrors.tripId }}</p>
          </div>
          <div class="field">
            <label for="op-status">Nuevo estado <span class="required-mark" aria-hidden="true">*</span></label>
            <Select
              id="op-status"
              ref="statusSelectEl"
              v-model="statusForm.status"
              :options="STATUS_OPTIONS"
              option-label="label"
              option-value="value"
              placeholder="Elegir estado…"
              show-clear
              :aria-invalid="!!statusFieldErrors.status"
              :aria-describedby="statusFieldErrors.status ? 'op-status-error' : undefined"
            />
            <p v-if="statusFieldErrors.status" id="op-status-error" role="alert" class="field-error">{{ statusFieldErrors.status }}</p>
          </div>
        </div>
        <p v-if="statusFormError" role="alert" class="form-error">{{ statusFormError }}</p>
        <Button type="submit" label="Cambiar estado" icon="pi pi-pencil" />
      </form>
    </fieldset>

    <fieldset class="op-card">
      <legend>Generar viaje manualmente</legend>
      <form novalidate @submit.prevent="askGenerate">
        <div class="field-grid">
          <div class="field">
            <label for="op-template-id">Plantilla <span class="required-mark" aria-hidden="true">*</span></label>
            <Select
              id="op-template-id"
              v-model="generateForm.templateId"
              :options="templateOptions"
              option-label="label"
              option-value="value"
              placeholder="Elegir plantilla…"
              show-clear
              filter
              :loading="relationsLoading"
              :aria-invalid="!!generateFieldErrors.templateId"
              :aria-describedby="generateFieldErrors.templateId ? 'op-template-id-error' : undefined"
            />
            <p v-if="generateFieldErrors.templateId" id="op-template-id-error" role="alert" class="field-error">{{ generateFieldErrors.templateId }}</p>
          </div>
          <div class="field">
            <label for="op-service-date">Fecha de servicio <span class="required-mark" aria-hidden="true">*</span></label>
            <input
              id="op-service-date"
              ref="serviceDateEl"
              v-model="generateForm.serviceDate"
              type="date"
              class="native-date"
              :aria-invalid="!!generateFieldErrors.serviceDate"
              :aria-describedby="generateFieldErrors.serviceDate ? 'op-service-date-error' : undefined"
            />
            <p v-if="generateFieldErrors.serviceDate" id="op-service-date-error" role="alert" class="field-error">{{ generateFieldErrors.serviceDate }}</p>
          </div>
        </div>
        <p v-if="generateFormError" role="alert" class="form-error">{{ generateFormError }}</p>
        <Button type="submit" label="Generar viaje" icon="pi pi-play" />
      </form>
    </fieldset>

    <Dialog
      v-model:visible="statusConfirmVisible"
      modal
      :closable="false"
      :draggable="false"
      header="Confirmar cambio de estado"
      :style="{ width: '26rem' }"
    >
      <p v-if="statusForm.tripId">
        ¿Confirma cambiar el estado del viaje
        <strong>#{{ statusForm.tripId }}</strong>
        a "{{ statusForm.status ? statusLabel(statusForm.status) : '' }}"? La acción se aplica de inmediato en producción.
      </p>
      <template #footer>
        <Button :label="LABELS.cancel" severity="secondary" text @click="cancelStatusChange" />
        <Button :label="LABELS.confirm" :loading="statusSubmitting" autofocus @click="confirmStatusChange" />
      </template>
    </Dialog>

    <Dialog
      v-model:visible="generateConfirmVisible"
      modal
      :closable="false"
      :draggable="false"
      header="Confirmar generación de viaje"
      :style="{ width: '26rem' }"
    >
      <p v-if="generateForm.templateId">
        Esto genera una instancia real de viaje para la plantilla
        <strong>#{{ generateForm.templateId }}</strong>
        el {{ generateForm.serviceDate }}. No hay vista previa: la acción impacta producción de inmediato. ¿Confirma?
      </p>
      <template #footer>
        <Button :label="LABELS.cancel" severity="secondary" text @click="cancelGenerate" />
        <Button :label="LABELS.confirm" :loading="generateSubmitting" autofocus @click="confirmGenerate" />
      </template>
    </Dialog>
  </section>
</template>

<style scoped>
.operations-view {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}
.operations-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}
.operations-header h1 {
  margin: 0 0 0.25rem;
}
.operations-subtitle {
  margin: 0;
  color: #52525b;
  font-size: 0.9375rem;
}
.operations-error {
  color: #b91c1c;
  margin: 0;
}
.op-card {
  border: 0;
  padding: 1rem;
  margin: 0;
  border-radius: 8px;
  border: 1px solid rgba(0, 0, 0, 0.09);
  background: rgba(0, 0, 0, 0.02);
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
.op-card legend {
  padding: 0 0.5rem;
  font-size: 0.75rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: #059669;
}
.op-card form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  align-items: flex-start;
}
.field-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.875rem;
  width: 100%;
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
.native-date {
  font: inherit;
  padding: 0.5rem 0.75rem;
  border: 1px solid #6b7280;
  border-radius: 6px;
  background: transparent;
  color: inherit;
  width: 100%;
  box-sizing: border-box;
}
.field-error,
.form-error {
  color: #b91c1c;
  font-size: 0.875rem;
  margin: 0;
}
.trip-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.75rem;
  width: 100%;
}

@media (max-width: 30rem) {
  .field-grid {
    grid-template-columns: 1fr;
  }
}

@media (prefers-color-scheme: dark) {
  .operations-subtitle {
    color: #a1a1aa;
  }
  .operations-error,
  .field-error,
  .form-error,
  .required-mark {
    color: #fca5a5;
  }
  .op-card {
    background: rgba(255, 255, 255, 0.03);
    border-color: rgba(255, 255, 255, 0.08);
  }
  .op-card legend {
    color: #34d399;
  }
}
</style>
