package admin

import (
	"context"
	"fmt"
	"time"

	"golang.org/x/crypto/bcrypt"

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/apperror"
	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/authctx"
	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/types"
)

// RoleADMIN es la constante del rol que puede acceder a las operaciones de
// administracion. Se repite como constante local para no acoplar este modulo
// al valor literal del ENUM de la BD en cada metodo.
const RoleADMIN = "ADMIN"

// AdminService define las operaciones de dominio del modulo admin. Cada
// metodo valida que el caller tenga rol ADMIN; el resto es delegacion al
// repositorio (las reglas de negocio viven en los SPs y constraints de la BD).
type AdminService interface {
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

	// Reportes
	GetScheduleConflicts(ctx context.Context, resourceType, dateFrom, dateTo string) ([]Conflict, error)
	GetRouteTimeMatrix(ctx context.Context, routeID int64, direction string, profileID int64) ([]MatrixEntry, error)
	GetTripSeatAvailability(ctx context.Context, tripID int64, state string) ([]SeatAvail, error)
}

// adminService es la implementacion concreta.
type adminService struct {
	repo AdminRepository
}

// NewService construye el servicio con su repositorio inyectado.
func NewService(repo AdminRepository) AdminService {
	return &adminService{repo: repo}
}

// requireAdmin valida que el caller del contexto tenga rol ADMIN. Devuelve
// ForbiddenError cuando no cumple. Centraliza el guard para no repetir el
// chequeo en cada metodo.
func requireAdmin(ctx context.Context) error {
	role, err := authctx.RoleFromContext(ctx)
	if err != nil {
		return apperror.UnauthorizedError{Reason: "token sin identidad"}
	}
	if role != RoleADMIN {
		return apperror.ForbiddenError{Reason: "solo el rol ADMIN puede acceder a administracion"}
	}
	return nil
}

// ----------------------------------------------------------------------------
// Paradas
// ----------------------------------------------------------------------------

// ListStops lista paradas con guard de rol ADMIN.
func (s *adminService) ListStops(ctx context.Context, pg types.PaginationParams) ([]Stop, int, error) {
	if err := requireAdmin(ctx); err != nil {
		return nil, 0, err
	}
	return s.repo.ListStops(ctx, pg)
}

// CreateStop crea una parada.
func (s *adminService) CreateStop(ctx context.Context, p StopCreateParams) (Stop, error) {
	if err := requireAdmin(ctx); err != nil {
		return Stop{}, err
	}
	return s.repo.CreateStop(ctx, p)
}

// UpdateStop actualiza una parada.
func (s *adminService) UpdateStop(ctx context.Context, id int64, p StopUpdateParams) error {
	if err := requireAdmin(ctx); err != nil {
		return err
	}
	return s.repo.UpdateStop(ctx, id, p)
}

// ----------------------------------------------------------------------------
// Usuarios
// ----------------------------------------------------------------------------

// ListUsers lista usuarios.
func (s *adminService) ListUsers(ctx context.Context, pg types.PaginationParams) ([]User, int, error) {
	if err := requireAdmin(ctx); err != nil {
		return nil, 0, err
	}
	return s.repo.ListUsers(ctx, pg)
}

// CreateUser crea un usuario. Recibe la password en texto plano (TLS) y la
// hashea con bcrypt antes de persistir; nunca se guarda ni se loguea en claro.
func (s *adminService) CreateUser(ctx context.Context, p UserCreateParams) (User, error) {
	if err := requireAdmin(ctx); err != nil {
		return User{}, err
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(p.Password), bcrypt.DefaultCost)
	if err != nil {
		return User{}, fmt.Errorf("hasheando password: %w", err)
	}
	p.Password = string(hash)
	return s.repo.CreateUser(ctx, p)
}

// UpdateUser actualiza un usuario. Si p.Password viene vacia no se toca el
// hash existente; si viene, se hashea con bcrypt antes de persistir (mismo
// chokepoint que CreateUser).
func (s *adminService) UpdateUser(ctx context.Context, id int64, p UserUpdateParams) error {
	if err := requireAdmin(ctx); err != nil {
		return err
	}
	if p.Password != "" {
		hash, err := bcrypt.GenerateFromPassword([]byte(p.Password), bcrypt.DefaultCost)
		if err != nil {
			return fmt.Errorf("hasheando password: %w", err)
		}
		p.Password = string(hash)
	}
	return s.repo.UpdateUser(ctx, id, p)
}

// ----------------------------------------------------------------------------
// Vehiculos
// ----------------------------------------------------------------------------

// ListVehicles lista vehiculos.
func (s *adminService) ListVehicles(ctx context.Context, pg types.PaginationParams) ([]Vehicle, int, error) {
	if err := requireAdmin(ctx); err != nil {
		return nil, 0, err
	}
	return s.repo.ListVehicles(ctx, pg)
}

// CreateVehicle crea un vehiculo.
func (s *adminService) CreateVehicle(ctx context.Context, p VehicleCreateParams) (Vehicle, error) {
	if err := requireAdmin(ctx); err != nil {
		return Vehicle{}, err
	}
	return s.repo.CreateVehicle(ctx, p)
}

// UpdateVehicle actualiza un vehiculo.
func (s *adminService) UpdateVehicle(ctx context.Context, id int64, p VehicleUpdateParams) error {
	if err := requireAdmin(ctx); err != nil {
		return err
	}
	return s.repo.UpdateVehicle(ctx, id, p)
}

// ----------------------------------------------------------------------------
// Rutas
// ----------------------------------------------------------------------------

// ListRoutes lista rutas.
func (s *adminService) ListRoutes(ctx context.Context, pg types.PaginationParams) ([]Route, int, error) {
	if err := requireAdmin(ctx); err != nil {
		return nil, 0, err
	}
	return s.repo.ListRoutes(ctx, pg)
}

// CreateRoute crea una ruta.
func (s *adminService) CreateRoute(ctx context.Context, p RouteCreateParams) (Route, error) {
	if err := requireAdmin(ctx); err != nil {
		return Route{}, err
	}
	return s.repo.CreateRoute(ctx, p)
}

// UpdateRoute actualiza una ruta.
func (s *adminService) UpdateRoute(ctx context.Context, id int64, p RouteUpdateParams) error {
	if err := requireAdmin(ctx); err != nil {
		return err
	}
	return s.repo.UpdateRoute(ctx, id, p)
}

// ----------------------------------------------------------------------------
// Paradas de ruta
// ----------------------------------------------------------------------------

// ListRouteStops lista paradas de una ruta.
func (s *adminService) ListRouteStops(ctx context.Context, routeID int64, pg types.PaginationParams) ([]RouteStop, int, error) {
	if err := requireAdmin(ctx); err != nil {
		return nil, 0, err
	}
	return s.repo.ListRouteStops(ctx, routeID, pg)
}

// CreateRouteStop crea una parada de ruta.
func (s *adminService) CreateRouteStop(ctx context.Context, p RouteStopCreateParams) (RouteStop, error) {
	if err := requireAdmin(ctx); err != nil {
		return RouteStop{}, err
	}
	return s.repo.CreateRouteStop(ctx, p)
}

// UpdateRouteStop actualiza una parada de ruta.
func (s *adminService) UpdateRouteStop(ctx context.Context, id int64, p RouteStopUpdateParams) error {
	if err := requireAdmin(ctx); err != nil {
		return err
	}
	return s.repo.UpdateRouteStop(ctx, id, p)
}

// ----------------------------------------------------------------------------
// Plantillas de viaje
// ----------------------------------------------------------------------------

// ListTemplates lista plantillas.
func (s *adminService) ListTemplates(ctx context.Context, pg types.PaginationParams) ([]Template, int, error) {
	if err := requireAdmin(ctx); err != nil {
		return nil, 0, err
	}
	return s.repo.ListTemplates(ctx, pg)
}

// CreateTemplate crea una plantilla de viaje.
func (s *adminService) CreateTemplate(ctx context.Context, p TemplateCreateParams) (Template, error) {
	if err := requireAdmin(ctx); err != nil {
		return Template{}, err
	}
	if err := validateTemplate(p); err != nil {
		return Template{}, err
	}
	return s.repo.CreateTemplate(ctx, p)
}

// UpdateTemplate actualiza una plantilla de viaje.
func (s *adminService) UpdateTemplate(ctx context.Context, id int64, p TemplateUpdateParams) error {
	if err := requireAdmin(ctx); err != nil {
		return err
	}
	if err := validateTemplate(p); err != nil {
		return err
	}
	return s.repo.UpdateTemplate(ctx, id, p)
}

// validateTemplate verifica el formato HH:MM:SS (o HH:MM) de departure_time.
// Los demas campos ya estan cubiertos por los tags validate del struct.
func validateTemplate(p TemplateCreateParams) error {
	if p.DepartureTime == "" {
		return apperror.ValidationError{Field: "departure_time", Reason: "es requerido"}
	}
	timeStr := p.DepartureTime
	if len(timeStr) == 5 {
		timeStr += ":00"
	}
	if _, err := time.Parse("15:04:05", timeStr); err != nil {
		return apperror.ValidationError{Field: "departure_time", Reason: "formato invalido, use HH:MM:SS o HH:MM"}
	}
	return nil
}

// ----------------------------------------------------------------------------
// Calendarios de servicio
// ----------------------------------------------------------------------------

// ListCalendars lista calendarios.
func (s *adminService) ListCalendars(ctx context.Context, pg types.PaginationParams) ([]Calendar, int, error) {
	if err := requireAdmin(ctx); err != nil {
		return nil, 0, err
	}
	return s.repo.ListCalendars(ctx, pg)
}

// CreateCalendar crea un calendario de servicio.
func (s *adminService) CreateCalendar(ctx context.Context, p CalendarCreateParams) (Calendar, error) {
	if err := requireAdmin(ctx); err != nil {
		return Calendar{}, err
	}
	if err := validateCalendarDateRange(p.ValidFrom, p.ValidUntil); err != nil {
		return Calendar{}, err
	}
	return s.repo.CreateCalendar(ctx, p)
}

// UpdateCalendar actualiza un calendario de servicio.
func (s *adminService) UpdateCalendar(ctx context.Context, id int64, p CalendarUpdateParams) error {
	if err := requireAdmin(ctx); err != nil {
		return err
	}
	if _, err := s.repo.GetCalendar(ctx, id); err != nil {
		return err
	}
	if err := validateCalendarDateRange(p.ValidFrom, p.ValidUntil); err != nil {
		return err
	}
	return s.repo.UpdateCalendar(ctx, id, p)
}

// validateCalendarDateRange verifica formato YYYY-MM-DD y que valid_from sea
// <= valid_until. La BD no tiene CHECK sobre el rango (es responsabilidad de
// la app); sin esto el admin podria dejar un calendario "al reves" que
// fn_service_operates nunca activaria.
func validateCalendarDateRange(validFrom, validUntil string) error {
	from, err := time.Parse("2006-01-02", validFrom)
	if err != nil {
		return apperror.ValidationError{Field: "valid_from", Reason: "formato invalido, use YYYY-MM-DD"}
	}
	until, err := time.Parse("2006-01-02", validUntil)
	if err != nil {
		return apperror.ValidationError{Field: "valid_until", Reason: "formato invalido, use YYYY-MM-DD"}
	}
	if from.After(until) {
		return apperror.ValidationError{Field: "valid_until", Reason: "debe ser igual o posterior a valid_from"}
	}
	return nil
}

// ----------------------------------------------------------------------------
// Tramos de ruta
// ----------------------------------------------------------------------------

// ListRouteSegments lista tramos de ruta.
func (s *adminService) ListRouteSegments(ctx context.Context, pg types.PaginationParams) ([]RouteSegment, int, error) {
	if err := requireAdmin(ctx); err != nil {
		return nil, 0, err
	}
	return s.repo.ListRouteSegments(ctx, pg)
}

// CreateRouteSegment crea un tramo de ruta.
func (s *adminService) CreateRouteSegment(ctx context.Context, p RouteSegmentCreateParams) (RouteSegment, error) {
	if err := requireAdmin(ctx); err != nil {
		return RouteSegment{}, err
	}
	return s.repo.CreateRouteSegment(ctx, p)
}

// UpdateRouteSegment actualiza un tramo de ruta.
func (s *adminService) UpdateRouteSegment(ctx context.Context, id int64, p RouteSegmentUpdateParams) error {
	if err := requireAdmin(ctx); err != nil {
		return err
	}
	return s.repo.UpdateRouteSegment(ctx, id, p)
}

// ----------------------------------------------------------------------------
// Perfiles de tiempo de viaje
// ----------------------------------------------------------------------------

// ListTravelTimeProfiles lista perfiles de tiempo.
func (s *adminService) ListTravelTimeProfiles(ctx context.Context, pg types.PaginationParams) ([]TravelTimeProfile, int, error) {
	if err := requireAdmin(ctx); err != nil {
		return nil, 0, err
	}
	return s.repo.ListTravelTimeProfiles(ctx, pg)
}

// CreateTravelTimeProfile crea un perfil de tiempo de viaje.
func (s *adminService) CreateTravelTimeProfile(ctx context.Context, p TravelTimeProfileCreateParams) (TravelTimeProfile, error) {
	if err := requireAdmin(ctx); err != nil {
		return TravelTimeProfile{}, err
	}
	if err := validateTravelTimeProfile(p); err != nil {
		return TravelTimeProfile{}, err
	}
	return s.repo.CreateTravelTimeProfile(ctx, p)
}

// UpdateTravelTimeProfile actualiza un perfil de tiempo de viaje.
func (s *adminService) UpdateTravelTimeProfile(ctx context.Context, id int64, p TravelTimeProfileUpdateParams) error {
	if err := requireAdmin(ctx); err != nil {
		return err
	}
	if err := validateTravelTimeProfile(p); err != nil {
		return err
	}
	return s.repo.UpdateTravelTimeProfile(ctx, id, p)
}

// validateTravelTimeProfile verifica la consistencia de fechas y horarios de
// un perfil. No delega todo al validador de structs porque las reglas cruzadas
// (valid_from <= valid_until, inicio < fin) no se expresan via tags.
func validateTravelTimeProfile(p TravelTimeProfileCreateParams) error {
	if p.ValidFrom != nil && p.ValidUntil != nil {
		from, err := time.Parse("2006-01-02", *p.ValidFrom)
		if err != nil {
			return apperror.ValidationError{Field: "valid_from", Reason: "formato invalido, use YYYY-MM-DD"}
		}
		until, err := time.Parse("2006-01-02", *p.ValidUntil)
		if err != nil {
			return apperror.ValidationError{Field: "valid_until", Reason: "formato invalido, use YYYY-MM-DD"}
		}
		if from.After(until) {
			return apperror.ValidationError{Field: "valid_until", Reason: "debe ser igual o posterior a valid_from"}
		}
	}

	if p.IsAllDay {
		if p.StartTime != nil || p.EndTime != nil {
			return apperror.ValidationError{Field: "is_all_day", Reason: "si aplica todo el dia no debe enviar horario de inicio/fin"}
		}
		return nil
	}

	if p.StartTime == nil {
		return apperror.ValidationError{Field: "start_time", Reason: "requerido cuando no es todo el dia"}
	}
	if p.EndTime == nil {
		return apperror.ValidationError{Field: "end_time", Reason: "requerido cuando no es todo el dia"}
	}

	start, err := time.Parse("15:04:05", *p.StartTime)
	if err != nil {
		return apperror.ValidationError{Field: "start_time", Reason: "formato invalido, use HH:MM:SS"}
	}
	end, err := time.Parse("15:04:05", *p.EndTime)
	if err != nil {
		return apperror.ValidationError{Field: "end_time", Reason: "formato invalido, use HH:MM:SS"}
	}
	if !end.After(start) {
		return apperror.ValidationError{Field: "end_time", Reason: "debe ser posterior a start_time"}
	}
	return nil
}

// ----------------------------------------------------------------------------
// Tiempos de tramo por perfil
// ----------------------------------------------------------------------------

// ListRouteSegmentTravelTimes lista tiempos de tramo.
func (s *adminService) ListRouteSegmentTravelTimes(ctx context.Context, pg types.PaginationParams) ([]RouteSegmentTravelTime, int, error) {
	if err := requireAdmin(ctx); err != nil {
		return nil, 0, err
	}
	return s.repo.ListRouteSegmentTravelTimes(ctx, pg)
}

// CreateRouteSegmentTravelTime crea un tiempo de tramo.
func (s *adminService) CreateRouteSegmentTravelTime(ctx context.Context, p RouteSegmentTravelTimeCreateParams) (RouteSegmentTravelTime, error) {
	if err := requireAdmin(ctx); err != nil {
		return RouteSegmentTravelTime{}, err
	}
	return s.repo.CreateRouteSegmentTravelTime(ctx, p)
}

// UpdateRouteSegmentTravelTime actualiza un tiempo de tramo.
func (s *adminService) UpdateRouteSegmentTravelTime(ctx context.Context, id int64, p RouteSegmentTravelTimeUpdateParams) error {
	if err := requireAdmin(ctx); err != nil {
		return err
	}
	return s.repo.UpdateRouteSegmentTravelTime(ctx, id, p)
}

// ----------------------------------------------------------------------------
// Asientos de vehiculo
// ----------------------------------------------------------------------------

func (s *adminService) ListVehicleSeats(ctx context.Context, vehicleID int64, pg types.PaginationParams) ([]VehicleSeat, int, error) {
	if err := requireAdmin(ctx); err != nil {
		return nil, 0, err
	}
	return s.repo.ListVehicleSeats(ctx, vehicleID, pg)
}

func (s *adminService) CreateVehicleSeat(ctx context.Context, p VehicleSeatCreateParams) (VehicleSeat, error) {
	if err := requireAdmin(ctx); err != nil {
		return VehicleSeat{}, err
	}
	return s.repo.CreateVehicleSeat(ctx, p)
}

func (s *adminService) UpdateVehicleSeat(ctx context.Context, id int64, p VehicleSeatUpdateParams) error {
	if err := requireAdmin(ctx); err != nil {
		return err
	}
	return s.repo.UpdateVehicleSeat(ctx, id, p)
}

// ----------------------------------------------------------------------------
// Excepciones de calendario
// ----------------------------------------------------------------------------

func (s *adminService) ListCalendarExceptions(ctx context.Context, calendarID int64, pg types.PaginationParams) ([]CalendarException, int, error) {
	if err := requireAdmin(ctx); err != nil {
		return nil, 0, err
	}
	return s.repo.ListCalendarExceptions(ctx, calendarID, pg)
}

func (s *adminService) CreateCalendarException(ctx context.Context, p CalendarExceptionCreateParams) (CalendarException, error) {
	if err := requireAdmin(ctx); err != nil {
		return CalendarException{}, err
	}
	if err := s.validateCalendarExceptionDate(ctx, p.CalendarID, p.ExceptionDate); err != nil {
		return CalendarException{}, err
	}
	return s.repo.CreateCalendarException(ctx, p)
}

func (s *adminService) UpdateCalendarException(ctx context.Context, id int64, p CalendarExceptionUpdateParams) error {
	if err := requireAdmin(ctx); err != nil {
		return err
	}
	if _, err := s.repo.GetCalendarException(ctx, id); err != nil {
		return err
	}
	if err := s.validateCalendarExceptionDate(ctx, p.CalendarID, p.ExceptionDate); err != nil {
		return err
	}
	return s.repo.UpdateCalendarException(ctx, id, p)
}

// validateCalendarExceptionDate verifica que la fecha de excepcion caiga
// dentro del rango de vigencia del calendario padre.
func (s *adminService) validateCalendarExceptionDate(ctx context.Context, calendarID int64, exceptionDate string) error {
	cal, err := s.repo.GetCalendar(ctx, calendarID)
	if err != nil {
		return err
	}
	excDate, err := time.Parse("2006-01-02", exceptionDate)
	if err != nil {
		return apperror.ValidationError{Field: "exception_date", Reason: "formato invalido, use YYYY-MM-DD"}
	}
	validFrom, err := time.Parse("2006-01-02", cal.ValidFrom)
	if err != nil {
		return apperror.InternalError{Err: fmt.Errorf("valid_from del calendario no es fecha: %w", err)}
	}
	validUntil, err := time.Parse("2006-01-02", cal.ValidUntil)
	if err != nil {
		return apperror.InternalError{Err: fmt.Errorf("valid_until del calendario no es fecha: %w", err)}
	}
	if excDate.Before(validFrom) || excDate.After(validUntil) {
		return apperror.ValidationError{Field: "exception_date", Reason: "debe estar dentro del rango del calendario"}
	}
	return nil
}

// ----------------------------------------------------------------------------
// Listados de solo lectura
// ----------------------------------------------------------------------------

func (s *adminService) ListTrips(ctx context.Context, date, status string, routeID int64, pg types.PaginationParams) ([]TripInstance, int, error) {
	if err := requireAdmin(ctx); err != nil {
		return nil, 0, err
	}
	return s.repo.ListTrips(ctx, date, status, routeID, pg)
}

// ListIncidents lista incidencias con filtros. La creacion la hace el
// driver via /api/driver/trips/{id}/incidents (no esta expuesto en el
// modulo admin); el admin solo lista y resuelve.
func (s *adminService) ListIncidents(ctx context.Context, status, incidentType, dateFrom, dateTo string, pg types.PaginationParams) ([]TripIncident, int, error) {
	if err := requireAdmin(ctx); err != nil {
		return nil, 0, err
	}
	return s.repo.ListIncidents(ctx, status, incidentType, dateFrom, dateTo, pg)
}

// GetIncident devuelve una incidencia por id (drill-down).
func (s *adminService) GetIncident(ctx context.Context, id int64) (TripIncident, error) {
	if err := requireAdmin(ctx); err != nil {
		return TripIncident{}, err
	}
	return s.repo.GetIncident(ctx, id)
}

// UpdateIncident cambia el estado de una incidencia y opcionalmente sus
// notas de resolucion. Valida el status contra el ENUM de la tabla y
// verifica que la incidencia exista antes de tocar la BD.
func (s *adminService) UpdateIncident(ctx context.Context, id int64, status string, resolutionNotes *string) (TripIncident, error) {
	if err := requireAdmin(ctx); err != nil {
		return TripIncident{}, err
	}
	switch status {
	case "OPEN", "IN_REVIEW", "RESOLVED":
		// ok
	default:
		return TripIncident{}, apperror.ValidationError{Field: "status", Reason: "debe ser OPEN, IN_REVIEW o RESOLVED"}
	}
	if _, err := s.repo.GetIncident(ctx, id); err != nil {
		return TripIncident{}, err
	}
	return s.repo.UpdateIncident(ctx, id, status, resolutionNotes)
}

// ListGenerationRuns lista corridas con filtros opcionales. La tabla es
// append-only (logs de auditoria del motor): no hay Create/Update/Delete
// en el service. Los filtros aceptados coinciden 1:1 con columnas reales
// de trip_generation_runs (status, window_start, window_end,
// triggered_by_user_id).
func (s *adminService) ListGenerationRuns(ctx context.Context, status, dateFrom, dateTo string, triggeredByUserID int64, pg types.PaginationParams) ([]GenerationRun, int, error) {
	if err := requireAdmin(ctx); err != nil {
		return nil, 0, err
	}
	return s.repo.ListGenerationRuns(ctx, status, dateFrom, dateTo, triggeredByUserID, pg)
}

// GetGenerationRun devuelve el detalle de una corrida + los trip_instances
// que produjo. Usado por la UI para drill-down desde la tabla principal.
func (s *adminService) GetGenerationRun(ctx context.Context, id int64) (GenerationRun, []TripInstance, error) {
	if err := requireAdmin(ctx); err != nil {
		return GenerationRun{}, nil, err
	}
	return s.repo.GetGenerationRun(ctx, id)
}

// ----------------------------------------------------------------------------
// Operaciones de viajes
// ----------------------------------------------------------------------------
func (s *adminService) UpdateTripStatus(ctx context.Context, tripID int64, status string) error {
	if err := requireAdmin(ctx); err != nil {
		return err
	}
	return s.repo.UpdateTripStatus(ctx, tripID, status)
}

// TriggerManualGeneration dispara la generacion manual de un viaje.
func (s *adminService) TriggerManualGeneration(ctx context.Context, templateID int64, serviceDate string) error {
	if err := requireAdmin(ctx); err != nil {
		return err
	}
	return s.repo.TriggerManualGeneration(ctx, templateID, serviceDate)
}

// ----------------------------------------------------------------------------
// Reportes
// ----------------------------------------------------------------------------

// GetScheduleConflicts devuelve los conflictos de horario detectados por la
// vista SQL, con filtros opcionales que matchean 1:1 columnas de la vista.
func (s *adminService) GetScheduleConflicts(ctx context.Context, resourceType, dateFrom, dateTo string) ([]Conflict, error) {
	if err := requireAdmin(ctx); err != nil {
		return nil, err
	}
	return s.repo.GetScheduleConflicts(ctx, resourceType, dateFrom, dateTo)
}

// GetRouteTimeMatrix devuelve la matriz de tiempos de ruta con filtros
// opcionales que matchean 1:1 columnas de la vista.
func (s *adminService) GetRouteTimeMatrix(ctx context.Context, routeID int64, direction string, profileID int64) ([]MatrixEntry, error) {
	if err := requireAdmin(ctx); err != nil {
		return nil, err
	}
	return s.repo.GetRouteTimeMatrix(ctx, routeID, direction, profileID)
}

// GetTripSeatAvailability devuelve la disponibilidad de asientos de un viaje.
// tripID es obligatorio; state es opcional ('' = todos).
func (s *adminService) GetTripSeatAvailability(ctx context.Context, tripID int64, state string) ([]SeatAvail, error) {
	if err := requireAdmin(ctx); err != nil {
		return nil, err
	}
	return s.repo.GetTripSeatAvailability(ctx, tripID, state)
}

// compile-time guard.
var _ AdminService = (*adminService)(nil)
