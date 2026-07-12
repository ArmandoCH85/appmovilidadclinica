<script setup lang="ts">
// Fase 3, tareas 3.5/3.6: shell de las rutas protegidas — nav+logout, banner
// proactivo T-2min (role="status") y el modal bloqueante de sesion expirada
// (backstop reactivo del 401, ver api/client.ts). Dialog de PrimeVue trae
// focus-trap incorporado (razon de eleccion de libreria en el diseno).
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import { useAuth } from '../auth/useAuth'
import { APP_TITLE, SESSION_LABELS } from '../messages'

const router = useRouter()
const route = useRoute()
const { user, secondsLeft, sessionExpired, logout, dismissSessionExpired } = useAuth()

const showExpiryBanner = computed(() => secondsLeft.value > 0 && secondsLeft.value <= 120)
const bannerMinutes = computed(() => Math.max(1, Math.ceil(secondsLeft.value / 60)))

// ponytail: no hay endpoint de renovacion (JWT 24h sin refresh, decision de
// diseno #2) — el banner es solo el aviso role="status"; la "accion" es que
// el propio texto le dice al admin que guarde en la vista donde esta parado.
// Un boton "renovar" sin backend que renueve seria una afordancia falsa.

function onManualLogout() {
  logout()
  router.push('/login')
}

function onConfirmSessionExpired() {
  dismissSessionExpired()
  logout()
  router.push({ path: '/login', query: { redirect: route.fullPath } })
}
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <span class="app-title">{{ APP_TITLE }}</span>
      <nav aria-label="Cuenta">
        <span v-if="user" class="app-user">{{ user.full_name }}</span>
        <Button :label="SESSION_LABELS.logout" severity="secondary" text @click="onManualLogout" />
      </nav>
    </header>

    <p v-if="showExpiryBanner" role="status" class="session-banner">
      {{ SESSION_LABELS.expiringSoon(bannerMinutes) }}
    </p>

    <main class="app-content">
      <RouterView />
    </main>

    <Dialog
      :visible="sessionExpired"
      modal
      :closable="false"
      :draggable="false"
      :header="SESSION_LABELS.expiredTitle"
    >
      <p>{{ SESSION_LABELS.expiredBody }}</p>
      <template #footer>
        <Button :label="SESSION_LABELS.expiredConfirm" autofocus @click="onConfirmSessionExpired" />
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.app-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid rgba(0, 0, 0, 0.15);
}
.app-title {
  font-weight: 700;
}
.app-user {
  margin-right: 0.75rem;
}
.session-banner {
  margin: 0;
  padding: 0.5rem 1rem;
  background: #fef3c7;
  color: #78350f;
}
.app-content {
  flex: 1;
  padding: 1rem;
}
@media (prefers-color-scheme: dark) {
  .app-header {
    border-bottom-color: rgba(255, 255, 255, 0.15);
  }
  .session-banner {
    background: #451a03;
    color: #fde68a;
  }
}
</style>
