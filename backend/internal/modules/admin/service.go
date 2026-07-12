package admin

import (
	"context"
	"fmt"

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

	// Operaciones de viajes
	UpdateTripStatus(ctx context.Context, tripID int64, status string) error
	TriggerManualGeneration(ctx context.Context, templateID int64, serviceDate string) error

	// Reportes
	GetScheduleConflicts(ctx context.Context) ([]Conflict, error)
	GetRouteTimeMatrix(ctx context.Context) ([]MatrixEntry, error)
	GetTripSeatAvailability(ctx context.Context, tripID int64) ([]SeatAvail, error)
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
	return s.repo.CreateTemplate(ctx, p)
}

// UpdateTemplate actualiza una plantilla de viaje.
func (s *adminService) UpdateTemplate(ctx context.Context, id int64, p TemplateUpdateParams) error {
	if err := requireAdmin(ctx); err != nil {
		return err
	}
	return s.repo.UpdateTemplate(ctx, id, p)
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
	return s.repo.CreateCalendar(ctx, p)
}

// UpdateCalendar actualiza un calendario de servicio.
func (s *adminService) UpdateCalendar(ctx context.Context, id int64, p CalendarUpdateParams) error {
	if err := requireAdmin(ctx); err != nil {
		return err
	}
	return s.repo.UpdateCalendar(ctx, id, p)
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
	return s.repo.CreateTravelTimeProfile(ctx, p)
}

// UpdateTravelTimeProfile actualiza un perfil de tiempo de viaje.
func (s *adminService) UpdateTravelTimeProfile(ctx context.Context, id int64, p TravelTimeProfileUpdateParams) error {
	if err := requireAdmin(ctx); err != nil {
		return err
	}
	return s.repo.UpdateTravelTimeProfile(ctx, id, p)
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

// GetScheduleConflicts devuelve los conflictos detectados por la vista.
func (s *adminService) GetScheduleConflicts(ctx context.Context) ([]Conflict, error) {
	if err := requireAdmin(ctx); err != nil {
		return nil, err
	}
	return s.repo.GetScheduleConflicts(ctx)
}

// GetRouteTimeMatrix devuelve la matriz de tiempos de ruta.
func (s *adminService) GetRouteTimeMatrix(ctx context.Context) ([]MatrixEntry, error) {
	if err := requireAdmin(ctx); err != nil {
		return nil, err
	}
	return s.repo.GetRouteTimeMatrix(ctx)
}

// GetTripSeatAvailability devuelve la disponibilidad de asientos de un viaje.
func (s *adminService) GetTripSeatAvailability(ctx context.Context, tripID int64) ([]SeatAvail, error) {
	if err := requireAdmin(ctx); err != nil {
		return nil, err
	}
	return s.repo.GetTripSeatAvailability(ctx, tripID)
}

// compile-time guard.
var _ AdminService = (*adminService)(nil)
