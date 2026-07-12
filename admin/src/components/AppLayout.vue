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
import { crudResources } from '../resources'

const router = useRouter()
const route = useRoute()
const { user, secondsLeft, sessionExpired, logout, dismissSessionExpired } = useAuth()

// Fase 5: nav de los 7 recursos, data-driven desde `crudResources` +
// `route-stops` (caso especial, ver RouteStopsView.vue). `aria-current`
// marca la ruta activa — nunca solo color (a11y, ver design.md non-functional).
const navItems = [
  ...crudResources.map(({ routePath, navLabel }) => ({ to: routePath, label: navLabel })),
  { to: '/route-stops', label: 'Paradas de ruta' },
  // Fase 6: no son recursos CRUD genericos, se agregan aparte del array
  // data-driven (mismo criterio que route-stops arriba).
  { to: '/operations', label: 'Operaciones' },
  { to: '/reports', label: 'Reportes' },
]

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
    <a href="#app-content" class="skip-link">Saltar al contenido</a>
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

    <div class="app-body">
      <nav class="app-nav" aria-label="Recursos administrables">
        <ul>
          <li v-for="item in navItems" :key="item.to">
            <!-- RouterLink ya setea aria-current="page" en la ruta activa
                 (default ariaCurrentValue) — se estiliza via ese atributo,
                 sin logica manual duplicada. -->
            <RouterLink :to="item.to">{{ item.label }}</RouterLink>
          </li>
        </ul>
      </nav>

      <main id="app-content" class="app-content" tabindex="-1">
        <RouterView />
      </main>
    </div>

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
.skip-link {
  position: absolute;
  left: -9999px;
  top: 0;
  z-index: 1000;
  padding: 0.5rem 1rem;
  background: var(--color-bg);
  color: var(--color-text);
  font-weight: 600;
  text-decoration: none;
  border-radius: 0 0 6px 6px;
}
.skip-link:focus {
  left: 0;
}
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
.app-body {
  flex: 1;
  display: flex;
  min-height: 0;
}
.app-nav {
  border-right: 1px solid rgba(0, 0, 0, 0.15);
  padding: 1rem 0.5rem;
  flex-shrink: 0;
}
.app-nav ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}
.app-nav a {
  display: block;
  padding: 0.5rem 0.75rem;
  border-radius: 6px;
  text-decoration: none;
  color: inherit;
}
/* Ruta activa: negrita + borde, nunca solo color (a11y). */
.app-nav a[aria-current='page'] {
  font-weight: 700;
  border-left: 3px solid currentColor;
  padding-left: calc(0.75rem - 3px);
}
.app-content {
  flex: 1;
  padding: 1rem;
  min-width: 0;
}
@media (prefers-color-scheme: dark) {
  .app-header {
    border-bottom-color: rgba(255, 255, 255, 0.15);
  }
  .session-banner {
    background: #451a03;
    color: #fde68a;
  }
  .app-nav {
    border-right-color: rgba(255, 255, 255, 0.15);
  }
}
</style>
