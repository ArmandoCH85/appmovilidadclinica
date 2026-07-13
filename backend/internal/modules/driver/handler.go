package driver

import (
	"encoding/json"
	"net/http"
	"strconv"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/apperror"
	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/validate"
)

// DriverHandler expone los endpoints HTTP del modulo driver.
type DriverHandler struct {
	svc DriverService
}

// NewHandler construye el handler con su servicio inyectado.
func NewHandler(svc DriverService) *DriverHandler {
	return &DriverHandler{svc: svc}
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

// writeJSON escribe 200 con el cuerpo JSON.
func writeJSON(w http.ResponseWriter, body any) {
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(body)
}

// ListTrips maneja GET /driver/trips?date=YYYY-MM-DD. Si date falta se usa
// la fecha actual en la zona horaria del servidor (America/Lima en el DSN).
func (h *DriverHandler) ListTrips(w http.ResponseWriter, r *http.Request) {
	date := r.URL.Query().Get("date")
	if date == "" {
		date = time.Now().Format("2006-01-02")
	}
	trips, err := h.svc.ListTrips(r.Context(), date)
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	if trips == nil {
		trips = []DriverTrip{}
	}
	writeJSON(w, trips)
}

// ListPassengers maneja GET /driver/trips/{id}/passengers.
func (h *DriverHandler) ListPassengers(w http.ResponseWriter, r *http.Request) {
	tripID, ok := parseID(w, r, "id")
	if !ok {
		return
	}
	passengers, err := h.svc.ListPassengers(r.Context(), tripID)
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	if passengers == nil {
		passengers = []Passenger{}
	}
	writeJSON(w, passengers)
}

// ListStops maneja GET /driver/trips/{id}/stops.
func (h *DriverHandler) ListStops(w http.ResponseWriter, r *http.Request) {
	tripID, ok := parseID(w, r, "id")
	if !ok {
		return
	}
	stops, err := h.svc.ListTripStops(r.Context(), tripID)
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	if stops == nil {
		stops = []TripStop{}
	}
	writeJSON(w, stops)
}

// MarkArrivalStop maneja POST /driver/trip-stops/{id}/arrival — marca la
// llegada del conductor a la parada indicada por trip_stop_time_id.
func (h *DriverHandler) MarkArrivalStop(w http.ResponseWriter, r *http.Request) {
	tripStopTimeID, ok := parseID(w, r, "id")
	if !ok {
		return
	}
	if err := h.svc.MarkArrival(r.Context(), tripStopTimeID); err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// Board maneja POST /driver/reservations/{id}/board — marca el abordaje del
// pasajero.
func (h *DriverHandler) Board(w http.ResponseWriter, r *http.Request) {
	reservationID, ok := parseID(w, r, "id")
	if !ok {
		return
	}
	if err := h.svc.MarkBoarded(r.Context(), reservationID); err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// NoShow maneja POST /driver/reservations/{id}/no-show — marca al pasajero
// como no presentado.
func (h *DriverHandler) NoShow(w http.ResponseWriter, r *http.Request) {
	reservationID, ok := parseID(w, r, "id")
	if !ok {
		return
	}
	if err := h.svc.MarkNoShow(r.Context(), reservationID); err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// Alight maneja POST /driver/reservations/{id}/alight — marca la bajada del
// pasajero en su destino.
func (h *DriverHandler) Alight(w http.ResponseWriter, r *http.Request) {
	reservationID, ok := parseID(w, r, "id")
	if !ok {
		return
	}
	if err := h.svc.MarkAlighted(r.Context(), reservationID); err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// reportIncidentResponse es la respuesta de POST /driver/trips/{id}/incidents.
type reportIncidentResponse struct {
	ID int64 `json:"id"`
}

// ReportIncident maneja POST /driver/trips/{id}/incidents — registra una
// incidencia reportada por el conductor para el viaje indicado en el path.
// El trip_id del cuerpo se ignora a favor del path param para evitar
// inconsistencias; IncidentParams se completa con el id del path.
func (h *DriverHandler) ReportIncident(w http.ResponseWriter, r *http.Request) {
	tripID, ok := parseID(w, r, "id")
	if !ok {
		return
	}
	var req IncidentParams
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "body", Reason: "json invalido"})
		return
	}
	// El trip_id autoritativo es el del path; el del body se descarta.
	req.TripID = tripID
	if err := validate.Default.Struct(req); err != nil {
		apperror.WriteJSONError(w, validate.ToAppError(err))
		return
	}
	id, err := h.svc.ReportIncident(r.Context(), req)
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	w.WriteHeader(http.StatusCreated)
	writeJSON(w, reportIncidentResponse{ID: id})
}

// RegisterRoutes monta los endpoints del modulo driver bajo /driver/*.
// El grupo padre (router.go Phase 3) aplica jwtauth.Verifier+Authenticator;
// el guard de rol DRIVER y la validacion de asignacion viven en el servicio.
func (h *DriverHandler) RegisterRoutes(r chi.Router) {
	r.Route("/driver", func(r chi.Router) {
		r.Get("/trips", h.ListTrips)
		r.Get("/trips/{id}/passengers", h.ListPassengers)
		r.Get("/trips/{id}/stops", h.ListStops)
		r.Post("/trip-stops/{id}/arrival", h.MarkArrivalStop)
		r.Post("/reservations/{id}/board", h.Board)
		r.Post("/reservations/{id}/no-show", h.NoShow)
		r.Post("/reservations/{id}/alight", h.Alight)
		r.Post("/trips/{id}/incidents", h.ReportIncident)
	})
}
