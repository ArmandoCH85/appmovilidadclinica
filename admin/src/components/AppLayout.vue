<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStorage } from '@vueuse/core'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Drawer from 'primevue/drawer'
import AppNavigation from './AppNavigation.vue'
import { useAuth } from '../auth/useAuth'
import { APP_TITLE, SESSION_LABELS } from '../messages'
import { crudResources, NAV_GROUP_ORDER, type NavGroup } from '../resources'

const router = useRouter()
const route = useRoute()
const { user, secondsLeft, sessionExpired, logout, dismissSessionExpired } = useAuth()

const navItems: Array<{ to: string; label: string; group: NavGroup; icon: string }> = [
  ...crudResources.map(({ routePath, navLabel, group, icon }) => ({ to: routePath, label: navLabel, group, icon })),
  { to: '/route-stops', label: 'Paradas de ruta', group: 'Rutas', icon: 'map' },
  { to: '/operations', label: 'Operaciones', group: 'Operación diaria', icon: 'cog' },
  { to: '/reports', label: 'Reportes', group: 'Reportes', icon: 'chart-bar' },
  { to: '/help', label: 'Ayuda', group: 'Reportes', icon: 'question-circle' },
]

const navGroups = NAV_GROUP_ORDER.map((group) => ({
  group,
  items: navItems.filter((item) => item.group === group),
})).filter((section) => section.items.length > 0)

const activeItem = computed(() => navItems.find((item) => item.to === route.path))
const activeGroup = computed(() => activeItem.value?.group)
const expandedGroup = useStorage<string>('admin-nav-expanded-group', activeGroup.value ?? navGroups[0]?.group ?? '')
const mobileNavVisible = ref(false)
const mainContent = ref<HTMLElement | null>(null)

const userInitials = computed(() => {
  const name = user.value?.full_name?.trim()
  if (!name) return 'A'
  return name
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0])
    .join('')
    .toUpperCase()
})

const roleLabel = computed(() => {
  if (user.value?.role === 'ADMIN') return 'Administrador'
  if (user.value?.role === 'DRIVER') return 'Conductor'
  return 'Usuario'
})

watch(
  activeGroup,
  (group) => {
    if (group) expandedGroup.value = group
  },
  { immediate: true },
)

watch(
  () => route.path,
  async () => {
    mobileNavVisible.value = false
    await nextTick()
    mainContent.value?.focus()
  },
)

const showExpiryBanner = computed(() => secondsLeft.value > 0 && secondsLeft.value <= 120)
const bannerMinutes = computed(() => Math.max(1, Math.ceil(secondsLeft.value / 60)))

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

    <aside class="app-sidebar">
      <div class="brand-lockup">
        <span class="brand-mark" aria-hidden="true"><i class="pi pi-directions"></i></span>
        <span class="brand-copy">
          <span>Movilidad clínica</span>
          <strong>{{ APP_TITLE }}</strong>
        </span>
      </div>

      <div class="nav-heading">
        <span>Navegación</span>
        <span>{{ navItems.length }} módulos</span>
      </div>
      <AppNavigation v-model:expanded="expandedGroup" :sections="navGroups" />
    </aside>

    <div class="app-workspace">
      <header class="app-topbar">
        <div class="topbar-leading">
          <Button
            icon="pi pi-bars"
            text
            rounded
            class="menu-button"
            aria-label="Abrir navegación"
            aria-controls="mobile-navigation"
            :aria-expanded="mobileNavVisible"
            @click="mobileNavVisible = true"
          />
          <div class="page-context">
            <span>{{ activeGroup ?? 'Panel administrativo' }}</span>
            <strong>{{ activeItem?.label ?? APP_TITLE }}</strong>
          </div>
        </div>

        <div class="account-area">
          <div v-if="user" class="account-summary">
            <span class="account-avatar" aria-hidden="true">{{ userInitials }}</span>
            <span class="account-copy">
              <strong>{{ user.full_name }}</strong>
              <small>{{ roleLabel }}</small>
            </span>
          </div>
          <span class="account-divider" aria-hidden="true"></span>
          <Button
            icon="pi pi-sign-out"
            text
            rounded
            severity="secondary"
            :aria-label="SESSION_LABELS.logout"
            :title="SESSION_LABELS.logout"
            @click="onManualLogout"
          />
        </div>
      </header>

      <p v-if="showExpiryBanner" role="status" class="session-banner">
        <i class="pi pi-clock" aria-hidden="true"></i>
        {{ SESSION_LABELS.expiringSoon(bannerMinutes) }}
      </p>

      <main id="app-content" ref="mainContent" class="app-content" tabindex="-1">
        <RouterView :key="route.fullPath" />
      </main>
    </div>

    <Drawer
      id="mobile-navigation"
      v-model:visible="mobileNavVisible"
      position="left"
      class="mobile-drawer"
      header="Navegación"
    >
      <template #header>
        <div class="brand-lockup brand-lockup-mobile">
          <span class="brand-mark" aria-hidden="true"><i class="pi pi-directions"></i></span>
          <span class="brand-copy">
            <span>Movilidad clínica</span>
            <strong>{{ APP_TITLE }}</strong>
          </span>
        </div>
      </template>
      <AppNavigation
        v-model:expanded="expandedGroup"
        :sections="navGroups"
        @navigate="mobileNavVisible = false"
      />
      <template #footer>
        <div v-if="user" class="drawer-account">
          <span class="account-avatar" aria-hidden="true">{{ userInitials }}</span>
          <span class="account-copy">
            <strong>{{ user.full_name }}</strong>
            <small>{{ roleLabel }}</small>
          </span>
          <Button
            icon="pi pi-sign-out"
            text
            rounded
            :aria-label="SESSION_LABELS.logout"
            @click="onManualLogout"
          />
        </div>
      </template>
    </Drawer>

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
  --shell-bg: #f4f6f5;
  --shell-surface: #ffffff;
  --shell-border: #dfe4e1;
  --shell-text: #18181b;
  --shell-muted: #69716d;
  min-height: 100dvh;
  display: grid;
  grid-template-columns: 17rem minmax(0, 1fr);
  background: var(--shell-bg);
  color: var(--shell-text);
}
.skip-link {
  position: fixed;
  top: 0;
  left: -9999px;
  z-index: 2000;
  padding: 0.6rem 1rem;
  border-radius: 0 0 0.5rem 0;
  background: #ffffff;
  color: #18181b;
  font-weight: 700;
  text-decoration: none;
}
.skip-link:focus {
  left: 0;
}
.app-sidebar {
  position: sticky;
  top: 0;
  height: 100dvh;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  padding: 1rem 0.65rem 0.65rem;
  overflow: hidden;
  background: #0b211b;
  color: #ecfdf5;
  box-shadow: 0.5rem 0 2rem rgba(6, 78, 59, 0.08);
}
.brand-lockup {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.25rem 0.4rem 1rem;
  border-bottom: 1px solid rgba(236, 253, 245, 0.14);
}
.brand-mark {
  width: 2.55rem;
  height: 2.55rem;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border: 1px solid rgba(236, 253, 245, 0.24);
  border-radius: 0.7rem;
  background: rgba(255, 255, 255, 0.07);
  color: #fbbf24;
}
.brand-copy,
.account-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.brand-copy span {
  color: #a7f3d0;
  font-size: 0.65rem;
  font-weight: 700;
  letter-spacing: 0.09em;
  text-transform: uppercase;
}
.brand-copy strong {
  overflow: hidden;
  color: #ffffff;
  font-size: 0.9rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.nav-heading {
  display: flex;
  justify-content: space-between;
  padding: 0.15rem 0.7rem 0;
  color: #78938a;
  font-size: 0.67rem;
  font-weight: 650;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}
.app-sidebar :deep(.app-navigation) {
  flex: 1;
  margin-right: -0.25rem;
  padding-right: 0.25rem;
}
.app-workspace {
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.app-topbar {
  position: sticky;
  top: 0;
  z-index: 100;
  min-height: 4.5rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.7rem clamp(1rem, 2.5vw, 2rem);
  border-bottom: 1px solid var(--shell-border);
  background: color-mix(in srgb, var(--shell-surface) 94%, transparent);
  backdrop-filter: blur(12px);
}
.topbar-leading,
.account-area,
.account-summary,
.drawer-account {
  min-width: 0;
  display: flex;
  align-items: center;
}
.topbar-leading {
  gap: 0.75rem;
}
.page-context {
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.page-context span {
  color: #047857;
  font-size: 0.67rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.page-context strong {
  overflow: hidden;
  color: var(--shell-text);
  font-size: 1.02rem;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.menu-button {
  display: none;
}
.account-area {
  gap: 0.65rem;
}
.account-summary,
.drawer-account {
  gap: 0.65rem;
}
.account-avatar {
  width: 2.25rem;
  height: 2.25rem;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border-radius: 0.65rem;
  background: #ecfdf5;
  color: #047857;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.03em;
}
.account-copy strong {
  max-width: 14rem;
  overflow: hidden;
  color: var(--shell-text);
  font-size: 0.8rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.account-copy small {
  color: var(--shell-muted);
  font-size: 0.7rem;
}
.account-divider {
  width: 1px;
  height: 1.75rem;
  background: var(--shell-border);
}
.session-banner {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0;
  padding: 0.55rem clamp(1rem, 2.5vw, 2rem);
  border-bottom: 1px solid #fde68a;
  background: #fffbeb;
  color: #78350f;
  font-size: 0.84rem;
}
.app-content {
  min-width: 0;
  flex: 1;
  padding: clamp(1rem, 2.5vw, 2rem);
  outline: none;
}
.app-content:focus-visible {
  box-shadow: inset 0 0 0 2px var(--color-focus-ring);
}
.app-content :deep(.p-datatable-table-container) {
  max-width: 100%;
  overflow-x: auto;
}
.brand-lockup-mobile {
  flex: 1;
  padding: 0;
  border: 0;
}
.drawer-account {
  width: 100%;
}
.drawer-account .account-copy {
  flex: 1;
}

:global(.mobile-drawer) {
  width: min(88vw, 19rem) !important;
  background: #0b211b !important;
  color: #ecfdf5 !important;
}
:global(.mobile-drawer .p-drawer-header) {
  padding: 1rem !important;
  border-bottom: 1px solid rgba(236, 253, 245, 0.14);
}
:global(.mobile-drawer .p-drawer-content) {
  padding: 0.75rem !important;
}
:global(.mobile-drawer .p-drawer-footer) {
  padding: 0.75rem 1rem !important;
  border-top: 1px solid rgba(236, 253, 245, 0.14);
}
:global(.mobile-drawer .p-drawer-close-button) {
  color: #d1fae5 !important;
}
:global(.mobile-drawer .account-avatar) {
  background: rgba(255, 255, 255, 0.1);
  color: #fbbf24;
}
:global(.mobile-drawer .account-copy strong) {
  color: #ffffff;
}
:global(.mobile-drawer .account-copy small) {
  color: #86a99e;
}

@media (max-width: 64rem) {
  .app-shell {
    grid-template-columns: minmax(0, 1fr);
  }
  .app-sidebar {
    display: none;
  }
  .menu-button {
    display: inline-flex;
  }
}

@media (max-width: 40rem) {
  .app-topbar {
    min-height: 4rem;
    padding-inline: 0.75rem;
  }
  .app-topbar .account-copy,
  .account-divider {
    display: none;
  }
  .account-area {
    gap: 0.2rem;
  }
  .account-avatar {
    width: 2rem;
    height: 2rem;
  }
  .page-context span {
    font-size: 0.6rem;
  }
  .page-context strong {
    max-width: 48vw;
    font-size: 0.92rem;
  }
  .app-content {
    padding: 1rem 0.75rem;
  }
}

@media (prefers-color-scheme: dark) {
  .app-shell {
    --shell-bg: #101216;
    --shell-surface: #191c21;
    --shell-border: #30343a;
    --shell-text: #f4f4f5;
    --shell-muted: #9ca3af;
  }
  .app-sidebar,
  :global(.mobile-drawer) {
    background: #071a15 !important;
  }
  .app-topbar {
    background: color-mix(in srgb, #191c21 94%, transparent);
  }
  .page-context span {
    color: #34d399;
  }
  .account-avatar {
    background: #052e22;
    color: #6ee7b7;
  }
  .session-banner {
    border-bottom-color: #78350f;
    background: #2b1d0a;
    color: #fde68a;
  }
}
</style>
