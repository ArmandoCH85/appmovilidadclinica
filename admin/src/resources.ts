// Contrato de configuracion de recurso CRUD (Fase 4, tarea 4.2 — alcance
// acotado por el batch: SOLO el contrato tipado que consume `CrudView.vue`.
// Las 7 configs reales (stops/users/vehicles/routes/route-stops/templates/
// calendars) son trabajo de la Fase 5 ("Wiring por recurso") y NO se cargan
// aca todavia — este archivo define la forma, no los datos.
export type CrudFieldType = 'text' | 'number' | 'boolean' | 'select' | 'textarea'

export interface CrudFieldOption {
  value: string
  label: string
}

/** Un campo del formulario de alta/edicion. `key` debe ser literal el json
 * tag que espera el backend en *CreateParams/*UpdateParams (ej. "stop_type",
 * "employee_code", ver backend/internal/modules/admin/repository.go) —
 * `CrudView` arma el body del POST/PUT directo desde estas claves, sin capa
 * de mapeo intermedia. */
export interface CrudField {
  key: string
  label: string
  type: CrudFieldType
  required?: boolean
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
  /** Path relativo bajo el API admin, ej. "/admin/stops". */
  path: string
  labelSingular: string
  labelPlural: string
  columns: CrudColumn[]
  fields: CrudField[]
}
