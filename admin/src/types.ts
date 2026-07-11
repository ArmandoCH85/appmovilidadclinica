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
}

// Forma de respuesta paginada del backend (`{items, page, page_size, total}`),
// consumida por `useCrudResource<T>` en la Fase 4.
export interface PaginatedResponse<T> {
  items: T[]
  page: number
  page_size: number
  total: number
}
