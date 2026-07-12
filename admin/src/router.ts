import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { defineComponent, h } from 'vue'
import AppLayout from './components/AppLayout.vue'
import LoginView from './components/LoginView.vue'
import { useAuth } from './auth/useAuth'

// Fase 5 reemplaza este placeholder por las rutas reales de los 7 recursos
// (children de AppLayout, prop `resource` -> CrudView). Placeholder inline
// para no inventar un archivo de componente que el diseno no contempla.
const HomePlaceholder = defineComponent({
  name: 'HomePlaceholder',
  render: () => h('p', 'Panel administrativo — en construcción.'),
})

const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'login', component: LoginView },
  {
    // AppLayout es el shell de toda ruta protegida (nav, banner T-2min,
    // modal de sesion expirada). meta.requiresAuth en el padre alcanza: los
    // hijos heredan el record matcheado, el guard de abajo los cubre a todos.
    path: '/',
    component: AppLayout,
    meta: { requiresAuth: true },
    children: [{ path: '', name: 'home', component: HomePlaceholder }],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// Guard de sesion (diseno "Route map + guards", Fase 3 tarea 3.4): ruta
// protegida sin sesion -> /login?redirect=destino; login exitoso navega de
// vuelta a `redirect` (lo resuelve LoginView.vue).
router.beforeEach((to) => {
  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth)
  if (!requiresAuth) return true

  const { isAuthenticated } = useAuth()
  if (!isAuthenticated.value) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
