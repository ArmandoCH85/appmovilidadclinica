package booking

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/apperror"
	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/authctx"
	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/validate"
)

// BookingHandler expone los endpoints HTTP del modulo booking.
type BookingHandler struct {
	svc BookingService
}

// NewHandler construye el handler con su servicio inyectado.
func NewHandler(svc BookingService) *BookingHandler {
	return &BookingHandler{svc: svc}
}

// Confirm maneja POST /reservations. El worker_id se toma del JWT del
// contexto; el body trae los IDs del viaje, asiento y paradas de subida/bajada.
func (h *BookingHandler) Confirm(w http.ResponseWriter, r *http.Request) {
	var req ConfirmRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "body", Reason: "json invalido"})
		return
	}
	if err := validate.Default.Struct(req); err != nil {
		apperror.WriteJSONError(w, validate.ToAppError(err))
		return
	}

	resp, err := h.svc.Confirm(r.Context(), req)
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	w.WriteHeader(http.StatusCreated)
	_ = json.NewEncoder(w).Encode(resp)
}

// Cancel maneja POST /reservations/{id}/cancel. El actor_user_id se toma del
// JWT del contexto (el propio trabajador o un admin/driver con permiso).
func (h *BookingHandler) Cancel(w http.ResponseWriter, r *http.Request) {
	reservationID, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "id", Reason: "debe ser un entero positivo"})
		return
	}
	actorID, err := authctx.UserIDFromContext(r.Context())
	if err != nil {
		apperror.WriteJSONError(w, apperror.UnauthorizedError{Reason: "token sin identidad"})
		return
	}

	if err := h.svc.Cancel(r.Context(), reservationID, actorID); err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// verifyQRRequest es el cuerpo de POST /reservations/verify-qr.
type verifyQRRequest struct {
	Token string `json:"token" validate:"required"`
}

// VerifyQR maneja POST /reservations/verify-qr. El conductor escanea el QR
// del pasajero y envia el token crudo; el servicio lo hashea (SHA-256) y
// devuelve la reserva para que el conductor la confirme en /driver/.../board.
func (h *BookingHandler) VerifyQR(w http.ResponseWriter, r *http.Request) {
	var req verifyQRRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "body", Reason: "json invalido"})
		return
	}
	if err := validate.Default.Struct(req); err != nil {
		apperror.WriteJSONError(w, validate.ToAppError(err))
		return
	}

	res, err := h.svc.VerifyQR(r.Context(), req.Token)
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	_ = json.NewEncoder(w).Encode(res)
}

// RegisterRoutes monta los endpoints del modulo booking.
func (h *BookingHandler) RegisterRoutes(r chi.Router) {
	r.Post("/reservations", h.Confirm)
	r.Post("/reservations/{id}/cancel", h.Cancel)
	r.Post("/reservations/verify-qr", h.VerifyQR)
}
