<script setup lang="ts">
import Accordion from 'primevue/accordion'
import AccordionPanel from 'primevue/accordionpanel'
import AccordionHeader from 'primevue/accordionheader'
import AccordionContent from 'primevue/accordioncontent'

interface NavItem {
  to: string
  label: string
  icon: string
}

interface NavSection {
  group: string
  items: NavItem[]
}

defineProps<{ sections: NavSection[] }>()
const expanded = defineModel<string[]>('expanded', { required: true })
const emit = defineEmits<{ navigate: [] }>()
</script>

<template>
  <nav class="app-navigation" aria-label="Navegación principal">
    <Accordion v-model:value="expanded" multiple>
      <AccordionPanel v-for="section in sections" :key="section.group" :value="section.group">
        <AccordionHeader>
          <span class="nav-section-label">
            {{ section.group }}
            <span class="nav-count">{{ section.items.length }}</span>
          </span>
        </AccordionHeader>
        <AccordionContent>
          <ul>
            <li v-for="item in section.items" :key="item.to">
              <RouterLink :to="item.to" class="nav-link" @click="emit('navigate')">
                <span class="nav-icon" aria-hidden="true"><i :class="`pi pi-${item.icon}`"></i></span>
                <span class="nav-label">{{ item.label }}</span>
              </RouterLink>
            </li>
          </ul>
        </AccordionContent>
      </AccordionPanel>
    </Accordion>
  </nav>
</template>

<style scoped>
.app-navigation {
  --nav-text: #ecfdf5;
  --nav-muted: #91aaa2;
  --nav-border: rgba(236, 253, 245, 0.1);
  --nav-accent: #34d399;
  --nav-hover: rgba(255, 255, 255, 0.05);
  min-height: 0;
  overflow-y: auto;
  scrollbar-color: rgba(167, 243, 208, 0.24) transparent;
  scrollbar-width: thin;
}
.app-navigation :deep(.p-accordion) {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
}
.app-navigation :deep(.p-accordionpanel) {
  border: 0;
}
.app-navigation :deep(.p-accordionheader) {
  width: 100%;
  min-height: 2.4rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.6rem 0.65rem;
  border: 0 !important;
  box-shadow: none !important;
  background: transparent !important;
  color: var(--nav-muted) !important;
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  cursor: pointer;
  transition: color 150ms ease;
}
.app-navigation :deep(.p-accordionheader:hover),
.app-navigation :deep(.p-accordionheader:focus-visible) {
  background: var(--nav-hover) !important;
  color: var(--nav-text) !important;
}
.app-navigation :deep(.p-accordionpanel[data-p-active='true'] .p-accordionheader) {
  color: var(--nav-text) !important;
}
.app-navigation :deep(.p-accordionheader-toggle-icon) {
  width: 0.65rem;
  height: 0.65rem;
  flex-shrink: 0;
}
.app-navigation :deep(.p-accordioncontent-content) {
  padding: 0 !important;
  background: transparent !important;
  color: inherit !important;
}
.nav-section-label {
  display: flex;
  align-items: center;
  gap: 0.45rem;
}
.nav-count {
  min-width: 1.25rem;
  padding: 0.08rem 0.3rem;
  border: 1px solid var(--nav-border);
  border-radius: 999px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.62rem;
  font-weight: 500;
  letter-spacing: 0;
  line-height: 1.1;
  text-align: center;
}
.app-navigation ul {
  margin: 0;
  padding: 0 0.2rem 0.45rem;
  list-style: none;
}
.nav-link {
  min-height: 2.5rem;
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.35rem 0.55rem;
  border-left: 2px solid transparent;
  border-radius: 0.45rem;
  color: var(--nav-muted);
  text-decoration: none;
  transition: background-color 150ms ease, border-color 150ms ease, color 150ms ease;
}
.nav-link:hover {
  background: var(--nav-hover);
  color: var(--nav-text);
}
.nav-link:active {
  background: rgba(255, 255, 255, 0.09);
}
.nav-icon {
  width: 1.75rem;
  height: 1.75rem;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border-radius: 0.4rem;
  background: rgba(255, 255, 255, 0.035);
}
.nav-icon .pi {
  font-size: 0.84rem;
}
.nav-label {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  font-size: 0.84rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.nav-link[aria-current='page'] {
  border-left-color: var(--nav-accent);
  background: rgba(52, 211, 153, 0.11);
  color: #ffffff;
  font-weight: 700;
}
.nav-link[aria-current='page'] .nav-icon {
  background: rgba(52, 211, 153, 0.16);
  color: #6ee7b7;
}
</style>
