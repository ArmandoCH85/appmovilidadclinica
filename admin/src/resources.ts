// Contrato de configuracion de recurso CRUD (Fase 4, tarea 4.2) + las 7
// configs reales de recursos (Fase 5, tarea 5.1/5.2), groundeadas 1:1 contra
// backend/internal/modules/admin/{handler,repository}.go — paths, columnas y
// campos (con sus validate tags) leidos directo del codigo real, no inventados.
export type CrudFieldType = 'text' | 'number' | 'boolean' | 'select' | 'textarea' | 'password' | 'date'

export interface CrudFieldOption {
  value: string
  label: string
}

/** Un campo del formulario de alta/edicion. `key` debe ser literal el json
 * tag que espera el backend en *CreateParams/*UpdateParams (ej. "stop_type",
 * "employee_code", ver backend/internal/modules/admin/repository.go) —
 * `CrudView` arma el body del POST/PUT directo desde estas claves, sin capa
 * de mapeo intermedia.
 *
 * `required: 'create'` (Fase 5, campo `password` de usuarios): el backend
 * exige el campo solo en el alta (`UserCreateParams.Password
 * validate:"required"`); en edicion es opcional — vacio significa "no
 * cambiar la contraseña" (`UserUpdateParams`, sin validate:"required", y
 * `service.go` UpdateUser no toca el hash si llega ""). Un `required:true`
 * uniforme forzaria reingresar la contraseña en cada edicion; `false`
 * perderia la validacion real en el alta. */
export interface CrudField {
  key: string
  label: string
  type: CrudFieldType
  required?: boolean | 'create'
  maxLength?: number
  /** Solo para type:'select' — refleja un `validate:"oneof=..."` del backend. */
  options?: CrudFieldOption[]
}

/** Una columna de la tabla. `key` referencia una propiedad del item listado. */
export interface CrudColumn {
  key: string
  label: string
}

export interface CrudResourceConfig {
  /** Path relativo bajo el API admin para alta/edicion, ej. "/admin/stops".
   * Tambien se usa para el listado salvo que el recurso tenga un endpoint de
   * lista distinto (ver `route-stops` mas abajo, y `listPath` en CrudView). */
  path: string
  labelSingular: string
  labelPlural: string
  columns: CrudColumn[]
  fields: CrudField[]
}

// ----------------------------------------------------------------------------
// Paradas (transport_stops) — StopCreateParams/StopUpdateParams (identicos)
// ----------------------------------------------------------------------------
export const stopsConfig: CrudResourceConfig = {
  path: '/admin/stops',
  labelSingular: 'parada',
  labelPlural: 'Paradas',
  columns: [
    { key: 'code', label: 'Código' },
    { key: 'name', label: 'Nombre' },
    { key: 'stop_type', label: 'Tipo' },
    { key: 'active', label: 'Activa' },
  ],
  fields: [
    { key: 'code', label: 'Código', type: 'text', required: true, maxLength: 30 },
    { key: 'name', label: 'Nombre', type: 'text', required: true, maxLength: 150 },
    {
      key: 'stop_type',
      label: 'Tipo',
      type: 'select',
      required: true,
      options: [
        { value: 'SEDE', label: 'Sede' },
        { value: 'PARADERO', label: 'Paradero' },
      ],
    },
    { key: 'reference_text', label: 'Referencia', type: 'textarea', maxLength: 255 },
    { key: 'latitude', label: 'Latitud', type: 'number' },
    { key: 'longitude', label: 'Longitud', type: 'number' },
    { key: 'active', label: 'Activa', type: 'boolean' },
  ],
}

// ----------------------------------------------------------------------------
// Usuarios (users) — UserCreateParams/UserUpdateParams
// ----------------------------------------------------------------------------
export const usersConfig: CrudResourceConfig = {
  path: '/admin/users',
  labelSingular: 'usuario',
  labelPlural: 'Usuarios',
  columns: [
    { key: 'employee_code', label: 'Legajo' },
    { key: 'full_name', label: 'Nombre completo' },
    { key: 'role', label: 'Rol' },
    { key: 'active', label: 'Activo' },
  ],
  fields: [
    { key: 'employee_code', label: 'Legajo', type: 'text', required: true, maxLength: 30 },
    { key: 'document_number', label: 'Número de documento', type: 'text', required: true, maxLength: 20 },
    // required:'create' — ver comentario en CrudField sobre este campo.
    { key: 'password', label: 'Contraseña', type: 'password', required: 'create' },
    { key: 'full_name', label: 'Nombre completo', type: 'text', required: true, maxLength: 150 },
    {
      key: 'role',
      label: 'Rol',
      type: 'select',
      required: true,
      options: [
        { value: 'ADMIN', label: 'Administrador' },
        { value: 'DRIVER', label: 'Conductor' },
        { value: 'WORKER', label: 'Trabajador' },
      ],
    },
    { key: 'department', label: 'Área', type: 'text', maxLength: 100 },
    { key: 'phone', label: 'Teléfono', type: 'text', maxLength: 25 },
    { key: 'preferred_stop_id', label: 'ID de parada preferida', type: 'number' },
    { key: 'active', label: 'Activo', type: 'boolean' },
  ],
}

// ----------------------------------------------------------------------------
// Vehiculos (vehicles) — VehicleCreateParams/VehicleUpdateParams (identicos)
// ----------------------------------------------------------------------------
export const vehiclesConfig: CrudResourceConfig = {
  path: '/admin/vehicles',
  labelSingular: 'vehículo',
  labelPlural: 'Vehículos',
  columns: [
    { key: 'internal_code', label: 'Código interno' },
    { key: 'plate', label: 'Patente' },
    { key: 'seat_capacity', label: 'Asientos' },
    { key: 'active', label: 'Activo' },
  ],
  fields: [
    { key: 'internal_code', label: 'Código interno', type: 'text', required: true, maxLength: 30 },
    { key: 'plate', label: 'Patente', type: 'text', required: true, maxLength: 15 },
    { key: 'description', label: 'Descripción', type: 'textarea', maxLength: 120 },
    { key: 'seat_capacity', label: 'Cantidad de asientos', type: 'number', required: true },
    { key: 'active', label: 'Activo', type: 'boolean' },
  ],
}

// ----------------------------------------------------------------------------
// Rutas (transport_routes) — RouteCreateParams/RouteUpdateParams (identicos)
// ----------------------------------------------------------------------------
export const routesConfig: CrudResourceConfig = {
  path: '/admin/routes',
  labelSingular: 'ruta',
  labelPlural: 'Rutas',
  columns: [
    { key: 'code', label: 'Código' },
    { key: 'name', label: 'Nombre' },
    { key: 'direction', label: 'Sentido' },
    { key: 'active', label: 'Activa' },
  ],
  fields: [
    { key: 'code', label: 'Código', type: 'text', required: true, maxLength: 40 },
    { key: 'name', label: 'Nombre', type: 'text', required: true, maxLength: 150 },
    {
      key: 'direction',
      label: 'Sentido',
      type: 'select',
      required: true,
      options: [
        { value: 'IDA', label: 'Ida' },
        { value: 'VUELTA', label: 'Vuelta' },
      ],
    },
    { key: 'paired_route_id', label: 'ID de ruta emparejada', type: 'number' },
    { key: 'active', label: 'Activa', type: 'boolean' },
  ],
}

// ----------------------------------------------------------------------------
// Paradas de ruta (route_stops) — RouteStopCreateParams/UpdateParams
// (identicos). SIN endpoint de listado plano: el backend solo registra
// GET /admin/routes/{id}/stops (anidado bajo una ruta), nunca
// GET /admin/route-stops (confirmado en handler.go RegisterRoutes — ese path
// solo tiene POST y PUT). `path` de abajo sirve para alta/edicion; el listado
// real lo resuelve `RouteStopsView.vue` con un selector de ruta + el prop
// `listPath` de CrudView apuntando a `/admin/routes/{id}/stops` (deviation
// documentada, ver apply-progress Fase 5).
// ----------------------------------------------------------------------------
export const routeStopsConfig: CrudResourceConfig = {
  path: '/admin/route-stops',
  labelSingular: 'parada de ruta',
  labelPlural: 'Paradas de ruta',
  columns: [
    { key: 'stop_id', label: 'ID de parada' },
    { key: 'stop_order', label: 'Orden' },
    { key: 'dwell_minutes', label: 'Minutos de espera' },
    { key: 'pickup_allowed', label: 'Permite subida' },
    { key: 'dropoff_allowed', label: 'Permite bajada' },
  ],
  fields: [
    { key: 'route_id', label: 'ID de ruta', type: 'number', required: true },
    { key: 'stop_id', label: 'ID de parada', type: 'number', required: true },
    { key: 'stop_order', label: 'Orden', type: 'number', required: true },
    { key: 'dwell_minutes', label: 'Minutos de espera', type: 'number' },
    { key: 'pickup_allowed', label: 'Permite subida', type: 'boolean' },
    { key: 'dropoff_allowed', label: 'Permite bajada', type: 'boolean' },
  ],
}

// ----------------------------------------------------------------------------
// Plantillas de viaje (trip_templates) — TemplateCreateParams
// (TemplateUpdateParams es el mismo tipo, ver repository.go:222)
// ----------------------------------------------------------------------------
export const templatesConfig: CrudResourceConfig = {
  path: '/admin/templates',
  labelSingular: 'plantilla',
  labelPlural: 'Plantillas de viaje',
  columns: [
    { key: 'code', label: 'Código' },
    { key: 'name', label: 'Nombre' },
    { key: 'route_id', label: 'ID de ruta' },
    { key: 'departure_time', label: 'Hora de salida' },
    { key: 'active', label: 'Activa' },
  ],
  fields: [
    { key: 'code', label: 'Código', type: 'text', required: true, maxLength: 50 },
    { key: 'name', label: 'Nombre', type: 'text', required: true, maxLength: 150 },
    { key: 'route_id', label: 'ID de ruta', type: 'number', required: true },
    { key: 'service_calendar_id', label: 'ID de calendario', type: 'number', required: true },
    { key: 'departure_time', label: 'Hora de salida (HH:MM:SS)', type: 'text', required: true },
    { key: 'default_vehicle_id', label: 'ID de vehículo por defecto', type: 'number', required: true },
    { key: 'default_driver_id', label: 'ID de conductor por defecto', type: 'number', required: true },
    {
      key: 'profile_reference_mode',
      label: 'Modo de referencia de perfil',
      type: 'select',
      required: true,
      options: [
        { value: 'TRIP_DEPARTURE', label: 'Salida del viaje' },
        { value: 'SEGMENT_DEPARTURE', label: 'Salida del segmento' },
      ],
    },
    { key: 'booking_open_days_before', label: 'Días de apertura de reserva', type: 'number' },
    { key: 'booking_close_minutes_before', label: 'Minutos de cierre de reserva', type: 'number' },
    { key: 'no_show_tolerance_minutes', label: 'Tolerancia de inasistencia (min)', type: 'number' },
    { key: 'automatic_publish', label: 'Publicación automática', type: 'boolean' },
    { key: 'active', label: 'Activa', type: 'boolean' },
  ],
}

// ----------------------------------------------------------------------------
// Calendarios de servicio (service_calendars) — CalendarCreateParams
// ----------------------------------------------------------------------------
export const calendarsConfig: CrudResourceConfig = {
  path: '/admin/calendars',
  labelSingular: 'calendario',
  labelPlural: 'Calendarios de servicio',
  columns: [
    { key: 'code', label: 'Código' },
    { key: 'name', label: 'Nombre' },
    { key: 'valid_from', label: 'Vigente desde' },
    { key: 'valid_until', label: 'Vigente hasta' },
    { key: 'active', label: 'Activo' },
  ],
  fields: [
    { key: 'code', label: 'Código', type: 'text', required: true, maxLength: 40 },
    { key: 'name', label: 'Nombre', type: 'text', required: true, maxLength: 120 },
    { key: 'valid_from', label: 'Vigente desde', type: 'date', required: true },
    { key: 'valid_until', label: 'Vigente hasta', type: 'date', required: true },
    { key: 'monday', label: 'Lunes', type: 'boolean' },
    { key: 'tuesday', label: 'Martes', type: 'boolean' },
    { key: 'wednesday', label: 'Miércoles', type: 'boolean' },
    { key: 'thursday', label: 'Jueves', type: 'boolean' },
    { key: 'friday', label: 'Viernes', type: 'boolean' },
    { key: 'saturday', label: 'Sábado', type: 'boolean' },
    { key: 'sunday', label: 'Domingo', type: 'boolean' },
    { key: 'active', label: 'Activo', type: 'boolean' },
  ],
}

// ----------------------------------------------------------------------------
// Segmentos de ruta (route_segments) — RouteSegmentCreateParams
// ----------------------------------------------------------------------------
export const routeSegmentsConfig: CrudResourceConfig = {
  path: '/admin/route-segments',
  labelSingular: 'segmento de ruta',
  labelPlural: 'Segmentos de ruta',
  columns: [
    { key: 'route_id', label: 'ID de ruta' },
    { key: 'segment_order', label: 'Orden' },
    { key: 'from_route_stop_id', label: 'Parada origen (route_stop_id)' },
    { key: 'to_route_stop_id', label: 'Parada destino (route_stop_id)' },
    { key: 'active', label: 'Activo' },
  ],
  fields: [
    { key: 'route_id', label: 'ID de ruta', type: 'number', required: true },
    { key: 'segment_order', label: 'Orden', type: 'number', required: true },
    { key: 'from_route_stop_id', label: 'Parada origen (route_stop_id)', type: 'number', required: true },
    { key: 'to_route_stop_id', label: 'Parada destino (route_stop_id)', type: 'number', required: true },
    { key: 'active', label: 'Activo', type: 'boolean' },
  ],
}

// ----------------------------------------------------------------------------
// Perfiles de tiempo de viaje (travel_time_profiles) — TravelTimeProfileCreateParams
// ----------------------------------------------------------------------------
export const travelProfilesConfig: CrudResourceConfig = {
  path: '/admin/travel-profiles',
  labelSingular: 'perfil de tiempo',
  labelPlural: 'Perfiles de tiempo de viaje',
  columns: [
    { key: 'code', label: 'Código' },
    { key: 'name', label: 'Nombre' },
    { key: 'priority', label: 'Prioridad' },
    { key: 'is_default', label: 'Por defecto' },
    { key: 'active', label: 'Activo' },
  ],
  fields: [
    { key: 'code', label: 'Código', type: 'text', required: true, maxLength: 40 },
    { key: 'name', label: 'Nombre', type: 'text', required: true, maxLength: 120 },
    { key: 'valid_from', label: 'Vigente desde', type: 'date' },
    { key: 'valid_until', label: 'Vigente hasta', type: 'date' },
    { key: 'start_time', label: 'Hora de inicio (HH:MM:SS)', type: 'text' },
    { key: 'end_time', label: 'Hora de fin (HH:MM:SS)', type: 'text' },
    { key: 'is_all_day', label: 'Todo el día', type: 'boolean' },
    { key: 'monday', label: 'Lunes', type: 'boolean' },
    { key: 'tuesday', label: 'Martes', type: 'boolean' },
    { key: 'wednesday', label: 'Miércoles', type: 'boolean' },
    { key: 'thursday', label: 'Jueves', type: 'boolean' },
    { key: 'friday', label: 'Viernes', type: 'boolean' },
    { key: 'saturday', label: 'Sábado', type: 'boolean' },
    { key: 'sunday', label: 'Domingo', type: 'boolean' },
    { key: 'priority', label: 'Prioridad', type: 'number' },
    { key: 'is_default', label: 'Por defecto', type: 'boolean' },
    { key: 'active', label: 'Activo', type: 'boolean' },
  ],
}

// ----------------------------------------------------------------------------
// Tiempos por segmento (route_segment_travel_times) — RouteSegmentTravelTimeCreateParams
// ----------------------------------------------------------------------------
export const segmentTimesConfig: CrudResourceConfig = {
  path: '/admin/segment-times',
  labelSingular: 'tiempo de segmento',
  labelPlural: 'Tiempos de segmento',
  columns: [
    { key: 'route_segment_id', label: 'ID de segmento' },
    { key: 'profile_id', label: 'ID de perfil' },
    { key: 'travel_minutes', label: 'Minutos de viaje' },
    { key: 'notes', label: 'Notas' },
  ],
  fields: [
    { key: 'route_segment_id', label: 'ID de segmento', type: 'number', required: true },
    { key: 'profile_id', label: 'ID de perfil', type: 'number', required: true },
    { key: 'travel_minutes', label: 'Minutos de viaje', type: 'number', required: true },
    { key: 'notes', label: 'Notas', type: 'textarea', maxLength: 255 },
  ],
}

export const vehicleSeatsConfig: CrudResourceConfig = {
  path: '/admin/vehicle-seats',
  labelSingular: 'asiento',
  labelPlural: 'Asientos de vehículos',
  columns: [
    { key: 'vehicle_id', label: 'ID de vehículo' },
    { key: 'seat_number', label: 'Número' },
    { key: 'seat_label', label: 'Etiqueta' },
    { key: 'status', label: 'Estado' },
  ],
  fields: [
    { key: 'vehicle_id', label: 'ID de vehículo', type: 'number', required: true },
    { key: 'seat_number', label: 'Número', type: 'number', required: true },
    { key: 'seat_label', label: 'Etiqueta', type: 'text', required: true, maxLength: 10 },
    {
      key: 'status',
      label: 'Estado',
      type: 'select',
      required: true,
      options: [
        { value: 'ACTIVE', label: 'Activo' },
        { value: 'BLOCKED', label: 'Bloqueado' },
        { value: 'RETIRED', label: 'Retirado' },
      ],
    },
    { key: 'block_reason', label: 'Motivo de bloqueo', type: 'textarea', maxLength: 255 },
  ],
}

export const calendarExceptionsConfig: CrudResourceConfig = {
  path: '/admin/calendar-exceptions',
  labelSingular: 'excepción',
  labelPlural: 'Excepciones de calendario',
  columns: [
    { key: 'calendar_id', label: 'ID de calendario' },
    { key: 'exception_date', label: 'Fecha' },
    { key: 'operation', label: 'Operación' },
    { key: 'reason', label: 'Motivo' },
  ],
  fields: [
    { key: 'calendar_id', label: 'ID de calendario', type: 'number', required: true },
    { key: 'exception_date', label: 'Fecha', type: 'date', required: true },
    {
      key: 'operation',
      label: 'Operación',
      type: 'select',
      required: true,
      options: [
        { value: 'ADD', label: 'Agregar' },
        { value: 'REMOVE', label: 'Remover' },
      ],
    },
    { key: 'reason', label: 'Motivo', type: 'textarea', maxLength: 255 },
  ],
}

export const tripsConfig: CrudResourceConfig = {
  path: '/admin/trips',
  labelSingular: 'viaje',
  labelPlural: 'Viajes',
  columns: [
    { key: 'trip_code', label: 'Código' },
    { key: 'service_date', label: 'Fecha' },
    { key: 'status', label: 'Estado' },
    { key: 'vehicle_id', label: 'ID de vehículo' },
    { key: 'driver_id', label: 'ID de conductor' },
  ],
  fields: [],
}

export const incidentsConfig: CrudResourceConfig = {
  path: '/admin/incidents',
  labelSingular: 'incidencia',
  labelPlural: 'Incidencias',
  columns: [
    { key: 'id', label: 'ID' },
    { key: 'trip_id', label: 'ID de viaje' },
    { key: 'incident_type', label: 'Tipo' },
    { key: 'status', label: 'Estado' },
    { key: 'reported_at', label: 'Reportado' },
  ],
  fields: [],
}

export const generationRunsConfig: CrudResourceConfig = {
  path: '/admin/generation-runs',
  labelSingular: 'corrida',
  labelPlural: 'Corridas de generación',
  columns: [
    { key: 'id', label: 'ID' },
    { key: 'window_start', label: 'Desde' },
    { key: 'window_end', label: 'Hasta' },
    { key: 'status', label: 'Estado' },
    { key: 'generated_count', label: 'Generados' },
    { key: 'skipped_count', label: 'Omitidos' },
    { key: 'failed_count', label: 'Fallidos' },
  ],
  fields: [],
}

/** Orden de grupos en el nav (AppLayout.vue). Agrupacion por dominio logico,
 * no por orden de implementacion — ver decision en memoria "admin nav
 * agrupado por dominio". */
export const NAV_GROUP_ORDER = [
  'Catálogos maestros',
  'Rutas',
  'Calendarios y tiempos',
  'Planificación',
  'Operación diaria',
  'Reportes',
] as const

export type NavGroup = (typeof NAV_GROUP_ORDER)[number]

export const crudResources: Array<{ routePath: string; navLabel: string; group: NavGroup; config: CrudResourceConfig; readOnly?: boolean }> = [
  { routePath: '/stops', navLabel: 'Paradas', group: 'Catálogos maestros', config: stopsConfig },
  { routePath: '/vehicles', navLabel: 'Vehículos', group: 'Catálogos maestros', config: vehiclesConfig },
  { routePath: '/vehicle-seats', navLabel: 'Asientos', group: 'Catálogos maestros', config: vehicleSeatsConfig },
  { routePath: '/users', navLabel: 'Usuarios', group: 'Catálogos maestros', config: usersConfig },
  { routePath: '/routes', navLabel: 'Rutas', group: 'Rutas', config: routesConfig },
  { routePath: '/route-segments', navLabel: 'Segmentos de ruta', group: 'Rutas', config: routeSegmentsConfig },
  { routePath: '/calendars', navLabel: 'Calendarios de servicio', group: 'Calendarios y tiempos', config: calendarsConfig },
  { routePath: '/calendar-exceptions', navLabel: 'Excepciones de calendario', group: 'Calendarios y tiempos', config: calendarExceptionsConfig },
  { routePath: '/travel-profiles', navLabel: 'Perfiles de tiempo', group: 'Calendarios y tiempos', config: travelProfilesConfig },
  { routePath: '/segment-times', navLabel: 'Tiempos de segmento', group: 'Calendarios y tiempos', config: segmentTimesConfig },
  { routePath: '/templates', navLabel: 'Plantillas de viaje', group: 'Planificación', config: templatesConfig },
  { routePath: '/generation-runs', navLabel: 'Corridas de generación', group: 'Planificación', config: generationRunsConfig, readOnly: true },
  { routePath: '/trips', navLabel: 'Viajes', group: 'Operación diaria', config: tripsConfig, readOnly: true },
  { routePath: '/incidents', navLabel: 'Incidencias', group: 'Operación diaria', config: incidentsConfig, readOnly: true },
]
