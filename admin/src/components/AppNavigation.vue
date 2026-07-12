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
            <li v-for="(item, idx) in section.items" :key="item.to">
              <RouterLink :to="item.to" class="nav-link" @click="emit('navigate')">
                <span class="nav-rail" aria-hidden="true">
                  <span v-if="idx > 0" class="nav-rail-line nav-rail-line-top"></span>
                  <span class="nav-node"></span>
                  <span v-if="idx < section.items.length - 1" class="nav-rail-line nav-rail-line-bottom"></span>
                </span>
                <span class="nav-row">
                  <i :class="`pi pi-${item.icon}`" aria-hidden="true"></i>
                  <span class="nav-label">{{ item.label }}</span>
                </span>
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
  --nav-text: #d1fae5;
  --nav-muted: #86a99e;
  --nav-border: rgba(236, 253, 245, 0.12);
  --nav-accent: #fbbf24;
  --nav-accent-rgb: 251, 191, 36;
  --nav-hover: rgba(255, 255, 255, 0.055);
  min-height: 0;
}
.app-navigation :deep(.p-accordion) {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}
.app-navigation :deep(.p-accordionpanel) {
  border: 0;
}
.app-navigation :deep(.p-accordionheader) {
  width: 100%;
  min-height: 2.5rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.65rem 0.75rem 0.35rem;
  border: 0;
  box-shadow: none;
  background: transparent;
  color: var(--nav-muted);
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  cursor: pointer;
  transition: color 150ms ease;
}
.app-navigation :deep(.p-accordionheader:hover),
.app-navigation :deep(.p-accordionheader:focus-visible) {
  background: transparent;
  color: var(--nav-text);
}
.app-navigation :deep(.p-accordionheader-toggle-icon) {
  width: 0.65rem;
  height: 0.65rem;
  flex-shrink: 0;
}
.app-navigation :deep(.p-accordioncontent-content) {
  padding: 0;
  background: transparent;
  color: inherit;
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
  padding: 0 0.35rem 0.45rem;
  list-style: none;
}
.nav-link {
  position: relative;
  min-height: 2.45rem;
  display: flex;
  align-items: stretch;
  border-radius: 0.55rem;
  color: var(--nav-muted);
  text-decoration: none;
  transition: background-color 150ms ease, color 150ms ease;
}
.nav-link:hover {
  background: var(--nav-hover);
  color: var(--nav-text);
}
.nav-link:active {
  background: rgba(255, 255, 255, 0.09);
}
.nav-rail {
  position: relative;
  width: 1.25rem;
  flex-shrink: 0;
}
.nav-rail-line {
  position: absolute;
  left: 50%;
  width: 1px;
  background: var(--nav-border);
  transform: translateX(-50%);
}
.nav-rail-line-top {
  top: 0;
  height: 50%;
}
.nav-rail-line-bottom {
  bottom: 0;
  height: 50%;
}
.nav-node {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 0.45rem;
  height: 0.45rem;
  border: 1.5px solid var(--nav-muted);
  border-radius: 50%;
  background: #064e3b;
  transform: translate(-50%, -50%);
  transition: background-color 150ms ease, border-color 150ms ease, box-shadow 150ms ease;
}
.nav-row {
  min-width: 0;
  display: flex;
  flex: 1;
  align-items: center;
  gap: 0.65rem;
  padding: 0.45rem 0.65rem 0.45rem 0.1rem;
}
.nav-row .pi {
  width: 1rem;
  flex-shrink: 0;
  color: currentColor;
  font-size: 0.88rem;
  text-align: center;
}
.nav-label {
  overflow: hidden;
  font-size: 0.86rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.nav-link[aria-current='page'] {
  background: rgba(255, 255, 255, 0.09);
  color: #ffffff;
  font-weight: 650;
}
.nav-link[aria-current='page']::after {
  content: '';
  position: absolute;
  top: 0.45rem;
  right: 0;
  bottom: 0.45rem;
  width: 2px;
  border-radius: 2px 0 0 2px;
  background: var(--nav-accent);
}
.nav-link[aria-current='page'] .nav-node {
  border-color: var(--nav-accent);
  background: var(--nav-accent);
  box-shadow: 0 0 0 3px rgba(var(--nav-accent-rgb), 0.16);
}

@media (prefers-color-scheme: dark) {
  .nav-node {
    background: #043f31;
  }
}
</style>
