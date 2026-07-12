<script setup lang="ts">
// Paradas de ruta (Fase 5): recurso especial entre los 7 — el backend NO
// expone GET /admin/route-stops (solo POST/PUT top-level, ver
// handler.go RegisterRoutes). El unico listado real es
// GET /admin/routes/{id}/stops, anidado bajo una ruta concreta. En vez de
// inventar un endpoint que no existe, esta vista agrega un selector de ruta
// (ponytail: unico bit de UI que no es generico) y delega TODO lo demas
// (tabla, alta, edicion, baja logica, validacion, mapeo 422) a `CrudView`
// via el prop `listPath` (Fase 5, ver api/crud.ts `list(listPathOverride)`).
import { onMounted, ref } from 'vue'
import Select from 'primevue/select'
import CrudView from './CrudView.vue'
import { request } from '../api/client'
import { ApiError } from '../api/client'
import { routeStopsConfig } from '../resources'
import type { Route } from '../types'

const routes = ref<Route[]>([])
const loadingRoutes = ref(false)
const routesError = ref('')
const selectedRouteId = ref<number | null>(null)

onMounted(async () => {
  loadingRoutes.value = true
  try {
    const res = await request<{ items: Route[] }>('GET', '/admin/routes?page=1&page_size=100')
    routes.value = res.items
  } catch (err) {
    routesError.value = err instanceof ApiError ? err.message : 'No se pudieron cargar las rutas.'
  } finally {
    loadingRoutes.value = false
  }
})
</script>

<template>
  <section class="route-stops-view">
    <div class="route-picker">
      <label for="route-stops-route-select">Ruta</label>
      <Select
        id="route-stops-route-select"
        v-model="selectedRouteId"
        :options="routes"
        optionLabel="name"
        optionValue="id"
        placeholder="Elegí una ruta para ver sus paradas"
        :loading="loadingRoutes"
      />
    </div>

    <p v-if="routesError" role="alert" class="route-stops-error">{{ routesError }}</p>

    <CrudView
      v-if="selectedRouteId !== null"
      :key="selectedRouteId"
      :config="routeStopsConfig"
      :list-path="`/admin/routes/${selectedRouteId}/stops`"
    />
    <p v-else>Elegí una ruta arriba para ver y administrar sus paradas.</p>
  </section>
</template>

<style scoped>
.route-stops-view {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.route-picker {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  max-width: 24rem;
}
.route-picker label {
  font-weight: 600;
}
.route-stops-error {
  color: #b91c1c;
  margin: 0;
}
@media (prefers-color-scheme: dark) {
  .route-stops-error {
    color: #fca5a5;
  }
}
</style>
