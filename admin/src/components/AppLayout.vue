<script setup lang="ts">
// Fase 3, tareas 3.5/3.6: shell de las rutas protegidas — nav+logout, banner
// proactivo T-2min (role="status") y el modal bloqueante de sesion expirada
// (backstop reactivo del 401, ver api/client.ts). Dialog de PrimeVue trae
// focus-trap incorporado (razon de eleccion de libreria en el diseno).
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStorage } from '@vueuse/core'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import Accordion from 'primevue/accordion'
import AccordionPanel from 'primevue/accordionpanel'
import AccordionHeader from 'primevue/accordionheader'
import AccordionContent from 'primevue/accordioncontent'
import { useAuth } from '../auth/useAuth'
import { APP_TITLE, SESSION_LABELS } from '../messages'
import { crudResources, NAV_GROUP_ORDER, type NavGroup } from '../resources'

const router = useRouter()
const route = useRoute()
const { user, secondsLeft, sessionExpired, logout, dismissSessionExpired } = useAuth()

// Nav agrupado por dominio logico (no por orden de implementacion, ver
// memoria "admin nav agrupado por dominio"). `route-stops`/`operations`/
// `reports` no son recursos CRUD genericos (ver comentarios en router.ts),
// se suman aca con el grupo que les corresponde por dominio.
const navItems: Array<{ to: string; label: string; group: NavGroup; icon: string }> = [
  ...crudResources.map(({ routePath, navLabel, group, icon }) => ({ to: routePath, label: navLabel, group, icon })),
  { to: '/route-stops', label: 'Paradas de ruta', group: 'Rutas', icon: 'map' },
  { to: '/operations', label: 'Operaciones', group: 'Operación diaria', icon: 'cog' },
  { to: '/reports', label: 'Reportes', group: 'Reportes', icon: 'chart-bar' },
]

const navGroups = NAV_GROUP_ORDER.map((group) => ({
  group,
  items: navItems.filter((item) => item.group === group),
})).filter((g) => g.items.length > 0)

const activeGroup = computed(() => navItems.find((item) => item.to === route.path)?.group)

// Grupos plegados/desplegados del sidebar, persistidos en localStorage
// (VueUse `useStorage`, ya instalado — no reinventar persistencia). Semilla
// inicial: solo el grupo de la ruta activa al primer ingreso.
const expandedGroups = useStorage<string[]>('admin-nav-expanded', activeGroup.value ? [activeGroup.value] : [])

// Si se navega (o refresca) a una ruta cuyo grupo esta plegado, se despliega
// solo — nunca deja al admin mirando un sidebar sin la seccion activa visible.
watch(
  activeGroup,
  (group) => {
    if (group && !expandedGroups.value.includes(group)) {
      expandedGroups.value = [...expandedGroups.value, group]
    }
  },
  { immediate: true },
)

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
        <Accordion v-model:value="expandedGroups" multiple>
          <AccordionPanel v-for="section in navGroups" :key="section.group" :value="section.group">
            <AccordionHeader>
              <span class="app-nav-heading-label">
                {{ section.group }}
                <span class="app-nav-count">{{ section.items.length }}</span>
              </span>
            </AccordionHeader>
            <AccordionContent>
              <ul>
                <!-- El riel + nodo por item traduce el propio dominio (paradas
                     sobre una linea de ruta) en la navegacion: cada recurso es
                     una "parada", la activa se ve encendida. Puramente CSS,
                     sin JS extra. -->
                <li v-for="(item, idx) in section.items" :key="item.to">
                  <!-- RouterLink ya setea aria-current="page" en la ruta activa
                       (default ariaCurrentValue) — se estiliza via ese atributo,
                       sin logica manual duplicada. -->
                  <RouterLink :to="item.to" class="app-nav-link">
                    <span class="app-nav-rail" aria-hidden="true">
                      <span v-if="idx > 0" class="app-nav-rail-line app-nav-rail-line--top"></span>
                      <span class="app-nav-node"></span>
                      <span v-if="idx < section.items.length - 1" class="app-nav-rail-line app-nav-rail-line--bottom"></span>
                    </span>
                    <span class="app-nav-row">
                      <i :class="`pi pi-${item.icon}`" aria-hidden="true"></i>
                      <span class="app-nav-label">{{ item.label }}</span>
                    </span>
                  </RouterLink>
                </li>
              </ul>
            </AccordionContent>
          </AccordionPanel>
        </Accordion>
      </nav>

      <main id="app-content" class="app-content" tabindex="-1">
        <!-- :key fuerza remount al cambiar de recurso: las rutas CRUD
             comparten el mismo componente (CrudView) y Vue Router reutiliza
             la instancia entre hermanas — sin esto, onMounted no revuelve a
             disparar list() y useCrudResource() sigue apuntando al basePath
             del primer recurso visitado (bug: tabla vacía/resource viejo al
             navegar por el sidebar, solo se arreglaba con F5). -->
        <RouterView :key="route.fullPath" />
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
/* Tokens propios del sidebar — independientes del primary de PrimeVue.
   El acento "ambar de senal" (transito/paradas) es una decision de
   wayfinding del nav, no el color de marca de botones/CTAs. */
.app-nav {
  --nav-bg: #fafaf9;
  --nav-border: rgba(15, 15, 20, 0.09);
  --nav-text: #27272a;
  --nav-text-muted: #85858c;
  --nav-accent: #b24f0f;
  --nav-accent-rgb: 178, 79, 15;
  --nav-hover-bg: rgba(15, 15, 20, 0.035);

  background: var(--nav-bg);
  border-right: 1px solid var(--nav-border);
  padding: 0.5rem 0;
  flex-shrink: 0;
  width: 15.5rem;
  overflow-y: auto;
}
@media (prefers-color-scheme: dark) {
  .app-nav {
    --nav-bg: #101216;
    --nav-border: rgba(255, 255, 255, 0.08);
    --nav-text: #e4e6ea;
    --nav-text-muted: #71757f;
    --nav-accent: #f0954f;
    --nav-accent-rgb: 240, 149, 79;
    --nav-hover-bg: rgba(255, 255, 255, 0.045);
  }
  .app-header {
    border-bottom-color: rgba(255, 255, 255, 0.15);
  }
  .session-banner {
    background: #451a03;
    color: #fde68a;
  }
}

/* Reset del look "card" default de PrimeVue: sin fondo/borde propios, cada
   grupo separado por un hairline (misma logica visual que un limite de
   segmento de ruta en los datos). */
.app-nav :deep(.p-accordion) {
  display: flex;
  flex-direction: column;
}
.app-nav :deep(.p-accordionpanel) {
  border-top: 1px solid var(--nav-border);
}
.app-nav :deep(.p-accordionpanel:first-child) {
  border-top: none;
}
.app-nav :deep(.p-accordionheader) {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  background: transparent;
  border: none;
  box-shadow: none;
  padding: 0.85rem 1rem 0.4rem;
  font-size: 0.68rem;
  font-weight: 600;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: var(--nav-text-muted);
  cursor: pointer;
  transition: color 0.15s ease;
}
.app-nav :deep(.p-accordionheader:hover) {
  color: var(--nav-text);
  background: transparent;
}
.app-nav :deep(.p-accordionheader-toggle-icon) {
  width: 0.65rem;
  height: 0.65rem;
  flex-shrink: 0;
}
.app-nav :deep(.p-accordioncontent-content) {
  padding: 0;
}
.app-nav-count {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-weight: 400;
  letter-spacing: 0;
  opacity: 0.65;
}

.app-nav ul {
  list-style: none;
  margin: 0;
  padding: 0 0.5rem 0.5rem;
}
.app-nav-link {
  position: relative;
  display: flex;
  align-items: stretch;
  text-decoration: none;
  color: var(--nav-text-muted);
  border-radius: 7px;
}
.app-nav-link:hover {
  background: var(--nav-hover-bg);
  color: var(--nav-text);
}

/* Riel + nodo: cada item es una "parada" sobre la linea de la ruta. La
   parada activa se enciende (relleno solido + halo) — misma metafora que
   un mapa de transito resaltando donde estas. */
.app-nav-rail {
  position: relative;
  width: 1.1rem;
  flex-shrink: 0;
}
.app-nav-rail-line {
  position: absolute;
  left: 50%;
  width: 1px;
  background: var(--nav-border);
  transform: translateX(-50%);
}
.app-nav-rail-line--top {
  top: 0;
  height: 50%;
}
.app-nav-rail-line--bottom {
  bottom: 0;
  height: 50%;
}
.app-nav-node {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--nav-bg);
  border: 1.5px solid var(--nav-text-muted);
  transform: translate(-50%, -50%);
  transition: background-color 0.15s ease, border-color 0.15s ease, box-shadow 0.15s ease;
}
.app-nav-link:hover .app-nav-node {
  border-color: var(--nav-accent);
}

.app-nav-row {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  flex: 1;
  min-width: 0;
  padding: 0.42rem 0.75rem 0.42rem 0.1rem;
}
.app-nav-row .pi {
  font-size: 0.85rem;
  width: 1rem;
  text-align: center;
  flex-shrink: 0;
}
.app-nav-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Ruta activa: tres senales no-color (peso, nodo relleno, halo) — nunca
   solo color (a11y, ver comentario historico de esta vista). */
.app-nav-link[aria-current='page'] {
  color: var(--nav-text);
  font-weight: 600;
}
.app-nav-link[aria-current='page'] .app-nav-node {
  background: var(--nav-accent);
  border-color: var(--nav-accent);
  box-shadow: 0 0 0 3px rgba(var(--nav-accent-rgb), 0.18);
}
.app-content {
  flex: 1;
  padding: 1rem;
  min-width: 0;
}
</style>
