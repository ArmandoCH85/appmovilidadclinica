// Interfaces de los 7 recursos administrables, en espejo de las structs Go
// de `backend/internal/modules/admin/repository.go`. Solo las formas de
// lectura (List*): los *CreateParams/*UpdateParams se derivan en
// `resources.ts` (Fase 4) junto a la config de formulario por recurso.

export interface Stop {
  id: number
  code: string
  name: string
  stop_type: 'SEDE' | 'PARADERO'
  reference_text?: string | null
  latitude?: number | null
  longitude?: number | null
  active: boolean
}

export interface User {
  id: number
  employee_code: string
  document_number: string
  full_name: string
  role: 'ADMIN' | 'DRIVER' | 'WORKER'
  department?: string | null
  phone?: string | null
  preferred_stop_id?: number | null
  active: boolean
}

export interface Vehicle {
  id: number
  internal_code: string
  plate: string
  description?: string | null
  seat_capacity: number
  active: boolean
}

export interface Route {
  id: number
  code: string
  name: string
  direction: 'IDA' | 'VUELTA'
  paired_route_id?: number | null
  active: boolean
}

export interface RouteStop {
  id: number
  route_id: number
  stop_id: number
  stop_order: number
  dwell_minutes: number
  pickup_allowed: boolean
  dropoff_allowed: boolean
}

export interface Template {
  id: number
  code: string
  name: string
  route_id: number
  service_calendar_id: number
  departure_time: string
  default_vehicle_id: number
  default_driver_id: number
  profile_reference_mode: 'TRIP_DEPARTURE' | 'SEGMENT_DEPARTURE'
  booking_open_days_before: number
  booking_close_minutes_before: number
  no_show_tolerance_minutes: number
  automatic_publish: boolean
  active: boolean
}

export interface Calendar {
  id: number
  code: string
  name: string
  valid_from: string
  valid_until: string
  monday: boolean
  tuesday: boolean
  wednesday: boolean
  thursday: boolean
  friday: boolean
  saturday: boolean
  sunday: boolean
  active: boolean
  exception_count: number
  template_count: number
  created_at: string
  updated_at: string
}

export interface CalendarException {
  id: number
  calendar_id: number
  calendar_code?: string
  calendar_name?: string
  exception_date: string
  operation: 'ADD' | 'REMOVE'
  reason?: string | null
  created_at: string
  updated_at: string
}

export interface TravelTimeProfile {
  id: number
  code: string
  name: string
  valid_from?: string | null
  valid_until?: string | null
  start_time?: string | null
  end_time?: string | null
  is_all_day: boolean
  monday: boolean
  tuesday: boolean
  wednesday: boolean
  thursday: boolean
  friday: boolean
  saturday: boolean
  sunday: boolean
  priority: number
  is_default: boolean
  active: boolean
}

// Forma de respuesta paginada del backend (`{items, page, page_size, total}`),
// consumida por `useCrudResource<T>` en la Fase 4.
export interface PaginatedResponse<T> {
  items: T[]
  page: number
  page_size: number
  total: number
}

// trip_generation_runs — auditoria del motor. Append-only: la UI no la
// crea/edita; solo lista y hace drill-down a los trip_instances que produjo.
export interface GenerationRun {
  id: number
  window_start: string
  window_end: string
  status: 'RUNNING' | 'COMPLETED' | 'COMPLETED_WITH_ERRORS' | 'FAILED'
  generated_count: number
  skipped_count: number
  failed_count: number
  error_summary?: string | null
  triggered_by_user_id?: number | null
  triggered_by_full_name?: string | null
  trip_count: number
  duration_seconds?: number | null
  started_at: string
  finished_at?: string | null
}

// trip_instances — vista minima para el drill-down de GenerationRunsView.
// No se carga como recurso CRUD independiente (read-only en /trips, ver
// resources.ts `tripsConfig readOnly: true`); el backend devuelve los campos
// necesarios directamente desde GetGenerationRun.
export interface TripInstanceSummary {
  id: number
  trip_code: string
  source: 'GENERATED' | 'MANUAL'
  route_id: number
  service_date: string
  scheduled_start_at: string
  scheduled_end_at: string
  vehicle_id: number
  driver_id: number
  status: string
}

// trip_incidents — la crea el driver (endpoint /api/driver/trips/{id}/incidents),
// el admin solo lista y resuelve (PATCH /admin/incidents/{id}). El backend
// enriquece con trip_code, route, reporter; no todo viaje puede tener una
// ruta (FK trip_instances.route_id NOT NULL), por eso los *_trip_* vienen
// siempre poblados.
export interface TripIncident {
  id: number
  trip_id: number
  trip_code: string
  trip_service_date: string
  trip_route_code: string
  trip_route_name: string
  reported_by_user_id: number
  reported_by_full_name: string
  reported_by_employee_code: string
  incident_type: 'BREAKDOWN' | 'DELAY' | 'ACCIDENT' | 'OTHER'
  description: string
  status: 'OPEN' | 'IN_REVIEW' | 'RESOLVED'
  reported_at: string
  resolved_at?: string | null
  resolution_notes?: string | null
}

/** trip_instances completo tal como lo devuelve ListTrips (handler.go). */
export interface TripInstance {
  id: number
  trip_code: string
  source: 'GENERATED' | 'MANUAL'
  trip_template_id?: number | null
  generation_run_id?: number | null
  route_id: number
  service_date: string
  scheduled_start_at: string
  scheduled_end_at: string
  booking_opens_at: string
  booking_closes_at: string
  vehicle_id: number
  driver_id: number
  seat_capacity_snapshot: number
  no_show_tolerance_minutes: number
  status: 'DRAFT' | 'PUBLISHED' | 'BOARDING' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
  actual_start_at?: string | null
  actual_end_at?: string | null
  cancellation_reason?: string | null
}

// ----------------------------------------------------------------------------
// Reportes (Fase 6) — en espejo de Conflict/MatrixEntry/SeatAvail de
// `backend/internal/modules/admin/repository.go` (columnas SELECT exactas,
// no inventadas). Respuesta sin paginar: `{items: T[]}` (ver handler.go
// seccion "Reportes (vistas)").
// ----------------------------------------------------------------------------

/** vw_schedule_conflicts — GET /admin/reports/conflicts (sin filtros). */
export interface ScheduleConflict {
  resource_type: string
  resource_id: number
  first_trip_id: number
  second_trip_id: number
  first_start_at: string
  first_end_at: string
  second_start_at: string
  second_end_at: string
}

/** vw_route_time_matrix — GET /admin/reports/time-matrix (sin filtros). */
export interface RouteTimeMatrixEntry {
  route_id: number
  route_code: string
  route_name: string
  direction: 'IDA' | 'VUELTA'
  route_segment_id: number
  segment_order: number
  from_stop_code: string
  from_stop_name: string
  to_stop_code: string
  to_stop_name: string
  profile_id: number
  profile_code: string
  profile_name: string
  travel_minutes: number
  priority: number
}

/** vw_trip_segment_seat_availability — GET /admin/reports/seat-availability?trip_id=
 * (trip_id obligatorio — el backend responde 422 sin un entero positivo). */
export interface TripSeatAvailability {
  trip_id: number
  trip_code: string
  service_date: string
  direction: 'IDA' | 'VUELTA'
  trip_seat_id: number
  seat_number: number
  seat_label: string
  segment_order: number
  available_or_occupied_from: string
  available_or_occupied_until: string
  state: string
  reservation_id?: number | null
  reservation_code?: string | null
  reserved_at?: string | null
  released_at?: string | null
}
