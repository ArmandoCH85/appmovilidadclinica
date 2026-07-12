import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import AppLayout from './components/AppLayout.vue'
import LoginView from './components/LoginView.vue'
import CrudView from './components/CrudView.vue'
import RouteStopsView from './components/RouteStopsView.vue'
import { useAuth } from './auth/useAuth'
import { crudResources } from './resources'

// Fase 5: las 6 rutas CRUD "planas" salen de `crudResources` (resources.ts)
// — data-driven, un solo bloque en vez de 6 literales (ponytail). `route-stops`
// es la 7ma pero no es generica (sin GET plano en el backend, ver
// RouteStopsView.vue) — se registra aparte con su propio componente.
const resourceChildren: RouteRecordRaw[] = crudResources.map(({ routePath, config }) => ({
  path: routePath.slice(1),
  name: routePath.slice(1),
  component: CrudView,
  props: { config },
}))

const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'login', component: LoginView },
  {
    // AppLayout es el shell de toda ruta protegida (nav, banner T-2min,
    // modal de sesion expirada). meta.requiresAuth en el padre alcanza: los
    // hijos heredan el record matcheado, el guard de abajo los cubre a todos.
    path: '/',
    component: AppLayout,
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/stops' },
      ...resourceChildren,
      { path: 'route-stops', name: 'route-stops', component: RouteStopsView },
    ],
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
