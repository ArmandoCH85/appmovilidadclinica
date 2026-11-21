package vehicles

import (
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"

	"github.com/go-chi/chi/v5"

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/apperror"
)

// Handler expone GET /api/vehicles/positions.
type Handler struct {
	svc *Service
}

// NewHandler construye el handler con su servicio inyectado.
func NewHandler(svc *Service) *Handler {
	return &Handler{svc: svc}
}

// RegisterRoutes monta las rutas del módulo bajo /api.
// El JWT lo exige el router (cualquier usuario autenticado: pasajero,
// conductor o admin). No hay chequeo de rol: solo devuelve posiciones de los
// vehículos configurados, sin credenciales ni ids internos de Traccar.
func (h *Handler) RegisterRoutes(r chi.Router) {
	r.Route("/vehicles", func(r chi.Router) {
		r.Get("/positions", h.LatestPositions)
	})
}

// LatestPositions maneja GET /api/vehicles/positions.
// Devuelve 200 con un array (nunca null) de posiciones conocidas.
// Si el servicio no está configurado o el servidor GPS falla, responde 500 con
// un mensaje genérico (el detalle queda en el log del servidor) y la app
// muestra el aviso conservando lo último bueno.
func (h *Handler) LatestPositions(w http.ResponseWriter, r *http.Request) {
	positions, err := h.svc.Latest(r.Context())
	if err != nil {
		if errors.Is(err, ErrNotConfigured) {
			slog.Error("vehicles: GPS no configurado (faltan TRACCAR_USER/TRACCAR_PASSWORD)")
		} else {
			slog.Error("vehicles: consultando el servidor GPS", "error", err)
		}
		apperror.WriteJSONError(w, apperror.InternalError{Err: err})
		return
	}

	if positions == nil {
		positions = []VehiclePosition{}
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	_ = json.NewEncoder(w).Encode(positions)
}
