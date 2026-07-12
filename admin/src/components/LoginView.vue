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
import { APP_TITLE, LABELS, LOGIN_LABELS } from '../messages'

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
  documentNumberError.value = documentNumber.value.trim() ? '' : LABELS.requiredField
  passwordError.value = password.value ? '' : LABELS.requiredField
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
    <section class="login-shell" aria-labelledby="login-heading">
      <div class="login-context">
        <div class="brand-lockup">
          <span class="brand-mark" aria-hidden="true"><i class="pi pi-directions"></i></span>
          <div>
            <span class="brand-kicker">Movilidad clínica</span>
            <span class="brand-name">{{ APP_TITLE }}</span>
          </div>
        </div>

        <div class="context-copy">
          <p class="context-eyebrow">Centro de operaciones</p>
          <h2>Coordinación clara para cada recorrido.</h2>
          <p>Gestione rutas, servicios y operaciones desde un único lugar.</p>
        </div>

        <ol class="route-line" aria-hidden="true">
          <li><span></span>Planificación</li>
          <li><span></span>Operación</li>
          <li><span></span>Seguimiento</li>
        </ol>
      </div>

      <div class="login-form-panel">
        <form class="login-form" novalidate @submit.prevent="onSubmit">
          <div class="form-heading">
            <p class="form-eyebrow">Acceso seguro</p>
            <h1 id="login-heading">Iniciar sesión</h1>
            <p>Ingrese sus credenciales para continuar.</p>
          </div>

          <p v-if="formError" role="alert" class="form-error">
            <i class="pi pi-exclamation-circle" aria-hidden="true"></i>
            <span>{{ formError }}</span>
          </p>

          <div class="field">
            <label for="document_number">{{ LOGIN_LABELS.documentNumber }}</label>
            <input
              id="document_number"
              ref="documentInputEl"
              v-model="documentNumber"
              name="document_number"
              type="text"
              inputmode="numeric"
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
              name="password"
              type="password"
              autocomplete="current-password"
              :aria-invalid="!!passwordError"
              :aria-describedby="passwordError ? 'password-error' : undefined"
            />
            <p v-if="passwordError" id="password-error" role="alert" class="field-error">
              {{ passwordError }}
            </p>
          </div>

          <Button type="submit" :label="LOGIN_LABELS.submit" icon="pi pi-arrow-right" iconPos="right" :loading="submitting" fluid />

          <p class="access-note">
            <i class="pi pi-lock" aria-hidden="true"></i>
            Acceso restringido a personal autorizado.
          </p>
        </form>
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  min-height: 100dvh;
  display: grid;
  place-items: center;
  padding: clamp(1rem, 4vw, 3rem);
  background: #f4f6f4;
}
.login-shell {
  width: min(100%, 62rem);
  min-height: 37rem;
  display: grid;
  grid-template-columns: minmax(18rem, 0.88fr) minmax(22rem, 1.12fr);
  overflow: hidden;
  border: 1px solid #dfe4e1;
  border-radius: 1rem;
  background: #ffffff;
  box-shadow: 0 1.5rem 4rem rgba(15, 23, 20, 0.12);
}
.login-context {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: clamp(2rem, 5vw, 3.5rem);
  overflow: hidden;
  background: #064e3b;
  color: #ecfdf5;
}
.login-context::after {
  content: '';
  position: absolute;
  right: -5rem;
  bottom: -7rem;
  width: 18rem;
  height: 18rem;
  border: 1px solid rgba(236, 253, 245, 0.14);
  border-radius: 50%;
  box-shadow: 0 0 0 3rem rgba(236, 253, 245, 0.04), 0 0 0 6rem rgba(236, 253, 245, 0.025);
  pointer-events: none;
}
.brand-lockup {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 0.875rem;
}
.brand-mark {
  width: 2.75rem;
  height: 2.75rem;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border: 1px solid rgba(236, 253, 245, 0.28);
  border-radius: 0.75rem;
  background: rgba(255, 255, 255, 0.08);
  color: #fbbf24;
  font-size: 1.15rem;
}
.brand-lockup > div {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
}
.brand-kicker,
.context-eyebrow,
.form-eyebrow {
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.11em;
  text-transform: uppercase;
}
.brand-kicker {
  color: #a7f3d0;
}
.brand-name {
  font-weight: 700;
}
.context-copy {
  position: relative;
  z-index: 1;
  margin-block: auto;
  padding-block: 3rem;
}
.context-eyebrow {
  margin: 0 0 0.75rem;
  color: #fbbf24;
}
.context-copy h2 {
  max-width: 15ch;
  margin: 0;
  font-size: clamp(1.8rem, 3vw, 2.6rem);
  line-height: 1.08;
  letter-spacing: -0.035em;
}
.context-copy > p:last-child {
  max-width: 34ch;
  margin: 1rem 0 0;
  color: #a7f3d0;
}
.route-line {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin: 0;
  padding: 0;
  list-style: none;
  color: #d1fae5;
  font-size: 0.75rem;
}
.route-line::before {
  content: '';
  position: absolute;
  top: 0.34rem;
  left: 0.35rem;
  right: 0.35rem;
  height: 1px;
  background: rgba(236, 253, 245, 0.3);
}
.route-line li {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}
.route-line li:nth-child(2) {
  align-items: center;
}
.route-line li:last-child {
  align-items: flex-end;
}
.route-line span {
  z-index: 1;
  width: 0.7rem;
  height: 0.7rem;
  border: 2px solid #fbbf24;
  border-radius: 50%;
  background: #064e3b;
}
.login-form-panel {
  display: grid;
  place-items: center;
  padding: clamp(2rem, 6vw, 5rem);
}
.login-form {
  width: min(100%, 23rem);
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
}
.form-heading {
  margin-bottom: 0.5rem;
}
.form-eyebrow {
  margin: 0 0 0.5rem;
  color: #047857;
}
.form-heading h1 {
  margin: 0;
  color: #18181b;
  font-size: clamp(1.75rem, 3vw, 2.15rem);
  line-height: 1.15;
  letter-spacing: -0.025em;
}
.form-heading > p:last-child {
  margin: 0.65rem 0 0;
  color: #5f6763;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}
.field label {
  font-weight: 600;
  font-size: 0.9rem;
  color: #27272a;
}
.field input {
  min-height: 3rem;
  font: inherit;
  padding: 0.65rem 0.8rem;
  border: 1px solid #a7afaa;
  border-radius: 0.5rem;
  background: #ffffff;
  color: #18181b;
  transition: border-color 150ms ease, box-shadow 150ms ease;
}
.field input:hover {
  border-color: #6b746f;
}
.field input:focus-visible {
  border-color: #059669;
  outline: none;
  box-shadow: 0 0 0 3px rgba(5, 150, 105, 0.16);
}
.field input[aria-invalid='true'] {
  border-color: #b91c1c;
  box-shadow: 0 0 0 1px #b91c1c;
}
.field-error,
.form-error {
  color: #b91c1c;
  font-size: 0.875rem;
  margin: 0;
}
.form-error {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 0.75rem;
  border: 1px solid #fecaca;
  border-radius: 0.5rem;
  background: #fef2f2;
}
.form-error i {
  margin-top: 0.15rem;
}
.access-note {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
  margin: 0.25rem 0 0;
  color: #6b746f;
  font-size: 0.78rem;
  text-align: center;
}
.access-note i {
  color: #047857;
  font-size: 0.75rem;
}

@media (max-width: 48rem) {
  .login-page {
    padding: 0;
    background: #ffffff;
  }
  .login-shell {
    min-height: 100dvh;
    grid-template-columns: 1fr;
    grid-template-rows: auto 1fr;
    border: 0;
    border-radius: 0;
    box-shadow: none;
  }
  .login-context {
    min-height: auto;
    padding: 1.25rem;
  }
  .context-copy,
  .route-line,
  .login-context::after {
    display: none;
  }
  .login-form-panel {
    padding: 2.5rem 1.25rem;
  }
}

@media (prefers-color-scheme: dark) {
  .login-page {
    background: #101216;
  }
  .login-shell {
    border-color: #30343a;
    background: #191c21;
    box-shadow: 0 1.5rem 4rem rgba(0, 0, 0, 0.35);
  }
  .login-context {
    background: #043f31;
  }
  .route-line span {
    background: #043f31;
  }
  .form-heading h1,
  .field label {
    color: #f4f4f5;
  }
  .form-heading > p:last-child,
  .access-note {
    color: #a1a1aa;
  }
  .form-eyebrow,
  .access-note i {
    color: #34d399;
  }
  .field input {
    border-color: #676d76;
    background: #16181d;
    color: #f4f4f5;
  }
  .field input:hover {
    border-color: #9ca3af;
  }
  .field-error,
  .form-error {
    color: #fca5a5;
  }
  .form-error {
    border-color: #7f1d1d;
    background: #2b1417;
  }
  .field input[aria-invalid='true'] {
    border-color: #fca5a5;
    box-shadow: 0 0 0 1px #fca5a5;
  }
}

@media (max-width: 48rem) and (prefers-color-scheme: dark) {
  .login-page {
    background: #191c21;
  }
}
</style>
