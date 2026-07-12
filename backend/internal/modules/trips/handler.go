package trips

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/apperror"
	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/validate"
)

// TripsHandler expone los endpoints HTTP del modulo trips.
type TripsHandler struct {
	svc TripsService
}

// NewHandler construye el handler con su servicio inyectado.
func NewHandler(svc TripsService) *TripsHandler {
	return &TripsHandler{svc: svc}
}

// searchQuery valida los query params de GET /trips.
type searchQuery struct {
	ServiceDate       string `validate:"required"`
	Direction         string `validate:"required,oneof=IDA VUELTA"`
	OriginStopID      int64  `validate:"required,gt=0"`
	DestinationStopID int64  `validate:"required,gt=0"`
}

// Search maneja GET /trips?date=&direction=&origin=&destination=.
// Devuelve 200 con el listado (array vacio si no hay resultados).
func (h *TripsHandler) Search(w http.ResponseWriter, r *http.Request) {
	q := searchQuery{
		ServiceDate: r.URL.Query().Get("date"),
		Direction:   r.URL.Query().Get("direction"),
	}
	originID, err := strconv.ParseInt(r.URL.Query().Get("origin"), 10, 64)
	if err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "origin", Reason: "debe ser un entero positivo"})
		return
	}
	destID, err := strconv.ParseInt(r.URL.Query().Get("destination"), 10, 64)
	if err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "destination", Reason: "debe ser un entero positivo"})
		return
	}
	q.OriginStopID = originID
	q.DestinationStopID = destID

	if err := validate.Default.Struct(q); err != nil {
		apperror.WriteJSONError(w, validate.ToAppError(err))
		return
	}

	results, err := h.svc.Search(r.Context(), q.ServiceDate, q.Direction, q.OriginStopID, q.DestinationStopID)
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	if results == nil {
		results = []TripSearchResult{}
	}
	_ = json.NewEncoder(w).Encode(results)
}

// GetDetail maneja GET /trips/{id}. Devuelve la cabecera + el cronograma de
// paradas. 404 si el viaje no existe.
func (h *TripsHandler) GetDetail(w http.ResponseWriter, r *http.Request) {
	tripID, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "id", Reason: "debe ser un entero positivo"})
		return
	}

	detail, stops, err := h.svc.GetDetail(r.Context(), tripID)
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	_ = json.NewEncoder(w).Encode(map[string]any{
		"trip":  detail,
		"stops": stops,
	})
}

// ListSeats maneja GET /trips/{id}/seats?origin=&destination=. Los params
// origin/destination son trip_stop_time_id (no stop_id): el cliente los
// obtiene del cronograma devuelto por GetDetail.
func (h *TripsHandler) ListSeats(w http.ResponseWriter, r *http.Request) {
	tripID, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "id", Reason: "debe ser un entero positivo"})
		return
	}
	originID, err := strconv.ParseInt(r.URL.Query().Get("origin"), 10, 64)
	if err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "origin", Reason: "trip_stop_time_id requerido"})
		return
	}
	destID, err := strconv.ParseInt(r.URL.Query().Get("destination"), 10, 64)
	if err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "destination", Reason: "trip_stop_time_id requerido"})
		return
	}

	seats, err := h.svc.ListSeats(r.Context(), tripID, originID, destID)
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	if seats == nil {
		seats = []SeatResult{}
	}
	_ = json.NewEncoder(w).Encode(seats)
}

// RegisterRoutes monta los endpoints del modulo trips.
func (h *TripsHandler) RegisterRoutes(r chi.Router) {
	r.Get("/trips", h.Search)
	r.Get("/trips/{id}", h.GetDetail)
	r.Get("/trips/{id}/seats", h.ListSeats)
	// GET /stops — catalogo de paradas para cualquier JWT valido.
	// Vive en este handler porque el router lo registra como ruta
	// hermana de /trips (mismo prefijo /api), no como sub-recurso
	// de trips. La autorizacion por JWT la aplica el router; este
	// handler no exige rol especifico. Ver `desarrollo_pasajero.md`
	// §5.2 para el contrato exacto.
	r.Get("/stops", h.ListStops)
}

// ListStops maneja GET /stops. Devuelve un array JSON (nunca null)
// con el catalogo completo de paradas activas. Sin paginar.
func (h *TripsHandler) ListStops(w http.ResponseWriter, r *http.Request) {
	stops, err := h.svc.ListStops(r.Context())
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	if stops == nil {
		stops = []Stop{}
	}
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(stops)
}
