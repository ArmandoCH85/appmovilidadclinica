<script setup lang="ts">
// Reportes — 11mo recurso del rollout visual (ver memoria
// "admin/crud-visual-redesign-pattern"). Las 3 vistas SQL son read-only
// por definicion (son SELECT sobre vw_*); NO hay formulario de alta ni
// de edicion — inventar uno iria contra el modelo. Lo que aporta valor:
//  - Tabs (TabView) para no apilar 3 secciones en una sola pantalla.
//  - Cada tab carga al activarse (lazy load) para no pedir 3 listados al
//    entrar a la pagina.
//  - Filtros por tab que matchean 1:1 columnas reales de la vista SQL
//    correspondiente. El backend rechaza valores invalidos con 422.
//  - DataTable con Tags de severidad para state (seat availability) y
//    resource_type (conflictos), formato de fechas amigable.
import { onMounted, reactive, ref, computed } from 'vue'
import Tabs from 'primevue/tabs'
import TabList from 'primevue/tablist'
import Tab from 'primevue/tab'
import TabPanels from 'primevue/tabpanels'
import TabPanel from 'primevue/tabpanel'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import DatePicker from 'primevue/datepicker'
import Tag from 'primevue/tag'
import { request, ApiError } from '../api/client'
import { LABELS } from '../messages'
import { exportRowsToExcel, timestampedFilename, type ExcelColumn } from '../utils/excel'
import type {
  ScheduleConflict,
  RouteTimeMatrixEntry,
  TripSeatAvailability,
  RouteOccupancyRow,
  TripStatusSummaryRow,
  DurationDeviationRow,
  DelayByRouteDayRow,
  ReservationChangeRow,
  TripIncidentReportRow,
  UserReservationActivityRow,
} from '../types'

// ---------------------------------------------------------------------------
// Helpers de formato
// ---------------------------------------------------------------------------

function formatCell(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  return String(value)
}

// ---------------------------------------------------------------------------
// Tab 1: Conflictos de horario (vw_schedule_conflicts)
// ---------------------------------------------------------------------------

const RESOURCE_TYPE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: '', label: 'Todos' },
  { value: 'VEHICLE', label: 'Vehículo' },
  { value: 'DRIVER', label: 'Conductor' },
]

const RESOURCE_TYPE_SEVERITIES: Record<string, 'warn' | 'danger'> = {
  VEHICLE: 'warn',
  DRIVER: 'danger',
}

const RESOURCE_TYPE_LABELS: Record<string, string> = {
  VEHICLE: 'Vehículo',
  DRIVER: 'Conductor',
}

const conflicts = ref<ScheduleConflict[]>([])
const conflictsLoading = ref(false)
const conflictsError = ref('')
const conflictsFilter = reactive<{ resourceType: string; dateFrom: Date | null; dateTo: Date | null }>({
  resourceType: '',
  dateFrom: null,
  dateTo: null,
})

function ymd(d: Date | null): string | undefined {
  if (!d) return undefined
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}

async function loadConflicts(): Promise<void> {
  conflictsLoading.value = true
  conflictsError.value = ''
  const params = new URLSearchParams()
  if (conflictsFilter.resourceType) params.set('resource_type', conflictsFilter.resourceType)
  const fromStr = ymd(conflictsFilter.dateFrom)
  if (fromStr) params.set('date_from', fromStr)
  const toStr = ymd(conflictsFilter.dateTo)
  if (toStr) params.set('date_to', toStr)
  const qs = params.toString()
  try {
    const res = await request<{ items: ScheduleConflict[] }>(
      'GET',
      `/admin/reports/conflicts${qs ? `?${qs}` : ''}`,
    )
    conflicts.value = res.items
  } catch (err) {
    conflictsError.value = err instanceof ApiError ? err.message : 'No se pudo cargar el reporte.'
    conflicts.value = []
  } finally {
    conflictsLoading.value = false
  }
}

function clearConflictsFilters(): void {
  conflictsFilter.resourceType = ''
  conflictsFilter.dateFrom = null
  conflictsFilter.dateTo = null
}

// --- Export a Excel del tab Conflictos ---
const conflictsExcelColumns: ExcelColumn[] = [
  { key: 'resource_type',     label: 'Tipo recurso',       width: 14 },
  { key: 'resource_id',       label: 'ID recurso',         width: 10 },
  { key: 'first_trip_id',     label: 'Viaje 1',            width: 10 },
  { key: 'second_trip_id',    label: 'Viaje 2',            width: 10 },
  { key: 'first_start_at',    label: 'Inicio viaje 1',     format: 'datetime', width: 20 },
  { key: 'first_end_at',      label: 'Fin viaje 1',        format: 'datetime', width: 20 },
  { key: 'second_start_at',   label: 'Inicio viaje 2',     format: 'datetime', width: 20 },
  { key: 'second_end_at',     label: 'Fin viaje 2',        format: 'datetime', width: 20 },
]

function exportConflicts(): void {
  const parts: string[] = []
  if (conflictsFilter.resourceType) parts.push(`Recurso=${conflictsFilter.resourceType}`)
  if (conflictsFilter.dateFrom || conflictsFilter.dateTo) {
    parts.push(`Rango=${ymd(conflictsFilter.dateFrom) ?? '*'} a ${ymd(conflictsFilter.dateTo) ?? '*'}`)
  }
  exportRowsToExcel(conflicts.value, conflictsExcelColumns, {
    filename: timestampedFilename('reporte_conflictos_horario'),
    sheetName: 'Conflictos de horario',
    title: 'Reporte #1: Conflictos de horario (vehiculo o conductor)',
    filterDescription: parts.join(', ') || undefined,
  })
}

const hasConflictsFilters = computed(
  () => Boolean(conflictsFilter.resourceType || conflictsFilter.dateFrom || conflictsFilter.dateTo),
)

// ---------------------------------------------------------------------------
// Tab 2: Matriz de tiempos (vw_route_time_matrix)
// ---------------------------------------------------------------------------

const DIRECTION_OPTIONS: Array<{ value: string; label: string }> = [
  { value: '', label: 'Todos' },
  { value: 'IDA', label: 'Ida' },
  { value: 'VUELTA', label: 'Vuelta' },
]

const matrix = ref<RouteTimeMatrixEntry[]>([])
const matrixLoading = ref(false)
const matrixError = ref('')
const matrixFilter = reactive<{ routeID: number | null; direction: string; profileID: number | null }>({
  routeID: null,
  direction: '',
  profileID: null,
})

async function loadMatrix(): Promise<void> {
  matrixLoading.value = true
  matrixError.value = ''
  const params = new URLSearchParams()
  if (matrixFilter.routeID && matrixFilter.routeID > 0) params.set('route_id', String(matrixFilter.routeID))
  if (matrixFilter.direction) params.set('direction', matrixFilter.direction)
  if (matrixFilter.profileID && matrixFilter.profileID > 0) params.set('profile_id', String(matrixFilter.profileID))
  const qs = params.toString()
  try {
    const res = await request<{ items: RouteTimeMatrixEntry[] }>(
      'GET',
      `/admin/reports/time-matrix${qs ? `?${qs}` : ''}`,
    )
    matrix.value = res.items
  } catch (err) {
    matrixError.value = err instanceof ApiError ? err.message : 'No se pudo cargar el reporte.'
    matrix.value = []
  } finally {
    matrixLoading.value = false
  }
}

function clearMatrixFilters(): void {
  matrixFilter.routeID = null
  matrixFilter.direction = ''
  matrixFilter.profileID = null
}

// --- Export a Excel del tab Matriz de tiempos ---
const matrixExcelColumns: ExcelColumn[] = [
  { key: 'route_code',     label: 'Código ruta',    width: 12 },
  { key: 'route_name',     label: 'Nombre ruta',    width: 28 },
  { key: 'direction',      label: 'Sentido',        width: 8 },
  { key: 'segment_order',  label: 'Segmento #',     width: 10 },
  { key: 'from_stop_code', label: 'Desde (código)', width: 14 },
  { key: 'from_stop_name', label: 'Desde (nombre)', width: 24 },
  { key: 'to_stop_code',   label: 'Hasta (código)', width: 14 },
  { key: 'to_stop_name',   label: 'Hasta (nombre)', width: 24 },
  { key: 'profile_code',   label: 'Perfil código',  width: 14 },
  { key: 'profile_name',   label: 'Perfil nombre',  width: 22 },
  { key: 'travel_minutes', label: 'Minutos',        format: 'number', width: 10 },
  { key: 'priority',       label: 'Prioridad',      format: 'number', width: 10 },
]

function exportMatrix(): void {
  const parts: string[] = []
  if (matrixFilter.routeID && matrixFilter.routeID > 0) parts.push(`Ruta ID=${matrixFilter.routeID}`)
  if (matrixFilter.direction) parts.push(`Sentido=${matrixFilter.direction}`)
  if (matrixFilter.profileID && matrixFilter.profileID > 0) parts.push(`Perfil ID=${matrixFilter.profileID}`)
  exportRowsToExcel(matrix.value, matrixExcelColumns, {
    filename: timestampedFilename('reporte_matriz_tiempos'),
    sheetName: 'Matriz de tiempos',
    title: 'Reporte #2: Matriz de tiempos por ruta',
    filterDescription: parts.join(', ') || undefined,
  })
}

const hasMatrixFilters = computed(
  () =>
    Boolean(
      (matrixFilter.routeID && matrixFilter.routeID > 0) ||
        matrixFilter.direction ||
        (matrixFilter.profileID && matrixFilter.profileID > 0),
    ),
)

// ---------------------------------------------------------------------------
// Tab 3: Disponibilidad de asientos (vw_trip_segment_seat_availability)
// ---------------------------------------------------------------------------

const SEAT_STATE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: '', label: 'Todos' },
  { value: 'AVAILABLE', label: 'Disponible' },
  { value: 'OCCUPIED_IN_REQUESTED_RANGE', label: 'Ocupado (rango solicitado)' },
  { value: 'BLOCKED', label: 'Bloqueado' },
]

const EXTENDED_OPTIONS: Array<{ value: string; label: string }> = [
  { value: '', label: 'Todas' },
  { value: 'true', label: 'Solo extendidas' },
  { value: 'false', label: 'Solo no extendidas' },
]

const SEAT_STATE_SEVERITIES: Record<string, 'success' | 'danger' | 'warn' | 'info'> = {
  AVAILABLE: 'success',
  OCCUPIED_IN_REQUESTED_RANGE: 'danger',
  BLOCKED: 'warn',
}

const SEAT_STATE_LABELS: Record<string, string> = {
  AVAILABLE: 'Disponible',
  OCCUPIED_IN_REQUESTED_RANGE: 'Ocupado',
  BLOCKED: 'Bloqueado',
}

const seats = ref<TripSeatAvailability[]>([])
const seatsLoading = ref(false)
const seatsError = ref('')
const seatsFieldError = ref('')
const seatsSearched = ref(false)
const seatFilter = reactive<{ tripId: number | null; state: string; extended: string }>({
  tripId: null,
  state: '',
  extended: '',
})

async function searchSeats(): Promise<void> {
  seatsFieldError.value = ''
  if (!seatFilter.tripId || seatFilter.tripId < 1) {
    seatsFieldError.value = LABELS.requiredField
    return
  }
  seatsLoading.value = true
  seatsError.value = ''
  seatsSearched.value = true
  const params = new URLSearchParams({ trip_id: String(seatFilter.tripId) })
  if (seatFilter.state) params.set('state', seatFilter.state)
  if (seatFilter.extended) params.set('extended', seatFilter.extended)
  try {
    const res = await request<{ items: TripSeatAvailability[] }>(
      'GET',
      `/admin/reports/seat-availability?${params.toString()}`,
    )
    seats.value = res.items
  } catch (err) {
    seatsError.value = err instanceof ApiError ? err.message : 'No se pudo cargar el reporte.'
    seats.value = []
  } finally {
    seatsLoading.value = false
  }
}

// --- Export a Excel del tab Disponibilidad de asientos ---
const seatsExcelColumns: ExcelColumn[] = [
  { key: 'trip_code',                     label: 'Código viaje',         width: 14 },
  { key: 'service_date',                  label: 'Fecha',                format: 'date', width: 12 },
  { key: 'direction',                     label: 'Sentido',              width: 8 },
  { key: 'seat_label',                    label: 'Asiento',              width: 8 },
  { key: 'segment_order',                 label: 'Segmento #',           format: 'number', width: 10 },
  { key: 'available_or_occupied_from',    label: 'Desde',                width: 12 },
  { key: 'available_or_occupied_until',   label: 'Hasta',                width: 12 },
  { key: 'state',                         label: 'Estado',               width: 12 },
  { key: 'reservation_code',              label: 'Código de reserva',    width: 18 },
  { key: 'reserved_at',                   label: 'Reservado en',         format: 'datetime', width: 20 },
  { key: 'released_at',                   label: 'Liberado en',          format: 'datetime', width: 20 },
]

function exportSeats(): void {
  const parts: string[] = []
  if (seatFilter.tripId) parts.push(`Viaje ID=${seatFilter.tripId}`)
  if (seatFilter.state) parts.push(`Estado=${seatFilter.state}`)
  exportRowsToExcel(seats.value, seatsExcelColumns, {
    filename: timestampedFilename('reporte_disponibilidad_asientos'),
    sheetName: 'Disponibilidad asientos',
    title: 'Reporte: Disponibilidad de asientos por viaje',
    filterDescription: parts.join(', ') || undefined,
  })
}

// ---------------------------------------------------------------------------
// Tab 4: Ocupación por ruta (#5, vw_route_occupancy)
// ---------------------------------------------------------------------------

const occupancy = ref<RouteOccupancyRow[]>([])
const occupancyLoading = ref(false)
const occupancyError = ref('')
const occupancyFilter = reactive<{ routeID: number | null; dateFrom: Date | null; dateTo: Date | null }>({
  routeID: null,
  dateFrom: null,
  dateTo: null,
})

async function loadOccupancy(): Promise<void> {
  occupancyLoading.value = true
  occupancyError.value = ''
  const params = new URLSearchParams()
  if (occupancyFilter.routeID && occupancyFilter.routeID > 0) params.set('route_id', String(occupancyFilter.routeID))
  const fromStr = ymd(occupancyFilter.dateFrom)
  if (fromStr) params.set('date_from', fromStr)
  const toStr = ymd(occupancyFilter.dateTo)
  if (toStr) params.set('date_to', toStr)
  const qs = params.toString()
  try {
    const res = await request<{ items: RouteOccupancyRow[] }>(
      'GET',
      `/admin/reports/occupancy-by-route${qs ? `?${qs}` : ''}`,
    )
    occupancy.value = res.items
  } catch (err) {
    occupancyError.value = err instanceof ApiError ? err.message : 'No se pudo cargar el reporte.'
    occupancy.value = []
  } finally {
    occupancyLoading.value = false
  }
}

function clearOccupancyFilters(): void {
  occupancyFilter.routeID = null
  occupancyFilter.dateFrom = null
  occupancyFilter.dateTo = null
}

// --- Export a Excel del tab Ocupacion por ruta ---
const occupancyExcelColumns: ExcelColumn[] = [
  { key: 'route_code',     label: 'Código ruta',     width: 14 },
  { key: 'route_name',     label: 'Nombre ruta',     width: 28 },
  { key: 'direction',      label: 'Sentido',         width: 8 },
  { key: 'service_date',   label: 'Fecha servicio',  format: 'date', width: 14 },
  { key: 'trip_count',     label: 'Viajes',          format: 'number', width: 8 },
  { key: 'seats_offered',  label: 'Asientos oferta.', format: 'number', width: 14 },
  { key: 'seats_reserved', label: 'Asientos reserva.', format: 'number', width: 14 },
  { key: 'occupancy_pct',  label: 'Ocupación %',    format: 'percent', width: 12 },
]

function exportOccupancy(): void {
  const parts: string[] = []
  if (occupancyFilter.routeID && occupancyFilter.routeID > 0) parts.push(`Ruta ID=${occupancyFilter.routeID}`)
  if (occupancyFilter.dateFrom || occupancyFilter.dateTo) {
    parts.push(`Rango=${ymd(occupancyFilter.dateFrom) ?? '*'} a ${ymd(occupancyFilter.dateTo) ?? '*'}`)
  }
  exportRowsToExcel(occupancy.value, occupancyExcelColumns, {
    filename: timestampedFilename('reporte_ocupacion_ruta'),
    sheetName: 'Ocupación por ruta',
    title: 'Reporte #3: Ocupación por ruta',
    filterDescription: parts.join(', ') || undefined,
  })
}

const hasOccupancyFilters = computed(
  () =>
    Boolean(
      (occupancyFilter.routeID && occupancyFilter.routeID > 0) ||
        occupancyFilter.dateFrom ||
        occupancyFilter.dateTo,
    ),
)

// ---------------------------------------------------------------------------
// Tab 5: Resumen de status de viajes (#11, vw_trips_status_summary)
// ---------------------------------------------------------------------------

const TRIP_STATUS_OPTIONS: Array<{ value: string; label: string }> = [
  { value: '', label: 'Todos' },
  { value: 'DRAFT', label: 'Borrador' },
  { value: 'PUBLISHED', label: 'Publicado' },
  { value: 'BOARDING', label: 'Embarcando' },
  { value: 'IN_PROGRESS', label: 'En curso' },
  { value: 'COMPLETED', label: 'Completado' },
  { value: 'CANCELLED', label: 'Cancelado' },
]

const TRIP_STATUS_SEVERITIES: Record<string, 'success' | 'danger' | 'info' | 'warn' | 'secondary'> = {
  DRAFT: 'secondary',
  PUBLISHED: 'info',
  BOARDING: 'warn',
  IN_PROGRESS: 'warn',
  COMPLETED: 'success',
  CANCELLED: 'danger',
}

const TRIP_STATUS_LABELS: Record<string, string> = {
  DRAFT: 'Borrador',
  PUBLISHED: 'Publicado',
  BOARDING: 'Embarcando',
  IN_PROGRESS: 'En curso',
  COMPLETED: 'Completado',
  CANCELLED: 'Cancelado',
}

const tripsStatus = ref<TripStatusSummaryRow[]>([])
const tripsStatusLoading = ref(false)
const tripsStatusError = ref('')
const tripsStatusFilter = reactive<{ dateFrom: Date | null; dateTo: Date | null; status: string }>({
  dateFrom: null,
  dateTo: null,
  status: '',
})

async function loadTripsStatus(): Promise<void> {
  tripsStatusLoading.value = true
  tripsStatusError.value = ''
  const params = new URLSearchParams()
  const fromStr = ymd(tripsStatusFilter.dateFrom)
  if (fromStr) params.set('date_from', fromStr)
  const toStr = ymd(tripsStatusFilter.dateTo)
  if (toStr) params.set('date_to', toStr)
  if (tripsStatusFilter.status) params.set('status', tripsStatusFilter.status)
  const qs = params.toString()
  try {
    const res = await request<{ items: TripStatusSummaryRow[] }>(
      'GET',
      `/admin/reports/trips-status-summary${qs ? `?${qs}` : ''}`,
    )
    tripsStatus.value = res.items
  } catch (err) {
    tripsStatusError.value = err instanceof ApiError ? err.message : 'No se pudo cargar el reporte.'
    tripsStatus.value = []
  } finally {
    tripsStatusLoading.value = false
  }
}

function clearTripsStatusFilters(): void {
  tripsStatusFilter.dateFrom = null
  tripsStatusFilter.dateTo = null
  tripsStatusFilter.status = ''
}

// --- Export a Excel del tab Status de viajes ---
const tripsStatusExcelColumns: ExcelColumn[] = [
  { key: 'service_date', label: 'Fecha',       format: 'date', width: 12 },
  { key: 'route_code',   label: 'Código ruta', width: 14 },
  { key: 'route_name',   label: 'Nombre ruta', width: 28 },
  { key: 'direction',    label: 'Sentido',     width: 8 },
  { key: 'status',       label: 'Status',      width: 14 },
  { key: 'trip_count',   label: 'Cantidad',    format: 'number', width: 10 },
]

function exportTripsStatus(): void {
  const parts: string[] = []
  if (tripsStatusFilter.status) parts.push(`Status=${tripsStatusFilter.status}`)
  if (tripsStatusFilter.dateFrom || tripsStatusFilter.dateTo) {
    parts.push(`Rango=${ymd(tripsStatusFilter.dateFrom) ?? '*'} a ${ymd(tripsStatusFilter.dateTo) ?? '*'}`)
  }
  exportRowsToExcel(tripsStatus.value, tripsStatusExcelColumns, {
    filename: timestampedFilename('reporte_status_viajes'),
    sheetName: 'Status de viajes',
    title: 'Reporte #4: Resumen de status de viajes',
    filterDescription: parts.join(', ') || undefined,
  })
}

const hasTripsStatusFilters = computed(
  () => Boolean(tripsStatusFilter.dateFrom || tripsStatusFilter.dateTo || tripsStatusFilter.status),
)

// ---------------------------------------------------------------------------
// Tab 6: Duración real vs estimada (#12, vw_duration_deviation)
// ---------------------------------------------------------------------------

const duration = ref<DurationDeviationRow[]>([])
const durationLoading = ref(false)
const durationError = ref('')
const durationFilter = reactive<{ routeID: number | null; dateFrom: Date | null; dateTo: Date | null }>({
  routeID: null,
  dateFrom: null,
  dateTo: null,
})

async function loadDuration(): Promise<void> {
  durationLoading.value = true
  durationError.value = ''
  const params = new URLSearchParams()
  if (durationFilter.routeID && durationFilter.routeID > 0) params.set('route_id', String(durationFilter.routeID))
  const fromStr = ymd(durationFilter.dateFrom)
  if (fromStr) params.set('date_from', fromStr)
  const toStr = ymd(durationFilter.dateTo)
  if (toStr) params.set('date_to', toStr)
  const qs = params.toString()
  try {
    const res = await request<{ items: DurationDeviationRow[] }>(
      'GET',
      `/admin/reports/duration-deviation${qs ? `?${qs}` : ''}`,
    )
    duration.value = res.items
  } catch (err) {
    durationError.value = err instanceof ApiError ? err.message : 'No se pudo cargar el reporte.'
    duration.value = []
  } finally {
    durationLoading.value = false
  }
}

function clearDurationFilters(): void {
  durationFilter.routeID = null
  durationFilter.dateFrom = null
  durationFilter.dateTo = null
}

// --- Export a Excel del tab Duracion real vs estimada ---
const durationExcelColumns: ExcelColumn[] = [
  { key: 'service_date',              label: 'Fecha',             format: 'date', width: 12 },
  { key: 'route_code',                label: 'Código ruta',       width: 14 },
  { key: 'route_name',                label: 'Nombre ruta',       width: 28 },
  { key: 'direction',                 label: 'Sentido',           width: 8 },
  { key: 'trip_code',                 label: 'Código viaje',      width: 14 },
  { key: 'scheduled_duration_minutes', label: 'Prog. (min)',       format: 'number', width: 12 },
  { key: 'actual_duration_minutes',    label: 'Real (min)',        format: 'number', width: 12 },
  { key: 'delta_minutes',             label: 'Delta (min)',       format: 'number', width: 12 },
  { key: 'delta_pct',                 label: 'Delta %',           format: 'percent', width: 10 },
]

function exportDuration(): void {
  const parts: string[] = []
  if (durationFilter.routeID && durationFilter.routeID > 0) parts.push(`Ruta ID=${durationFilter.routeID}`)
  if (durationFilter.dateFrom || durationFilter.dateTo) {
    parts.push(`Rango=${ymd(durationFilter.dateFrom) ?? '*'} a ${ymd(durationFilter.dateTo) ?? '*'}`)
  }
  exportRowsToExcel(duration.value, durationExcelColumns, {
    filename: timestampedFilename('reporte_duracion_real_vs_programada'),
    sheetName: 'Duración real vs programada',
    title: 'Reporte #5: Duración real vs estimada',
    filterDescription: parts.join(', ') || undefined,
  })
}

const hasDurationFilters = computed(
  () =>
    Boolean(
      (durationFilter.routeID && durationFilter.routeID > 0) ||
        durationFilter.dateFrom ||
        durationFilter.dateTo,
    ),
)

// ---------------------------------------------------------------------------
// Tab 7: Retrasos por ruta/día (#13, vw_delays_by_route_day)
// ---------------------------------------------------------------------------

const delays = ref<DelayByRouteDayRow[]>([])
const delaysLoading = ref(false)
const delaysError = ref('')
const delaysFilter = reactive<{
  routeID: number | null
  direction: string
  dateFrom: Date | null
  dateTo: Date | null
}>({
  routeID: null,
  direction: '',
  dateFrom: null,
  dateTo: null,
})

async function loadDelays(): Promise<void> {
  delaysLoading.value = true
  delaysError.value = ''
  const params = new URLSearchParams()
  if (delaysFilter.routeID && delaysFilter.routeID > 0) params.set('route_id', String(delaysFilter.routeID))
  if (delaysFilter.direction) params.set('direction', delaysFilter.direction)
  const fromStr = ymd(delaysFilter.dateFrom)
  if (fromStr) params.set('date_from', fromStr)
  const toStr = ymd(delaysFilter.dateTo)
  if (toStr) params.set('date_to', toStr)
  const qs = params.toString()
  try {
    const res = await request<{ items: DelayByRouteDayRow[] }>(
      'GET',
      `/admin/reports/delays-by-route-day${qs ? `?${qs}` : ''}`,
    )
    delays.value = res.items
  } catch (err) {
    delaysError.value = err instanceof ApiError ? err.message : 'No se pudo cargar el reporte.'
    delays.value = []
  } finally {
    delaysLoading.value = false
  }
}

function clearDelaysFilters(): void {
  delaysFilter.routeID = null
  delaysFilter.direction = ''
  delaysFilter.dateFrom = null
  delaysFilter.dateTo = null
}

// --- Export a Excel del tab Retrasos por ruta/dia ---
const delaysExcelColumns: ExcelColumn[] = [
  { key: 'service_date',       label: 'Fecha',            format: 'date', width: 12 },
  { key: 'route_code',         label: 'Código ruta',      width: 14 },
  { key: 'route_name',         label: 'Nombre ruta',      width: 28 },
  { key: 'direction',          label: 'Sentido',          width: 8 },
  { key: 'trip_count',         label: 'Viajes',           format: 'number', width: 8 },
  { key: 'avg_delay_minutes',  label: 'Retraso prom. (min)', format: 'number', width: 18 },
  { key: 'max_delay_minutes',  label: 'Retraso máx. (min)',  format: 'number', width: 18 },
  { key: 'late_trip_count',    label: 'Viajes tarde',     format: 'number', width: 14 },
  { key: 'on_time_trip_count', label: 'Viajes puntuales', format: 'number', width: 14 },
]

function exportDelays(): void {
  const parts: string[] = []
  if (delaysFilter.routeID && delaysFilter.routeID > 0) parts.push(`Ruta ID=${delaysFilter.routeID}`)
  if (delaysFilter.direction) parts.push(`Sentido=${delaysFilter.direction}`)
  if (delaysFilter.dateFrom || delaysFilter.dateTo) {
    parts.push(`Rango=${ymd(delaysFilter.dateFrom) ?? '*'} a ${ymd(delaysFilter.dateTo) ?? '*'}`)
  }
  exportRowsToExcel(delays.value, delaysExcelColumns, {
    filename: timestampedFilename('reporte_retrasos_ruta_dia'),
    sheetName: 'Retrasos por ruta/día',
    title: 'Reporte #6: Retrasos por ruta/día',
    filterDescription: parts.join(', ') || undefined,
  })
}

const hasDelaysFilters = computed(
  () =>
    Boolean(
      (delaysFilter.routeID && delaysFilter.routeID > 0) ||
        delaysFilter.direction ||
        delaysFilter.dateFrom ||
        delaysFilter.dateTo,
    ),
)

// ---------------------------------------------------------------------------
// Tab 8: Cambios en reservas (#26, vw_reservation_changes)
// ---------------------------------------------------------------------------

const RES_EVENT_OPTIONS: Array<{ value: string; label: string }> = [
  { value: '', label: 'Todos' },
  { value: 'CONFIRMED', label: 'Confirmada' },
  { value: 'BOARDED', label: 'Abordó' },
  { value: 'ALIGHTED', label: 'Bajó' },
  { value: 'NO_SHOW', label: 'No show' },
  { value: 'SEGMENTS_RELEASED', label: 'Segmentos liberados' },
  { value: 'CANCELLED', label: 'Cancelada' },
]

const changes = ref<ReservationChangeRow[]>([])
const changesLoading = ref(false)
const changesError = ref('')
const changesFilter = reactive<{
  reservationID: number | null
  eventType: string
  dateFrom: Date | null
  dateTo: Date | null
}>({
  reservationID: null,
  eventType: '',
  dateFrom: null,
  dateTo: null,
})

async function loadChanges(): Promise<void> {
  changesLoading.value = true
  changesError.value = ''
  const params = new URLSearchParams()
  if (changesFilter.reservationID && changesFilter.reservationID > 0) {
    params.set('reservation_id', String(changesFilter.reservationID))
  }
  if (changesFilter.eventType) params.set('event_type', changesFilter.eventType)
  const fromStr = ymd(changesFilter.dateFrom)
  if (fromStr) params.set('date_from', fromStr)
  const toStr = ymd(changesFilter.dateTo)
  if (toStr) params.set('date_to', toStr)
  const qs = params.toString()
  try {
    const res = await request<{ items: ReservationChangeRow[] }>(
      'GET',
      `/admin/reports/reservation-changes${qs ? `?${qs}` : ''}`,
    )
    changes.value = res.items
  } catch (err) {
    changesError.value = err instanceof ApiError ? err.message : 'No se pudo cargar el reporte.'
    changes.value = []
  } finally {
    changesLoading.value = false
  }
}

function clearChangesFilters(): void {
  changesFilter.reservationID = null
  changesFilter.eventType = ''
  changesFilter.dateFrom = null
  changesFilter.dateTo = null
}

// --- Export a Excel del tab Cambios en reservas ---
const changesExcelColumns: ExcelColumn[] = [
  { key: 'event_at',          label: 'Fecha evento',     format: 'datetime', width: 20 },
  { key: 'reservation_code',  label: 'Código reserva',   width: 18 },
  { key: 'event_type',        label: 'Tipo evento',      width: 18 },
  { key: 'worker_name',       label: 'Trabajador',       width: 24 },
  { key: 'actor_name',        label: 'Actor',            width: 24 },
  { key: 'trip_code',         label: 'Código viaje',     width: 14 },
  { key: 'service_date',      label: 'Fecha servicio',   format: 'date', width: 14 },
  { key: 'route_code',        label: 'Código ruta',      width: 14 },
  { key: 'details',           label: 'Detalles',         width: 36 },
]

function exportChanges(): void {
  const parts: string[] = []
  if (changesFilter.reservationID && changesFilter.reservationID > 0) parts.push(`Reserva ID=${changesFilter.reservationID}`)
  if (changesFilter.eventType) parts.push(`Tipo=${changesFilter.eventType}`)
  if (changesFilter.dateFrom || changesFilter.dateTo) {
    parts.push(`Rango=${ymd(changesFilter.dateFrom) ?? '*'} a ${ymd(changesFilter.dateTo) ?? '*'}`)
  }
  exportRowsToExcel(changes.value, changesExcelColumns, {
    filename: timestampedFilename('reporte_cambios_reservas'),
    sheetName: 'Cambios en reservas',
    title: 'Reporte #7: Historial de cambios en reservas',
    filterDescription: parts.join(', ') || undefined,
  })
}

const hasChangesFilters = computed(
  () =>
    Boolean(
      (changesFilter.reservationID && changesFilter.reservationID > 0) ||
        changesFilter.eventType ||
        changesFilter.dateFrom ||
        changesFilter.dateTo,
    ),
)

// ---------------------------------------------------------------------------
// Tab 9: Tickets / quejas (#27, vw_trip_incidents)
// ---------------------------------------------------------------------------

const INCIDENT_TYPE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: '', label: 'Todos' },
  { value: 'BREAKDOWN', label: 'Avería' },
  { value: 'DELAY', label: 'Retraso' },
  { value: 'ACCIDENT', label: 'Accidente' },
  { value: 'OTHER', label: 'Otro' },
]

const INCIDENT_STATUS_OPTIONS: Array<{ value: string; label: string }> = [
  { value: '', label: 'Todos' },
  { value: 'OPEN', label: 'Abierto' },
  { value: 'IN_REVIEW', label: 'En revisión' },
  { value: 'RESOLVED', label: 'Resuelto' },
]

const INCIDENT_STATUS_SEVERITIES: Record<string, 'danger' | 'warn' | 'success'> = {
  OPEN: 'danger',
  IN_REVIEW: 'warn',
  RESOLVED: 'success',
}

const INCIDENT_TYPE_SEVERITIES: Record<string, 'danger' | 'warn' | 'info'> = {
  BREAKDOWN: 'warn',
  DELAY: 'warn',
  ACCIDENT: 'danger',
  OTHER: 'info',
}

const INCIDENT_TYPE_LABELS: Record<string, string> = {
  BREAKDOWN: 'Avería',
  DELAY: 'Retraso',
  ACCIDENT: 'Accidente',
  OTHER: 'Otro',
}

const INCIDENT_STATUS_LABELS: Record<string, string> = {
  OPEN: 'Abierto',
  IN_REVIEW: 'En revisión',
  RESOLVED: 'Resuelto',
}

const incidents = ref<TripIncidentReportRow[]>([])
const incidentsLoading = ref(false)
const incidentsError = ref('')
const incidentsFilter = reactive<{
  routeID: number | null
  incidentType: string
  status: string
  dateFrom: Date | null
  dateTo: Date | null
}>({
  routeID: null,
  incidentType: '',
  status: '',
  dateFrom: null,
  dateTo: null,
})

async function loadIncidents(): Promise<void> {
  incidentsLoading.value = true
  incidentsError.value = ''
  const params = new URLSearchParams()
  if (incidentsFilter.routeID && incidentsFilter.routeID > 0) params.set('route_id', String(incidentsFilter.routeID))
  if (incidentsFilter.incidentType) params.set('incident_type', incidentsFilter.incidentType)
  if (incidentsFilter.status) params.set('status', incidentsFilter.status)
  const fromStr = ymd(incidentsFilter.dateFrom)
  if (fromStr) params.set('date_from', fromStr)
  const toStr = ymd(incidentsFilter.dateTo)
  if (toStr) params.set('date_to', toStr)
  const qs = params.toString()
  try {
    const res = await request<{ items: TripIncidentReportRow[] }>(
      'GET',
      `/admin/reports/incidents${qs ? `?${qs}` : ''}`,
    )
    incidents.value = res.items
  } catch (err) {
    incidentsError.value = err instanceof ApiError ? err.message : 'No se pudo cargar el reporte.'
    incidents.value = []
  } finally {
    incidentsLoading.value = false
  }
}

function clearIncidentsFilters(): void {
  incidentsFilter.routeID = null
  incidentsFilter.incidentType = ''
  incidentsFilter.status = ''
  incidentsFilter.dateFrom = null
  incidentsFilter.dateTo = null
}

// --- Export a Excel del tab Tickets / quejas ---
const incidentsExcelColumns: ExcelColumn[] = [
  { key: 'reported_at',       label: 'Reportado en',     format: 'datetime', width: 20 },
  { key: 'incident_type',     label: 'Tipo',             width: 12 },
  { key: 'status',            label: 'Estado',           width: 12 },
  { key: 'trip_code',         label: 'Código viaje',     width: 14 },
  { key: 'service_date',      label: 'Fecha servicio',   format: 'date', width: 14 },
  { key: 'route_code',        label: 'Código ruta',      width: 12 },
  { key: 'route_name',        label: 'Nombre ruta',      width: 24 },
  { key: 'direction',         label: 'Sentido',          width: 8 },
  { key: 'reported_by_name',  label: 'Reportado por',    width: 24 },
  { key: 'description',       label: 'Descripción',      width: 36 },
  { key: 'resolved_at',       label: 'Resuelto en',      format: 'datetime', width: 20 },
  { key: 'resolution_notes',  label: 'Notas resolución', width: 36 },
]

function exportIncidents(): void {
  const parts: string[] = []
  if (incidentsFilter.routeID && incidentsFilter.routeID > 0) parts.push(`Ruta ID=${incidentsFilter.routeID}`)
  if (incidentsFilter.incidentType) parts.push(`Tipo=${incidentsFilter.incidentType}`)
  if (incidentsFilter.status) parts.push(`Estado=${incidentsFilter.status}`)
  if (incidentsFilter.dateFrom || incidentsFilter.dateTo) {
    parts.push(`Rango=${ymd(incidentsFilter.dateFrom) ?? '*'} a ${ymd(incidentsFilter.dateTo) ?? '*'}`)
  }
  exportRowsToExcel(incidents.value, incidentsExcelColumns, {
    filename: timestampedFilename('reporte_tickets_quejas'),
    sheetName: 'Tickets / quejas',
    title: 'Reporte #8: Tickets y quejas de usuarios',
    filterDescription: parts.join(', ') || undefined,
  })
}

const hasIncidentsFilters = computed(
  () =>
    Boolean(
      (incidentsFilter.routeID && incidentsFilter.routeID > 0) ||
        incidentsFilter.incidentType ||
        incidentsFilter.status ||
        incidentsFilter.dateFrom ||
        incidentsFilter.dateTo,
    ),
)

// ---------------------------------------------------------------------------
// Tab 10: Actividad de reservas por usuario (vw_user_reservation_activity)
// Reporte #28 (migration 0005). Muestra conteos por usuario WORKER/DRIVER:
//   total_reservations, confirmed_by_self, confirmed_by_driver,
//   cancelled_by_self, not_confirmed.
// Filtros opcionales: role (WORKER|DRIVER), active (true|false), department.
// ---------------------------------------------------------------------------

const ACTIVITY_ROLE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: '', label: 'Todos' },
  { value: 'WORKER', label: 'Trabajador' },
  { value: 'DRIVER', label: 'Conductor' },
]

const ACTIVE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: '', label: 'Todos' },
  { value: 'true', label: 'Activos' },
  { value: 'false', label: 'Inactivos' },
]

const ROLE_LABELS: Record<string, string> = {
  WORKER: 'Trabajador',
  DRIVER: 'Conductor',
}

const activity = ref<UserReservationActivityRow[]>([])
const activityLoading = ref(false)
const activityError = ref('')
const activityFilter = reactive<{
  role: string
  active: string
  department: string
  dateFrom: Date | null
  dateTo: Date | null
}>({
  role: '',
  active: '',
  department: '',
  dateFrom: null,
  dateTo: null,
})

async function loadActivity(): Promise<void> {
  activityLoading.value = true
  activityError.value = ''
  const params = new URLSearchParams()
  if (activityFilter.role) params.set('role', activityFilter.role)
  if (activityFilter.active) params.set('active', activityFilter.active)
  if (activityFilter.department) params.set('department', activityFilter.department)
  const fromStr = ymd(activityFilter.dateFrom)
  if (fromStr) params.set('date_from', fromStr)
  const toStr = ymd(activityFilter.dateTo)
  if (toStr) params.set('date_to', toStr)
  const qs = params.toString()
  try {
    const res = await request<{ items: UserReservationActivityRow[] }>(
      'GET',
      `/admin/reports/user-reservation-activity${qs ? `?${qs}` : ''}`,
    )
    activity.value = res.items
  } catch (err) {
    activityError.value = err instanceof ApiError ? err.message : 'No se pudo cargar el reporte.'
    activity.value = []
  } finally {
    activityLoading.value = false
  }
}

function clearActivityFilters(): void {
  activityFilter.role = ''
  activityFilter.active = ''
  activityFilter.department = ''
  activityFilter.dateFrom = null
  activityFilter.dateTo = null
}

// --- Export a Excel del tab Actividad por usuario ---
// Las columnas estan agrupadas en el mismo orden que la UI: primero
// estado final (mutuamente excluyente) y despues acciones.
const activityExcelColumns: ExcelColumn[] = [
  { key: 'employee_code',      label: 'Código',                  width: 10 },
  { key: 'full_name',          label: 'Nombre',                  width: 28 },
  { key: 'role',               label: 'Rol',                     width: 12 },
  { key: 'department',         label: 'Departamento',            width: 18 },
  { key: 'active',             label: 'Activo',                  width: 8 },
  // Estado final
  { key: 'total_reservations', label: 'Total reservas',          format: 'number', width: 14 },
  { key: 'completadas',        label: 'Completadas',            format: 'number', width: 12 },
  { key: 'pendientes',         label: 'Pendientes',             format: 'number', width: 12 },
  { key: 'canceladas',         label: 'Canceladas',             format: 'number', width: 12 },
  { key: 'no_show',            label: 'No show',                format: 'number', width: 10 },
  // Acciones
  { key: 'confirmed_by_self',  label: 'Bookeadas por sí mismo',  format: 'number', width: 22 },
  { key: 'confirmed_by_driver',label: 'Boardings por conductor', format: 'number', width: 24 },
  { key: 'cancelled_by_self',  label: 'Canc. por sí mismo',     format: 'number', width: 20 },
  // Metadata
  { key: 'last_activity_at',   label: 'Última actividad',        format: 'datetime', width: 20 },
]

function exportActivity(): void {
  const parts: string[] = []
  if (activityFilter.role) parts.push(`Rol=${activityFilter.role}`)
  if (activityFilter.active) parts.push(`Activo=${activityFilter.active}`)
  if (activityFilter.department) parts.push(`Departamento=${activityFilter.department}`)
  if (activityFilter.dateFrom || activityFilter.dateTo) {
    parts.push(`Rango=${ymd(activityFilter.dateFrom) ?? '*'} a ${ymd(activityFilter.dateTo) ?? '*'}`)
  }
  // Totales para el footer del Excel — incluye estado final + acciones.
  // Los campos que no aplican a totales quedan como string vacio.
  const totals = {
    total_reservations:    activityStatusTotals.value.total,
    completadas:           activityStatusTotals.value.completadas,
    pendientes:            activityStatusTotals.value.pendientes,
    canceladas:            activityStatusTotals.value.canceladas,
    no_show:               activityStatusTotals.value.no_show,
    confirmed_by_self:     activityActionTotals.value.bySelf,
    confirmed_by_driver:   activityActionTotals.value.byDriver,
    cancelled_by_self:     activityActionTotals.value.cancelledBySelf,
    last_activity_at:      '',
    employee_code:         '',
    full_name:             '',
    role:                  '',
    department:            '',
    active:                '',
  }
  exportRowsToExcel(activity.value, activityExcelColumns, {
    filename: timestampedFilename('reporte_actividad_usuarios'),
    sheetName: 'Actividad por usuario',
    title: 'Reporte #9: Actividad de reservas por usuario',
    filterDescription: parts.join(', ') || undefined,
    totals,
    totalsLabel: `TOTAL (${activity.value.length} usuarios)`,
  })
}

const hasActivityFilters = computed(
  () =>
    Boolean(
      activityFilter.role ||
        activityFilter.active ||
        activityFilter.department ||
        activityFilter.dateFrom ||
        activityFilter.dateTo,
    ),
)

// Etiqueta legible del rango activo, para el footer del reporte.
const activityDateRangeLabel = computed(() => {
  const from = ymd(activityFilter.dateFrom)
  const to = ymd(activityFilter.dateTo)
  if (from && to) return `${from} a ${to}`
  if (from) return `desde ${from}`
  if (to) return `hasta ${to}`
  return 'sin filtro de fechas'
})

// Convierte el ISO string del backend a un Date para formatear de forma
// consistente con el resto del front. Devuelve null si el string es invalido
// (la API envia null cuando el usuario nunca tuvo actividad).
function parseLastActivity(value: string | null | undefined): Date | null {
  if (!value) return null
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? null : d
}

function formatLastActivity(value: string | null | undefined): string {
  const d = parseLastActivity(value)
  if (!d) return '—'
  // YYYY-MM-DD HH:MM en timezone local (la BD guarda UTC; Date lo convierte
  // al timezone del navegador, que para el operador peruano es GMT-5).
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

// Suma total de reservas del filtro actual, para mostrar el contador
// debajo de la tabla sin pedir otra query.
// Las dos secciones (estado final + acciones) tienen totales independientes
// para que el footer sea claro.
const activityStatusTotals = computed(() => {
  return activity.value.reduce(
    (acc, row) => {
      acc.total += row.total_reservations
      acc.completadas += row.completadas
      acc.pendientes  += row.pendientes
      acc.canceladas  += row.canceladas
      acc.no_show     += row.no_show
      return acc
    },
    { total: 0, completadas: 0, pendientes: 0, canceladas: 0, no_show: 0 },
  )
})

const activityActionTotals = computed(() => {
  return activity.value.reduce(
    (acc, row) => {
      acc.total          += row.total_reservations
      acc.bySelf         += row.confirmed_by_self
      acc.byDriver       += row.confirmed_by_driver
      acc.cancelledBySelf += row.cancelled_by_self
      return acc
    },
    { total: 0, bySelf: 0, byDriver: 0, cancelledBySelf: 0 },
  )
})

// ---------------------------------------------------------------------------
// Tabs (lazy load: cada tab dispara su consulta al activarse por primera vez)
// ---------------------------------------------------------------------------

type ReportTab =
  | 'conflicts'
  | 'matrix'
  | 'seats'
  | 'occupancy'
  | 'tripsStatus'
  | 'duration'
  | 'delays'
  | 'changes'
  | 'incidents'
  | 'activity'

const activeTab = ref<ReportTab>('conflicts')
const conflictsLoaded = ref(false)
const matrixLoaded = ref(false)
const occupancyLoaded = ref(false)
const tripsStatusLoaded = ref(false)
const durationLoaded = ref(false)
const delaysLoaded = ref(false)
const changesLoaded = ref(false)
const incidentsLoaded = ref(false)
const activityLoaded = ref(false)

function onTabChange(value: string | number | undefined): void {
  const tab = String(value ?? '') as ReportTab
  activeTab.value = tab
  if (tab === 'conflicts' && !conflictsLoaded.value) {
    conflictsLoaded.value = true
    loadConflicts()
  } else if (tab === 'matrix' && !matrixLoaded.value) {
    matrixLoaded.value = true
    loadMatrix()
  } else if (tab === 'occupancy' && !occupancyLoaded.value) {
    occupancyLoaded.value = true
    loadOccupancy()
  } else if (tab === 'tripsStatus' && !tripsStatusLoaded.value) {
    tripsStatusLoaded.value = true
    loadTripsStatus()
  } else if (tab === 'duration' && !durationLoaded.value) {
    durationLoaded.value = true
    loadDuration()
  } else if (tab === 'delays' && !delaysLoaded.value) {
    delaysLoaded.value = true
    loadDelays()
  } else if (tab === 'changes' && !changesLoaded.value) {
    changesLoaded.value = true
    loadChanges()
  } else if (tab === 'incidents' && !incidentsLoaded.value) {
    incidentsLoaded.value = true
    loadIncidents()
  } else if (tab === 'activity' && !activityLoaded.value) {
    activityLoaded.value = true
    loadActivity()
  }
}

// ---------------------------------------------------------------------------
// Init: tab 0 ya cargado onMounted
// ---------------------------------------------------------------------------

onMounted(() => {
  conflictsLoaded.value = true
  loadConflicts()
})
</script>

<template>
  <section class="reports-view">
    <header class="reports-header">
      <h1>Reportes</h1>
      <p class="reports-subtitle">
        Vistas SQL de solo lectura sobre datos ya materializados: solapamientos de vehículo/conductor, matriz de
        tiempos por ruta y disponibilidad de asientos por viaje.
      </p>
    </header>

    <Tabs :value="activeTab" @update:value="onTabChange">
      <TabList>
        <Tab value="conflicts">
          <i class="pi pi-exclamation-circle tab-icon" aria-hidden="true"></i>
          Conflictos de horario
        </Tab>
        <Tab value="matrix">
          <i class="pi pi-stopwatch tab-icon" aria-hidden="true"></i>
          Matriz de tiempos
        </Tab>
        <Tab value="seats">
          <i class="pi pi-th-large tab-icon" aria-hidden="true"></i>
          Disponibilidad de asientos
        </Tab>
        <Tab value="occupancy">
          <i class="pi pi-chart-line tab-icon" aria-hidden="true"></i>
          Ocupación por ruta
        </Tab>
        <Tab value="tripsStatus">
          <i class="pi pi-list-check tab-icon" aria-hidden="true"></i>
          Status de viajes
        </Tab>
        <Tab value="duration">
          <i class="pi pi-clock tab-icon" aria-hidden="true"></i>
          Duración real vs estimada
        </Tab>
        <Tab value="delays">
          <i class="pi pi-hourglass tab-icon" aria-hidden="true"></i>
          Retrasos por ruta/día
        </Tab>
        <Tab value="changes">
          <i class="pi pi-history tab-icon" aria-hidden="true"></i>
          Cambios en reservas
        </Tab>
        <Tab value="incidents">
          <i class="pi pi-flag tab-icon" aria-hidden="true"></i>
          Tickets / quejas
        </Tab>
        <Tab value="activity">
          <i class="pi pi-users tab-icon" aria-hidden="true"></i>
          Actividad por usuario
        </Tab>
      </TabList>

      <TabPanels>
        <!-- Tab 1: Conflictos -->
        <TabPanel value="conflicts">
          <div class="report-filters">
            <div class="filter">
              <label for="conf-resource">Recurso</label>
              <Select
                id="conf-resource"
                v-model="conflictsFilter.resourceType"
                :options="RESOURCE_TYPE_OPTIONS"
                optionLabel="label"
                optionValue="value"
              />
            </div>
            <div class="filter">
              <label for="conf-from">Desde</label>
              <DatePicker
                id="conf-from"
                v-model="conflictsFilter.dateFrom"
                date-format="yy-mm-dd"
                show-icon
              />
            </div>
            <div class="filter">
              <label for="conf-to">Hasta</label>
              <DatePicker
                id="conf-to"
                v-model="conflictsFilter.dateTo"
                date-format="yy-mm-dd"
                show-icon
              />
            </div>
            <div class="filter-actions">
              <Button
                label="Aplicar"
                icon="pi pi-search"
                :loading="conflictsLoading"
                @click="loadConflicts"
              />
              <Button
                v-if="hasConflictsFilters"
                label="Limpiar"
                icon="pi pi-filter-slash"
                severity="secondary"
                text
                @click="clearConflictsFilters"
              />
              <Button
                label="Exportar Excel"
                icon="pi pi-download"
                severity="secondary"
                :disabled="conflicts.length === 0"
                @click="exportConflicts"
              />
            </div>
          </div>

          <p v-if="conflictsError" role="alert" class="reports-error">
            {{ conflictsError }}
            <Button label="Reintentar" text size="small" @click="loadConflicts" />
          </p>

          <DataTable
            :value="conflicts"
            :loading="conflictsLoading"
            dataKey="first_trip_id"
            paginator
            :rows="10"
            class="reports-table"
          >
            <template #empty>
              <p class="reports-empty">
                <i class="pi pi-check-circle" aria-hidden="true"></i>
                Sin conflictos para los filtros aplicados.
              </p>
            </template>
            <Column header="Recurso" style="width: 8rem">
              <template #body="{ data }">
                <Tag
                  :value="RESOURCE_TYPE_LABELS[data.resource_type] ?? data.resource_type"
                  :severity="RESOURCE_TYPE_SEVERITIES[data.resource_type] ?? 'secondary'"
                />
              </template>
            </Column>
            <Column field="resource_id" header="ID recurso" style="width: 6rem" />
            <Column field="first_trip_id" header="Viaje 1" style="width: 6rem" />
            <Column field="first_start_at" header="Inicio V1" />
            <Column field="first_end_at" header="Fin V1" />
            <Column field="second_trip_id" header="Viaje 2" style="width: 6rem" />
            <Column field="second_start_at" header="Inicio V2" />
            <Column field="second_end_at" header="Fin V2" />
          </DataTable>

          <p v-if="!conflictsLoading && !conflictsError" class="reports-total">
            {{ conflicts.length }} conflicto(s) detectado(s)
          </p>
        </TabPanel>

        <!-- Tab 2: Matriz de tiempos -->
        <TabPanel value="matrix">
          <div class="report-filters">
            <div class="filter">
              <label for="mtx-route">ID de ruta</label>
              <InputNumber
                inputId="mtx-route"
                v-model="matrixFilter.routeID"
                :min="0"
                placeholder="Todas"
              />
            </div>
            <div class="filter">
              <label for="mtx-direction">Sentido</label>
              <Select
                id="mtx-direction"
                v-model="matrixFilter.direction"
                :options="DIRECTION_OPTIONS"
                optionLabel="label"
                optionValue="value"
              />
            </div>
            <div class="filter">
              <label for="mtx-profile">ID de perfil</label>
              <InputNumber
                inputId="mtx-profile"
                v-model="matrixFilter.profileID"
                :min="0"
                placeholder="Todos"
              />
            </div>
            <div class="filter-actions">
              <Button
                label="Aplicar"
                icon="pi pi-search"
                :loading="matrixLoading"
                @click="loadMatrix"
              />
              <Button
                v-if="hasMatrixFilters"
                label="Limpiar"
                icon="pi pi-filter-slash"
                severity="secondary"
                text
                @click="clearMatrixFilters"
              />
              <Button
                label="Exportar Excel"
                icon="pi pi-download"
                severity="secondary"
                :disabled="matrix.length === 0"
                @click="exportMatrix"
              />
            </div>
          </div>

          <p v-if="matrixError" role="alert" class="reports-error">
            {{ matrixError }}
            <Button label="Reintentar" text size="small" @click="loadMatrix" />
          </p>

          <DataTable :value="matrix" :loading="matrixLoading" paginator :rows="15" class="reports-table">
            <template #empty>
              <p class="reports-empty">No hay entradas para los filtros aplicados.</p>
            </template>
            <Column field="route_code" header="Ruta" style="width: 6rem" />
            <Column field="direction" header="Sentido" style="width: 5rem" />
            <Column field="from_stop_name" header="Desde" />
            <Column field="to_stop_name" header="Hasta" />
            <Column field="profile_name" header="Perfil" />
            <Column field="travel_minutes" header="Min" style="width: 4rem" />
            <Column field="priority" header="Prioridad" style="width: 5rem" />
          </DataTable>

          <p v-if="!matrixLoading && !matrixError" class="reports-total">
            {{ matrix.length }} entrada(s) en la matriz
          </p>
        </TabPanel>

        <!-- Tab 3: Disponibilidad de asientos -->
        <TabPanel value="seats">
          <form novalidate class="seat-filter" @submit.prevent="searchSeats">
            <div class="filter">
              <label for="seats-trip-id">ID de viaje <span class="required-mark" aria-hidden="true">*</span></label>
              <InputNumber
                inputId="seats-trip-id"
                v-model="seatFilter.tripId"
                :min="1"
                :aria-invalid="!!seatsFieldError"
              />
              <p v-if="seatsFieldError" role="alert" class="field-error">{{ seatsFieldError }}</p>
            </div>
            <div class="filter">
              <label for="seats-state">Estado</label>
              <Select
                id="seats-state"
                v-model="seatFilter.state"
                :options="SEAT_STATE_OPTIONS"
                optionLabel="label"
                optionValue="value"
              />
            </div>
            <div class="filter">
              <label for="seats-extended">Extendida</label>
              <Select
                id="seats-extended"
                v-model="seatFilter.extended"
                :options="EXTENDED_OPTIONS"
                optionLabel="label"
                optionValue="value"
              />
            </div>
            <div class="filter-actions">
              <Button type="submit" label="Buscar" icon="pi pi-search" :loading="seatsLoading" />
              <Button
                label="Exportar Excel"
                icon="pi pi-download"
                severity="secondary"
                :disabled="seats.length === 0"
                @click="exportSeats"
              />
            </div>
          </form>

          <p v-if="seatsError" role="alert" class="reports-error">
            {{ seatsError }}
            <Button label="Reintentar" text size="small" @click="searchSeats" />
          </p>

          <DataTable
            v-if="seatsSearched"
            :value="seats"
            :loading="seatsLoading"
            paginator
            :rows="15"
            class="reports-table"
          >
            <template #empty>
              <p class="reports-empty">No hay asientos para los filtros aplicados.</p>
            </template>
            <Column field="seat_label" header="Asiento" style="width: 5rem" />
            <Column field="segment_order" header="Segmento" style="width: 5rem" />
            <Column header="Estado" style="width: 9rem">
              <template #body="{ data }">
                <Tag
                  :value="SEAT_STATE_LABELS[data.state] ?? data.state"
                  :severity="SEAT_STATE_SEVERITIES[data.state] ?? 'info'"
                />
              </template>
            </Column>
            <Column field="available_or_occupied_from" header="Desde" />
            <Column field="available_or_occupied_until" header="Hasta" />
            <Column field="reservation_code" header="Código de reserva">
              <template #body="{ data }">{{ formatCell(data.reservation_code) }}</template>
            </Column>
            <Column header="Extendida" style="width: 7rem">
              <template #body="{ data }">
                <Tag
                  :value="data.reservation_extended ? 'Sí' : 'No'"
                  :severity="data.reservation_extended ? 'info' : 'secondary'"
                />
              </template>
            </Column>
            <Column header="Destino original → actual">
              <template #body="{ data }">
                {{ formatCell(data.original_destination_name) }} →
                {{ formatCell(data.current_destination_name) }}
              </template>
            </Column>
          </DataTable>
          <p v-else class="reports-hint">Ingresá un ID de viaje para ver la disponibilidad de sus asientos.</p>
        </TabPanel>

        <!-- Tab 4: Ocupación por ruta (#5) -->
        <TabPanel value="occupancy">
          <div class="report-filters">
            <div class="filter">
              <label for="occ-route">ID de ruta</label>
              <InputNumber inputId="occ-route" v-model="occupancyFilter.routeID" :min="0" placeholder="Todas" />
            </div>
            <div class="filter">
              <label for="occ-from">Desde</label>
              <DatePicker id="occ-from" v-model="occupancyFilter.dateFrom" date-format="yy-mm-dd" show-icon />
            </div>
            <div class="filter">
              <label for="occ-to">Hasta</label>
              <DatePicker id="occ-to" v-model="occupancyFilter.dateTo" date-format="yy-mm-dd" show-icon />
            </div>
            <div class="filter-actions">
              <Button label="Aplicar" icon="pi pi-search" :loading="occupancyLoading" @click="loadOccupancy" />
              <Button
                v-if="hasOccupancyFilters"
                label="Limpiar"
                icon="pi pi-filter-slash"
                severity="secondary"
                text
                @click="clearOccupancyFilters"
              />
              <Button
                label="Exportar Excel"
                icon="pi pi-download"
                severity="secondary"
                :disabled="occupancy.length === 0"
                @click="exportOccupancy"
              />
            </div>
          </div>

          <p v-if="occupancyError" role="alert" class="reports-error">
            {{ occupancyError }}
            <Button label="Reintentar" text size="small" @click="loadOccupancy" />
          </p>

          <DataTable :value="occupancy" :loading="occupancyLoading" paginator :rows="15" class="reports-table">
            <template #empty>
              <p class="reports-empty">Sin datos de ocupación para los filtros aplicados.</p>
            </template>
            <Column field="route_code" header="Ruta" style="width: 6rem" />
            <Column field="route_name" header="Nombre" />
            <Column field="direction" header="Sentido" style="width: 5rem" />
            <Column field="service_date" header="Fecha" style="width: 7rem" />
            <Column field="trip_count" header="Viajes" style="width: 5rem" />
            <Column field="seats_offered" header="Ofrecidos" style="width: 6rem" />
            <Column field="seats_reserved" header="Reservados" style="width: 6rem" />
            <Column header="% Ocupación" style="width: 7rem">
              <template #body="{ data }">{{ data.occupancy_pct.toFixed(1) }}%</template>
            </Column>
          </DataTable>

          <p v-if="!occupancyLoading && !occupancyError" class="reports-total">
            {{ occupancy.length }} fila(s) de ocupación
          </p>
        </TabPanel>

        <!-- Tab 5: Resumen status viajes (#11) -->
        <TabPanel value="tripsStatus">
          <div class="report-filters">
            <div class="filter">
              <label for="ts-from">Desde</label>
              <DatePicker id="ts-from" v-model="tripsStatusFilter.dateFrom" date-format="yy-mm-dd" show-icon />
            </div>
            <div class="filter">
              <label for="ts-to">Hasta</label>
              <DatePicker id="ts-to" v-model="tripsStatusFilter.dateTo" date-format="yy-mm-dd" show-icon />
            </div>
            <div class="filter">
              <label for="ts-status">Status</label>
              <Select
                id="ts-status"
                v-model="tripsStatusFilter.status"
                :options="TRIP_STATUS_OPTIONS"
                optionLabel="label"
                optionValue="value"
              />
            </div>
            <div class="filter-actions">
              <Button label="Aplicar" icon="pi pi-search" :loading="tripsStatusLoading" @click="loadTripsStatus" />
              <Button
                v-if="hasTripsStatusFilters"
                label="Limpiar"
                icon="pi pi-filter-slash"
                severity="secondary"
                text
                @click="clearTripsStatusFilters"
              />
              <Button
                label="Exportar Excel"
                icon="pi pi-download"
                severity="secondary"
                :disabled="tripsStatus.length === 0"
                @click="exportTripsStatus"
              />
            </div>
          </div>

          <p v-if="tripsStatusError" role="alert" class="reports-error">
            {{ tripsStatusError }}
            <Button label="Reintentar" text size="small" @click="loadTripsStatus" />
          </p>

          <DataTable :value="tripsStatus" :loading="tripsStatusLoading" paginator :rows="15" class="reports-table">
            <template #empty>
              <p class="reports-empty">Sin viajes para los filtros aplicados.</p>
            </template>
            <Column field="service_date" header="Fecha" style="width: 7rem" />
            <Column field="route_code" header="Ruta" style="width: 6rem" />
            <Column field="route_name" header="Nombre" />
            <Column field="direction" header="Sentido" style="width: 5rem" />
            <Column header="Status" style="width: 8rem">
              <template #body="{ data }">
                <Tag
                  :value="TRIP_STATUS_LABELS[data.status] ?? data.status"
                  :severity="TRIP_STATUS_SEVERITIES[data.status] ?? 'secondary'"
                />
              </template>
            </Column>
            <Column field="trip_count" header="Cantidad" style="width: 5rem" />
          </DataTable>

          <p v-if="!tripsStatusLoading && !tripsStatusError" class="reports-total">
            {{ tripsStatus.length }} fila(s) de status
          </p>
        </TabPanel>

        <!-- Tab 6: Duración real vs estimada (#12) -->
        <TabPanel value="duration">
          <div class="report-filters">
            <div class="filter">
              <label for="dur-route">ID de ruta</label>
              <InputNumber inputId="dur-route" v-model="durationFilter.routeID" :min="0" placeholder="Todas" />
            </div>
            <div class="filter">
              <label for="dur-from">Desde</label>
              <DatePicker id="dur-from" v-model="durationFilter.dateFrom" date-format="yy-mm-dd" show-icon />
            </div>
            <div class="filter">
              <label for="dur-to">Hasta</label>
              <DatePicker id="dur-to" v-model="durationFilter.dateTo" date-format="yy-mm-dd" show-icon />
            </div>
            <div class="filter-actions">
              <Button label="Aplicar" icon="pi pi-search" :loading="durationLoading" @click="loadDuration" />
              <Button
                v-if="hasDurationFilters"
                label="Limpiar"
                icon="pi pi-filter-slash"
                severity="secondary"
                text
                @click="clearDurationFilters"
              />
              <Button
                label="Exportar Excel"
                icon="pi pi-download"
                severity="secondary"
                :disabled="duration.length === 0"
                @click="exportDuration"
              />
            </div>
          </div>

          <p v-if="durationError" role="alert" class="reports-error">
            {{ durationError }}
            <Button label="Reintentar" text size="small" @click="loadDuration" />
          </p>

          <DataTable :value="duration" :loading="durationLoading" paginator :rows="15" class="reports-table">
            <template #empty>
              <p class="reports-empty">Sin desvíos de duración para los filtros aplicados.</p>
            </template>
            <Column field="trip_code" header="Viaje" style="width: 10rem" />
            <Column field="service_date" header="Fecha" style="width: 7rem" />
            <Column field="route_code" header="Ruta" style="width: 6rem" />
            <Column field="scheduled_duration_minutes" header="Programado (min)" style="width: 7rem" />
            <Column field="actual_duration_minutes" header="Real (min)" style="width: 6rem" />
            <Column header="Δ (min)" style="width: 6rem">
              <template #body="{ data }">
                <Tag
                  :value="(data.delta_minutes >= 0 ? '+' : '') + data.delta_minutes"
                  :severity="data.delta_minutes > 5 ? 'danger' : data.delta_minutes < -5 ? 'success' : 'info'"
                />
              </template>
            </Column>
            <Column header="Δ %" style="width: 5rem">
              <template #body="{ data }">{{ data.delta_pct != null ? data.delta_pct.toFixed(1) + '%' : '—' }}</template>
            </Column>
          </DataTable>

          <p v-if="!durationLoading && !durationError" class="reports-total">
            {{ duration.length }} viaje(s) cerrados
          </p>
        </TabPanel>

        <!-- Tab 7: Retrasos por ruta/día (#13) -->
        <TabPanel value="delays">
          <div class="report-filters">
            <div class="filter">
              <label for="dly-route">ID de ruta</label>
              <InputNumber inputId="dly-route" v-model="delaysFilter.routeID" :min="0" placeholder="Todas" />
            </div>
            <div class="filter">
              <label for="dly-direction">Sentido</label>
              <Select
                id="dly-direction"
                v-model="delaysFilter.direction"
                :options="DIRECTION_OPTIONS"
                optionLabel="label"
                optionValue="value"
              />
            </div>
            <div class="filter">
              <label for="dly-from">Desde</label>
              <DatePicker id="dly-from" v-model="delaysFilter.dateFrom" date-format="yy-mm-dd" show-icon />
            </div>
            <div class="filter">
              <label for="dly-to">Hasta</label>
              <DatePicker id="dly-to" v-model="delaysFilter.dateTo" date-format="yy-mm-dd" show-icon />
            </div>
            <div class="filter-actions">
              <Button label="Aplicar" icon="pi pi-search" :loading="delaysLoading" @click="loadDelays" />
              <Button
                v-if="hasDelaysFilters"
                label="Limpiar"
                icon="pi pi-filter-slash"
                severity="secondary"
                text
                @click="clearDelaysFilters"
              />
              <Button
                label="Exportar Excel"
                icon="pi pi-download"
                severity="secondary"
                :disabled="delays.length === 0"
                @click="exportDelays"
              />
            </div>
          </div>

          <p v-if="delaysError" role="alert" class="reports-error">
            {{ delaysError }}
            <Button label="Reintentar" text size="small" @click="loadDelays" />
          </p>

          <DataTable :value="delays" :loading="delaysLoading" paginator :rows="15" class="reports-table">
            <template #empty>
              <p class="reports-empty">Sin datos de retraso para los filtros aplicados.</p>
            </template>
            <Column field="route_code" header="Ruta" style="width: 6rem" />
            <Column field="direction" header="Sentido" style="width: 5rem" />
            <Column field="service_date" header="Fecha" style="width: 7rem" />
            <Column field="trip_count" header="Viajes" style="width: 5rem" />
            <Column header="Δ promedio (min)" style="width: 8rem">
              <template #body="{ data }">
                <Tag
                  :value="(data.avg_delay_minutes >= 0 ? '+' : '') + data.avg_delay_minutes.toFixed(1)"
                  :severity="data.avg_delay_minutes > 5 ? 'danger' : data.avg_delay_minutes < -1 ? 'success' : 'info'"
                />
              </template>
            </Column>
            <Column field="max_delay_minutes" header="Δ máx (min)" style="width: 7rem" />
            <Column field="late_trip_count" header="Tarde" style="width: 5rem" />
            <Column field="on_time_trip_count" header="En hora" style="width: 5rem" />
          </DataTable>

          <p v-if="!delaysLoading && !delaysError" class="reports-total">
            {{ delays.length }} día(s) / ruta con datos
          </p>
        </TabPanel>

        <!-- Tab 8: Cambios en reservas (#26) -->
        <TabPanel value="changes">
          <div class="report-filters">
            <div class="filter">
              <label for="ch-res">ID de reserva</label>
              <InputNumber inputId="ch-res" v-model="changesFilter.reservationID" :min="0" placeholder="Todas" />
            </div>
            <div class="filter">
              <label for="ch-type">Tipo de evento</label>
              <Select
                id="ch-type"
                v-model="changesFilter.eventType"
                :options="RES_EVENT_OPTIONS"
                optionLabel="label"
                optionValue="value"
              />
            </div>
            <div class="filter">
              <label for="ch-from">Desde</label>
              <DatePicker id="ch-from" v-model="changesFilter.dateFrom" date-format="yy-mm-dd" show-icon />
            </div>
            <div class="filter">
              <label for="ch-to">Hasta</label>
              <DatePicker id="ch-to" v-model="changesFilter.dateTo" date-format="yy-mm-dd" show-icon />
            </div>
            <div class="filter-actions">
              <Button label="Aplicar" icon="pi pi-search" :loading="changesLoading" @click="loadChanges" />
              <Button
                v-if="hasChangesFilters"
                label="Limpiar"
                icon="pi pi-filter-slash"
                severity="secondary"
                text
                @click="clearChangesFilters"
              />
              <Button
                label="Exportar Excel"
                icon="pi pi-download"
                severity="secondary"
                :disabled="changes.length === 0"
                @click="exportChanges"
              />
            </div>
          </div>

          <p v-if="changesError" role="alert" class="reports-error">
            {{ changesError }}
            <Button label="Reintentar" text size="small" @click="loadChanges" />
          </p>

          <DataTable :value="changes" :loading="changesLoading" paginator :rows="15" class="reports-table">
            <template #empty>
              <p class="reports-empty">Sin cambios registrados para los filtros aplicados.</p>
            </template>
            <Column field="event_at" header="Fecha y hora" style="width: 9rem" />
            <Column field="reservation_code" header="Reserva" style="width: 11rem" />
            <Column field="event_type" header="Evento" style="width: 8rem" />
            <Column field="worker_name" header="Trabajador" />
            <Column field="actor_name" header="Actor" />
            <Column field="trip_code" header="Viaje" style="width: 10rem" />
            <Column field="service_date" header="Servicio" style="width: 7rem" />
            <Column field="route_code" header="Ruta" style="width: 6rem" />
            <Column header="Detalle">
              <template #body="{ data }">{{ data.details || '—' }}</template>
            </Column>
          </DataTable>

          <p v-if="!changesLoading && !changesError" class="reports-total">
            {{ changes.length }} evento(s) en el historial
          </p>
        </TabPanel>

        <!-- Tab 9: Tickets / quejas (#27) -->
        <TabPanel value="incidents">
          <div class="report-filters">
            <div class="filter">
              <label for="inc-route">ID de ruta</label>
              <InputNumber inputId="inc-route" v-model="incidentsFilter.routeID" :min="0" placeholder="Todas" />
            </div>
            <div class="filter">
              <label for="inc-type">Tipo</label>
              <Select
                id="inc-type"
                v-model="incidentsFilter.incidentType"
                :options="INCIDENT_TYPE_OPTIONS"
                optionLabel="label"
                optionValue="value"
              />
            </div>
            <div class="filter">
              <label for="inc-status">Estado</label>
              <Select
                id="inc-status"
                v-model="incidentsFilter.status"
                :options="INCIDENT_STATUS_OPTIONS"
                optionLabel="label"
                optionValue="value"
              />
            </div>
            <div class="filter">
              <label for="inc-from">Desde</label>
              <DatePicker id="inc-from" v-model="incidentsFilter.dateFrom" date-format="yy-mm-dd" show-icon />
            </div>
            <div class="filter">
              <label for="inc-to">Hasta</label>
              <DatePicker id="inc-to" v-model="incidentsFilter.dateTo" date-format="yy-mm-dd" show-icon />
            </div>
            <div class="filter-actions">
              <Button label="Aplicar" icon="pi pi-search" :loading="incidentsLoading" @click="loadIncidents" />
              <Button
                v-if="hasIncidentsFilters"
                label="Limpiar"
                icon="pi pi-filter-slash"
                severity="secondary"
                text
                @click="clearIncidentsFilters"
              />
              <Button
                label="Exportar Excel"
                icon="pi pi-download"
                severity="secondary"
                :disabled="incidents.length === 0"
                @click="exportIncidents"
              />
            </div>
          </div>

          <p v-if="incidentsError" role="alert" class="reports-error">
            {{ incidentsError }}
            <Button label="Reintentar" text size="small" @click="loadIncidents" />
          </p>

          <DataTable :value="incidents" :loading="incidentsLoading" paginator :rows="15" class="reports-table">
            <template #empty>
              <p class="reports-empty">Sin tickets/quejas para los filtros aplicados.</p>
            </template>
            <Column field="reported_at" header="Reportado" style="width: 9rem" />
            <Column header="Tipo" style="width: 7rem">
              <template #body="{ data }">
                <Tag
                  :value="INCIDENT_TYPE_LABELS[data.incident_type] ?? data.incident_type"
                  :severity="INCIDENT_TYPE_SEVERITIES[data.incident_type] ?? 'info'"
                />
              </template>
            </Column>
            <Column header="Estado" style="width: 7rem">
              <template #body="{ data }">
                <Tag
                  :value="INCIDENT_STATUS_LABELS[data.status] ?? data.status"
                  :severity="INCIDENT_STATUS_SEVERITIES[data.status] ?? 'secondary'"
                />
              </template>
            </Column>
            <Column field="trip_code" header="Viaje" style="width: 10rem" />
            <Column field="service_date" header="Fecha" style="width: 7rem" />
            <Column field="route_code" header="Ruta" style="width: 6rem" />
            <Column field="reported_by_name" header="Reportado por" />
            <Column header="Descripción">
              <template #body="{ data }">{{ data.description }}</template>
            </Column>
            <Column field="resolved_at" header="Resuelto" style="width: 9rem" />
          </DataTable>

          <p v-if="!incidentsLoading && !incidentsError" class="reports-total">
            {{ incidents.length }} incidente(s) reportado(s)
          </p>
        </TabPanel>

        <!-- Tab 10: Actividad de reservas por usuario -->
        <TabPanel value="activity">
          <div class="report-filters">
            <div class="filter">
              <label for="act-role">Rol</label>
              <Select
                id="act-role"
                v-model="activityFilter.role"
                :options="ACTIVITY_ROLE_OPTIONS"
                optionLabel="label"
                optionValue="value"
              />
            </div>
            <div class="filter">
              <label for="act-active">Estado</label>
              <Select
                id="act-active"
                v-model="activityFilter.active"
                :options="ACTIVE_OPTIONS"
                optionLabel="label"
                optionValue="value"
              />
            </div>
            <div class="filter">
              <label for="act-dept">Departamento</label>
              <InputText
                id="act-dept"
                v-model="activityFilter.department"
                placeholder="Ej. Logística"
              />
            </div>
            <div class="filter">
              <label for="act-from">Desde</label>
              <DatePicker id="act-from" v-model="activityFilter.dateFrom" date-format="yy-mm-dd" show-icon />
            </div>
            <div class="filter">
              <label for="act-to">Hasta</label>
              <DatePicker id="act-to" v-model="activityFilter.dateTo" date-format="yy-mm-dd" show-icon />
            </div>
            <div class="filter-actions">
              <Button label="Aplicar" icon="pi pi-search" :loading="activityLoading" @click="loadActivity" />
              <Button
                v-if="hasActivityFilters"
                label="Limpiar"
                icon="pi pi-filter-slash"
                severity="secondary"
                text
                @click="clearActivityFilters"
              />
              <Button
                label="Exportar Excel"
                icon="pi pi-download"
                severity="secondary"
                :disabled="activity.length === 0"
                @click="exportActivity"
              />
            </div>
          </div>

          <p v-if="activityError" role="alert" class="reports-error">
            {{ activityError }}
            <Button label="Reintentar" text size="small" @click="loadActivity" />
          </p>

          <!-- ============================================================== -->
          <!-- Seccion 1: ESTADO FINAL (mutuamente excluyentes, suman al total) -->
          <!-- ============================================================== -->
          <section class="activity-section">
            <header class="activity-section-header">
              <h3>Estado final de sus reservas</h3>
              <p class="activity-section-hint">
                Cada reserva termina en <strong>exactamente un</strong> estado. Las 4 columnas suman al total.
              </p>
            </header>

            <DataTable :value="activity" :loading="activityLoading" paginator :rows="20" class="reports-table">
              <template #empty>
                <p class="reports-empty">Sin actividad para los filtros aplicados.</p>
              </template>
              <Column field="employee_code" header="Código" style="width: 6rem" />
              <Column field="full_name" header="Nombre" />
              <Column header="Rol" style="width: 8rem">
                <template #body="{ data }">
                  <Tag
                    :value="ROLE_LABELS[data.role] ?? data.role"
                    :severity="data.role === 'DRIVER' ? 'info' : 'secondary'"
                  />
                </template>
              </Column>
              <Column field="department" header="Departamento" style="width: 9rem">
                <template #body="{ data }">{{ formatCell(data.department) }}</template>
              </Column>
              <Column field="total_reservations" header="Total" style="width: 5rem">
                <template #body="{ data }">
                  <strong>{{ data.total_reservations }}</strong>
                </template>
              </Column>
              <Column field="completadas" header="Completadas" style="width: 7rem">
                <template #body="{ data }">
                  <span :class="data.completadas > 0 ? 'count-pos' : 'count-zero'">{{ data.completadas }}</span>
                </template>
              </Column>
              <Column field="pendientes" header="Pendientes" style="width: 7rem">
                <template #body="{ data }">
                  <span :class="data.pendientes > 0 ? 'count-pending' : 'count-zero'">{{ data.pendientes }}</span>
                </template>
              </Column>
              <Column field="canceladas" header="Canceladas" style="width: 7rem">
                <template #body="{ data }">
                  <span :class="data.canceladas > 0 ? 'count-neg' : 'count-zero'">{{ data.canceladas }}</span>
                </template>
              </Column>
              <Column field="no_show" header="No show" style="width: 6rem">
                <template #body="{ data }">
                  <span :class="data.no_show > 0 ? 'count-neg' : 'count-zero'">{{ data.no_show }}</span>
                </template>
              </Column>
              <Column header="Última actividad" style="width: 12rem">
                <template #body="{ data }">{{ formatLastActivity(data.last_activity_at) }}</template>
              </Column>
            </DataTable>

            <p v-if="!activityLoading && !activityError && activity.length > 0" class="reports-total">
              <strong>{{ activity.length }} usuario(s)</strong> en rango {{ activityDateRangeLabel }} —
              <strong>{{ activityStatusTotals.total }}</strong> reservas totales:
              <span class="legend-pos">✓ {{ activityStatusTotals.completadas }} completadas</span>,
              <span class="legend-pending">⏳ {{ activityStatusTotals.pendientes }} pendientes</span>,
              <span class="legend-neg">✗ {{ activityStatusTotals.canceladas }} canceladas</span>,
              <span class="legend-neg">⚠ {{ activityStatusTotals.no_show }} no show</span>
            </p>
          </section>

          <!-- ============================================================== -->
          <!-- Seccion 2: ACCIONES REGISTRADAS (pueden solaparse)            -->
          <!-- ============================================================== -->
          <section class="activity-section">
            <header class="activity-section-header">
              <h3>Acciones registradas</h3>
              <p class="activity-section-hint">
                Quién disparó cada evento. <strong>Pueden solaparse</strong> (una reserva tiene varios eventos): no suman al total.
              </p>
            </header>

            <DataTable :value="activity" :loading="activityLoading" paginator :rows="20" class="reports-table">
              <template #empty>
                <p class="reports-empty">Sin acciones para los filtros aplicados.</p>
              </template>
              <Column field="employee_code" header="Código" style="width: 6rem" />
              <Column field="full_name" header="Nombre" />
              <Column field="confirmed_by_self" header="Bookeadas por sí mismo" style="width: 9rem">
                <template #body="{ data }">{{ data.confirmed_by_self }}</template>
              </Column>
              <Column field="confirmed_by_driver" header="Boardings por conductor" style="width: 9rem">
                <template #body="{ data }">{{ data.confirmed_by_driver }}</template>
              </Column>
              <Column field="cancelled_by_self" header="Canc. por sí mismo" style="width: 8rem">
                <template #body="{ data }">
                  <span :class="data.cancelled_by_self > 0 ? 'count-neg' : 'count-zero'">{{ data.cancelled_by_self }}</span>
                </template>
              </Column>
            </DataTable>

            <p v-if="!activityLoading && !activityError && activity.length > 0" class="reports-total">
              <strong>{{ activityActionTotals.bySelf }}</strong> bookings hechos por el pasajero,
              <strong>{{ activityActionTotals.byDriver }}</strong> boardings confirmados por el conductor,
              <strong>{{ activityActionTotals.cancelledBySelf }}</strong> cancelaciones hechas por el pasajero
            </p>
          </section>
        </TabPanel>
      </TabPanels>
    </Tabs>
  </section>
</template>

<style scoped>
.reports-view {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}
.reports-header h1 {
  margin: 0 0 0.25rem;
}
.reports-subtitle {
  margin: 0;
  color: #52525b;
  font-size: 0.9375rem;
  max-width: 56rem;
}
.tab-icon {
  font-size: 0.875rem;
  margin-right: 0.375rem;
}

.report-filters,
.seat-filter {
  display: flex;
  align-items: flex-end;
  gap: 0.75rem;
  flex-wrap: wrap;
  margin: 1rem 0;
}
.filter {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}
.filter label {
  font-size: 0.8125rem;
  font-weight: 600;
  color: #52525b;
}
.filter-actions {
  display: flex;
  gap: 0.5rem;
}

.reports-table :deep(.p-datatable-tbody td) {
  vertical-align: middle;
}

.reports-error {
  color: #b91c1c;
  margin: 0;
}
.reports-empty {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 1rem 0;
  color: #71717a;
}
.reports-total {
  margin: 0.5rem 0 0;
  font-size: 0.875rem;
  color: #71717a;
}
.reports-hint {
  margin: 1rem 0;
  color: #71717a;
}

.required-mark {
  color: #b91c1c;
}
.field-error {
  color: #b91c1c;
  font-size: 0.875rem;
  margin: 0;
}

@media (prefers-color-scheme: dark) {
  .reports-subtitle {
    color: #a1a1aa;
  }
  .reports-error,
  .field-error,
  .required-mark {
    color: #fca5a5;
  }
  .reports-empty,
  .reports-total,
  .reports-hint {
    color: #a1a1aa;
  }
  .filter label {
    color: #a1a1aa;
  }
}

/* === Secciones del reporte Actividad por usuario (Opcion C) === */
.activity-section {
  margin-top: 1.5rem;
}
.activity-section-header {
  margin-bottom: 0.5rem;
}
.activity-section-header h3 {
  margin: 0 0 0.25rem;
  font-size: 1.05rem;
  color: #18181b;
}
.activity-section-hint {
  margin: 0 0 0.5rem;
  color: #71717a;
  font-size: 0.85rem;
}
.activity-section-hint strong {
  color: #52525b;
}
.count-pos {
  color: #15803d;
  font-weight: 600;
}
.count-pending {
  color: #b45309;
  font-weight: 600;
}
.count-neg {
  color: #b91c1c;
  font-weight: 600;
}
.count-zero {
  color: #a1a1aa;
}
.reports-total .legend-pos {
  color: #15803d;
}
.reports-total .legend-pending {
  color: #b45309;
}
.reports-total .legend-neg {
  color: #b91c1c;
}
</style>