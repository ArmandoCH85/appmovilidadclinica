// Package admin implementa el modulo de administracion: CRUD de las tablas
// maestras, transiciones de estado de viajes, generacion manual de instancias
// y reportes de las vistas de apoyo. Sigue la arquitectura de 3 capas; el
// repositorio es la unica puerta a la BD y usa database/sql con sentencias
// preparadas. Las transiciones de estado y la generacion se delegan a SPs.
package admin

import (
	"context"
	"database/sql"
	"fmt"
	"strings"
	"time"

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/apperror"
	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/dberr"
	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/types"
)

// ----------------------------------------------------------------------------
// Tipos de dominio (filas de las tablas maestras)
// ----------------------------------------------------------------------------

// Stop refleja una fila de transport_stops.
type Stop struct {
	ID            int64    `json:"id"`
	Code          string   `json:"code"`
	Name          string   `json:"name"`
	StopType      string   `json:"stop_type"`
	ReferenceText *string  `json:"reference_text,omitempty"`
	Latitude      *float64 `json:"latitude,omitempty"`
	Longitude     *float64 `json:"longitude,omitempty"`
	Active        bool     `json:"active"`
}

// StopCreateParams son los campos requeridos para crear una parada.
type StopCreateParams struct {
	Code          string   `json:"code" validate:"required,max=30"`
	Name          string   `json:"name" validate:"required,max=150"`
	StopType      string   `json:"stop_type" validate:"required,oneof=SEDE PARADERO"`
	ReferenceText *string  `json:"reference_text,omitempty" validate:"omitempty,max=255"`
	Latitude      *float64 `json:"latitude,omitempty"`
	Longitude     *float64 `json:"longitude,omitempty"`
	Active        bool     `json:"active"`
}

// StopUpdateParams actualiza los campos editables de una parada.
type StopUpdateParams struct {
	Code          string   `json:"code" validate:"required,max=30"`
	Name          string   `json:"name" validate:"required,max=150"`
	StopType      string   `json:"stop_type" validate:"required,oneof=SEDE PARADERO"`
	ReferenceText *string  `json:"reference_text,omitempty" validate:"omitempty,max=255"`
	Latitude      *float64 `json:"latitude,omitempty"`
	Longitude     *float64 `json:"longitude,omitempty"`
	Active        bool     `json:"active"`
}

// User refleja una fila de users (sin password_hash).
type User struct {
	ID              int64   `json:"id"`
	EmployeeCode    string  `json:"employee_code"`
	DocumentNumber  string  `json:"document_number"`
	FullName        string  `json:"full_name"`
	Role            string  `json:"role"`
	Department      *string `json:"department,omitempty"`
	Phone           *string `json:"phone,omitempty"`
	PreferredStopID *int64  `json:"preferred_stop_id,omitempty"`
	Active          bool    `json:"active"`
}

// UserCreateParams crea un usuario. Password llega en texto plano (TLS); el
// servicio la hashea con bcrypt antes de invocar al repositorio, que ya
// recibe el hash listo para persistir.
type UserCreateParams struct {
	EmployeeCode    string  `json:"employee_code" validate:"required,max=30"`
	DocumentNumber  string  `json:"document_number" validate:"required,max=20"`
	Password        string  `json:"password" validate:"required"`
	FullName        string  `json:"full_name" validate:"required,max=150"`
	Role            string  `json:"role" validate:"required,oneof=ADMIN DRIVER WORKER"`
	Department      *string `json:"department,omitempty" validate:"omitempty,max=100"`
	Phone           *string `json:"phone,omitempty" validate:"omitempty,max=25"`
	PreferredStopID *int64  `json:"preferred_stop_id,omitempty"`
	Active          bool    `json:"active"`
}

// UserUpdateParams actualiza un usuario. Password es opcional en texto plano:
// si llega vacia no se modifica el hash existente; si llega, el servicio la
// hashea antes de pasarla al repositorio.
type UserUpdateParams struct {
	EmployeeCode    string  `json:"employee_code" validate:"required,max=30"`
	DocumentNumber  string  `json:"document_number" validate:"required,max=20"`
	Password        string  `json:"password,omitempty"`
	FullName        string  `json:"full_name" validate:"required,max=150"`
	Role            string  `json:"role" validate:"required,oneof=ADMIN DRIVER WORKER"`
	Department      *string `json:"department,omitempty" validate:"omitempty,max=100"`
	Phone           *string `json:"phone,omitempty" validate:"omitempty,max=25"`
	PreferredStopID *int64  `json:"preferred_stop_id,omitempty"`
	Active          bool    `json:"active"`
}

// Vehicle refleja una fila de vehicles.
type Vehicle struct {
	ID           int64   `json:"id"`
	InternalCode string  `json:"internal_code"`
	Plate        string  `json:"plate"`
	Description  *string `json:"description,omitempty"`
	SeatCapacity int     `json:"seat_capacity"`
	Active       bool    `json:"active"`
}

// VehicleCreateParams crea un vehiculo.
type VehicleCreateParams struct {
	InternalCode string  `json:"internal_code" validate:"required,max=30"`
	Plate        string  `json:"plate" validate:"required,max=15"`
	Description  *string `json:"description,omitempty" validate:"omitempty,max=120"`
	SeatCapacity int     `json:"seat_capacity" validate:"required,gt=0"`
	Active       bool    `json:"active"`
}

// VehicleUpdateParams actualiza un vehiculo.
type VehicleUpdateParams struct {
	InternalCode string  `json:"internal_code" validate:"required,max=30"`
	Plate        string  `json:"plate" validate:"required,max=15"`
	Description  *string `json:"description,omitempty" validate:"omitempty,max=120"`
	SeatCapacity int     `json:"seat_capacity" validate:"required,gt=0"`
	Active       bool    `json:"active"`
}

// Route refleja una fila de transport_routes.
type Route struct {
	ID            int64  `json:"id"`
	Code          string `json:"code"`
	Name          string `json:"name"`
	Direction     string `json:"direction"`
	PairedRouteID *int64 `json:"paired_route_id,omitempty"`
	Active        bool   `json:"active"`
}

// RouteCreateParams crea una ruta.
type RouteCreateParams struct {
	Code          string `json:"code" validate:"required,max=40"`
	Name          string `json:"name" validate:"required,max=150"`
	Direction     string `json:"direction" validate:"required,oneof=IDA VUELTA"`
	PairedRouteID *int64 `json:"paired_route_id,omitempty"`
	Active        bool   `json:"active"`
}

// RouteUpdateParams actualiza una ruta.
type RouteUpdateParams struct {
	Code          string `json:"code" validate:"required,max=40"`
	Name          string `json:"name" validate:"required,max=150"`
	Direction     string `json:"direction" validate:"required,oneof=IDA VUELTA"`
	PairedRouteID *int64 `json:"paired_route_id,omitempty"`
	Active        bool   `json:"active"`
}

// RouteStop refleja una fila de route_stops.
type RouteStop struct {
	ID             int64 `json:"id"`
	RouteID        int64 `json:"route_id"`
	StopID         int64 `json:"stop_id"`
	StopOrder      int   `json:"stop_order"`
	DwellMinutes   int   `json:"dwell_minutes"`
	PickupAllowed  bool  `json:"pickup_allowed"`
	DropoffAllowed bool  `json:"dropoff_allowed"`
}

// RouteStopCreateParams crea una parada de ruta.
type RouteStopCreateParams struct {
	RouteID        int64 `json:"route_id" validate:"required,gt=0"`
	StopID         int64 `json:"stop_id" validate:"required,gt=0"`
	StopOrder      int   `json:"stop_order" validate:"required,gt=0"`
	DwellMinutes   int   `json:"dwell_minutes" validate:"gte=0"`
	PickupAllowed  bool  `json:"pickup_allowed"`
	DropoffAllowed bool  `json:"dropoff_allowed"`
}

// RouteStopUpdateParams actualiza una parada de ruta.
type RouteStopUpdateParams struct {
	RouteID        int64 `json:"route_id" validate:"required,gt=0"`
	StopID         int64 `json:"stop_id" validate:"required,gt=0"`
	StopOrder      int   `json:"stop_order" validate:"required,gt=0"`
	DwellMinutes   int   `json:"dwell_minutes" validate:"gte=0"`
	PickupAllowed  bool  `json:"pickup_allowed"`
	DropoffAllowed bool  `json:"dropoff_allowed"`
}

// Template refleja una fila de trip_templates.
type Template struct {
	ID                        int64  `json:"id"`
	Code                      string `json:"code"`
	Name                      string `json:"name"`
	RouteID                   int64  `json:"route_id"`
	ServiceCalendarID         int64  `json:"service_calendar_id"`
	DepartureTime             string `json:"departure_time"`
	DefaultVehicleID          int64  `json:"default_vehicle_id"`
	DefaultDriverID           int64  `json:"default_driver_id"`
	ProfileReferenceMode      string `json:"profile_reference_mode"`
	BookingOpenDaysBefore     int    `json:"booking_open_days_before"`
	BookingCloseMinutesBefore int    `json:"booking_close_minutes_before"`
	NoShowToleranceMinutes    int    `json:"no_show_tolerance_minutes"`
	AutomaticPublish          bool   `json:"automatic_publish"`
	Active                    bool   `json:"active"`
}

// TemplateCreateParams crea una plantilla de viaje.
type TemplateCreateParams struct {
	Code                      string `json:"code" validate:"required,max=50"`
	Name                      string `json:"name" validate:"required,max=150"`
	RouteID                   int64  `json:"route_id" validate:"required,gt=0"`
	ServiceCalendarID         int64  `json:"service_calendar_id" validate:"required,gt=0"`
	DepartureTime             string `json:"departure_time" validate:"required"`
	DefaultVehicleID          int64  `json:"default_vehicle_id" validate:"required,gt=0"`
	DefaultDriverID           int64  `json:"default_driver_id" validate:"required,gt=0"`
	ProfileReferenceMode      string `json:"profile_reference_mode" validate:"required,oneof=TRIP_DEPARTURE SEGMENT_DEPARTURE"`
	BookingOpenDaysBefore     int    `json:"booking_open_days_before" validate:"gte=0"`
	BookingCloseMinutesBefore int    `json:"booking_close_minutes_before" validate:"gte=0"`
	NoShowToleranceMinutes    int    `json:"no_show_tolerance_minutes" validate:"gte=0"`
	AutomaticPublish          bool   `json:"automatic_publish"`
	Active                    bool   `json:"active"`
}

// TemplateUpdateParams actualiza una plantilla de viaje.
type TemplateUpdateParams = TemplateCreateParams

// Calendar refleja una fila de service_calendars. ExceptionCount y
// TemplateCount se completan con subqueries en List/Get (son 0 en Create) para
// que el admin vea de un vistazo cuantos registros lo referencian — caso
// contrario desactivarlo podria romper la generacion de viajes sin aviso.
type Calendar struct {
	ID             int64     `json:"id"`
	Code           string    `json:"code"`
	Name           string    `json:"name"`
	ValidFrom      string    `json:"valid_from"`
	ValidUntil     string    `json:"valid_until"`
	Monday         bool      `json:"monday"`
	Tuesday        bool      `json:"tuesday"`
	Wednesday      bool      `json:"wednesday"`
	Thursday       bool      `json:"thursday"`
	Friday         bool      `json:"friday"`
	Saturday       bool      `json:"saturday"`
	Sunday         bool      `json:"sunday"`
	Active         bool      `json:"active"`
	ExceptionCount int       `json:"exception_count"`
	TemplateCount  int       `json:"template_count"`
	CreatedAt      time.Time `json:"created_at"`
	UpdatedAt      time.Time `json:"updated_at"`
}

// CalendarCreateParams crea un calendario de servicio.
type CalendarCreateParams struct {
	Code       string `json:"code" validate:"required,max=40"`
	Name       string `json:"name" validate:"required,max=120"`
	ValidFrom  string `json:"valid_from" validate:"required"`
	ValidUntil string `json:"valid_until" validate:"required"`
	Monday     bool   `json:"monday"`
	Tuesday    bool   `json:"tuesday"`
	Wednesday  bool   `json:"wednesday"`
	Thursday   bool   `json:"thursday"`
	Friday     bool   `json:"friday"`
	Saturday   bool   `json:"saturday"`
	Sunday     bool   `json:"sunday"`
	Active     bool   `json:"active"`
}

// CalendarUpdateParams actualiza un calendario de servicio.
type CalendarUpdateParams = CalendarCreateParams

// RouteSegment refleja una fila de route_segments.
type RouteSegment struct {
	ID              int64 `json:"id"`
	RouteID         int64 `json:"route_id"`
	SegmentOrder    int   `json:"segment_order"`
	FromRouteStopID int64 `json:"from_route_stop_id"`
	ToRouteStopID   int64 `json:"to_route_stop_id"`
	Active          bool  `json:"active"`
}

// RouteSegmentCreateParams crea un tramo de ruta.
type RouteSegmentCreateParams struct {
	RouteID         int64 `json:"route_id" validate:"required,gt=0"`
	SegmentOrder    int   `json:"segment_order" validate:"required,gt=0"`
	FromRouteStopID int64 `json:"from_route_stop_id" validate:"required,gt=0"`
	ToRouteStopID   int64 `json:"to_route_stop_id" validate:"required,gt=0"`
	Active          bool  `json:"active"`
}

// RouteSegmentUpdateParams actualiza un tramo de ruta.
type RouteSegmentUpdateParams = RouteSegmentCreateParams

// TravelTimeProfile refleja una fila de travel_time_profiles.
type TravelTimeProfile struct {
	ID         int64   `json:"id"`
	Code       string  `json:"code"`
	Name       string  `json:"name"`
	ValidFrom  *string `json:"valid_from,omitempty"`
	ValidUntil *string `json:"valid_until,omitempty"`
	StartTime  *string `json:"start_time,omitempty"`
	EndTime    *string `json:"end_time,omitempty"`
	IsAllDay   bool    `json:"is_all_day"`
	Monday     bool    `json:"monday"`
	Tuesday    bool    `json:"tuesday"`
	Wednesday  bool    `json:"wednesday"`
	Thursday   bool    `json:"thursday"`
	Friday     bool    `json:"friday"`
	Saturday   bool    `json:"saturday"`
	Sunday     bool    `json:"sunday"`
	Priority   int     `json:"priority"`
	IsDefault  bool    `json:"is_default"`
	Active     bool    `json:"active"`
}

// TravelTimeProfileCreateParams crea un perfil de tiempos de viaje.
type TravelTimeProfileCreateParams struct {
	Code       string  `json:"code" validate:"required,max=40"`
	Name       string  `json:"name" validate:"required,max=120"`
	ValidFrom  *string `json:"valid_from,omitempty"`
	ValidUntil *string `json:"valid_until,omitempty"`
	StartTime  *string `json:"start_time,omitempty"`
	EndTime    *string `json:"end_time,omitempty"`
	IsAllDay   bool    `json:"is_all_day"`
	Monday     bool    `json:"monday"`
	Tuesday    bool    `json:"tuesday"`
	Wednesday  bool    `json:"wednesday"`
	Thursday   bool    `json:"thursday"`
	Friday     bool    `json:"friday"`
	Saturday   bool    `json:"saturday"`
	Sunday     bool    `json:"sunday"`
	Priority   int     `json:"priority" validate:"gte=0"`
	IsDefault  bool    `json:"is_default"`
	Active     bool    `json:"active"`
}

// TravelTimeProfileUpdateParams actualiza un perfil de tiempos de viaje.
type TravelTimeProfileUpdateParams = TravelTimeProfileCreateParams

// RouteSegmentTravelTime refleja una fila de route_segment_travel_times.
type RouteSegmentTravelTime struct {
	ID             int64   `json:"id"`
	RouteSegmentID int64   `json:"route_segment_id"`
	ProfileID      int64   `json:"profile_id"`
	TravelMinutes  int     `json:"travel_minutes"`
	Notes          *string `json:"notes,omitempty"`
}

// RouteSegmentTravelTimeCreateParams crea un tiempo de tramo.
type RouteSegmentTravelTimeCreateParams struct {
	RouteSegmentID int64   `json:"route_segment_id" validate:"required,gt=0"`
	ProfileID      int64   `json:"profile_id" validate:"required,gt=0"`
	TravelMinutes  int     `json:"travel_minutes" validate:"required,gt=0"`
	Notes          *string `json:"notes,omitempty" validate:"omitempty,max=255"`
}

// RouteSegmentTravelTimeUpdateParams actualiza un tiempo de tramo.
type RouteSegmentTravelTimeUpdateParams = RouteSegmentTravelTimeCreateParams

// Conflict refleja una fila de vw_schedule_conflicts.
type Conflict struct {
	ResourceType  string    `json:"resource_type"`
	ResourceID    int64     `json:"resource_id"`
	FirstTripID   int64     `json:"first_trip_id"`
	SecondTripID  int64     `json:"second_trip_id"`
	FirstStartAt  time.Time `json:"first_start_at"`
	FirstEndAt    time.Time `json:"first_end_at"`
	SecondStartAt time.Time `json:"second_start_at"`
	SecondEndAt   time.Time `json:"second_end_at"`
}

// MatrixEntry refleja una fila de vw_route_time_matrix.
type MatrixEntry struct {
	RouteID        int64  `json:"route_id"`
	RouteCode      string `json:"route_code"`
	RouteName      string `json:"route_name"`
	Direction      string `json:"direction"`
	RouteSegmentID int64  `json:"route_segment_id"`
	SegmentOrder   int    `json:"segment_order"`
	FromStopCode   string `json:"from_stop_code"`
	FromStopName   string `json:"from_stop_name"`
	ToStopCode     string `json:"to_stop_code"`
	ToStopName     string `json:"to_stop_name"`
	ProfileID      int64  `json:"profile_id"`
	ProfileCode    string `json:"profile_code"`
	ProfileName    string `json:"profile_name"`
	TravelMinutes  int    `json:"travel_minutes"`
	Priority       int    `json:"priority"`
}

// SeatAvail refleja una fila de vw_trip_segment_seat_availability.
type SeatAvail struct {
	TripID          int64      `json:"trip_id"`
	TripCode        string     `json:"trip_code"`
	ServiceDate     string     `json:"service_date"`
	Direction       string     `json:"direction"`
	TripSeatID      int64      `json:"trip_seat_id"`
	SeatNumber      int        `json:"seat_number"`
	SeatLabel       string     `json:"seat_label"`
	SegmentOrder    int        `json:"segment_order"`
	AvailableFrom   string     `json:"available_or_occupied_from"`
	AvailableUntil  string     `json:"available_or_occupied_until"`
	State           string     `json:"state"`
	ReservationID   *int64     `json:"reservation_id,omitempty"`
	ReservationCode *string    `json:"reservation_code,omitempty"`
	ReservedAt      *time.Time `json:"reserved_at,omitempty"`
	ReleasedAt      *time.Time `json:"released_at,omitempty"`
}

type VehicleSeat struct {
	ID          int64   `json:"id"`
	VehicleID   int64   `json:"vehicle_id"`
	SeatNumber  int     `json:"seat_number"`
	SeatLabel   string  `json:"seat_label"`
	Status      string  `json:"status"`
	BlockReason *string `json:"block_reason,omitempty"`
}

type VehicleSeatCreateParams struct {
	VehicleID   int64   `json:"vehicle_id" validate:"required,gt=0"`
	SeatNumber  int     `json:"seat_number" validate:"required,gt=0"`
	SeatLabel   string  `json:"seat_label" validate:"required,max=10"`
	Status      string  `json:"status" validate:"required,oneof=ACTIVE BLOCKED RETIRED"`
	BlockReason *string `json:"block_reason,omitempty" validate:"omitempty,max=255"`
}

type VehicleSeatUpdateParams = VehicleSeatCreateParams

type CalendarException struct {
	ID            int64     `json:"id"`
	CalendarID    int64     `json:"calendar_id"`
	CalendarCode  string    `json:"calendar_code,omitempty"`
	CalendarName  string    `json:"calendar_name,omitempty"`
	ExceptionDate string    `json:"exception_date"`
	Operation     string    `json:"operation"`
	Reason        *string   `json:"reason,omitempty"`
	CreatedAt     time.Time `json:"created_at"`
	UpdatedAt     time.Time `json:"updated_at"`
}

type CalendarExceptionCreateParams struct {
	CalendarID    int64   `json:"calendar_id" validate:"required,gt=0"`
	ExceptionDate string  `json:"exception_date" validate:"required"`
	Operation     string  `json:"operation" validate:"required,oneof=ADD REMOVE"`
	Reason        *string `json:"reason,omitempty" validate:"omitempty,max=255"`
}

type CalendarExceptionUpdateParams = CalendarExceptionCreateParams

type TripInstance struct {
	ID                     int64      `json:"id"`
	TripCode               string     `json:"trip_code"`
	Source                 string     `json:"source"`
	TripTemplateID         *int64     `json:"trip_template_id,omitempty"`
	GenerationRunID        *int64     `json:"generation_run_id,omitempty"`
	RouteID                int64      `json:"route_id"`
	ServiceDate            string     `json:"service_date"`
	ScheduledStartAt       time.Time  `json:"scheduled_start_at"`
	ScheduledEndAt         time.Time  `json:"scheduled_end_at"`
	BookingOpensAt         time.Time  `json:"booking_opens_at"`
	BookingClosesAt        time.Time  `json:"booking_closes_at"`
	VehicleID              int64      `json:"vehicle_id"`
	DriverID               int64      `json:"driver_id"`
	SeatCapacitySnapshot   int        `json:"seat_capacity_snapshot"`
	NoShowToleranceMinutes int        `json:"no_show_tolerance_minutes"`
	Status                 string     `json:"status"`
	ActualStartAt          *time.Time `json:"actual_start_at,omitempty"`
	ActualEndAt            *time.Time `json:"actual_end_at,omitempty"`
	CancellationReason     *string    `json:"cancellation_reason,omitempty"`
}

type TripIncident struct {
	ID                       int64      `json:"id"`
	TripID                   int64      `json:"trip_id"`
	TripCode                 string     `json:"trip_code,omitempty"`
	TripServiceDate          string     `json:"trip_service_date,omitempty"`
	TripRouteCode            string     `json:"trip_route_code,omitempty"`
	TripRouteName            string     `json:"trip_route_name,omitempty"`
	ReportedByUserID         int64      `json:"reported_by_user_id"`
	ReportedByFullName       string     `json:"reported_by_full_name,omitempty"`
	ReportedByEmployeeCode   string     `json:"reported_by_employee_code,omitempty"`
	IncidentType             string     `json:"incident_type"`
	Description              string     `json:"description"`
	Status                   string     `json:"status"`
	ReportedAt               time.Time  `json:"reported_at"`
	ResolvedAt               *time.Time `json:"resolved_at,omitempty"`
	ResolutionNotes          *string    `json:"resolution_notes,omitempty"`
}

type GenerationRun struct {
	ID                int64      `json:"id"`
	WindowStart       string     `json:"window_start"`
	WindowEnd         string     `json:"window_end"`
	Status            string     `json:"status"`
	GeneratedCount    int        `json:"generated_count"`
	SkippedCount      int        `json:"skipped_count"`
	FailedCount       int        `json:"failed_count"`
	ErrorSummary      *string    `json:"error_summary,omitempty"`
	TriggeredByUserID *int64     `json:"triggered_by_user_id,omitempty"`
	// Datos del usuario que disparo la corrida (enriquecido via LEFT JOIN).
	// Solo para lectura; null cuando fue el job automatico (triggered_by_user_id IS NULL).
	TriggeredByFullName *string `json:"triggered_by_full_name,omitempty"`
	// Cantidad de trip_instances que genero esta corrida (FK reversa
	// trip_instances.generation_run_id). 0 si todavia no termino o si fallo.
	TripCount int `json:"trip_count"`
	// Duracion en segundos, derivada de finished_at - started_at.
	// Null mientras la corrida esta RUNNING.
	DurationSeconds *int `json:"duration_seconds,omitempty"`
	StartedAt       time.Time  `json:"started_at"`
	FinishedAt      *time.Time `json:"finished_at,omitempty"`
}

// ----------------------------------------------------------------------------
// Interfaz del repositorio
// ----------------------------------------------------------------------------

// AdminRepository abstrae el acceso a BD del modulo admin. Cubre el CRUD de
// las tablas maestras, las operaciones de viajes y los reportes de las vistas.
type AdminRepository interface {
	// Paradas
	ListStops(ctx context.Context, pg types.PaginationParams) ([]Stop, int, error)
	CreateStop(ctx context.Context, p StopCreateParams) (Stop, error)
	UpdateStop(ctx context.Context, id int64, p StopUpdateParams) error

	// Usuarios
	ListUsers(ctx context.Context, pg types.PaginationParams) ([]User, int, error)
	CreateUser(ctx context.Context, p UserCreateParams) (User, error)
	UpdateUser(ctx context.Context, id int64, p UserUpdateParams) error

	// Vehiculos
	ListVehicles(ctx context.Context, pg types.PaginationParams) ([]Vehicle, int, error)
	CreateVehicle(ctx context.Context, p VehicleCreateParams) (Vehicle, error)
	UpdateVehicle(ctx context.Context, id int64, p VehicleUpdateParams) error

	// Rutas
	ListRoutes(ctx context.Context, pg types.PaginationParams) ([]Route, int, error)
	CreateRoute(ctx context.Context, p RouteCreateParams) (Route, error)
	UpdateRoute(ctx context.Context, id int64, p RouteUpdateParams) error

	// Paradas de ruta
	ListRouteStops(ctx context.Context, routeID int64, pg types.PaginationParams) ([]RouteStop, int, error)
	CreateRouteStop(ctx context.Context, p RouteStopCreateParams) (RouteStop, error)
	UpdateRouteStop(ctx context.Context, id int64, p RouteStopUpdateParams) error

	// Plantillas de viaje
	ListTemplates(ctx context.Context, pg types.PaginationParams) ([]Template, int, error)
	CreateTemplate(ctx context.Context, p TemplateCreateParams) (Template, error)
	UpdateTemplate(ctx context.Context, id int64, p TemplateUpdateParams) error

	// Calendarios de servicio
	ListCalendars(ctx context.Context, pg types.PaginationParams) ([]Calendar, int, error)
	GetCalendar(ctx context.Context, id int64) (Calendar, error)
	CreateCalendar(ctx context.Context, p CalendarCreateParams) (Calendar, error)
	UpdateCalendar(ctx context.Context, id int64, p CalendarUpdateParams) error

	// Tramos de ruta
	ListRouteSegments(ctx context.Context, pg types.PaginationParams) ([]RouteSegment, int, error)
	CreateRouteSegment(ctx context.Context, p RouteSegmentCreateParams) (RouteSegment, error)
	UpdateRouteSegment(ctx context.Context, id int64, p RouteSegmentUpdateParams) error

	// Perfiles de tiempo de viaje
	ListTravelTimeProfiles(ctx context.Context, pg types.PaginationParams) ([]TravelTimeProfile, int, error)
	CreateTravelTimeProfile(ctx context.Context, p TravelTimeProfileCreateParams) (TravelTimeProfile, error)
	UpdateTravelTimeProfile(ctx context.Context, id int64, p TravelTimeProfileUpdateParams) error

	// Tiempos de tramo por perfil
	ListRouteSegmentTravelTimes(ctx context.Context, pg types.PaginationParams) ([]RouteSegmentTravelTime, int, error)
	CreateRouteSegmentTravelTime(ctx context.Context, p RouteSegmentTravelTimeCreateParams) (RouteSegmentTravelTime, error)
	UpdateRouteSegmentTravelTime(ctx context.Context, id int64, p RouteSegmentTravelTimeUpdateParams) error

	// Asientos de vehiculo
	ListVehicleSeats(ctx context.Context, vehicleID int64, pg types.PaginationParams) ([]VehicleSeat, int, error)
	CreateVehicleSeat(ctx context.Context, p VehicleSeatCreateParams) (VehicleSeat, error)
	UpdateVehicleSeat(ctx context.Context, id int64, p VehicleSeatUpdateParams) error

	// Excepciones de calendario
	ListCalendarExceptions(ctx context.Context, calendarID int64, pg types.PaginationParams) ([]CalendarException, int, error)
	GetCalendarException(ctx context.Context, id int64) (CalendarException, error)
	CreateCalendarException(ctx context.Context, p CalendarExceptionCreateParams) (CalendarException, error)
	UpdateCalendarException(ctx context.Context, id int64, p CalendarExceptionUpdateParams) error

	// Listados de solo lectura
	ListTrips(ctx context.Context, date, status string, routeID int64, pg types.PaginationParams) ([]TripInstance, int, error)
	ListIncidents(ctx context.Context, status, incidentType, dateFrom, dateTo string, pg types.PaginationParams) ([]TripIncident, int, error)
	GetIncident(ctx context.Context, id int64) (TripIncident, error)
	UpdateIncident(ctx context.Context, id int64, status string, resolutionNotes *string) (TripIncident, error)
	ListGenerationRuns(ctx context.Context, status, dateFrom, dateTo string, triggeredByUserID int64, pg types.PaginationParams) ([]GenerationRun, int, error)
	GetGenerationRun(ctx context.Context, id int64) (GenerationRun, []TripInstance, error)

	// Operaciones de viajes
	UpdateTripStatus(ctx context.Context, tripID int64, status string) error
	TriggerManualGeneration(ctx context.Context, templateID int64, serviceDate string) error

	// Reportes (vistas)
	GetScheduleConflicts(ctx context.Context, resourceType, dateFrom, dateTo string) ([]Conflict, error)
	GetRouteTimeMatrix(ctx context.Context, routeID int64, direction string, profileID int64) ([]MatrixEntry, error)
	GetTripSeatAvailability(ctx context.Context, tripID int64, state string) ([]SeatAvail, error)
}

// adminRepository es la implementacion concreta con database/sql.
type adminRepository struct {
	db *sql.DB
}

// NewRepository construye el repositorio.
func NewRepository(db *sql.DB) AdminRepository {
	return &adminRepository{db: db}
}

// ----------------------------------------------------------------------------
// Paradas (transport_stops)
// ----------------------------------------------------------------------------

// ListStops devuelve la pagina solicitada de paradas y el total de filas.
func (r *adminRepository) ListStops(ctx context.Context, pg types.PaginationParams) ([]Stop, int, error) {
	pg.Normalize()
	const q = `
        SELECT id, code, name, stop_type, reference_text, latitude, longitude, active
          FROM transport_stops
         ORDER BY id
         LIMIT ? OFFSET ?`
	rows, err := r.db.QueryContext(ctx, q, pg.Limit(), pg.Offset())
	if err != nil {
		return nil, 0, fmt.Errorf("listando paradas: %w", err)
	}
	defer rows.Close()

	var stops []Stop
	for rows.Next() {
		var s Stop
		var ref sql.NullString
		var lat, lon sql.NullFloat64
		if err := rows.Scan(&s.ID, &s.Code, &s.Name, &s.StopType, &ref, &lat, &lon, &s.Active); err != nil {
			return nil, 0, fmt.Errorf("escaneando parada: %w", err)
		}
		s.ReferenceText = nullableStr(ref)
		s.Latitude = nullableFloat(lat)
		s.Longitude = nullableFloat(lon)
		stops = append(stops, s)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, err
	}
	total, err := r.count(ctx, "transport_stops", "")
	if err != nil {
		return nil, 0, err
	}
	return stops, total, nil
}

// CreateStop inserta una parada y devuelve la fila creada.
func (r *adminRepository) CreateStop(ctx context.Context, p StopCreateParams) (Stop, error) {
	res, err := r.db.ExecContext(ctx, `
        INSERT INTO transport_stops (code, name, stop_type, reference_text, latitude, longitude, active)
        VALUES (?, ?, ?, ?, ?, ?, ?)`,
		p.Code, p.Name, p.StopType, p.ReferenceText, p.Latitude, p.Longitude, p.Active)
	if err != nil {
		return Stop{}, fmt.Errorf("creando parada: %w", err)
	}
	id, err := res.LastInsertId()
	if err != nil {
		return Stop{}, fmt.Errorf("obteniendo id de parada: %w", err)
	}
	return Stop{
		ID: id, Code: p.Code, Name: p.Name, StopType: p.StopType,
		ReferenceText: p.ReferenceText, Latitude: p.Latitude, Longitude: p.Longitude,
		Active: p.Active,
	}, nil
}

// UpdateStop actualiza una parada por id.
func (r *adminRepository) UpdateStop(ctx context.Context, id int64, p StopUpdateParams) error {
	res, err := r.db.ExecContext(ctx, `
        UPDATE transport_stops
           SET code = ?, name = ?, stop_type = ?, reference_text = ?,
               latitude = ?, longitude = ?, active = ?
         WHERE id = ?`,
		p.Code, p.Name, p.StopType, p.ReferenceText, p.Latitude, p.Longitude, p.Active, id)
	if err != nil {
		return fmt.Errorf("actualizando parada: %w", err)
	}
	return ensureAffected(res, "parada", id)
}

// ----------------------------------------------------------------------------
// Usuarios (users)
// ----------------------------------------------------------------------------

// ListUsers devuelve la pagina de usuarios (sin password_hash).
func (r *adminRepository) ListUsers(ctx context.Context, pg types.PaginationParams) ([]User, int, error) {
	pg.Normalize()
	const q = `
        SELECT id, employee_code, document_number, full_name, role,
               department, phone, preferred_stop_id, active
          FROM users
         ORDER BY id
         LIMIT ? OFFSET ?`
	rows, err := r.db.QueryContext(ctx, q, pg.Limit(), pg.Offset())
	if err != nil {
		return nil, 0, fmt.Errorf("listando usuarios: %w", err)
	}
	defer rows.Close()

	var users []User
	for rows.Next() {
		var u User
		var dept, phone sql.NullString
		var prefStop sql.NullInt64
		if err := rows.Scan(&u.ID, &u.EmployeeCode, &u.DocumentNumber, &u.FullName,
			&u.Role, &dept, &phone, &prefStop, &u.Active); err != nil {
			return nil, 0, fmt.Errorf("escaneando usuario: %w", err)
		}
		u.Department = nullableStr(dept)
		u.Phone = nullableStr(phone)
		u.PreferredStopID = nullableInt(prefStop)
		users = append(users, u)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, err
	}
	total, err := r.count(ctx, "users", "")
	if err != nil {
		return nil, 0, err
	}
	return users, total, nil
}

// CreateUser inserta un usuario y devuelve la fila creada (sin password_hash).
// p.Password ya llega hasheada con bcrypt: el servicio la hashea antes de
// invocar este metodo (el repositorio no conoce bcrypt).
func (r *adminRepository) CreateUser(ctx context.Context, p UserCreateParams) (User, error) {
	res, err := r.db.ExecContext(ctx, `
        INSERT INTO users (employee_code, document_number, password_hash, full_name,
               role, department, phone, preferred_stop_id, active)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		p.EmployeeCode, p.DocumentNumber, p.Password, p.FullName, p.Role,
		p.Department, p.Phone, p.PreferredStopID, p.Active)
	if err != nil {
		return User{}, fmt.Errorf("creando usuario: %w", err)
	}
	id, err := res.LastInsertId()
	if err != nil {
		return User{}, fmt.Errorf("obteniendo id de usuario: %w", err)
	}
	return User{
		ID: id, EmployeeCode: p.EmployeeCode, DocumentNumber: p.DocumentNumber,
		FullName: p.FullName, Role: p.Role, Department: p.Department, Phone: p.Phone,
		PreferredStopID: p.PreferredStopID, Active: p.Active,
	}, nil
}

// UpdateUser actualiza un usuario. Si p.Password viene vacio no se modifica
// el hash existente; si viene, ya llega hasheada con bcrypt por el servicio.
func (r *adminRepository) UpdateUser(ctx context.Context, id int64, p UserUpdateParams) error {
	var res sql.Result
	var err error
	if p.Password == "" {
		res, err = r.db.ExecContext(ctx, `
            UPDATE users
               SET employee_code = ?, document_number = ?, full_name = ?, role = ?,
                   department = ?, phone = ?, preferred_stop_id = ?, active = ?
             WHERE id = ?`,
			p.EmployeeCode, p.DocumentNumber, p.FullName, p.Role,
			p.Department, p.Phone, p.PreferredStopID, p.Active, id)
	} else {
		res, err = r.db.ExecContext(ctx, `
            UPDATE users
               SET employee_code = ?, document_number = ?, password_hash = ?, full_name = ?,
                   role = ?, department = ?, phone = ?, preferred_stop_id = ?, active = ?
             WHERE id = ?`,
			p.EmployeeCode, p.DocumentNumber, p.Password, p.FullName, p.Role,
			p.Department, p.Phone, p.PreferredStopID, p.Active, id)
	}
	if err != nil {
		return fmt.Errorf("actualizando usuario: %w", err)
	}
	return ensureAffected(res, "usuario", id)
}

// ----------------------------------------------------------------------------
// Vehiculos (vehicles)
// ----------------------------------------------------------------------------

// ListVehicles devuelve la pagina de vehiculos.
func (r *adminRepository) ListVehicles(ctx context.Context, pg types.PaginationParams) ([]Vehicle, int, error) {
	pg.Normalize()
	const q = `
        SELECT id, internal_code, plate, description, seat_capacity, active
          FROM vehicles
         ORDER BY id
         LIMIT ? OFFSET ?`
	rows, err := r.db.QueryContext(ctx, q, pg.Limit(), pg.Offset())
	if err != nil {
		return nil, 0, fmt.Errorf("listando vehiculos: %w", err)
	}
	defer rows.Close()

	var vehicles []Vehicle
	for rows.Next() {
		var v Vehicle
		var desc sql.NullString
		if err := rows.Scan(&v.ID, &v.InternalCode, &v.Plate, &desc, &v.SeatCapacity, &v.Active); err != nil {
			return nil, 0, fmt.Errorf("escaneando vehiculo: %w", err)
		}
		v.Description = nullableStr(desc)
		vehicles = append(vehicles, v)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, err
	}
	total, err := r.count(ctx, "vehicles", "")
	if err != nil {
		return nil, 0, err
	}
	return vehicles, total, nil
}

// CreateVehicle inserta un vehiculo.
func (r *adminRepository) CreateVehicle(ctx context.Context, p VehicleCreateParams) (Vehicle, error) {
	res, err := r.db.ExecContext(ctx, `
        INSERT INTO vehicles (internal_code, plate, description, seat_capacity, active)
        VALUES (?, ?, ?, ?, ?)`,
		p.InternalCode, p.Plate, p.Description, p.SeatCapacity, p.Active)
	if err != nil {
		return Vehicle{}, fmt.Errorf("creando vehiculo: %w", err)
	}
	id, err := res.LastInsertId()
	if err != nil {
		return Vehicle{}, fmt.Errorf("obteniendo id de vehiculo: %w", err)
	}
	return Vehicle{
		ID: id, InternalCode: p.InternalCode, Plate: p.Plate,
		Description: p.Description, SeatCapacity: p.SeatCapacity, Active: p.Active,
	}, nil
}

// UpdateVehicle actualiza un vehiculo por id.
func (r *adminRepository) UpdateVehicle(ctx context.Context, id int64, p VehicleUpdateParams) error {
	res, err := r.db.ExecContext(ctx, `
        UPDATE vehicles
           SET internal_code = ?, plate = ?, description = ?, seat_capacity = ?, active = ?
         WHERE id = ?`,
		p.InternalCode, p.Plate, p.Description, p.SeatCapacity, p.Active, id)
	if err != nil {
		return fmt.Errorf("actualizando vehiculo: %w", err)
	}
	return ensureAffected(res, "vehiculo", id)
}

// ----------------------------------------------------------------------------
// Rutas (transport_routes)
// ----------------------------------------------------------------------------

// ListRoutes devuelve la pagina de rutas.
func (r *adminRepository) ListRoutes(ctx context.Context, pg types.PaginationParams) ([]Route, int, error) {
	pg.Normalize()
	const q = `
        SELECT id, code, name, direction, paired_route_id, active
          FROM transport_routes
         ORDER BY id
         LIMIT ? OFFSET ?`
	rows, err := r.db.QueryContext(ctx, q, pg.Limit(), pg.Offset())
	if err != nil {
		return nil, 0, fmt.Errorf("listando rutas: %w", err)
	}
	defer rows.Close()

	var routes []Route
	for rows.Next() {
		var rt Route
		var paired sql.NullInt64
		if err := rows.Scan(&rt.ID, &rt.Code, &rt.Name, &rt.Direction, &paired, &rt.Active); err != nil {
			return nil, 0, fmt.Errorf("escaneando ruta: %w", err)
		}
		rt.PairedRouteID = nullableInt(paired)
		routes = append(routes, rt)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, err
	}
	total, err := r.count(ctx, "transport_routes", "")
	if err != nil {
		return nil, 0, err
	}
	return routes, total, nil
}

// CreateRoute inserta una ruta.
func (r *adminRepository) CreateRoute(ctx context.Context, p RouteCreateParams) (Route, error) {
	res, err := r.db.ExecContext(ctx, `
        INSERT INTO transport_routes (code, name, direction, paired_route_id, active)
        VALUES (?, ?, ?, ?, ?)`,
		p.Code, p.Name, p.Direction, p.PairedRouteID, p.Active)
	if err != nil {
		return Route{}, fmt.Errorf("creando ruta: %w", err)
	}
	id, err := res.LastInsertId()
	if err != nil {
		return Route{}, fmt.Errorf("obteniendo id de ruta: %w", err)
	}
	return Route{
		ID: id, Code: p.Code, Name: p.Name, Direction: p.Direction,
		PairedRouteID: p.PairedRouteID, Active: p.Active,
	}, nil
}

// UpdateRoute actualiza una ruta por id.
func (r *adminRepository) UpdateRoute(ctx context.Context, id int64, p RouteUpdateParams) error {
	res, err := r.db.ExecContext(ctx, `
        UPDATE transport_routes
           SET code = ?, name = ?, direction = ?, paired_route_id = ?, active = ?
         WHERE id = ?`,
		p.Code, p.Name, p.Direction, p.PairedRouteID, p.Active, id)
	if err != nil {
		return fmt.Errorf("actualizando ruta: %w", err)
	}
	return ensureAffected(res, "ruta", id)
}

// ----------------------------------------------------------------------------
// Paradas de ruta (route_stops)
// ----------------------------------------------------------------------------

// ListRouteStops devuelve las paradas de una ruta con paginacion.
func (r *adminRepository) ListRouteStops(ctx context.Context, routeID int64, pg types.PaginationParams) ([]RouteStop, int, error) {
	pg.Normalize()
	const q = `
        SELECT id, route_id, stop_id, stop_order, dwell_minutes,
               pickup_allowed, dropoff_allowed
          FROM route_stops
         WHERE route_id = ?
         ORDER BY stop_order
         LIMIT ? OFFSET ?`
	rows, err := r.db.QueryContext(ctx, q, routeID, pg.Limit(), pg.Offset())
	if err != nil {
		return nil, 0, fmt.Errorf("listando paradas de ruta: %w", err)
	}
	defer rows.Close()

	var rstops []RouteStop
	for rows.Next() {
		var rs RouteStop
		if err := rows.Scan(&rs.ID, &rs.RouteID, &rs.StopID, &rs.StopOrder,
			&rs.DwellMinutes, &rs.PickupAllowed, &rs.DropoffAllowed); err != nil {
			return nil, 0, fmt.Errorf("escaneando parada de ruta: %w", err)
		}
		rstops = append(rstops, rs)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, err
	}
	total, err := r.count(ctx, "route_stops", "route_id = ?", routeID)
	if err != nil {
		return nil, 0, err
	}
	return rstops, total, nil
}

// CreateRouteStop inserta una parada de ruta.
func (r *adminRepository) CreateRouteStop(ctx context.Context, p RouteStopCreateParams) (RouteStop, error) {
	res, err := r.db.ExecContext(ctx, `
        INSERT INTO route_stops (route_id, stop_id, stop_order, dwell_minutes,
               pickup_allowed, dropoff_allowed)
        VALUES (?, ?, ?, ?, ?, ?)`,
		p.RouteID, p.StopID, p.StopOrder, p.DwellMinutes, p.PickupAllowed, p.DropoffAllowed)
	if err != nil {
		return RouteStop{}, fmt.Errorf("creando parada de ruta: %w", err)
	}
	id, err := res.LastInsertId()
	if err != nil {
		return RouteStop{}, fmt.Errorf("obteniendo id de parada de ruta: %w", err)
	}
	return RouteStop{
		ID: id, RouteID: p.RouteID, StopID: p.StopID, StopOrder: p.StopOrder,
		DwellMinutes: p.DwellMinutes, PickupAllowed: p.PickupAllowed,
		DropoffAllowed: p.DropoffAllowed,
	}, nil
}

// UpdateRouteStop actualiza una parada de ruta por id.
func (r *adminRepository) UpdateRouteStop(ctx context.Context, id int64, p RouteStopUpdateParams) error {
	res, err := r.db.ExecContext(ctx, `
        UPDATE route_stops
           SET route_id = ?, stop_id = ?, stop_order = ?, dwell_minutes = ?,
               pickup_allowed = ?, dropoff_allowed = ?
         WHERE id = ?`,
		p.RouteID, p.StopID, p.StopOrder, p.DwellMinutes, p.PickupAllowed,
		p.DropoffAllowed, id)
	if err != nil {
		// trg_route_stops_protect_structure (0001_schema.up.sql) rechaza con
		// SIGNAL '45000' un cambio de route_id/stop_id/stop_order si la
		// parada ya tiene tramos armados en route_segments — sin esta
		// traduccion caia como 500 generico (mismo caso que route_segments).
		if spErr := dberr.TranslateSP(err); spErr != err {
			return spErr
		}
		return fmt.Errorf("actualizando parada de ruta: %w", err)
	}
	return ensureAffected(res, "parada de ruta", id)
}

// ----------------------------------------------------------------------------
// Plantillas de viaje (trip_templates)
// ----------------------------------------------------------------------------

// ListTemplates devuelve la pagina de plantillas.
func (r *adminRepository) ListTemplates(ctx context.Context, pg types.PaginationParams) ([]Template, int, error) {
	pg.Normalize()
	const q = `
        SELECT id, code, name, route_id, service_calendar_id, departure_time,
               default_vehicle_id, default_driver_id, profile_reference_mode,
               booking_open_days_before, booking_close_minutes_before,
               no_show_tolerance_minutes, automatic_publish, active
          FROM trip_templates
         ORDER BY id
         LIMIT ? OFFSET ?`
	rows, err := r.db.QueryContext(ctx, q, pg.Limit(), pg.Offset())
	if err != nil {
		return nil, 0, fmt.Errorf("listando plantillas: %w", err)
	}
	defer rows.Close()

	var templates []Template
	for rows.Next() {
		var t Template
		var departure sql.NullString
		if err := rows.Scan(&t.ID, &t.Code, &t.Name, &t.RouteID, &t.ServiceCalendarID,
			&departure, &t.DefaultVehicleID, &t.DefaultDriverID, &t.ProfileReferenceMode,
			&t.BookingOpenDaysBefore, &t.BookingCloseMinutesBefore,
			&t.NoShowToleranceMinutes, &t.AutomaticPublish, &t.Active); err != nil {
			return nil, 0, fmt.Errorf("escaneando plantilla: %w", err)
		}
		if departure.Valid {
			t.DepartureTime = departure.String
		}
		templates = append(templates, t)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, err
	}
	total, err := r.count(ctx, "trip_templates", "")
	if err != nil {
		return nil, 0, err
	}
	return templates, total, nil
}

// CreateTemplate inserta una plantilla de viaje.
func (r *adminRepository) CreateTemplate(ctx context.Context, p TemplateCreateParams) (Template, error) {
	res, err := r.db.ExecContext(ctx, `
        INSERT INTO trip_templates (code, name, route_id, service_calendar_id, departure_time,
               default_vehicle_id, default_driver_id, profile_reference_mode,
               booking_open_days_before, booking_close_minutes_before,
               no_show_tolerance_minutes, automatic_publish, active)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		p.Code, p.Name, p.RouteID, p.ServiceCalendarID, p.DepartureTime,
		p.DefaultVehicleID, p.DefaultDriverID, p.ProfileReferenceMode,
		p.BookingOpenDaysBefore, p.BookingCloseMinutesBefore,
		p.NoShowToleranceMinutes, p.AutomaticPublish, p.Active)
	if err != nil {
		return Template{}, fmt.Errorf("creando plantilla: %w", err)
	}
	id, err := res.LastInsertId()
	if err != nil {
		return Template{}, fmt.Errorf("obteniendo id de plantilla: %w", err)
	}
	return Template{
		ID: id, Code: p.Code, Name: p.Name, RouteID: p.RouteID,
		ServiceCalendarID: p.ServiceCalendarID, DepartureTime: p.DepartureTime,
		DefaultVehicleID: p.DefaultVehicleID, DefaultDriverID: p.DefaultDriverID,
		ProfileReferenceMode:      p.ProfileReferenceMode,
		BookingOpenDaysBefore:     p.BookingOpenDaysBefore,
		BookingCloseMinutesBefore: p.BookingCloseMinutesBefore,
		NoShowToleranceMinutes:    p.NoShowToleranceMinutes,
		AutomaticPublish:          p.AutomaticPublish, Active: p.Active,
	}, nil
}

// UpdateTemplate actualiza una plantilla por id.
func (r *adminRepository) UpdateTemplate(ctx context.Context, id int64, p TemplateUpdateParams) error {
	res, err := r.db.ExecContext(ctx, `
        UPDATE trip_templates
           SET code = ?, name = ?, route_id = ?, service_calendar_id = ?, departure_time = ?,
               default_vehicle_id = ?, default_driver_id = ?, profile_reference_mode = ?,
               booking_open_days_before = ?, booking_close_minutes_before = ?,
               no_show_tolerance_minutes = ?, automatic_publish = ?, active = ?
         WHERE id = ?`,
		p.Code, p.Name, p.RouteID, p.ServiceCalendarID, p.DepartureTime,
		p.DefaultVehicleID, p.DefaultDriverID, p.ProfileReferenceMode,
		p.BookingOpenDaysBefore, p.BookingCloseMinutesBefore,
		p.NoShowToleranceMinutes, p.AutomaticPublish, p.Active, id)
	if err != nil {
		return fmt.Errorf("actualizando plantilla: %w", err)
	}
	return ensureAffected(res, "plantilla", id)
}

// ----------------------------------------------------------------------------
// Calendarios de servicio (service_calendars)
// ----------------------------------------------------------------------------

// ListCalendars devuelve la pagina de calendarios.
func (r *adminRepository) ListCalendars(ctx context.Context, pg types.PaginationParams) ([]Calendar, int, error) {
	pg.Normalize()
	const q = `
        SELECT c.id, c.code, c.name,
               DATE_FORMAT(c.valid_from, '%Y-%m-%d') AS valid_from,
               DATE_FORMAT(c.valid_until, '%Y-%m-%d') AS valid_until,
               c.monday, c.tuesday, c.wednesday, c.thursday, c.friday,
               c.saturday, c.sunday, c.active,
               (SELECT COUNT(*) FROM service_calendar_exceptions e WHERE e.calendar_id = c.id) AS exception_count,
               (SELECT COUNT(*) FROM trip_templates t WHERE t.service_calendar_id = c.id) AS template_count,
               c.created_at, c.updated_at
          FROM service_calendars c
         ORDER BY c.id
         LIMIT ? OFFSET ?`
	rows, err := r.db.QueryContext(ctx, q, pg.Limit(), pg.Offset())
	if err != nil {
		return nil, 0, fmt.Errorf("listando calendarios: %w", err)
	}
	defer rows.Close()

	var cals []Calendar
	for rows.Next() {
		var c Calendar
		var validFrom, validUntil sql.NullString
		if err := rows.Scan(&c.ID, &c.Code, &c.Name, &validFrom, &validUntil,
			&c.Monday, &c.Tuesday, &c.Wednesday, &c.Thursday, &c.Friday,
			&c.Saturday, &c.Sunday, &c.Active,
			&c.ExceptionCount, &c.TemplateCount,
			&c.CreatedAt, &c.UpdatedAt); err != nil {
			return nil, 0, fmt.Errorf("escaneando calendario: %w", err)
		}
		if validFrom.Valid {
			c.ValidFrom = validFrom.String
		}
		if validUntil.Valid {
			c.ValidUntil = validUntil.String
		}
		cals = append(cals, c)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, err
	}
	total, err := r.count(ctx, "service_calendars", "")
	if err != nil {
		return nil, 0, err
	}
	return cals, total, nil
}

// GetCalendar devuelve un calendario por ID con conteos de referencias.
func (r *adminRepository) GetCalendar(ctx context.Context, id int64) (Calendar, error) {
	const q = `
        SELECT c.id, c.code, c.name,
               DATE_FORMAT(c.valid_from, '%Y-%m-%d') AS valid_from,
               DATE_FORMAT(c.valid_until, '%Y-%m-%d') AS valid_until,
               c.monday, c.tuesday, c.wednesday, c.thursday, c.friday,
               c.saturday, c.sunday, c.active,
               (SELECT COUNT(*) FROM service_calendar_exceptions e WHERE e.calendar_id = c.id) AS exception_count,
               (SELECT COUNT(*) FROM trip_templates t WHERE t.service_calendar_id = c.id) AS template_count,
               c.created_at, c.updated_at
          FROM service_calendars c
         WHERE c.id = ?`
	var c Calendar
	var validFrom, validUntil sql.NullString
	err := r.db.QueryRowContext(ctx, q, id).Scan(&c.ID, &c.Code, &c.Name, &validFrom, &validUntil,
		&c.Monday, &c.Tuesday, &c.Wednesday, &c.Thursday, &c.Friday,
		&c.Saturday, &c.Sunday, &c.Active,
		&c.ExceptionCount, &c.TemplateCount,
		&c.CreatedAt, &c.UpdatedAt)
	if err != nil {
		if err == sql.ErrNoRows {
			return Calendar{}, apperror.NotFoundError{Entity: "calendario", ID: id}
		}
		return Calendar{}, fmt.Errorf("obteniendo calendario: %w", err)
	}
	if validFrom.Valid {
		c.ValidFrom = validFrom.String
	}
	if validUntil.Valid {
		c.ValidUntil = validUntil.String
	}
	return c, nil
}

// CreateCalendar inserta un calendario y devuelve el row completo
// (releyendolo via GetCalendar) con timestamps + conteos consistentes.
func (r *adminRepository) CreateCalendar(ctx context.Context, p CalendarCreateParams) (Calendar, error) {
	res, err := r.db.ExecContext(ctx, `
        INSERT INTO service_calendars (code, name, valid_from, valid_until,
               monday, tuesday, wednesday, thursday, friday, saturday, sunday, active)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		p.Code, p.Name, p.ValidFrom, p.ValidUntil,
		p.Monday, p.Tuesday, p.Wednesday, p.Thursday, p.Friday, p.Saturday,
		p.Sunday, p.Active)
	if err != nil {
		return Calendar{}, dberr.TranslatePlainSQL(err, "calendario", "")
	}
	id, err := res.LastInsertId()
	if err != nil {
		return Calendar{}, fmt.Errorf("obteniendo id de calendario: %w", err)
	}
	return r.GetCalendar(ctx, id)
}

// UpdateCalendar actualiza un calendario por id y traduce errores MySQL
// (UNIQUE duplicado en code -> 409).
func (r *adminRepository) UpdateCalendar(ctx context.Context, id int64, p CalendarUpdateParams) error {
	res, err := r.db.ExecContext(ctx, `
        UPDATE service_calendars
           SET code = ?, name = ?, valid_from = ?, valid_until = ?,
               monday = ?, tuesday = ?, wednesday = ?, thursday = ?,
               friday = ?, saturday = ?, sunday = ?, active = ?
         WHERE id = ?`,
		p.Code, p.Name, p.ValidFrom, p.ValidUntil,
		p.Monday, p.Tuesday, p.Wednesday, p.Thursday, p.Friday, p.Saturday,
		p.Sunday, p.Active, id)
	if err != nil {
		return dberr.TranslatePlainSQL(err, "calendario", "")
	}
	return ensureAffected(res, "calendario", id)
}

// ----------------------------------------------------------------------------
// Tramos de ruta (route_segments)
// ----------------------------------------------------------------------------

// ListRouteSegments devuelve la pagina de tramos de ruta.
func (r *adminRepository) ListRouteSegments(ctx context.Context, pg types.PaginationParams) ([]RouteSegment, int, error) {
	pg.Normalize()
	const q = `
        SELECT id, route_id, segment_order, from_route_stop_id, to_route_stop_id, active
          FROM route_segments
         ORDER BY id
         LIMIT ? OFFSET ?`
	rows, err := r.db.QueryContext(ctx, q, pg.Limit(), pg.Offset())
	if err != nil {
		return nil, 0, fmt.Errorf("listando tramos de ruta: %w", err)
	}
	defer rows.Close()

	var segs []RouteSegment
	for rows.Next() {
		var s RouteSegment
		if err := rows.Scan(&s.ID, &s.RouteID, &s.SegmentOrder,
			&s.FromRouteStopID, &s.ToRouteStopID, &s.Active); err != nil {
			return nil, 0, fmt.Errorf("escaneando tramo de ruta: %w", err)
		}
		segs = append(segs, s)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, err
	}
	total, err := r.count(ctx, "route_segments", "")
	if err != nil {
		return nil, 0, err
	}
	return segs, total, nil
}

// CreateRouteSegment inserta un tramo de ruta.
func (r *adminRepository) CreateRouteSegment(ctx context.Context, p RouteSegmentCreateParams) (RouteSegment, error) {
	res, err := r.db.ExecContext(ctx, `
        INSERT INTO route_segments (route_id, segment_order, from_route_stop_id, to_route_stop_id, active)
        VALUES (?, ?, ?, ?, ?)`,
		p.RouteID, p.SegmentOrder, p.FromRouteStopID, p.ToRouteStopID, p.Active)
	if err != nil {
		// trg_route_segments_validate_insert (0001_schema.up.sql) rechaza
		// con SIGNAL '45000' un tramo entre paradas no consecutivas o de
		// otra ruta — sin esta traduccion cae como 500 generico.
		if spErr := dberr.TranslateSP(err); spErr != err {
			return RouteSegment{}, spErr
		}
		return RouteSegment{}, fmt.Errorf("creando tramo de ruta: %w", err)
	}
	id, err := res.LastInsertId()
	if err != nil {
		return RouteSegment{}, fmt.Errorf("obteniendo id de tramo de ruta: %w", err)
	}
	return RouteSegment{
		ID: id, RouteID: p.RouteID, SegmentOrder: p.SegmentOrder,
		FromRouteStopID: p.FromRouteStopID, ToRouteStopID: p.ToRouteStopID,
		Active: p.Active,
	}, nil
}

// UpdateRouteSegment actualiza un tramo de ruta por id.
func (r *adminRepository) UpdateRouteSegment(ctx context.Context, id int64, p RouteSegmentUpdateParams) error {
	res, err := r.db.ExecContext(ctx, `
        UPDATE route_segments
           SET route_id = ?, segment_order = ?, from_route_stop_id = ?, to_route_stop_id = ?, active = ?
         WHERE id = ?`,
		p.RouteID, p.SegmentOrder, p.FromRouteStopID, p.ToRouteStopID, p.Active, id)
	if err != nil {
		// trg_route_segments_validate_update — misma regla que en el insert.
		if spErr := dberr.TranslateSP(err); spErr != err {
			return spErr
		}
		return fmt.Errorf("actualizando tramo de ruta: %w", err)
	}
	return ensureAffected(res, "tramo de ruta", id)
}

// ----------------------------------------------------------------------------
// Perfiles de tiempo de viaje (travel_time_profiles)
// ----------------------------------------------------------------------------

// ListTravelTimeProfiles devuelve la pagina de perfiles.
func (r *adminRepository) ListTravelTimeProfiles(ctx context.Context, pg types.PaginationParams) ([]TravelTimeProfile, int, error) {
	pg.Normalize()
	const q = `
        SELECT id, code, name,
               DATE_FORMAT(valid_from, '%Y-%m-%d') AS valid_from,
               DATE_FORMAT(valid_until, '%Y-%m-%d') AS valid_until,
               start_time, end_time,
               is_all_day, monday, tuesday, wednesday, thursday, friday, saturday, sunday,
               priority, is_default, active
          FROM travel_time_profiles
         ORDER BY id
         LIMIT ? OFFSET ?`
	rows, err := r.db.QueryContext(ctx, q, pg.Limit(), pg.Offset())
	if err != nil {
		return nil, 0, fmt.Errorf("listando perfiles de tiempo: %w", err)
	}
	defer rows.Close()

	var profs []TravelTimeProfile
	for rows.Next() {
		var p TravelTimeProfile
		var validFrom, validUntil, startTime, endTime sql.NullString
		if err := rows.Scan(&p.ID, &p.Code, &p.Name, &validFrom, &validUntil,
			&startTime, &endTime, &p.IsAllDay,
			&p.Monday, &p.Tuesday, &p.Wednesday, &p.Thursday, &p.Friday,
			&p.Saturday, &p.Sunday, &p.Priority, &p.IsDefault, &p.Active); err != nil {
			return nil, 0, fmt.Errorf("escaneando perfil de tiempo: %w", err)
		}
		p.ValidFrom = nullableStr(validFrom)
		p.ValidUntil = nullableStr(validUntil)
		p.StartTime = nullableStr(startTime)
		p.EndTime = nullableStr(endTime)
		profs = append(profs, p)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, err
	}
	total, err := r.count(ctx, "travel_time_profiles", "")
	if err != nil {
		return nil, 0, err
	}
	return profs, total, nil
}

// CreateTravelTimeProfile inserta un perfil de tiempo de viaje.
func (r *adminRepository) CreateTravelTimeProfile(ctx context.Context, p TravelTimeProfileCreateParams) (TravelTimeProfile, error) {
	res, err := r.db.ExecContext(ctx, `
        INSERT INTO travel_time_profiles (code, name, valid_from, valid_until, start_time, end_time,
               is_all_day, monday, tuesday, wednesday, thursday, friday, saturday, sunday,
               priority, is_default, active)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		p.Code, p.Name, p.ValidFrom, p.ValidUntil, p.StartTime, p.EndTime,
		p.IsAllDay, p.Monday, p.Tuesday, p.Wednesday, p.Thursday, p.Friday,
		p.Saturday, p.Sunday, p.Priority, p.IsDefault, p.Active)
	if err != nil {
		return TravelTimeProfile{}, fmt.Errorf("creando perfil de tiempo: %w", err)
	}
	id, err := res.LastInsertId()
	if err != nil {
		return TravelTimeProfile{}, fmt.Errorf("obteniendo id de perfil de tiempo: %w", err)
	}
	return TravelTimeProfile{
		ID: id, Code: p.Code, Name: p.Name,
		ValidFrom: p.ValidFrom, ValidUntil: p.ValidUntil,
		StartTime: p.StartTime, EndTime: p.EndTime,
		IsAllDay: p.IsAllDay, Monday: p.Monday, Tuesday: p.Tuesday,
		Wednesday: p.Wednesday, Thursday: p.Thursday, Friday: p.Friday,
		Saturday: p.Saturday, Sunday: p.Sunday,
		Priority: p.Priority, IsDefault: p.IsDefault, Active: p.Active,
	}, nil
}

// UpdateTravelTimeProfile actualiza un perfil de tiempo por id.
func (r *adminRepository) UpdateTravelTimeProfile(ctx context.Context, id int64, p TravelTimeProfileUpdateParams) error {
	res, err := r.db.ExecContext(ctx, `
        UPDATE travel_time_profiles
           SET code = ?, name = ?, valid_from = ?, valid_until = ?, start_time = ?, end_time = ?,
               is_all_day = ?, monday = ?, tuesday = ?, wednesday = ?, thursday = ?,
               friday = ?, saturday = ?, sunday = ?, priority = ?, is_default = ?, active = ?
         WHERE id = ?`,
		p.Code, p.Name, p.ValidFrom, p.ValidUntil, p.StartTime, p.EndTime,
		p.IsAllDay, p.Monday, p.Tuesday, p.Wednesday, p.Thursday, p.Friday,
		p.Saturday, p.Sunday, p.Priority, p.IsDefault, p.Active, id)
	if err != nil {
		return fmt.Errorf("actualizando perfil de tiempo: %w", err)
	}
	return ensureAffected(res, "perfil de tiempo", id)
}

// ----------------------------------------------------------------------------
// Tiempos de tramo por perfil (route_segment_travel_times)
// ----------------------------------------------------------------------------

// ListRouteSegmentTravelTimes devuelve la pagina de tiempos de tramo.
func (r *adminRepository) ListRouteSegmentTravelTimes(ctx context.Context, pg types.PaginationParams) ([]RouteSegmentTravelTime, int, error) {
	pg.Normalize()
	const q = `
        SELECT id, route_segment_id, profile_id, travel_minutes, notes
          FROM route_segment_travel_times
         ORDER BY id
         LIMIT ? OFFSET ?`
	rows, err := r.db.QueryContext(ctx, q, pg.Limit(), pg.Offset())
	if err != nil {
		return nil, 0, fmt.Errorf("listando tiempos de tramo: %w", err)
	}
	defer rows.Close()

	var items []RouteSegmentTravelTime
	for rows.Next() {
		var t RouteSegmentTravelTime
		var notes sql.NullString
		if err := rows.Scan(&t.ID, &t.RouteSegmentID, &t.ProfileID,
			&t.TravelMinutes, &notes); err != nil {
			return nil, 0, fmt.Errorf("escaneando tiempo de tramo: %w", err)
		}
		t.Notes = nullableStr(notes)
		items = append(items, t)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, err
	}
	total, err := r.count(ctx, "route_segment_travel_times", "")
	if err != nil {
		return nil, 0, err
	}
	return items, total, nil
}

// CreateRouteSegmentTravelTime inserta un tiempo de tramo.
func (r *adminRepository) CreateRouteSegmentTravelTime(ctx context.Context, p RouteSegmentTravelTimeCreateParams) (RouteSegmentTravelTime, error) {
	res, err := r.db.ExecContext(ctx, `
        INSERT INTO route_segment_travel_times (route_segment_id, profile_id, travel_minutes, notes)
        VALUES (?, ?, ?, ?)`,
		p.RouteSegmentID, p.ProfileID, p.TravelMinutes, p.Notes)
	if err != nil {
		return RouteSegmentTravelTime{}, fmt.Errorf("creando tiempo de tramo: %w", err)
	}
	id, err := res.LastInsertId()
	if err != nil {
		return RouteSegmentTravelTime{}, fmt.Errorf("obteniendo id de tiempo de tramo: %w", err)
	}
	return RouteSegmentTravelTime{
		ID: id, RouteSegmentID: p.RouteSegmentID, ProfileID: p.ProfileID,
		TravelMinutes: p.TravelMinutes, Notes: p.Notes,
	}, nil
}

// UpdateRouteSegmentTravelTime actualiza un tiempo de tramo por id.
func (r *adminRepository) UpdateRouteSegmentTravelTime(ctx context.Context, id int64, p RouteSegmentTravelTimeUpdateParams) error {
	res, err := r.db.ExecContext(ctx, `
        UPDATE route_segment_travel_times
           SET route_segment_id = ?, profile_id = ?, travel_minutes = ?, notes = ?
         WHERE id = ?`,
		p.RouteSegmentID, p.ProfileID, p.TravelMinutes, p.Notes, id)
	if err != nil {
		return fmt.Errorf("actualizando tiempo de tramo: %w", err)
	}
	return ensureAffected(res, "tiempo de tramo", id)
}

// ----------------------------------------------------------------------------
// Asientos de vehiculo (vehicle_seats)
// ----------------------------------------------------------------------------

func (r *adminRepository) ListVehicleSeats(ctx context.Context, vehicleID int64, pg types.PaginationParams) ([]VehicleSeat, int, error) {
	pg.Normalize()
	q := `SELECT id, vehicle_id, seat_number, seat_label, status, block_reason
           FROM vehicle_seats`
	var where string
	var fargs []any
	if vehicleID > 0 {
		where = "vehicle_id = ?"
		q += " WHERE " + where
		fargs = append(fargs, vehicleID)
	}
	q += " ORDER BY id LIMIT ? OFFSET ?"
	qargs := append([]any{}, fargs...)
	qargs = append(qargs, pg.Limit(), pg.Offset())
	rows, err := r.db.QueryContext(ctx, q, qargs...)
	if err != nil {
		return nil, 0, fmt.Errorf("listando asientos de vehiculo: %w", err)
	}
	defer rows.Close()

	var seats []VehicleSeat
	for rows.Next() {
		var s VehicleSeat
		var blockReason sql.NullString
		if err := rows.Scan(&s.ID, &s.VehicleID, &s.SeatNumber, &s.SeatLabel,
			&s.Status, &blockReason); err != nil {
			return nil, 0, fmt.Errorf("escaneando asiento de vehiculo: %w", err)
		}
		s.BlockReason = nullableStr(blockReason)
		seats = append(seats, s)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, err
	}
	total, err := r.count(ctx, "vehicle_seats", where, fargs...)
	if err != nil {
		return nil, 0, err
	}
	return seats, total, nil
}

func (r *adminRepository) CreateVehicleSeat(ctx context.Context, p VehicleSeatCreateParams) (VehicleSeat, error) {
	res, err := r.db.ExecContext(ctx, `
        INSERT INTO vehicle_seats (vehicle_id, seat_number, seat_label, status, block_reason)
        VALUES (?, ?, ?, ?, ?)`,
		p.VehicleID, p.SeatNumber, p.SeatLabel, p.Status, p.BlockReason)
	if err != nil {
		return VehicleSeat{}, fmt.Errorf("creando asiento de vehiculo: %w", err)
	}
	id, err := res.LastInsertId()
	if err != nil {
		return VehicleSeat{}, fmt.Errorf("obteniendo id de asiento de vehiculo: %w", err)
	}
	return VehicleSeat{
		ID: id, VehicleID: p.VehicleID, SeatNumber: p.SeatNumber,
		SeatLabel: p.SeatLabel, Status: p.Status, BlockReason: p.BlockReason,
	}, nil
}

func (r *adminRepository) UpdateVehicleSeat(ctx context.Context, id int64, p VehicleSeatUpdateParams) error {
	res, err := r.db.ExecContext(ctx, `
        UPDATE vehicle_seats
           SET vehicle_id = ?, seat_number = ?, seat_label = ?, status = ?, block_reason = ?
         WHERE id = ?`,
		p.VehicleID, p.SeatNumber, p.SeatLabel, p.Status, p.BlockReason, id)
	if err != nil {
		return fmt.Errorf("actualizando asiento de vehiculo: %w", err)
	}
	return ensureAffected(res, "asiento de vehiculo", id)
}

// ----------------------------------------------------------------------------
// Excepciones de calendario (service_calendar_exceptions)
// ----------------------------------------------------------------------------

func (r *adminRepository) ListCalendarExceptions(ctx context.Context, calendarID int64, pg types.PaginationParams) ([]CalendarException, int, error) {
	pg.Normalize()
	q := `SELECT e.id, e.calendar_id, c.code, c.name,
               DATE_FORMAT(e.exception_date, '%Y-%m-%d') AS exception_date,
               e.operation, e.reason,
               e.created_at, e.updated_at
          FROM service_calendar_exceptions e
          JOIN service_calendars c ON c.id = e.calendar_id`
	var where string
	var fargs []any
	if calendarID > 0 {
		where = "e.calendar_id = ?"
		q += " WHERE " + where
		fargs = append(fargs, calendarID)
	}
	q += " ORDER BY e.exception_date, e.id LIMIT ? OFFSET ?"
	qargs := append([]any{}, fargs...)
	qargs = append(qargs, pg.Limit(), pg.Offset())
	rows, err := r.db.QueryContext(ctx, q, qargs...)
	if err != nil {
		return nil, 0, fmt.Errorf("listando excepciones de calendario: %w", err)
	}
	defer rows.Close()

	var excs []CalendarException
	for rows.Next() {
		var e CalendarException
		var excDate sql.NullString
		var reason sql.NullString
		if err := rows.Scan(&e.ID, &e.CalendarID, &e.CalendarCode, &e.CalendarName,
			&excDate, &e.Operation, &reason,
			&e.CreatedAt, &e.UpdatedAt); err != nil {
			return nil, 0, fmt.Errorf("escaneando excepcion de calendario: %w", err)
		}
		if excDate.Valid {
			e.ExceptionDate = excDate.String
		}
		e.Reason = nullableStr(reason)
		excs = append(excs, e)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, err
	}
	total, err := r.count(ctx, "service_calendar_exceptions", where, fargs...)
	if err != nil {
		return nil, 0, err
	}
	return excs, total, nil
}

func (r *adminRepository) CreateCalendarException(ctx context.Context, p CalendarExceptionCreateParams) (CalendarException, error) {
	res, err := r.db.ExecContext(ctx, `
        INSERT INTO service_calendar_exceptions (calendar_id, exception_date, operation, reason)
        VALUES (?, ?, ?, ?)`,
		p.CalendarID, p.ExceptionDate, p.Operation, p.Reason)
	if err != nil {
		return CalendarException{}, dberr.TranslatePlainSQL(err, "excepcion de calendario", "calendario")
	}
	id, err := res.LastInsertId()
	if err != nil {
		return CalendarException{}, fmt.Errorf("obteniendo id de excepcion de calendario: %w", err)
	}
	return r.GetCalendarException(ctx, id)
}

func (r *adminRepository) UpdateCalendarException(ctx context.Context, id int64, p CalendarExceptionUpdateParams) error {
	res, err := r.db.ExecContext(ctx, `
        UPDATE service_calendar_exceptions
           SET calendar_id = ?, exception_date = ?, operation = ?, reason = ?
         WHERE id = ?`,
		p.CalendarID, p.ExceptionDate, p.Operation, p.Reason, id)
	if err != nil {
		return dberr.TranslatePlainSQL(err, "excepcion de calendario", "calendario")
	}
	return ensureAffected(res, "excepcion de calendario", id)
}

func (r *adminRepository) GetCalendarException(ctx context.Context, id int64) (CalendarException, error) {
	const q = `
        SELECT e.id, e.calendar_id, c.code, c.name,
               DATE_FORMAT(e.exception_date, '%Y-%m-%d') AS exception_date,
               e.operation, e.reason,
               e.created_at, e.updated_at
          FROM service_calendar_exceptions e
          JOIN service_calendars c ON c.id = e.calendar_id
         WHERE e.id = ?`
	var e CalendarException
	var excDate sql.NullString
	var reason sql.NullString
	err := r.db.QueryRowContext(ctx, q, id).Scan(&e.ID, &e.CalendarID, &e.CalendarCode, &e.CalendarName,
		&excDate, &e.Operation, &reason,
		&e.CreatedAt, &e.UpdatedAt)
	if err != nil {
		return CalendarException{}, dberr.NotFound(err, "excepcion de calendario", id)
	}
	if excDate.Valid {
		e.ExceptionDate = excDate.String
	}
	e.Reason = nullableStr(reason)
	return e, nil
}

// ----------------------------------------------------------------------------
// Listados de solo lectura (trip_instances, trip_incidents, trip_generation_runs)
// ----------------------------------------------------------------------------

func (r *adminRepository) ListTrips(ctx context.Context, date, status string, routeID int64, pg types.PaginationParams) ([]TripInstance, int, error) {
	pg.Normalize()
	q := `SELECT id, trip_code, source, trip_template_id, generation_run_id, route_id,
                 service_date, scheduled_start_at, scheduled_end_at, booking_opens_at,
                 booking_closes_at, vehicle_id, driver_id, seat_capacity_snapshot,
                 no_show_tolerance_minutes, status, actual_start_at, actual_end_at,
                 cancellation_reason
            FROM trip_instances`
	var conds []string
	var fargs []any
	if date != "" {
		conds = append(conds, "service_date = ?")
		fargs = append(fargs, date)
	}
	if status != "" {
		conds = append(conds, "status = ?")
		fargs = append(fargs, status)
	}
	if routeID > 0 {
		conds = append(conds, "route_id = ?")
		fargs = append(fargs, routeID)
	}
	where := strings.Join(conds, " AND ")
	if where != "" {
		q += " WHERE " + where
	}
	q += " ORDER BY id LIMIT ? OFFSET ?"
	qargs := append([]any{}, fargs...)
	qargs = append(qargs, pg.Limit(), pg.Offset())
	rows, err := r.db.QueryContext(ctx, q, qargs...)
	if err != nil {
		return nil, 0, fmt.Errorf("listando viajes: %w", err)
	}
	defer rows.Close()

	var trips []TripInstance
	for rows.Next() {
		var t TripInstance
		var tmplID, runID sql.NullInt64
		var svcDate sql.NullString
		var actualStart, actualEnd sql.NullTime
		var cancelReason sql.NullString
		if err := rows.Scan(&t.ID, &t.TripCode, &t.Source, &tmplID, &runID,
			&t.RouteID, &svcDate, &t.ScheduledStartAt, &t.ScheduledEndAt,
			&t.BookingOpensAt, &t.BookingClosesAt, &t.VehicleID, &t.DriverID,
			&t.SeatCapacitySnapshot, &t.NoShowToleranceMinutes, &t.Status,
			&actualStart, &actualEnd, &cancelReason); err != nil {
			return nil, 0, fmt.Errorf("escaneando viaje: %w", err)
		}
		t.TripTemplateID = nullableInt(tmplID)
		t.GenerationRunID = nullableInt(runID)
		if svcDate.Valid {
			t.ServiceDate = svcDate.String
		}
		t.ActualStartAt = nullableTime(actualStart)
		t.ActualEndAt = nullableTime(actualEnd)
		t.CancellationReason = nullableStr(cancelReason)
		trips = append(trips, t)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, err
	}
	total, err := r.count(ctx, "trip_instances", where, fargs...)
	if err != nil {
		return nil, 0, err
	}
	return trips, total, nil
}

// ListIncidents devuelve incidencias con filtros opcionales (status,
// incident_type, ventana de reported_at). Enriquece cada fila con
// datos del viaje asociado (trip_instances + transport_routes) y del
// usuario que reporto (users). La creacion la hace el driver via
// /api/driver/trips/{id}/incidents (no expuesta aca); el admin solo
// lista y resuelve (UpdateIncident).
func (r *adminRepository) ListIncidents(ctx context.Context, status, incidentType, dateFrom, dateTo string, pg types.PaginationParams) ([]TripIncident, int, error) {
	pg.Normalize()
	var conds []string
	var fargs []any
	if status != "" {
		conds = append(conds, "i.status = ?")
		fargs = append(fargs, status)
	}
	if incidentType != "" {
		conds = append(conds, "i.incident_type = ?")
		fargs = append(fargs, incidentType)
	}
	if dateFrom != "" {
		conds = append(conds, "i.reported_at >= ?")
		fargs = append(fargs, dateFrom)
	}
	if dateTo != "" {
		conds = append(conds, "i.reported_at <= ?")
		fargs = append(fargs, dateTo)
	}
	where := ""
	if len(conds) > 0 {
		where = " WHERE " + strings.Join(conds, " AND ")
	}

	q := `SELECT i.id, i.trip_id, t.trip_code,
               DATE_FORMAT(t.service_date, '%Y-%m-%d') AS trip_service_date,
               tr.code AS trip_route_code, tr.name AS trip_route_name,
               i.reported_by_user_id, u.full_name, u.employee_code,
               i.incident_type, i.description, i.status,
               i.reported_at, i.resolved_at, i.resolution_notes
          FROM trip_incidents i
          JOIN trip_instances t ON t.id = i.trip_id
          JOIN transport_routes tr ON tr.id = t.route_id
          JOIN users u ON u.id = i.reported_by_user_id` +
		where + `
         ORDER BY i.id DESC
         LIMIT ? OFFSET ?`
	qargs := append([]any{}, fargs...)
	qargs = append(qargs, pg.Limit(), pg.Offset())
	rows, err := r.db.QueryContext(ctx, q, qargs...)
	if err != nil {
		return nil, 0, fmt.Errorf("listando incidentes: %w", err)
	}
	defer rows.Close()

	var incs []TripIncident
	for rows.Next() {
		var i TripIncident
		var resolvedAt sql.NullTime
		var notes sql.NullString
		if err := rows.Scan(&i.ID, &i.TripID, &i.TripCode,
			&i.TripServiceDate, &i.TripRouteCode, &i.TripRouteName,
			&i.ReportedByUserID, &i.ReportedByFullName, &i.ReportedByEmployeeCode,
			&i.IncidentType, &i.Description, &i.Status,
			&i.ReportedAt, &resolvedAt, &notes); err != nil {
			return nil, 0, fmt.Errorf("escaneando incidente: %w", err)
		}
		i.ResolvedAt = nullableTime(resolvedAt)
		i.ResolutionNotes = nullableStr(notes)
		incs = append(incs, i)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, err
	}
	total, err := r.count(ctx, "trip_incidents", where, fargs...)
	if err != nil {
		return nil, 0, err
	}
	return incs, total, nil
}

// GetIncident devuelve una incidencia con todos sus campos enriquecidos
// (mismos JOINs que ListIncidents). 404 si no existe.
func (r *adminRepository) GetIncident(ctx context.Context, id int64) (TripIncident, error) {
	const q = `
        SELECT i.id, i.trip_id, t.trip_code,
               DATE_FORMAT(t.service_date, '%Y-%m-%d') AS trip_service_date,
               tr.code AS trip_route_code, tr.name AS trip_route_name,
               i.reported_by_user_id, u.full_name, u.employee_code,
               i.incident_type, i.description, i.status,
               i.reported_at, i.resolved_at, i.resolution_notes
          FROM trip_incidents i
          JOIN trip_instances t ON t.id = i.trip_id
          JOIN transport_routes tr ON tr.id = t.route_id
          JOIN users u ON u.id = i.reported_by_user_id
         WHERE i.id = ?`
	var i TripIncident
	var resolvedAt sql.NullTime
	var notes sql.NullString
	err := r.db.QueryRowContext(ctx, q, id).Scan(&i.ID, &i.TripID, &i.TripCode,
		&i.TripServiceDate, &i.TripRouteCode, &i.TripRouteName,
		&i.ReportedByUserID, &i.ReportedByFullName, &i.ReportedByEmployeeCode,
		&i.IncidentType, &i.Description, &i.Status,
		&i.ReportedAt, &resolvedAt, &notes)
	if err != nil {
		return TripIncident{}, dberr.NotFound(err, "incidencia", id)
	}
	i.ResolvedAt = nullableTime(resolvedAt)
	i.ResolutionNotes = nullableStr(notes)
	return i, nil
}

// UpdateIncident cambia status + resolution_notes de una incidencia.
// Cuando status pasa a RESOLVED, fija resolved_at=NOW() si venia NULL.
// Devuelve la fila refrescada para que la UI vea el cambio.
func (r *adminRepository) UpdateIncident(ctx context.Context, id int64, status string, resolutionNotes *string) (TripIncident, error) {
	// Si pasan a RESOLVED y resolved_at era NULL, lo fijamos. Si pasan
	// a un estado no-RESOLVED y venia con resolved_at, lo limpiamos
	// (re-abrir la incidencia).
	if status == "RESOLVED" {
		_, err := r.db.ExecContext(ctx, `
            UPDATE trip_incidents
               SET status = ?, resolution_notes = ?,
                   resolved_at = COALESCE(resolved_at, CURRENT_TIMESTAMP)
             WHERE id = ?`,
			status, resolutionNotes, id)
		if err != nil {
			return TripIncident{}, fmt.Errorf("actualizando estado de incidencia: %w", err)
		}
	} else {
		_, err := r.db.ExecContext(ctx, `
            UPDATE trip_incidents
               SET status = ?, resolution_notes = ?,
                   resolved_at = NULL
             WHERE id = ?`,
			status, resolutionNotes, id)
		if err != nil {
			return TripIncident{}, fmt.Errorf("actualizando estado de incidencia: %w", err)
		}
	}
	return r.GetIncident(ctx, id)
}

// ListGenerationRuns devuelve corridas con filtros opcionales (status,
// ventana de fechas del run, usuario que la disparo). Enriquece cada fila
// con el nombre del usuario (LEFT JOIN a users) y el conteo de trip_instances
// producidas (subquery correlacionada a trip_instances.generation_run_id).
// trip_generation_runs es append-only (auditoria del motor): la UI la trata
// como read-only, no hay Create/Update/Delete.
func (r *adminRepository) ListGenerationRuns(ctx context.Context, status, dateFrom, dateTo string, triggeredByUserID int64, pg types.PaginationParams) ([]GenerationRun, int, error) {
	pg.Normalize()
	var conds []string
	var fargs []any
	if status != "" {
		conds = append(conds, "r.status = ?")
		fargs = append(fargs, status)
	}
	if dateFrom != "" {
		conds = append(conds, "r.window_start >= ?")
		fargs = append(fargs, dateFrom)
	}
	if dateTo != "" {
		conds = append(conds, "r.window_end <= ?")
		fargs = append(fargs, dateTo)
	}
	if triggeredByUserID > 0 {
		conds = append(conds, "r.triggered_by_user_id = ?")
		fargs = append(fargs, triggeredByUserID)
	}
	where := ""
	if len(conds) > 0 {
		where = " WHERE " + strings.Join(conds, " AND ")
	}

	q := `SELECT r.id,
               DATE_FORMAT(r.window_start, '%Y-%m-%d') AS window_start,
               DATE_FORMAT(r.window_end, '%Y-%m-%d') AS window_end,
               r.status, r.generated_count, r.skipped_count, r.failed_count,
               r.error_summary, r.triggered_by_user_id, u.full_name,
               TIMESTAMPDIFF(SECOND, r.started_at, r.finished_at) AS duration_seconds,
               (SELECT COUNT(*) FROM trip_instances t WHERE t.generation_run_id = r.id) AS trip_count,
               r.started_at, r.finished_at
          FROM trip_generation_runs r
          LEFT JOIN users u ON u.id = r.triggered_by_user_id` +
		where + `
         ORDER BY r.id DESC
         LIMIT ? OFFSET ?`
	qargs := append([]any{}, fargs...)
	qargs = append(qargs, pg.Limit(), pg.Offset())
	rows, err := r.db.QueryContext(ctx, q, qargs...)
	if err != nil {
		return nil, 0, fmt.Errorf("listando corridas de generacion: %w", err)
	}
	defer rows.Close()

	var runs []GenerationRun
	for rows.Next() {
		var gr GenerationRun
		var winStart, winEnd sql.NullString
		var errSummary sql.NullString
		var triggeredBy sql.NullInt64
		var triggeredByName sql.NullString
		var durationSec sql.NullInt64
		var finishedAt sql.NullTime
		if err := rows.Scan(&gr.ID, &winStart, &winEnd, &gr.Status,
			&gr.GeneratedCount, &gr.SkippedCount, &gr.FailedCount,
			&errSummary, &triggeredBy, &triggeredByName,
			&durationSec, &gr.TripCount,
			&gr.StartedAt, &finishedAt); err != nil {
			return nil, 0, fmt.Errorf("escaneando corrida de generacion: %w", err)
		}
		if winStart.Valid {
			gr.WindowStart = winStart.String
		}
		if winEnd.Valid {
			gr.WindowEnd = winEnd.String
		}
		gr.ErrorSummary = nullableStr(errSummary)
		gr.TriggeredByUserID = nullableInt(triggeredBy)
		gr.TriggeredByFullName = nullableStr(triggeredByName)
		if durationSec.Valid {
			d := int(durationSec.Int64)
			gr.DurationSeconds = &d
		}
		gr.FinishedAt = nullableTime(finishedAt)
		runs = append(runs, gr)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, err
	}
	total, err := r.count(ctx, "trip_generation_runs", where, fargs...)
	if err != nil {
		return nil, 0, err
	}
	return runs, total, nil
}

// GetGenerationRun devuelve el detalle de una corrida + los trip_instances
// que produjo (mini-listado embebido). Usado por la UI para drill-down desde
// la tabla principal; los trip_instances vienen acotados a los primeros N
// ordenados por fecha para no devolver listas enormes.
func (r *adminRepository) GetGenerationRun(ctx context.Context, id int64) (GenerationRun, []TripInstance, error) {
	const q = `
        SELECT r.id,
               DATE_FORMAT(r.window_start, '%Y-%m-%d') AS window_start,
               DATE_FORMAT(r.window_end, '%Y-%m-%d') AS window_end,
               r.status, r.generated_count, r.skipped_count, r.failed_count,
               r.error_summary, r.triggered_by_user_id, u.full_name,
               TIMESTAMPDIFF(SECOND, r.started_at, r.finished_at) AS duration_seconds,
               (SELECT COUNT(*) FROM trip_instances t WHERE t.generation_run_id = r.id) AS trip_count,
               r.started_at, r.finished_at
          FROM trip_generation_runs r
          LEFT JOIN users u ON u.id = r.triggered_by_user_id
         WHERE r.id = ?`
	var gr GenerationRun
	var winStart, winEnd sql.NullString
	var errSummary sql.NullString
	var triggeredBy sql.NullInt64
	var triggeredByName sql.NullString
	var durationSec sql.NullInt64
	var finishedAt sql.NullTime
	err := r.db.QueryRowContext(ctx, q, id).Scan(&gr.ID, &winStart, &winEnd, &gr.Status,
		&gr.GeneratedCount, &gr.SkippedCount, &gr.FailedCount,
		&errSummary, &triggeredBy, &triggeredByName,
		&durationSec, &gr.TripCount,
		&gr.StartedAt, &finishedAt)
	if err != nil {
		return GenerationRun{}, nil, dberr.NotFound(err, "corrida de generacion", id)
	}
	if winStart.Valid {
		gr.WindowStart = winStart.String
	}
	if winEnd.Valid {
		gr.WindowEnd = winEnd.String
	}
	gr.ErrorSummary = nullableStr(errSummary)
	gr.TriggeredByUserID = nullableInt(triggeredBy)
	gr.TriggeredByFullName = nullableStr(triggeredByName)
	if durationSec.Valid {
		d := int(durationSec.Int64)
		gr.DurationSeconds = &d
	}
	gr.FinishedAt = nullableTime(finishedAt)

	trips, err := r.ListTripsByGenerationRun(ctx, id, 200)
	if err != nil {
		return GenerationRun{}, nil, err
	}
	return gr, trips, nil
}

// ListTripsByGenerationRun devuelve los trip_instances que genero una corrida,
// acotado a `limit` (ordenados por service_date ASC) para que el drill-down
// no cargue listas enormes si el rango es amplio.
func (r *adminRepository) ListTripsByGenerationRun(ctx context.Context, runID int64, limit int) ([]TripInstance, error) {
	if limit <= 0 || limit > 500 {
		limit = 200
	}
	const q = `
        SELECT id, trip_code, source, trip_template_id, generation_run_id, route_id,
               DATE_FORMAT(service_date, '%Y-%m-%d') AS service_date,
               scheduled_start_at, scheduled_end_at, booking_opens_at, booking_closes_at,
               vehicle_id, driver_id, seat_capacity_snapshot, no_show_tolerance_minutes,
               status, actual_start_at, actual_end_at, cancellation_reason
          FROM trip_instances
         WHERE generation_run_id = ?
         ORDER BY service_date, scheduled_start_at
         LIMIT ?`
	rows, err := r.db.QueryContext(ctx, q, runID, limit)
	if err != nil {
		return nil, fmt.Errorf("listando viajes de la corrida: %w", err)
	}
	defer rows.Close()

	var trips []TripInstance
	for rows.Next() {
		var t TripInstance
		var srcDate sql.NullString
		var tmplID, runIDsql sql.NullInt64
		var actualStart, actualEnd sql.NullTime
		var cancelReason sql.NullString
		if err := rows.Scan(&t.ID, &t.TripCode, &t.Source, &tmplID, &runIDsql, &t.RouteID,
			&srcDate, &t.ScheduledStartAt, &t.ScheduledEndAt, &t.BookingOpensAt, &t.BookingClosesAt,
			&t.VehicleID, &t.DriverID, &t.SeatCapacitySnapshot, &t.NoShowToleranceMinutes,
			&t.Status, &actualStart, &actualEnd, &cancelReason); err != nil {
			return nil, fmt.Errorf("escaneando viaje de corrida: %w", err)
		}
		if srcDate.Valid {
			t.ServiceDate = srcDate.String
		}
		t.TripTemplateID = nullableInt(tmplID)
		t.GenerationRunID = nullableInt(runIDsql)
		t.ActualStartAt = nullableTime(actualStart)
		t.ActualEndAt = nullableTime(actualEnd)
		t.CancellationReason = nullableStr(cancelReason)
		trips = append(trips, t)
	}
	return trips, rows.Err()
}

// ----------------------------------------------------------------------------
// Operaciones de viajes
// ----------------------------------------------------------------------------

// UpdateTripStatus cambia el estado de un viaje. El estado se valida contra el
// ENUM de la columna trip_instances.status en la BD.
func (r *adminRepository) UpdateTripStatus(ctx context.Context, tripID int64, status string) error {
	res, err := r.db.ExecContext(ctx, `
        UPDATE trip_instances
           SET status = ?
         WHERE id = ?`,
		status, tripID)
	if err != nil {
		return fmt.Errorf("actualizando estado de viaje: %w", err)
	}
	return ensureAffected(res, "viaje", tripID)
}

// TriggerManualGeneration invoca sp_generate_trip_instance para una fecha
// concreta. generation_run_id se pasa NULL porque la generacion manual no
// pertenece a una corrida batch.
func (r *adminRepository) TriggerManualGeneration(ctx context.Context, templateID int64, serviceDate string) error {
	_, err := r.db.ExecContext(ctx, "CALL sp_generate_trip_instance(?, ?, NULL)",
		templateID, serviceDate)
	if err != nil {
		if spErr := dberr.TranslateSP(err); spErr != err {
			return spErr
		}
		return fmt.Errorf("llamando sp_generate_trip_instance: %w", err)
	}
	return nil
}

// ----------------------------------------------------------------------------
// Reportes (vistas)
// ----------------------------------------------------------------------------

// GetScheduleConflicts consulta vw_schedule_conflicts con filtros
// opcionales: resourceType (VEHICLE|DRIVER|'' para todos) y dateFrom/dateTo
// acotando por first_start_at (la "primera" de las dos trip solapadas).
// Esos son los filtros que la vista SQL soporta sin agregar JOINs: la vista
// ya emite resource_type y los timestamps.
func (r *adminRepository) GetScheduleConflicts(ctx context.Context, resourceType, dateFrom, dateTo string) ([]Conflict, error) {
	var conds []string
	var fargs []any
	if resourceType != "" {
		conds = append(conds, "resource_type = ?")
		fargs = append(fargs, resourceType)
	}
	if dateFrom != "" {
		conds = append(conds, "first_start_at >= ?")
		fargs = append(fargs, dateFrom)
	}
	if dateTo != "" {
		conds = append(conds, "first_start_at <= ?")
		fargs = append(fargs, dateTo)
	}
	where := ""
	if len(conds) > 0 {
		where = " WHERE " + strings.Join(conds, " AND ")
	}
	q := `SELECT resource_type, resource_id, first_trip_id, second_trip_id,
               first_start_at, first_end_at, second_start_at, second_end_at
          FROM vw_schedule_conflicts` + where + `
         ORDER BY first_start_at DESC, resource_type, resource_id`
	rows, err := r.db.QueryContext(ctx, q, fargs...)
	if err != nil {
		return nil, fmt.Errorf("consultando vw_schedule_conflicts: %w", err)
	}
	defer rows.Close()

	var conflicts []Conflict
	for rows.Next() {
		var c Conflict
		if err := rows.Scan(&c.ResourceType, &c.ResourceID, &c.FirstTripID,
			&c.SecondTripID, &c.FirstStartAt, &c.FirstEndAt,
			&c.SecondStartAt, &c.SecondEndAt); err != nil {
			return nil, fmt.Errorf("escaneando conflicto: %w", err)
		}
		conflicts = append(conflicts, c)
	}
	return conflicts, rows.Err()
}

// GetRouteTimeMatrix consulta vw_route_time_matrix con filtros opcionales:
// routeID, direction (IDA|VUELTA|''), profileID. Esos filtros los soporta
// la vista directo (cada columna existe en el SELECT). 0 significa "todos".
func (r *adminRepository) GetRouteTimeMatrix(ctx context.Context, routeID int64, direction string, profileID int64) ([]MatrixEntry, error) {
	var conds []string
	var fargs []any
	if routeID > 0 {
		conds = append(conds, "route_id = ?")
		fargs = append(fargs, routeID)
	}
	if direction != "" {
		conds = append(conds, "direction = ?")
		fargs = append(fargs, direction)
	}
	if profileID > 0 {
		conds = append(conds, "profile_id = ?")
		fargs = append(fargs, profileID)
	}
	where := ""
	if len(conds) > 0 {
		where = " WHERE " + strings.Join(conds, " AND ")
	}
	q := `SELECT route_id, route_code, route_name, direction, route_segment_id,
               segment_order, from_stop_code, from_stop_name, to_stop_code,
               to_stop_name, profile_id, profile_code, profile_name,
               travel_minutes, priority
          FROM vw_route_time_matrix` + where + `
         ORDER BY route_code, direction, segment_order, priority DESC`
	rows, err := r.db.QueryContext(ctx, q, fargs...)
	if err != nil {
		return nil, fmt.Errorf("consultando vw_route_time_matrix: %w", err)
	}
	defer rows.Close()

	var entries []MatrixEntry
	for rows.Next() {
		var m MatrixEntry
		if err := rows.Scan(&m.RouteID, &m.RouteCode, &m.RouteName, &m.Direction,
			&m.RouteSegmentID, &m.SegmentOrder, &m.FromStopCode, &m.FromStopName,
			&m.ToStopCode, &m.ToStopName, &m.ProfileID, &m.ProfileCode,
			&m.ProfileName, &m.TravelMinutes, &m.Priority); err != nil {
			return nil, fmt.Errorf("escaneando matriz de tiempos: %w", err)
		}
		entries = append(entries, m)
	}
	return entries, rows.Err()
}

// GetTripSeatAvailability consulta vw_trip_segment_seat_availability para
// un viaje (trip_id obligatorio, era el filtro original) y opcionalmente
// filtra por state (AVAILABLE|BLOCKED|OCCUPIED_IN_REQUESTED_RANGE|...).
// '' = todos los estados.
func (r *adminRepository) GetTripSeatAvailability(ctx context.Context, tripID int64, state string) ([]SeatAvail, error) {
	var conds []string
	var fargs []any
	conds = append(conds, "trip_id = ?")
	fargs = append(fargs, tripID)
	if state != "" {
		conds = append(conds, "state = ?")
		fargs = append(fargs, state)
	}
	where := " WHERE " + strings.Join(conds, " AND ")
	q := `SELECT trip_id, trip_code, service_date, direction, trip_seat_id,
               seat_number, seat_label, segment_order, available_or_occupied_from,
               available_or_occupied_until, state, reservation_id,
               reservation_code, reserved_at, released_at
          FROM vw_trip_segment_seat_availability` + where + `
         ORDER BY seat_number, segment_order`
	rows, err := r.db.QueryContext(ctx, q, fargs...)
	if err != nil {
		return nil, fmt.Errorf("consultando vw_trip_segment_seat_availability: %w", err)
	}
	defer rows.Close()

	var avail []SeatAvail
	for rows.Next() {
		var s SeatAvail
		var resID sql.NullInt64
		var resCode sql.NullString
		var resAt, relAt sql.NullTime
		if err := rows.Scan(&s.TripID, &s.TripCode, &s.ServiceDate, &s.Direction,
			&s.TripSeatID, &s.SeatNumber, &s.SeatLabel, &s.SegmentOrder,
			&s.AvailableFrom, &s.AvailableUntil, &s.State, &resID, &resCode,
			&resAt, &relAt); err != nil {
			return nil, fmt.Errorf("escaneando disponibilidad de asientos: %w", err)
		}
		s.ReservationID = nullableInt(resID)
		s.ReservationCode = nullableStr(resCode)
		s.ReservedAt = nullableTime(resAt)
		s.ReleasedAt = nullableTime(relAt)
		avail = append(avail, s)
	}
	return avail, rows.Err()
}

// ----------------------------------------------------------------------------
// Helpers internos
// ----------------------------------------------------------------------------

// count devuelve el total de filas de una tabla. where es una condicion
// opcional (sin la palabra WHERE). Cuando se pasa, se interpola tal cual y
// los argumentos se pasan por separado via args. Se usa solo con tablas y
// condiciones fijas dentro de este modulo, nunca con entrada del usuario.
func (r *adminRepository) count(ctx context.Context, table, where string, args ...any) (int, error) {
	q := "SELECT COUNT(*) FROM " + table
	if where != "" {
		q += " WHERE " + where
	}
	var total int
	if err := r.db.QueryRowContext(ctx, q, args...).Scan(&total); err != nil {
		return 0, fmt.Errorf("contando filas de %s: %w", table, err)
	}
	return total, nil
}

// ensureAffected envuelve el RowsAffected == 0 tipico en NotFoundError.
func ensureAffected(res sql.Result, entity string, id int64) error {
	n, err := res.RowsAffected()
	if err != nil {
		return fmt.Errorf("verificando filas afectadas de %s: %w", entity, err)
	}
	if n == 0 {
		return dberr.NotFound(sql.ErrNoRows, entity, id)
	}
	return nil
}

// nullableStr convierte sql.NullString en *string.
func nullableStr(ns sql.NullString) *string {
	if !ns.Valid {
		return nil
	}
	s := ns.String
	return &s
}

// nullableInt convierte sql.NullInt64 en *int64.
func nullableInt(ni sql.NullInt64) *int64 {
	if !ni.Valid {
		return nil
	}
	v := ni.Int64
	return &v
}

// nullableFloat convierte sql.NullFloat64 en *float64.
func nullableFloat(nf sql.NullFloat64) *float64 {
	if !nf.Valid {
		return nil
	}
	v := nf.Float64
	return &v
}

// nullableTime convierte sql.NullTime en *time.Time.
func nullableTime(nt sql.NullTime) *time.Time {
	if !nt.Valid {
		return nil
	}
	t := nt.Time
	return &t
}

// compile-time guard.
var _ AdminRepository = (*adminRepository)(nil)
