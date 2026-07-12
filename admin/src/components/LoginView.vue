<script setup lang="ts">
// Fase 3, tarea 3.3. Inputs nativos (no PrimeVue InputText/Password): un
// login de 2 campos no necesita el scaffolding de PrimeVue (esa eleccion de
// diseno es para DataTable/Dialog/Form de las 7 pantallas CRUD, Fase 4) y un
// <input> nativo da control directo de foco para el manejo de errores 422/401
// (a11y prioridad #1). Button de PrimeVue si, por consistencia visual/loading.
import { nextTick, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import { useAuth } from '../auth/useAuth'
import { ApiError } from '../api/client'
import { APP_TITLE, LOGIN_LABELS } from '../messages'

const router = useRouter()
const route = useRoute()
const { login } = useAuth()

const documentNumber = ref('')
const password = ref('')
const documentNumberError = ref('')
const passwordError = ref('')
const formError = ref('')
const submitting = ref(false)

const documentInputEl = ref<HTMLInputElement | null>(null)
const passwordInputEl = ref<HTMLInputElement | null>(null)

function validate(): boolean {
  documentNumberError.value = documentNumber.value.trim() ? '' : LOGIN_LABELS.requiredField
  passwordError.value = password.value ? '' : LOGIN_LABELS.requiredField
  return !documentNumberError.value && !passwordError.value
}

async function onSubmit() {
  formError.value = ''
  if (!validate()) {
    await nextTick()
    ;(documentNumberError.value ? documentInputEl.value : passwordInputEl.value)?.focus()
    return
  }

  submitting.value = true
  try {
    await login(documentNumber.value.trim(), password.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.push(redirect)
  } catch (err) {
    formError.value = err instanceof ApiError ? err.message : LOGIN_LABELS.unexpectedError
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <form class="login-form" novalidate @submit.prevent="onSubmit">
      <h1>{{ APP_TITLE }}</h1>

      <p v-if="formError" role="alert" class="form-error">{{ formError }}</p>

      <div class="field">
        <label for="document_number">{{ LOGIN_LABELS.documentNumber }}</label>
        <input
          id="document_number"
          ref="documentInputEl"
          v-model="documentNumber"
          type="text"
          autocomplete="username"
          :aria-invalid="!!documentNumberError"
          :aria-describedby="documentNumberError ? 'document_number-error' : undefined"
        />
        <p v-if="documentNumberError" id="document_number-error" role="alert" class="field-error">
          {{ documentNumberError }}
        </p>
      </div>

      <div class="field">
        <label for="password">{{ LOGIN_LABELS.password }}</label>
        <input
          id="password"
          ref="passwordInputEl"
          v-model="password"
          type="password"
          autocomplete="current-password"
          :aria-invalid="!!passwordError"
          :aria-describedby="passwordError ? 'password-error' : undefined"
        />
        <p v-if="passwordError" id="password-error" role="alert" class="field-error">
          {{ passwordError }}
        </p>
      </div>

      <Button type="submit" :label="LOGIN_LABELS.submit" :loading="submitting" />
    </form>
  </main>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
}
.login-form {
  width: 100%;
  max-width: 22rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}
.field label {
  font-weight: 600;
}
.field input {
  font: inherit;
  padding: 0.5rem 0.625rem;
  border: 1px solid #6b7280;
  border-radius: 0.375rem;
  background: var(--color-bg);
  color: var(--color-text);
}
.field input[aria-invalid='true'] {
  border-color: #b91c1c;
  border-width: 2px;
}
.field-error,
.form-error {
  color: #b91c1c;
  font-size: 0.875rem;
  margin: 0;
}
@media (prefers-color-scheme: dark) {
  .field-error,
  .form-error {
    color: #fca5a5;
  }
  .field input[aria-invalid='true'] {
    border-color: #fca5a5;
  }
}
</style>
