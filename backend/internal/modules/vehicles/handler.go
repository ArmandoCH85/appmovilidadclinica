package vehicles

import (
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"

	"github.com/go-chi/chi/v5"

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/apperror"
	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/authctx"
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
// El JWT lo exige el router. Además, el servicio filtra por usuario: solo
// devuelve el vehículo de una reserva cuyo recorrido YA INICIÓ el conductor
// (seguridad: no se expone dónde está el bus antes de arrancar).
func (h *Handler) RegisterRoutes(r chi.Router) {
	r.Route("/vehicles", func(r chi.Router) {
		r.Get("/positions", h.LatestPositions)
	})
}

// LatestPositions maneja GET /api/vehicles/positions.
// Devuelve 200 con un array (nunca null): vacío cuando el conductor todavía no
// inició el recorrido de la reserva del pasajero.
func (h *Handler) LatestPositions(w http.ResponseWriter, r *http.Request) {
	userID, err := authctx.UserIDFromContext(r.Context())
	if err != nil {
		apperror.WriteJSONError(w, apperror.UnauthorizedError{Reason: "token JWT requerido o invalido"})
		return
	}

	positions, err := h.svc.LatestForUser(r.Context(), userID)
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
