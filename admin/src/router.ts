import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { defineComponent, h } from 'vue'

// Fase 2: shell de arranque sin rutas reales todavia. Fase 3 agrega el guard
// de auth (beforeEach); Fase 5 conecta las rutas de los 7 recursos via
// CrudView. Placeholder inline para no inventar un archivo de componente que
// el diseno no contempla.
const HomePlaceholder = defineComponent({
  name: 'HomePlaceholder',
  render: () => h('p', 'Panel administrativo — en construcción.'),
})

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: HomePlaceholder,
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
