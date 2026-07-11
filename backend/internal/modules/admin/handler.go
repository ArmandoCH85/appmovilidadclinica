package admin

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/apperror"
	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/types"
	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/validate"
)

// AdminHandler expone los endpoints HTTP del modulo admin.
type AdminHandler struct {
	svc AdminService
	log *slog.Logger
}

// NewHandler construye el handler con su servicio inyectado.
func NewHandler(svc AdminService, log *slog.Logger) *AdminHandler {
	if log == nil {
		log = slog.Default()
	}
	return &AdminHandler{svc: svc, log: log}
}

// parsePagination normaliza page/page_size del query string.
func parsePagination(r *http.Request) types.PaginationParams {
	pg := types.PaginationParams{
		Page:     atoiDefault(r.URL.Query().Get("page"), types.DefaultPage),
		PageSize: atoiDefault(r.URL.Query().Get("page_size"), types.DefaultPageSize),
	}
	pg.Normalize()
	return pg
}

// atoiDefault parsea un entero con fallback.
func atoiDefault(s string, def int) int {
	if s == "" {
		return def
	}
	n, err := strconv.Atoi(s)
	if err != nil || n < 1 {
		return def
	}
	return n
}

// parseID extrae un {id} numerico del path. Devuelve ValidationError si no
// es un entero positivo.
func parseID(w http.ResponseWriter, r *http.Request, name string) (int64, bool) {
	id, err := strconv.ParseInt(chi.URLParam(r, name), 10, 64)
	if err != nil || id < 1 {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: name, Reason: "debe ser un entero positivo"})
		return 0, false
	}
	return id, true
}

// writeJSON escribe 200 con el cuerpo JSON o un error 500 si falla.
func writeJSON(w http.ResponseWriter, body any) {
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(body)
}

// ----------------------------------------------------------------------------
// Paradas (transport_stops)
// ----------------------------------------------------------------------------

// ListStops maneja GET /admin/stops?page=&page_size=.
func (h *AdminHandler) ListStops(w http.ResponseWriter, r *http.Request) {
	pg := parsePagination(r)
	stops, total, err := h.svc.ListStops(r.Context(), pg)
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	writeJSON(w, map[string]any{
		"items":     orEmpty(stops, stopSlice),
		"page":      pg.Page,
		"page_size": pg.PageSize,
		"total":     total,
	})
}

// CreateStop maneja POST /admin/stops.
func (h *AdminHandler) CreateStop(w http.ResponseWriter, r *http.Request) {
	var req StopCreateParams
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "body", Reason: "json invalido"})
		return
	}
	if err := validate.Default.Struct(req); err != nil {
		apperror.WriteJSONError(w, validate.ToAppError(err))
		return
	}
	stop, err := h.svc.CreateStop(r.Context(), req)
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	w.WriteHeader(http.StatusCreated)
	writeJSON(w, stop)
}

// UpdateStop maneja PUT /admin/stops/{id}.
func (h *AdminHandler) UpdateStop(w http.ResponseWriter, r *http.Request) {
	id, ok := parseID(w, r, "id")
	if !ok {
		return
	}
	var req StopUpdateParams
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "body", Reason: "json invalido"})
		return
	}
	if err := validate.Default.Struct(req); err != nil {
		apperror.WriteJSONError(w, validate.ToAppError(err))
		return
	}
	if err := h.svc.UpdateStop(r.Context(), id, req); err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// ----------------------------------------------------------------------------
// Usuarios (users)
// ----------------------------------------------------------------------------

// ListUsers maneja GET /admin/users.
func (h *AdminHandler) ListUsers(w http.ResponseWriter, r *http.Request) {
	pg := parsePagination(r)
	users, total, err := h.svc.ListUsers(r.Context(), pg)
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	writeJSON(w, map[string]any{
		"items":     orEmpty(users, userSlice),
		"page":      pg.Page,
		"page_size": pg.PageSize,
		"total":     total,
	})
}

// CreateUser maneja POST /admin/users. Recibe password_hash ya hasheado con
// bcrypt desde el cliente administrador (la app web de admin hashea antes de
// enviar; el backend MVP confia en ese flujo).
func (h *AdminHandler) CreateUser(w http.ResponseWriter, r *http.Request) {
	var req UserCreateParams
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "body", Reason: "json invalido"})
		return
	}
	if err := validate.Default.Struct(req); err != nil {
		apperror.WriteJSONError(w, validate.ToAppError(err))
		return
	}
	user, err := h.svc.CreateUser(r.Context(), req)
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	w.WriteHeader(http.StatusCreated)
	writeJSON(w, user)
}

// UpdateUser maneja PUT /admin/users/{id}.
func (h *AdminHandler) UpdateUser(w http.ResponseWriter, r *http.Request) {
	id, ok := parseID(w, r, "id")
	if !ok {
		return
	}
	var req UserUpdateParams
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "body", Reason: "json invalido"})
		return
	}
	if err := validate.Default.Struct(req); err != nil {
		apperror.WriteJSONError(w, validate.ToAppError(err))
		return
	}
	if err := h.svc.UpdateUser(r.Context(), id, req); err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// ----------------------------------------------------------------------------
// Vehiculos (vehicles)
// ----------------------------------------------------------------------------

// ListVehicles maneja GET /admin/vehicles.
func (h *AdminHandler) ListVehicles(w http.ResponseWriter, r *http.Request) {
	pg := parsePagination(r)
	vehicles, total, err := h.svc.ListVehicles(r.Context(), pg)
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	writeJSON(w, map[string]any{
		"items":     orEmpty(vehicles, vehicleSlice),
		"page":      pg.Page,
		"page_size": pg.PageSize,
		"total":     total,
	})
}

// CreateVehicle maneja POST /admin/vehicles.
func (h *AdminHandler) CreateVehicle(w http.ResponseWriter, r *http.Request) {
	var req VehicleCreateParams
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "body", Reason: "json invalido"})
		return
	}
	if err := validate.Default.Struct(req); err != nil {
		apperror.WriteJSONError(w, validate.ToAppError(err))
		return
	}
	vehicle, err := h.svc.CreateVehicle(r.Context(), req)
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	w.WriteHeader(http.StatusCreated)
	writeJSON(w, vehicle)
}

// UpdateVehicle maneja PUT /admin/vehicles/{id}.
func (h *AdminHandler) UpdateVehicle(w http.ResponseWriter, r *http.Request) {
	id, ok := parseID(w, r, "id")
	if !ok {
		return
	}
	var req VehicleUpdateParams
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "body", Reason: "json invalido"})
		return
	}
	if err := validate.Default.Struct(req); err != nil {
		apperror.WriteJSONError(w, validate.ToAppError(err))
		return
	}
	if err := h.svc.UpdateVehicle(r.Context(), id, req); err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// ----------------------------------------------------------------------------
// Rutas (transport_routes)
// ----------------------------------------------------------------------------

// ListRoutes maneja GET /admin/routes.
func (h *AdminHandler) ListRoutes(w http.ResponseWriter, r *http.Request) {
	pg := parsePagination(r)
	routes, total, err := h.svc.ListRoutes(r.Context(), pg)
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	writeJSON(w, map[string]any{
		"items":     orEmpty(routes, routeSlice),
		"page":      pg.Page,
		"page_size": pg.PageSize,
		"total":     total,
	})
}

// CreateRoute maneja POST /admin/routes.
func (h *AdminHandler) CreateRoute(w http.ResponseWriter, r *http.Request) {
	var req RouteCreateParams
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "body", Reason: "json invalido"})
		return
	}
	if err := validate.Default.Struct(req); err != nil {
		apperror.WriteJSONError(w, validate.ToAppError(err))
		return
	}
	route, err := h.svc.CreateRoute(r.Context(), req)
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	w.WriteHeader(http.StatusCreated)
	writeJSON(w, route)
}

// UpdateRoute maneja PUT /admin/routes/{id}.
func (h *AdminHandler) UpdateRoute(w http.ResponseWriter, r *http.Request) {
	id, ok := parseID(w, r, "id")
	if !ok {
		return
	}
	var req RouteUpdateParams
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "body", Reason: "json invalido"})
		return
	}
	if err := validate.Default.Struct(req); err != nil {
		apperror.WriteJSONError(w, validate.ToAppError(err))
		return
	}
	if err := h.svc.UpdateRoute(r.Context(), id, req); err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// ----------------------------------------------------------------------------
// Paradas de ruta (route_stops)
// ----------------------------------------------------------------------------

// ListRouteStops maneja GET /admin/routes/{id}/stops.
func (h *AdminHandler) ListRouteStops(w http.ResponseWriter, r *http.Request) {
	routeID, ok := parseID(w, r, "id")
	if !ok {
		return
	}
	pg := parsePagination(r)
	stops, total, err := h.svc.ListRouteStops(r.Context(), routeID, pg)
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	writeJSON(w, map[string]any{
		"items":     orEmpty(stops, routeStopSlice),
		"page":      pg.Page,
		"page_size": pg.PageSize,
		"total":     total,
	})
}

// CreateRouteStop maneja POST /admin/route-stops.
func (h *AdminHandler) CreateRouteStop(w http.ResponseWriter, r *http.Request) {
	var req RouteStopCreateParams
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "body", Reason: "json invalido"})
		return
	}
	if err := validate.Default.Struct(req); err != nil {
		apperror.WriteJSONError(w, validate.ToAppError(err))
		return
	}
	stop, err := h.svc.CreateRouteStop(r.Context(), req)
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	w.WriteHeader(http.StatusCreated)
	writeJSON(w, stop)
}

// UpdateRouteStop maneja PUT /admin/route-stops/{id}.
func (h *AdminHandler) UpdateRouteStop(w http.ResponseWriter, r *http.Request) {
	id, ok := parseID(w, r, "id")
	if !ok {
		return
	}
	var req RouteStopUpdateParams
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "body", Reason: "json invalido"})
		return
	}
	if err := validate.Default.Struct(req); err != nil {
		apperror.WriteJSONError(w, validate.ToAppError(err))
		return
	}
	if err := h.svc.UpdateRouteStop(r.Context(), id, req); err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// ----------------------------------------------------------------------------
// Plantillas de viaje (trip_templates)
// ----------------------------------------------------------------------------

// ListTemplates maneja GET /admin/templates.
func (h *AdminHandler) ListTemplates(w http.ResponseWriter, r *http.Request) {
	pg := parsePagination(r)
	templates, total, err := h.svc.ListTemplates(r.Context(), pg)
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	writeJSON(w, map[string]any{
		"items":     orEmpty(templates, templateSlice),
		"page":      pg.Page,
		"page_size": pg.PageSize,
		"total":     total,
	})
}

// CreateTemplate maneja POST /admin/templates.
func (h *AdminHandler) CreateTemplate(w http.ResponseWriter, r *http.Request) {
	var req TemplateCreateParams
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "body", Reason: "json invalido"})
		return
	}
	if err := validate.Default.Struct(req); err != nil {
		apperror.WriteJSONError(w, validate.ToAppError(err))
		return
	}
	template, err := h.svc.CreateTemplate(r.Context(), req)
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	w.WriteHeader(http.StatusCreated)
	writeJSON(w, template)
}

// UpdateTemplate maneja PUT /admin/templates/{id}.
func (h *AdminHandler) UpdateTemplate(w http.ResponseWriter, r *http.Request) {
	id, ok := parseID(w, r, "id")
	if !ok {
		return
	}
	var req TemplateUpdateParams
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "body", Reason: "json invalido"})
		return
	}
	if err := validate.Default.Struct(req); err != nil {
		apperror.WriteJSONError(w, validate.ToAppError(err))
		return
	}
	if err := h.svc.UpdateTemplate(r.Context(), id, req); err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// ----------------------------------------------------------------------------
// Calendarios de servicio (service_calendars)
// ----------------------------------------------------------------------------

// ListCalendars maneja GET /admin/calendars.
func (h *AdminHandler) ListCalendars(w http.ResponseWriter, r *http.Request) {
	pg := parsePagination(r)
	cals, total, err := h.svc.ListCalendars(r.Context(), pg)
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	writeJSON(w, map[string]any{
		"items":     orEmpty(cals, calendarSlice),
		"page":      pg.Page,
		"page_size": pg.PageSize,
		"total":     total,
	})
}

// CreateCalendar maneja POST /admin/calendars.
func (h *AdminHandler) CreateCalendar(w http.ResponseWriter, r *http.Request) {
	var req CalendarCreateParams
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "body", Reason: "json invalido"})
		return
	}
	if err := validate.Default.Struct(req); err != nil {
		apperror.WriteJSONError(w, validate.ToAppError(err))
		return
	}
	calendar, err := h.svc.CreateCalendar(r.Context(), req)
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	w.WriteHeader(http.StatusCreated)
	writeJSON(w, calendar)
}

// UpdateCalendar maneja PUT /admin/calendars/{id}.
func (h *AdminHandler) UpdateCalendar(w http.ResponseWriter, r *http.Request) {
	id, ok := parseID(w, r, "id")
	if !ok {
		return
	}
	var req CalendarUpdateParams
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "body", Reason: "json invalido"})
		return
	}
	if err := validate.Default.Struct(req); err != nil {
		apperror.WriteJSONError(w, validate.ToAppError(err))
		return
	}
	if err := h.svc.UpdateCalendar(r.Context(), id, req); err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// ----------------------------------------------------------------------------
// Operaciones de viajes
// ----------------------------------------------------------------------------

// updateTripStatusRequest es el cuerpo de POST /admin/trips/{id}/status.
type updateTripStatusRequest struct {
	Status string `json:"status" validate:"required,oneof=DRAFT PUBLISHED BOARDING IN_PROGRESS COMPLETED CANCELLED"`
}

// UpdateTripStatus maneja POST /admin/trips/{id}/status.
func (h *AdminHandler) UpdateTripStatus(w http.ResponseWriter, r *http.Request) {
	tripID, ok := parseID(w, r, "id")
	if !ok {
		return
	}
	var req updateTripStatusRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "body", Reason: "json invalido"})
		return
	}
	if err := validate.Default.Struct(req); err != nil {
		apperror.WriteJSONError(w, validate.ToAppError(err))
		return
	}
	if err := h.svc.UpdateTripStatus(r.Context(), tripID, req.Status); err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// generateTripRequest es el cuerpo de POST /admin/trips/generate.
type generateTripRequest struct {
	TemplateID  int64  `json:"template_id" validate:"required,gt=0"`
	ServiceDate string `json:"service_date" validate:"required"`
}

// GenerateTrip maneja POST /admin/trips/generate — dispara la generacion
// manual de una instancia de viaje para una fecha concreta.
func (h *AdminHandler) GenerateTrip(w http.ResponseWriter, r *http.Request) {
	var req generateTripRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "body", Reason: "json invalido"})
		return
	}
	if err := validate.Default.Struct(req); err != nil {
		apperror.WriteJSONError(w, validate.ToAppError(err))
		return
	}
	if err := h.svc.TriggerManualGeneration(r.Context(), req.TemplateID, req.ServiceDate); err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// ----------------------------------------------------------------------------
// Reportes (vistas)
// ----------------------------------------------------------------------------

// ConflictsReport maneja GET /admin/reports/conflicts.
func (h *AdminHandler) ConflictsReport(w http.ResponseWriter, r *http.Request) {
	conflicts, err := h.svc.GetScheduleConflicts(r.Context())
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	writeJSON(w, map[string]any{"items": orEmpty(conflicts, conflictSlice)})
}

// TimeMatrixReport maneja GET /admin/reports/time-matrix.
func (h *AdminHandler) TimeMatrixReport(w http.ResponseWriter, r *http.Request) {
	entries, err := h.svc.GetRouteTimeMatrix(r.Context())
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	writeJSON(w, map[string]any{"items": orEmpty(entries, matrixSlice)})
}

// SeatAvailabilityReport maneja GET /admin/reports/seat-availability?trip_id=.
func (h *AdminHandler) SeatAvailabilityReport(w http.ResponseWriter, r *http.Request) {
	tripID, err := strconv.ParseInt(r.URL.Query().Get("trip_id"), 10, 64)
	if err != nil || tripID < 1 {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "trip_id", Reason: "entero positivo requerido"})
		return
	}
	avail, err := h.svc.GetTripSeatAvailability(r.Context(), tripID)
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	writeJSON(w, map[string]any{"items": orEmpty(avail, seatAvailSlice)})
}

// ----------------------------------------------------------------------------
// Registro de rutas
// ----------------------------------------------------------------------------

// RegisterRoutes monta los endpoints del modulo admin bajo /admin/*.
// El grupo padre (router.go Phase 3) aplica jwtauth.Verifier+Authenticator;
// el guard de rol ADMIN vive en el servicio.
func (h *AdminHandler) RegisterRoutes(r chi.Router) {
	r.Route("/admin", func(r chi.Router) {
		// Maestros: stops
		r.Get("/stops", h.ListStops)
		r.Post("/stops", h.CreateStop)
		r.Put("/stops/{id}", h.UpdateStop)

		// Maestros: users
		r.Get("/users", h.ListUsers)
		r.Post("/users", h.CreateUser)
		r.Put("/users/{id}", h.UpdateUser)

		// Maestros: vehicles
		r.Get("/vehicles", h.ListVehicles)
		r.Post("/vehicles", h.CreateVehicle)
		r.Put("/vehicles/{id}", h.UpdateVehicle)

		// Maestros: routes + su sub-recurso route_stops
		r.Get("/routes", h.ListRoutes)
		r.Post("/routes", h.CreateRoute)
		r.Put("/routes/{id}", h.UpdateRoute)
		r.Get("/routes/{id}/stops", h.ListRouteStops)

		// route-stops como recurso top-level para crear/actualizar por id
		r.Post("/route-stops", h.CreateRouteStop)
		r.Put("/route-stops/{id}", h.UpdateRouteStop)

		// Maestros: templates
		r.Get("/templates", h.ListTemplates)
		r.Post("/templates", h.CreateTemplate)
		r.Put("/templates/{id}", h.UpdateTemplate)

		// Maestros: calendars
		r.Get("/calendars", h.ListCalendars)
		r.Post("/calendars", h.CreateCalendar)
		r.Put("/calendars/{id}", h.UpdateCalendar)

		// Operaciones de viajes
		r.Post("/trips/{id}/status", h.UpdateTripStatus)
		r.Post("/trips/generate", h.GenerateTrip)

		// Reportes
		r.Get("/reports/conflicts", h.ConflictsReport)
		r.Get("/reports/time-matrix", h.TimeMatrixReport)
		r.Get("/reports/seat-availability", h.SeatAvailabilityReport)
	})
}

// ----------------------------------------------------------------------------
// Helpers de slices vacios
// ----------------------------------------------------------------------------

// typeName es un id simbolico para el helper orEmpty.
type sliceKind int

const (
	stopSlice sliceKind = iota
	userSlice
	vehicleSlice
	routeSlice
	routeStopSlice
	templateSlice
	calendarSlice
	conflictSlice
	matrixSlice
	seatAvailSlice
)

// orEmpty devuelve el slice recibido o un slice vacio tipado si es nil.
// Previene que el JSON devuelva null en listas vacias (mejor UX para el
// cliente). Se usa un switch por tipo para no recurrir a generics ni a any.
func orEmpty(v any, kind sliceKind) any {
	switch kind {
	case stopSlice:
		if s, ok := v.([]Stop); ok && s != nil {
			return s
		}
		return []Stop{}
	case userSlice:
		if s, ok := v.([]User); ok && s != nil {
			return s
		}
		return []User{}
	case vehicleSlice:
		if s, ok := v.([]Vehicle); ok && s != nil {
			return s
		}
		return []Vehicle{}
	case routeSlice:
		if s, ok := v.([]Route); ok && s != nil {
			return s
		}
		return []Route{}
	case routeStopSlice:
		if s, ok := v.([]RouteStop); ok && s != nil {
			return s
		}
		return []RouteStop{}
	case templateSlice:
		if s, ok := v.([]Template); ok && s != nil {
			return s
		}
		return []Template{}
	case calendarSlice:
		if s, ok := v.([]Calendar); ok && s != nil {
			return s
		}
		return []Calendar{}
	case conflictSlice:
		if s, ok := v.([]Conflict); ok && s != nil {
			return s
		}
		return []Conflict{}
	case matrixSlice:
		if s, ok := v.([]MatrixEntry); ok && s != nil {
			return s
		}
		return []MatrixEntry{}
	case seatAvailSlice:
		if s, ok := v.([]SeatAvail); ok && s != nil {
			return s
		}
		return []SeatAvail{}
	}
	return v
}
