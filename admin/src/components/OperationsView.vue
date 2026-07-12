<script setup lang="ts">
// Fase 6, tarea 6.1: operaciones de viaje — cambio de estado (POST
// /admin/trips/{id}/status) y generacion manual (POST /admin/trips/generate),
// groundeados 1:1 contra backend/internal/modules/admin/handler.go (seccion
// "Operaciones de viajes"). Ninguno tiene endpoint de listado/preview (gap
// ListTrips, ver diseno decision #5) -> trip_id/template_id se ingresan a
// mano, mismo patron que los campos FK numericos de resources.ts. Ambas
// acciones son irreversibles en produccion -> Dialog de confirmacion (mismo
// patron que la baja logica de CrudView) antes de disparar. Sin CrudView: no
// hay tabla, son 2 formularios de accion independientes.
import { nextTick, reactive, ref } from 'vue'
import Button from 'primevue/button'
import Select from 'primevue/select'
import InputNumber from 'primevue/inputnumber'
import Dialog from 'primevue/dialog'
import { request, ApiError } from '../api/client'
import { LABELS } from '../messages'

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

// ---------------------------------------------------------------------------
// Cambio de estado — POST /admin/trips/{id}/status {status}
// ---------------------------------------------------------------------------
const statusForm = reactive<{ tripId: number | null; status: string | null }>({
  tripId: null,
  status: null,
})
const statusFieldErrors = reactive<{ tripId: string; status: string }>({ tripId: '', status: '' })
const statusFormError = ref('')
const statusSuccess = ref('')
const statusConfirmVisible = ref(false)
const statusSubmitting = ref(false)
const tripIdEl = ref<{ focus?: () => void; $el?: { focus?: () => void } } | null>(null)
const statusSelectEl = ref<{ focus?: () => void; $el?: { focus?: () => void } } | null>(null)

function focusEl(el: { focus?: () => void; $el?: { focus?: () => void } } | null): void {
  if (!el) return
  if (typeof el.focus === 'function') el.focus()
  else el.$el?.focus?.()
}

async function askStatusChange(): Promise<void> {
  statusFormError.value = ''
  statusSuccess.value = ''
  statusFieldErrors.tripId = statusForm.tripId ? '' : LABELS.requiredField
  statusFieldErrors.status = statusForm.status ? '' : LABELS.requiredField
  if (statusFieldErrors.tripId || statusFieldErrors.status) {
    await nextTick()
    focusEl(statusFieldErrors.tripId ? tripIdEl.value : statusSelectEl.value)
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
    statusSuccess.value = `Estado del viaje #${statusForm.tripId} actualizado a "${statusLabel(statusForm.status)}".`
  } catch (err) {
    statusConfirmVisible.value = false
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
const generateSuccess = ref('')
const generateConfirmVisible = ref(false)
const generateSubmitting = ref(false)
const templateIdEl = ref<{ focus?: () => void; $el?: { focus?: () => void } } | null>(null)
const serviceDateEl = ref<HTMLInputElement | null>(null)

async function askGenerate(): Promise<void> {
  generateFormError.value = ''
  generateSuccess.value = ''
  generateFieldErrors.templateId = generateForm.templateId ? '' : LABELS.requiredField
  generateFieldErrors.serviceDate = generateForm.serviceDate ? '' : LABELS.requiredField
  if (generateFieldErrors.templateId || generateFieldErrors.serviceDate) {
    await nextTick()
    ;(generateFieldErrors.templateId ? focusEl(templateIdEl.value) : serviceDateEl.value?.focus())
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
    generateSuccess.value = `Viaje generado para la plantilla #${generateForm.templateId} el ${generateForm.serviceDate}.`
  } catch (err) {
    generateConfirmVisible.value = false
    generateFormError.value = err instanceof ApiError ? err.message : 'Ocurrió un error inesperado.'
  } finally {
    generateSubmitting.value = false
  }
}
</script>

<template>
  <section class="operations-view">
    <h1>Operaciones de viaje</h1>

    <section class="op-card" aria-labelledby="status-heading">
      <h2 id="status-heading">Cambiar estado de un viaje</h2>
      <form novalidate @submit.prevent="askStatusChange">
        <div class="field">
          <label for="op-trip-id">ID de viaje</label>
          <InputNumber
            inputId="op-trip-id"
            ref="tripIdEl"
            v-model="statusForm.tripId"
            :min="1"
            :aria-invalid="!!statusFieldErrors.tripId"
            :aria-describedby="statusFieldErrors.tripId ? 'op-trip-id-error' : undefined"
          />
          <p v-if="statusFieldErrors.tripId" id="op-trip-id-error" role="alert" class="field-error">
            {{ statusFieldErrors.tripId }}
          </p>
        </div>
        <div class="field">
          <label for="op-status">Nuevo estado</label>
          <Select
            inputId="op-status"
            ref="statusSelectEl"
            v-model="statusForm.status"
            :options="STATUS_OPTIONS"
            optionLabel="label"
            optionValue="value"
            placeholder="Elija un estado"
            :aria-invalid="!!statusFieldErrors.status"
            :aria-describedby="statusFieldErrors.status ? 'op-status-error' : undefined"
          />
          <p v-if="statusFieldErrors.status" id="op-status-error" role="alert" class="field-error">
            {{ statusFieldErrors.status }}
          </p>
        </div>
        <p v-if="statusFormError" role="alert" class="form-error">{{ statusFormError }}</p>
        <p v-if="statusSuccess" role="status" class="form-success">{{ statusSuccess }}</p>
        <Button type="submit" label="Cambiar estado" />
      </form>
    </section>

    <section class="op-card" aria-labelledby="generate-heading">
      <h2 id="generate-heading">Generar viaje manualmente</h2>
      <form novalidate @submit.prevent="askGenerate">
        <div class="field">
          <label for="op-template-id">ID de plantilla</label>
          <InputNumber
            inputId="op-template-id"
            ref="templateIdEl"
            v-model="generateForm.templateId"
            :min="1"
            :aria-invalid="!!generateFieldErrors.templateId"
            :aria-describedby="generateFieldErrors.templateId ? 'op-template-id-error' : undefined"
          />
          <p v-if="generateFieldErrors.templateId" id="op-template-id-error" role="alert" class="field-error">
            {{ generateFieldErrors.templateId }}
          </p>
        </div>
        <div class="field">
          <label for="op-service-date">Fecha de servicio</label>
          <input
            id="op-service-date"
            ref="serviceDateEl"
            v-model="generateForm.serviceDate"
            type="date"
            class="native-date"
            :aria-invalid="!!generateFieldErrors.serviceDate"
            :aria-describedby="generateFieldErrors.serviceDate ? 'op-service-date-error' : undefined"
          />
          <p v-if="generateFieldErrors.serviceDate" id="op-service-date-error" role="alert" class="field-error">
            {{ generateFieldErrors.serviceDate }}
          </p>
        </div>
        <p v-if="generateFormError" role="alert" class="form-error">{{ generateFormError }}</p>
        <p v-if="generateSuccess" role="status" class="form-success">{{ generateSuccess }}</p>
        <Button type="submit" label="Generar viaje" />
      </form>
    </section>

    <Dialog
      :visible="statusConfirmVisible"
      modal
      :closable="false"
      :draggable="false"
      header="Confirmar cambio de estado"
      :style="{ width: '26rem' }"
    >
      <p>
        ¿Confirma cambiar el estado del viaje #{{ statusForm.tripId }} a "{{
          statusForm.status ? statusLabel(statusForm.status) : ''
        }}"? La acción se aplica de inmediato en producción.
      </p>
      <template #footer>
        <Button :label="LABELS.cancel" severity="secondary" text @click="cancelStatusChange" />
        <Button :label="LABELS.confirm" :loading="statusSubmitting" autofocus @click="confirmStatusChange" />
      </template>
    </Dialog>

    <Dialog
      :visible="generateConfirmVisible"
      modal
      :closable="false"
      :draggable="false"
      header="Confirmar generación de viaje"
      :style="{ width: '26rem' }"
    >
      <p>
        Esto genera una instancia real de viaje para la plantilla #{{ generateForm.templateId }} el
        {{ generateForm.serviceDate }}. No hay vista previa: la acción impacta producción de inmediato. ¿Confirma?
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
  gap: 1.5rem;
}
.op-card {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1rem;
  border: 1px solid rgba(0, 0, 0, 0.15);
  border-radius: 8px;
}
.op-card form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  align-items: flex-start;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  width: 100%;
  max-width: 20rem;
}
.field label {
  font-weight: 600;
}
.native-date {
  font: inherit;
  padding: 0.5rem 0.75rem;
  /* Fase 7: mismo fix de contraste que CrudView.vue (ver comentario ahí). */
  border: 1px solid #6b7280;
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
.form-success {
  color: #166534;
  font-size: 0.875rem;
  margin: 0;
}
@media (prefers-color-scheme: dark) {
  .op-card {
    border-color: rgba(255, 255, 255, 0.15);
  }
  .field-error,
  .form-error {
    color: #fca5a5;
  }
  .form-success {
    color: #86efac;
  }
}
</style>
