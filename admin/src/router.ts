import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import AppLayout from './components/AppLayout.vue'
import LoginView from './components/LoginView.vue'
import CrudView from './components/CrudView.vue'
import StopsView from './components/StopsView.vue'
import VehiclesView from './components/VehiclesView.vue'
import VehicleSeatsView from './components/VehicleSeatsView.vue'
import UsersView from './components/UsersView.vue'
import RoutesView from './components/RoutesView.vue'
import RouteSegmentsView from './components/RouteSegmentsView.vue'
import RouteStopsView from './components/RouteStopsView.vue'
import CalendarExceptionsView from './components/CalendarExceptionsView.vue'
import CalendarsView from './components/CalendarsView.vue'
import TravelProfilesView from './components/TravelProfilesView.vue'
import OperationsView from './components/OperationsView.vue'
import ReportsView from './components/ReportsView.vue'
import { useAuth } from './auth/useAuth'
import { crudResources } from './resources'

// Fase 5: las rutas CRUD "planas" salen de `crudResources` (resources.ts)
// — data-driven, un solo bloque en vez de N literales (ponytail).
// REDESIGNED_PATHS se excluyen: tienen su propio componente rediseñado
// (rollout sección por sección del patron visual — ver memoria
// "admin/crud-visual-redesign-pattern"). `route-stops` es otro caso especial
// (sin GET plano en el backend, ver RouteStopsView.vue) — se registra aparte.
const REDESIGNED_PATHS = new Set([
  '/stops',
  '/vehicles',
  '/vehicle-seats',
  '/users',
  '/routes',
  '/route-segments',
  '/calendars',
  '/calendar-exceptions',
  '/travel-profiles',
])
const resourceChildren: RouteRecordRaw[] = crudResources
  .filter(({ routePath }) => !REDESIGNED_PATHS.has(routePath))
  .map(({ routePath, config, readOnly }) => ({
    path: routePath.slice(1),
    name: routePath.slice(1),
    component: CrudView,
    props: { config, readOnly },
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
      { path: 'stops', name: 'stops', component: StopsView },
      { path: 'vehicles', name: 'vehicles', component: VehiclesView },
      { path: 'vehicle-seats', name: 'vehicle-seats', component: VehicleSeatsView },
      { path: 'users', name: 'users', component: UsersView },
      { path: 'routes', name: 'routes', component: RoutesView },
      { path: 'route-segments', name: 'route-segments', component: RouteSegmentsView },
      ...resourceChildren,
      { path: 'route-stops', name: 'route-stops', component: RouteStopsView },
      { path: 'calendars', name: 'calendars', component: CalendarsView },
      { path: 'calendar-exceptions', name: 'calendar-exceptions', component: CalendarExceptionsView },
      { path: 'travel-profiles', name: 'travel-profiles', component: TravelProfilesView },
      // Fase 6: operaciones de viaje + reportes — no son recursos CRUD
      // genericos (sin listado/alta/edicion tabular), se registran aparte.
      { path: 'operations', name: 'operations', component: OperationsView },
      { path: 'reports', name: 'reports', component: ReportsView },
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
